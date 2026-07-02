package com.bushop

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Feature flags for gradual rollout and instant kill-switch.
 *
 * Usage:
 *   if (FeatureFlags.NEW_BUS_TIMELINE.isEnabled(this)) {
 *       showNewTimeline()
 *   }
 *
 * Toggle at runtime via the debug menu (long-press version in settings).
 * All flags default to false (dark by default). Enable gradually.
 *
 * Reads are synchronous (backed by an in-memory cache refreshed from DataStore).
 * Writes are fire-and-forget coroutines on Dispatchers.IO.
 */
enum class FeatureFlag(
    val key: String,
    val default: Boolean = false,
    val description: String = "",
) {
    /** Example: New bus timeline UI. Replace with real flags as needed. */
    NEW_BUS_TIMELINE(
        key = "ff_new_timeline",
        description = "Enable the redesigned bus arrival timeline",
    ),

    /** Example: Show experimental nearby stops v2. */
    NEARBY_STOPS_V2(
        key = "ff_nearby_v2",
        description = "Enable enhanced nearby stops with filters",
    ),

    /** Example: New drag-and-drop reorder. */
    PINNED_REORDER(
        key = "ff_pinned_reorder",
        description = "Enable pinned-stop reorder gestures",
    ),
    ;

    companion object {
        private const val PREFS_NAME = "feature_flags"

        private val Context.featureFlagsDataStore: DataStore<Preferences> by
            preferencesDataStore(name = PREFS_NAME)

        // Background scope for DataStore writes (never blocks the calling thread).
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // In-memory cache: [key] -> [override value] or absent if using default.
        // Updated reactively by collecting overridesFlow. Synchronous reads never touch DataStore.
        @Volatile
        private var overrideCache: Map<String, Boolean> = emptyMap()

        // Ensure the cache collection is started exactly once.
        @Volatile
        private var cacheInitialized = false

        /**
         * Start collecting overrides into the in-memory cache.
         * Safe to call multiple times — only the first call starts the collection.
         */
        private fun ensureCache(context: Context) {
            if (cacheInitialized) return
            cacheInitialized = true
            // One-time migration from old SharedPreferences to DataStore
            migrateFromSharedPreferences(context)
            ioScope.launch {
                overridesFlow(context).collect { overrides ->
                    overrideCache = overrides
                }
            }
        }

        /** One-time migration from old SharedPreferences to DataStore Preferences. */
        private fun migrateFromSharedPreferences(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.all.isEmpty()) return
            ioScope.launch {
                context.featureFlagsDataStore.edit { store ->
                    entries.forEach { flag ->
                        if (prefs.contains(flag.key)) {
                            store[booleanPreferencesKey(flag.key)] =
                                prefs.getBoolean(flag.key, flag.default)
                        }
                    }
                }
            }
            prefs.edit().clear().apply()
        }

        /**
         * Returns a map of flag-key -> boolean for every flag that has an explicit
         * override set. Synchronous — backed by the in-memory cache.
         */
        fun getOverrides(context: Context): Map<String, Boolean> {
            ensureCache(context)
            return overrideCache
        }

        /**
         * Persist a runtime override for the given flag [key].
         * Writes are fire-and-forget on Dispatchers.IO — never blocks.
         */
        fun setOverride(
            context: Context,
            key: String,
            enabled: Boolean,
        ) {
            ensureCache(context)
            // Optimistically update cache
            overrideCache = overrideCache + (key to enabled)
            ioScope.launch {
                context.featureFlagsDataStore.edit { prefs ->
                    prefs[booleanPreferencesKey(key)] = enabled
                }
            }
        }

        /**
         * Remove any persisted override for [key], reverting to the flag's default.
         * Writes are fire-and-forget — never blocks.
         */
        fun clearOverride(
            context: Context,
            key: String,
        ) {
            ensureCache(context)
            overrideCache = overrideCache - key
            ioScope.launch {
                context.featureFlagsDataStore.edit { prefs ->
                    prefs.remove(booleanPreferencesKey(key))
                }
            }
        }

        /**
         * Remove ALL overrides, reverting every flag to its default.
         * Writes are fire-and-forget — never blocks.
         */
        fun resetAll(context: Context) {
            ensureCache(context)
            overrideCache = emptyMap()
            ioScope.launch {
                context.featureFlagsDataStore.edit { prefs ->
                    entries.forEach { flag ->
                        prefs.remove(booleanPreferencesKey(flag.key))
                    }
                }
            }
        }

        /**
         * Reactive [Flow] that emits the current overrides map every time
         * DataStore changes. Useful for observing flag toggles reactively.
         */
        fun overridesFlow(context: Context): Flow<Map<String, Boolean>> = context.featureFlagsDataStore.data.map { prefs ->
            entries.mapNotNull { flag ->
                val value = prefs[booleanPreferencesKey(flag.key)]
                if (value != null) flag.key to value else null
            }.toMap()
        }
    }

    /**
     * Check whether this flag is currently enabled.
     * Returns the persisted override if one exists, otherwise the [default].
     * Synchronous — backed by the in-memory cache, never blocks.
     */
    fun isEnabled(context: Context): Boolean {
        ensureCache(context)
        return overrideCache[key] ?: default
    }
}
