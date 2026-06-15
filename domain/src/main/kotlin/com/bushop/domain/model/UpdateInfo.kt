package com.bushop.domain.model

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val hasUpdate: Boolean,
)
