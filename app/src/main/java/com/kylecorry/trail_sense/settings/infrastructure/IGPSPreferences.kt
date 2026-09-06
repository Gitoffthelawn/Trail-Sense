package com.kylecorry.trail_sense.settings.infrastructure

import com.kylecorry.sol.units.Coordinate

interface IGPSPreferences {
    var useAutoLocation: Boolean
    val requiresSatellites: Boolean
    val filterLocationReadings: Boolean
    val useFilteredGPS: Boolean
    val useNMEA: Boolean
    var locationOverride: Coordinate
    val hasLocationOverride: Boolean
}
