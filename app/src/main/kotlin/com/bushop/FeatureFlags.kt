package com.bushop

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ┌─ FeatureFlag ────────────────────────────────────┐
 * │  app/ layer · Runtime toggles                    │
 * │                                                   │
 * │  NEW_BUS_TIMELINE ─→ new arrival timeline UI      │
 * │  NEARBY_STOPS_V2  ─→ enhanced nearby stops       │
 * │  PINNED_REORDER   ─→ pinned reorder gestures     │
 * │                                                   │
 * │  isEnabled(ctx) ─→ read DataStore Preferences    │
 * │  setOverride()  ─→ toggle at runtime             │
 * │  resetAll()     ─→ clear all overrides           │
 * │  Backed by DataStore Preferences, dark by default │
 * └───────────────────────────────────────────────────┘
 */

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

        private val migrationNeeded = AtomicBoolean(true)

        /**
         * One-time migration from old SharedPreferences to DataStore Preferences.
         * Reads any existing overrides from SharedPreferences, writes them into
         * DataStore, then clears the legacy SharedPreferences file.
         */
        private fun migrateFromSharedPreferences(context: Context) {
            if (!migrationNeeded.getAndSet(false)) return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.all.isEmpty()) return
            runBlocking(Dispatchers.IO) {
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
         * Returns a map of flag-key → boolean for every flag that has an explicit
         * override set (i.e. excludes flags still using their default value).
         * Synchronous — safe to call from Compose.
         */
        fun getOverrides(context: Context): Map<String, Boolean> {
            migrateFromSharedPreferences(context)
            return runBlocking(Dispatchers.IO) {
                context.featureFlagsDataStore.data.first().let { prefs ->
                    entries.mapNotNull { flag ->
                        val value = prefs[booleanPreferencesKey(flag.key)]
                        if (value != null) flag.key to value else null
                    }.toMap()
                }
            }
        }

        /**
         * Persist a runtime override for the given flag [key].
         * Synchronous — safe to call from Compose.
         */
        fun setOverride(
            context: Context,
            key: String,
            enabled: Boolean,
        ) {
            migrateFromSharedPreferences(context)
            runBlocking(Dispatchers.IO) {
                context.featureFlagsDataStore.edit { prefs ->
                    prefs[booleanPreferencesKey(key)] = enabled
                }
            }
        }

        /**
         * Remove any persisted override for [key], reverting to the flag's default.
         * Synchronous — safe to call from Compose.
         */
        fun clearOverride(
            context: Context,
            key: String,
        ) {
            migrateFromSharedPreferences(context)
            runBlocking(Dispatchers.IO) {
                context.featureFlagsDataStore.edit { prefs ->
                    prefs.remove(booleanPreferencesKey(key))
                }
            }
        }

        /**
         * Remove ALL overrides, reverting every flag to its default.
         * Synchronous — safe to call from Compose.
         */
        fun resetAll(context: Context) {
            migrateFromSharedPreferences(context)
            runBlocking(Dispatchers.IO) {
                context.featureFlagsDataStore.edit { prefs ->
                    entries.forEach { flag ->
                        prefs.remove(booleanPreferencesKey(flag.key))
                    }
                }
            }
        }

        /**
         * Reactive [Flow] that emits the current overrides map every time
         * DataStore changes. Useful for observing flag toggles reactively
         * (e.g. in ViewModels).
         */
        fun overridesFlow(context: Context): Flow<Map<String, Boolean>> {
            migrateFromSharedPreferences(context)
            return context.featureFlagsDataStore.data.map { prefs ->
                entries.mapNotNull { flag ->
                    val value = prefs[booleanPreferencesKey(flag.key)]
                    if (value != null) flag.key to value else null
                }.toMap()
            }
        }
    }

    /**
     * Check whether this flag is currently enabled.
     * Returns the persisted override if one exists, otherwise the [default].
     * Synchronous — safe to call from Compose.
     */
    fun isEnabled(context: Context): Boolean {
        migrateFromSharedPreferences(context)
        return runBlocking(Dispatchers.IO) {
            context.featureFlagsDataStore.data.first()
                .let { prefs -> prefs[booleanPreferencesKey(key)] ?: default }
        }
    }
}
