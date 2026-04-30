package com.kylecorry.trail_sense.plugins.domain

data class PluginResourceServiceDetails(
    val packageId: String,
    val name: String,
    val version: String?,
    val features: PluginResourceServiceFeatures
)
