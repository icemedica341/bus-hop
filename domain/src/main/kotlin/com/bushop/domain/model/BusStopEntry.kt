package com.bushop.domain.model

data class BusStopEntry(
    val code: String,
    val name: String,
    val road: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
) {
    val displayName: String get() = if (name.isNotBlank()) "$name, $road" else code
}
