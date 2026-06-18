package com.bushop

import android.content.Context
import android.content.SharedPreferences

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

        fun getOverrides(context: Context): Map<String, Boolean> {
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return entries
                .mapNotNull { flag ->
                    if (prefs.contains(flag.key)) {
                        flag.key to prefs.getBoolean(flag.key, flag.default)
                    } else {
                        null
                    }
                }.toMap()
        }

        fun setOverride(
            context: Context,
            key: String,
            enabled: Boolean,
        ) {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key, enabled)
                .apply()
        }

        fun clearOverride(
            context: Context,
            key: String,
        ) {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(key)
                .apply()
        }

        fun resetAll(context: Context) {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(key, default)
    }
}
