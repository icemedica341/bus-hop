package com.bushop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val timeFormatter =
    DateTimeFormatter
        .ofPattern("HH:mm")

internal fun formatLastUpdated(timestamp: Long): String {
    val zdt =
        ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault(),
        )
    return timeFormatter.format(zdt)
}

@Composable
internal fun ApiStatusBanner(
    status: ApiStatus,
    onDismiss: () -> Unit,
) {
    val visible = status != ApiStatus.Healthy
    val bgColor: Color
    val textColor: Color
    val message: String
    val showDismiss: Boolean
    when (status) {
        ApiStatus.Healthy -> {
            bgColor = MaterialTheme.colorScheme.surface
            textColor = MaterialTheme.colorScheme.onSurface
            message = ""
            showDismiss = false
        }

        ApiStatus.Degraded -> {
            bgColor = MaterialTheme.colorScheme.tertiaryContainer
            textColor = MaterialTheme.colorScheme.onTertiaryContainer
            message = "Bus arrival data may be delayed"
            showDismiss = false
        }

        ApiStatus.Down -> {
            bgColor = MaterialTheme.colorScheme.errorContainer
            textColor = MaterialTheme.colorScheme.onErrorContainer
            message = "Bus arrival API is under maintenance. Some data may be unavailable."
            showDismiss = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bgColor),
            shape = RoundedCornerShape(8.dp),
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    modifier = Modifier.weight(1f),
                )
                if (showDismiss) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            tint = textColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
