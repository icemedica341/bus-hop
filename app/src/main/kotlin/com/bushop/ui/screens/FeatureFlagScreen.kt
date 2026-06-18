package com.bushop.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bushop.FeatureFlag

/**
 * Debug dialog to toggle feature flags at runtime.
 * Access: long-press the version label in Settings/About.
 *
 * Toggled flags persist to SharedPreferences. Reset All clears overrides.
 */
@Composable
fun FeatureFlagDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var overrides by remember { mutableStateOf(FeatureFlag.getOverrides(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Feature Flags", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column {
                Text(
                    "Toggle flags to enable/disable in-progress features. " +
                        "Changes apply immediately. Reset removes all overrides.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FeatureFlag.entries.forEach { flag ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(flag.name, style = MaterialTheme.typography.bodyMedium)
                            if (flag.description.isNotBlank()) {
                                Text(
                                    flag.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = overrides[flag.key] ?: flag.default,
                            onCheckedChange = { enabled ->
                                FeatureFlag.setOverride(context, flag.key, enabled)
                                overrides = FeatureFlag.getOverrides(context)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                FeatureFlag.resetAll(context)
                overrides = emptyMap()
            }) {
                Text("Reset All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}
