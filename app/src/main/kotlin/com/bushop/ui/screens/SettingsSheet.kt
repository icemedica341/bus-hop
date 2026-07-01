package com.bushop.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bushop.BuildConfig
import com.bushop.domain.model.ColorSchemeOption
import com.bushop.domain.model.ThemeMode
import com.bushop.domain.model.UpdateInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingsSheet(
    currentTheme: ThemeMode,
    currentInterval: Int,
    currentColorScheme: ColorSchemeOption,
    onThemeChange: (ThemeMode) -> Unit,
    onColorSchemeChange: (ColorSchemeOption) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onCheckUpdate: () -> Unit,
    isCheckingUpdate: Boolean,
    isDownloadingUpdate: Boolean,
    updateInfo: UpdateInfo?,
    onDownloadUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onOpenFeatureFlags: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
        text = {
            Column {
                Text("Theme", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                ThemeMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onThemeChange(mode) },
                    ) {
                        RadioButton(selected = currentTheme == mode, onClick = { onThemeChange(mode) })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text =
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Auto Refresh",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val intervals = listOf(0 to "Off", 30 to "30s", 60 to "1m", 120 to "2m", 300 to "5m")
                intervals.forEach { (seconds, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onIntervalChange(seconds) },
                    ) {
                        RadioButton(selected = currentInterval == seconds, onClick = { onIntervalChange(seconds) })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(label)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Colour Scheme",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                ColorSchemeOption.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onColorSchemeChange(option) },
                    ) {
                        RadioButton(selected = currentColorScheme == option, onClick = { onColorSchemeChange(option) })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(option.displayName)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Icon Legend",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(com.bushop.R.drawable.ic_accessibility),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Wheelchair accessible bus", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(com.bushop.R.drawable.ic_directions_bus),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Operator badge logo", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Updates", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onOpenFeatureFlags,
                    ),
                )
                TextButton(onClick = onCheckUpdate, enabled = !isCheckingUpdate) {
                    Text(if (isCheckingUpdate) "Checking…" else "Check for updates")
                }
                if (updateInfo != null) {
                    Text(
                        "Update v${updateInfo.latestVersion} available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = onDownloadUpdate, enabled = !isDownloadingUpdate) {
                        Text(if (isDownloadingUpdate) "Downloading…" else "Download & Install")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
