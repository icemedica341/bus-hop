package com.bushop.data.local

/**
 * ┌─ BusStopIndex ───────────────────────────────────┐
 * │  data/ layer · In-memory search engine           │
 * │                                                   │
 * │  TokenTrie O(k) prefix search                    │
 * │  Levenshtein fuzzy matching                      │
 * │  5,201 Singapore bus stops indexed from JSON     │
 * │  findNearby(lat, lng, radius) ─→ geo search      │
 * │  Loaded async on first access                    │
 * └───────────────────────────────────────────────────┘
 */

import android.content.Context
import android.util.Log
import com.bushop.data.api.GsonProvider
import com.bushop.domain.model.BusStopEntry
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.random.Random

/**
 * In-memory index of all Singapore bus stops.
 *
 * Search uses a 3-stage filter cascade for maximum speed:
 *   Stage 1 — Inverted index + Trie (name + road tokens) → O(1) exact lookup + O(k) prefix lookup
 *   Stage 2 — Length filter + character-frequency pre-check → cheap skip before Levenshtein
 *   Stage 3 — Full token matching with typo-tolerant Levenshtein
 *
 * Abbreviations expanded: bt→bukit, blk→block, int→interchange, amk→ang mo kio, cck→choa chu kang, etc.
 */

class BusStopIndex(
    private val context: Context,
) {
    companion object {
        private const val MAX_RESULTS = 20

        // ── Scoring constants ──
        private object Scoring {
            /** Token matches query exactly (e.g. "jurong" == "jurong"). */
            const val EXACT_MATCH = 1000

            /** Token starts with the query text. */
            const val PREFIX_MATCH = 800

            /** Query text starts with the token (shorter token fully embedded). */
            const val QUERY_PREFIX_OF_TOKEN = 600

            /** Token contains the query text somewhere. */
            const val CONTAINS = 400

            /** Query text contains the token. */
            const val TOKEN_INSIDE_QUERY = 300

            /** Fuzzy Levenshtein match for raw query against token. */
            const val FUZZY_RAW = 250

            /** Fuzzy Levenshtein match for expanded query against token. */
            const val FUZZY_EXPANDED = 220

            /** Code field matches the search query exactly. */
            const val CODE_EXACT = 50

            /** Code field starts with the search query. */
            const val CODE_PREFIX = 35

            /** Code field contains the search query. */
            const val CODE_CONTAINS = 10

            /** Bonus when all query tokens match the stop name. */
            const val ALL_TOKENS_BONUS = 500

            /** Road match scores are divided by this to deprioritize vs name matches. */
            const val ROAD_SCORE_DIVISOR = 3
        }
    }

    private val gson = GsonProvider.gson
    private val abbreviationMap =
        mapOf(
            "bt" to "bukit",
            "bkt" to "bukit",
            "blk" to "block",
            "int" to "interchange",
            "opp" to "opposite",
            "ave" to "avenue",
            "rd" to "road",
            "dr" to "drive",
            "st" to "street",
            "ctr" to "centre",
            "ctrl" to "central",
            "jln" to "jalan",
            "lor" to "lorong",
            "nth" to "north",
            "sth" to "south",
            "wst" to "west",
            "tpy" to "toa payoh",
            "amk" to "ang mo kio",
            "cck" to "choa chu kang",
            "sbw" to "sembawang",
            "pgl" to "punggol",
            "sgo" to "sengkang",
            "bp" to "bukit panjang",
            "pjs" to "panjang",
        )

    @Volatile
    private var stops: Map<String, BusStopEntry> = emptyMap()

    private data class IndexedStop(
        val entry: BusStopEntry,
        val nameTokens: List<String>,
        val expandedNameTokens: List<String>,
        val roadTokens: List<String>,
        val expandedRoadTokens: List<String>,
        val codeLower: String,
    )

    @Volatile
    private var indexedStops: List<IndexedStop> = emptyList()

    /** Inverted index: name token → list of stop indices containing that token in name. */
    @Volatile
    private var nameTokenIndex: Map<String, List<Int>> = emptyMap()

    /** Inverted index: road token → stop indices containing that token in road. */
    @Volatile
    private var roadTokenIndex: Map<String, List<Int>> = emptyMap()

    /** Trie for O(k) prefix matching on name tokens. */
    @Volatile
    private var nameTrie: TokenTrie = TokenTrie()

    /** Trie for O(k) prefix matching on road tokens. */
    @Volatile
    private var roadTrie: TokenTrie = TokenTrie()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    fun setTestData(testStops: List<BusStopEntry>) {
        stops = testStops.associateBy { it.code }
        indexedStops = testStops.map { it.toIndexed() }
        nameTokenIndex = buildTokenIndex { it.nameTokens }
        roadTokenIndex = buildTokenIndex { it.roadTokens }
        nameTrie = buildTrie { it.nameTokens }
        roadTrie = buildTrie { it.roadTokens }
        _isReady.value = true
    }

    private fun expandAbbrev(token: String): String = abbreviationMap[token] ?: token

    private fun tokenise(text: String): List<String> = text.lowercase().split(Regex("""[\s,./()&'\-]+""")).filter { it.isNotEmpty() }

    private fun BusStopEntry.toIndexed(): IndexedStop {
        val nTok = tokenise(name)
        val rTok = if (road.isNotBlank()) tokenise(road) else emptyList()
        return IndexedStop(
            entry = this,
            nameTokens = nTok,
            expandedNameTokens = nTok.map { expandAbbrev(it) },
            roadTokens = rTok,
            expandedRoadTokens = rTok.map { expandAbbrev(it) },
            codeLower = code.lowercase(),
        )
    }

    private fun buildTokenIndex(tokenSelector: (IndexedStop) -> List<String>): Map<String, List<Int>> {
        val map = mutableMapOf<String, MutableList<Int>>()
        for ((i, idx) in indexedStops.withIndex()) {
            for (t in tokenSelector(idx)) {
                map.getOrPut(t) { mutableListOf() }.add(i)
            }
        }
        return map
    }

    /** Build a Trie for O(k) prefix matching from the same data as the inverted index. */
    private fun buildTrie(tokenSelector: (IndexedStop) -> List<String>): TokenTrie {
        val trie = TokenTrie()
        for ((i, idx) in indexedStops.withIndex()) {
            for (t in tokenSelector(idx)) {
                if (t.length >= 2) trie.insert(t, i)
            }
        }
        return trie
    }

    suspend fun load() {
        withContext(Dispatchers.IO) {
            val json =
                try {
                    context.assets
                        .open("bus_stops.json")
                        .bufferedReader()
                        .use { it.readText() }
                } catch (e: Exception) {
                    "{}"
                }
            val raw: Map<String, List<Any>> =
                try {
                    val mapType =
                        TypeToken
                            .getParameterized(
                                Map::class.java,
                                String::class.java,
                                TypeToken.getParameterized(List::class.java, Any::class.java).type,
                            ).type
                    gson.fromJson(json, mapType) ?: emptyMap()
                } catch (e: Exception) {
                    Log.w("BusStopIndex", "Failed to parse bus_stops.json", e)
                    emptyMap()
                }
            val parsed =
                raw.mapNotNull { (code, data) ->
                    if (data.size >= 3) {
                        val name = data[2].toString().trim()
                        val road = data.getOrNull(3)?.toString()?.trim() ?: ""
                        val lat = data.getOrNull(1) as? Double
                        val lng = data.getOrNull(0) as? Double
                        if (name.isNotBlank()) BusStopEntry(code, name, road, lat, lng) else null
                    } else {
                        null
                    }
                }
            stops = parsed.associateBy { it.code }
            indexedStops = parsed.map { it.toIndexed() }
            nameTokenIndex = buildTokenIndex { it.nameTokens }
            roadTokenIndex = buildTokenIndex { it.roadTokens }
            nameTrie = buildTrie { it.nameTokens }
            roadTrie = buildTrie { it.roadTokens }
        }
        _isReady.value = true
    }

    // ── Stage 2 filters (pre-Levenshtein) ──

    /** Skip levenshtein when string lengths differ by more than the limit. */
    private fun lengthFilter(
        a: String,
        b: String,
        limit: Int,
    ): Boolean = kotlin.math.abs(a.length - b.length) <= limit

    /** Quick character-frequency pre-check: at least half the query chars must appear in target. */
    private fun charFilter(
        query: String,
        target: String,
    ): Boolean {
        if (query.length < 3) return true // too short to filter reliably
        val qChars = query.toCharArray().distinct()
        val tLower = target.lowercase()
        val matchCount = qChars.count { tLower.contains(it) }
        return matchCount >= (qChars.size + 1) / 2 // majority must match
    }

    // ── Levenshtein with 3 optimisations: row-min exit, length filter, char filter ──

    private fun levenshtein(
        s1: String,
        s2: String,
        limit: Int = 2,
    ): Int {
        // Stage 2a: length filter
        if (!lengthFilter(s1, s2, limit)) return limit + 1
        if (s1 == s2) return 0
        // Stage 2b: character frequency quick-check
        if (!charFilter(s1, s2)) return limit + 1

        // Stage 3: rolling 1D array DP with row-min early exit
        val prev = IntArray(s2.length + 1) { it }
        val curr = IntArray(s2.length + 1)
        for (i in 1..s1.length) {
            curr[0] = i
            var rowMin = Int.MAX_VALUE
            val si = s1[i - 1]
            for (j in 1..s2.length) {
                val cost = if (si == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost,
                )
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > limit) return limit + 1
            // Swap rows: copy curr into prev for next iteration
            System.arraycopy(curr, 0, prev, 0, s2.length + 1)
        }
        return prev[s2.length]
    }

    // ── Query preparation ──

    private data class QueryToken(
        val raw: String,
        val expanded: String,
        val expandedSubs: List<String>,
    )

    private fun prepareQueryTokens(q: String): List<QueryToken> = q.split(Regex("""\s+""")).filter { it.isNotEmpty() }.map { t ->
        val exp = expandAbbrev(t)
        QueryToken(t, exp, if (exp.contains(' ')) tokenise(exp) else listOf(exp))
    }

    // ── Token matching ──

    private fun matchToken(
        qt: QueryToken,
        rawTokens: List<String>,
        expandedTokens: List<String>,
    ): Int {
        if (qt.raw in rawTokens || qt.expanded in expandedTokens) return Scoring.EXACT_MATCH

        // Iterate both lists separately to avoid allocating a merged list
        for (t in rawTokens) {
            val r = matchTokenScore(qt, t)
            if (r != 0) return r
        }
        for (t in expandedTokens) {
            val r = matchTokenScore(qt, t)
            if (r != 0) return r
        }

        // Multi-word abbreviation: match any expansion sub-token
        if (qt.expandedSubs.size > 1) {
            for (sub in qt.expandedSubs) {
                if (sub.length < 2) continue
                for (t in rawTokens) {
                    if (t.length >= 2 && (t == sub || t.startsWith(sub) || sub.startsWith(t))) return Scoring.CONTAINS
                }
                for (t in expandedTokens) {
                    if (t.length >= 2 && (t == sub || t.startsWith(sub) || sub.startsWith(t))) return Scoring.CONTAINS
                }
            }
        }

        // Fuzzy: Levenshtein with optimised pre-checks
        for (t in rawTokens) {
            val r = matchTokenFuzzy(qt, t)
            if (r != 0) return r
        }
        for (t in expandedTokens) {
            val r = matchTokenFuzzy(qt, t)
            if (r != 0) return r
        }

        return 0
    }

    /** Score a single token against the query — returns 0 if no match. */
    private fun matchTokenScore(
        qt: QueryToken,
        t: String,
    ): Int = when {
        t.startsWith(qt.raw) || t.startsWith(qt.expanded) -> Scoring.PREFIX_MATCH
        qt.raw.startsWith(t) && t.length >= 2 -> Scoring.QUERY_PREFIX_OF_TOKEN
        t.contains(qt.raw) || t.contains(qt.expanded) -> Scoring.CONTAINS
        qt.raw.contains(t) && t.length >= 2 -> Scoring.TOKEN_INSIDE_QUERY
        else -> 0
    }

    /** Fuzzy (Levenshtein) match for a single token — returns 0 if no match. */
    private fun matchTokenFuzzy(
        qt: QueryToken,
        t: String,
    ): Int {
        val limit = if (t.length >= 6) 2 else 1
        if (qt.raw.length >= 3 && levenshtein(t, qt.raw, limit) <= limit) return Scoring.FUZZY_RAW
        if (qt.expanded.length >= 3 && levenshtein(t, qt.expanded, limit) <= limit) return Scoring.FUZZY_EXPANDED
        return 0
    }

    /** Collect candidate stop indices from both name and road inverted indexes. */
    private fun collectCandidates(qt: QueryToken): Set<Int> {
        val set = mutableSetOf<Int>()

        fun addFromIndex(
            index: Map<String, List<Int>>,
            key: String,
        ) {
            index[key]?.let { set.addAll(it) }
        }

        // Exact token matches
        addFromIndex(nameTokenIndex, qt.raw)
        addFromIndex(roadTokenIndex, qt.raw)
        if (qt.expanded != qt.raw) {
            addFromIndex(nameTokenIndex, qt.expanded)
            addFromIndex(roadTokenIndex, qt.expanded)
        }

        // Prefix matches via Trie (O(k) per query instead of O(N) over all tokens)
        set.addAll(nameTrie.findByPrefix(qt.raw))
        set.addAll(roadTrie.findByPrefix(qt.raw))
        if (qt.expanded != qt.raw) {
            set.addAll(nameTrie.findByPrefix(qt.expanded))
            set.addAll(roadTrie.findByPrefix(qt.expanded))
        }
        // Also match tokens that are prefixes of the query (e.g., query "jurong" matches token "jur")
        set.addAll(nameTrie.findPrefixesOf(qt.raw))
        set.addAll(roadTrie.findPrefixesOf(qt.raw))
        if (qt.expanded != qt.raw) {
            set.addAll(nameTrie.findPrefixesOf(qt.expanded))
            set.addAll(roadTrie.findPrefixesOf(qt.expanded))
        }

        return set
    }

    private fun qtInName(
        qt: QueryToken,
        nameTokens: List<String>,
    ): Boolean {
        for (t in nameTokens) {
            if (t.startsWith(qt.raw) ||
                t.contains(qt.raw) ||
                t.startsWith(qt.expanded) ||
                t.contains(qt.expanded) ||
                (qt.raw.contains(t) && t.length >= 2)
            ) {
                return true
            }
        }
        for (sub in qt.expandedSubs) {
            if (sub.length < 2) continue
            for (t in nameTokens) {
                if (t.length >= 2 && (t.startsWith(sub) || sub.startsWith(t))) return true
            }
        }
        return false
    }

    // ── Main search ──

    fun search(query: String): List<BusStopEntry> {
        val q = query.trim().lowercase()
        if (q.length < 1 || stops.isEmpty()) return emptyList()

        val queryTokens = prepareQueryTokens(q)

        // Fast path: single exact code match
        if (queryTokens.size == 1) {
            val exact = stops[queryTokens[0].raw.uppercase()]
            if (exact != null) return listOf(exact)
        }

        // Stage 1: collect candidates from name + road inverted indexes
        val candidateSet = mutableSetOf<Int>()
        for (qt in queryTokens) {
            candidateSet.addAll(collectCandidates(qt))
        }

        // Score candidates (or all stops if no index hits)
        val results = mutableListOf<Pair<BusStopEntry, Int>>()
        val iterable = if (candidateSet.isNotEmpty()) candidateSet.map { indexedStops[it] } else indexedStops

        for (idx in iterable) {
            var score = 0
            var matchedAny = false

            for (qt in queryTokens) {
                var best = matchToken(qt, idx.nameTokens, idx.expandedNameTokens)

                if (best == 0 && idx.roadTokens.isNotEmpty()) {
                    val rs = matchToken(qt, idx.roadTokens, idx.expandedRoadTokens)
                    if (rs > 0) best = rs / Scoring.ROAD_SCORE_DIVISOR
                }

                if (best == 0 && qt.raw.length >= 2) {
                    best =
                        when {
                            idx.codeLower == qt.raw -> Scoring.CODE_EXACT
                            idx.codeLower.startsWith(qt.raw) -> Scoring.CODE_PREFIX
                            idx.codeLower.contains(qt.raw) -> Scoring.CODE_CONTAINS
                            else -> 0
                        }
                }

                if (best > 0) {
                    matchedAny = true
                    score += best
                }
            }

            if (!matchedAny) continue

            // All-tokens-match bonus (only for name matches, not road-only)
            if (queryTokens.all { qtInName(it, idx.nameTokens) }) score += Scoring.ALL_TOKENS_BONUS

            results.add(idx.entry to score)
        }

        results.sortByDescending { it.second }
        return results.take(MAX_RESULTS).map { it.first }
    }

    fun findByCode(code: String): BusStopEntry? = stops[code]

    /** Returns a single random entry without copying the entire collection. */
    fun randomEntry(): BusStopEntry? {
        if (stops.isEmpty()) return null
        val keys = stops.keys
        return stops[keys.elementAt(Random.nextInt(keys.size))]
    }

    fun allEntries(): List<BusStopEntry> = stops.values.toList()

    private fun haversineKm(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double,
    ): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a =
            kotlin.math.sin(dLat / 2).pow(2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLng / 2).pow(2)
        return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    fun findNearby(
        lat: Double,
        lng: Double,
        radiusKm: Double = 0.5,
    ): List<BusStopEntry> {
        data class WithDist(
            val entry: BusStopEntry,
            val dist: Double,
        )
        return stops.values
            .mapNotNull { entry ->
                entry.lat?.let { elat ->
                    entry.lng?.let { elng ->
                        val dist = haversineKm(lat, lng, elat, elng)
                        if (dist <= radiusKm) WithDist(entry, dist) else null
                    }
                }
            }.sortedBy { it.dist }
            .take(MAX_RESULTS)
            .map { it.entry }
    }
}
