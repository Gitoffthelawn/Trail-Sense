package com.kylecorry.trail_sense.settings.infrastructure

import android.content.Context
import com.kylecorry.andromeda.preferences.BooleanPreference
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.trail_sense.R

class GPSPreferences(context: Context) : PreferenceRepo(context), IGPSPreferences {

    override var useAutoLocation by BooleanPreference(
        cache,
        getString(R.string.pref_auto_location),
        true
    )

    override val requiresSatellites by BooleanPreference(
        cache,
        getString(R.string.pref_require_satellites),
        true
    )

    override val filterLocationReadings by BooleanPreference(
        cache,
        getString(R.string.pref_filter_location_readings),
        true
    )

    override val useFilteredGPS by BooleanPreference(
        cache,
        getString(R.string.pref_use_filtered_gps),
        false
    )

    override val useNMEA by BooleanPreference(
        cache,
        getString(R.string.pref_nmea_altitude),
        false
    )

    override var locationOverride: Coordinate
        get() {
            val latStr = cache.getString(getString(R.string.pref_latitude_override)) ?: "0.0"
            val lngStr = cache.getString(getString(R.string.pref_longitude_override)) ?: "0.0"

            val lat = latStr.toDoubleOrNull() ?: 0.0
            val lng = lngStr.toDoubleOrNull() ?: 0.0

            return Coordinate(lat, lng)
        }
        set(value) {
            cache.putString(getString(R.string.pref_latitude_override), value.latitude.toString())
            cache.putString(getString(R.string.pref_longitude_override), value.longitude.toString())
        }

    override val hasLocationOverride: Boolean
        get() = cache.contains(getString(R.string.pref_latitude_override)) &&
                cache.contains(getString(R.string.pref_longitude_override))
}
