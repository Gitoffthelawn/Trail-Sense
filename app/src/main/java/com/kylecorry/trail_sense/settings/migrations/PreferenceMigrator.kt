package com.kylecorry.trail_sense.settings.migrations

import android.content.Context
import com.kylecorry.andromeda.core.system.Screen
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.astronomy.infrastructure.AstronomyDailyWorker
import com.kylecorry.trail_sense.main.AppState
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.preferences.PreferencesSubsystem
import com.kylecorry.trail_sense.shared.sensors.CustomGPS
import com.kylecorry.trail_sense.shared.sensors.altimeter.CachingAltimeterWrapper
import com.kylecorry.trail_sense.shared.sensors.compass.CompassSource
import com.kylecorry.trail_sense.shared.sensors.providers.CompassProvider
import com.kylecorry.trail_sense.tools.pedometer.infrastructure.StepCounter
import java.time.Duration

class PreferenceMigrator private constructor() {

    private val lock = Object()

    fun migrate(context: Context) {
        synchronized(lock) {
            val prefs = PreferencesSubsystem.getInstance(context).preferences
            var currentVersion = prefs.getInt("pref_version") ?: 0

            AppState.isReturningUser = currentVersion > 0

            while (currentVersion < version) {
                val current = currentVersion
                val next = currentVersion + 1
                val migration =
                    migrations.find { it.fromVersion == current && it.toVersion == next }
                migration?.action?.invoke(context, prefs)
                currentVersion++
                prefs.putInt("pref_version", currentVersion)
            }
        }
    }
    companion object {
        private var instance: PreferenceMigrator? = null
        private val staticLock = Object()

        private const val version = 16
        private val migrations = listOf(
            PreferenceMigration(0, 1) { context, prefs ->
                if (prefs.contains("pref_enable_experimental")) {
                    prefs.remove("pref_enable_experimental")
                    prefs.remove("pref_use_camera_features")
                }
            },
            PreferenceMigration(1, 2) { context, prefs ->
                if (prefs.getBoolean(context.getString(R.string.pref_onboarding_completed)) == true) {
                    if (!prefs.contains(context.getString(R.string.pref_sunset_alerts))) {
                        prefs.putBoolean(context.getString(R.string.pref_sunset_alerts), true)
                    }

                    if (!prefs.contains(context.getString(R.string.pref_monitor_weather))) {
                        prefs.putBoolean(context.getString(R.string.pref_monitor_weather), true)
                    }
                }
            },
            PreferenceMigration(2, 3) { _, prefs ->
                prefs.remove("cache_pressure_setpoint")
                prefs.remove("cache_pressure_setpoint_altitude")
                prefs.remove("cache_pressure_setpoint_temperature")
                prefs.remove("cache_pressure_setpoint_time")
            },
            PreferenceMigration(3, 4) { context, prefs ->
                try {
                    val color = prefs.getInt(context.getString(R.string.pref_backtrack_path_color))
                        ?: return@PreferenceMigration
                    prefs.remove(context.getString(R.string.pref_backtrack_path_color))
                    prefs.putLong(
                        context.getString(R.string.pref_backtrack_path_color),
                        color.toLong()
                    )
                } catch (e: Exception) {
                    prefs.remove(context.getString(R.string.pref_backtrack_path_color))
                }
            },
            PreferenceMigration(4, 5) { _, prefs ->
                prefs.remove("pref_path_waypoint_style")
            },
            PreferenceMigration(5, 6) { _, prefs ->
                prefs.remove("pref_experimental_barometer_calibration")
                prefs.remove("pref_sea_level_require_dwell")
                prefs.remove("pref_barometer_altitude_change")
                prefs.remove("pref_sea_level_pressure_change_thresh")
                prefs.remove("pref_sea_level_use_rapid")
            },
            PreferenceMigration(6, 7) { context, prefs ->
                val distance = prefs.getFloat("odometer_distance")
                if (distance != null) {
                    val stride = UserPreferences(context).pedometer.strideLength.meters().distance
                    if (stride > 0f) {
                        val steps = (distance / stride).toLong()
                        prefs.putLong(StepCounter.STEPS_KEY, steps)
                    }
                }
                prefs.remove("odometer_distance")
                prefs.remove("last_odometer_location")

                prefs.putBoolean(
                    context.getString(R.string.pref_pedometer_enabled),
                    prefs.getString("pref_odometer_source") == "pedometer"
                )
            },
            PreferenceMigration(7, 8) { context, _ ->
                val prefs = UserPreferences(context).navigation
                val currentScale = prefs.rulerScale
                if (currentScale == 1f || currentScale == 0f) {
                    return@PreferenceMigration
                }

                val dpi = Screen.dpi(context)
                val ydpi = Screen.ydpi(context)
                val adjustedDpi = dpi / currentScale
                prefs.rulerScale = ydpi / adjustedDpi
            },
            PreferenceMigration(8, 9) { context, prefs ->
                val userPrefs = UserPreferences(context)
                prefs.getString("pref_backtrack_frequency")?.toLongOrNull()?.let {
                    userPrefs.backtrackRecordFrequency = Duration.ofMinutes(it)
                }
                prefs.getString("pref_weather_update_frequency")?.toLongOrNull()?.let {
                    userPrefs.weather.weatherUpdateFrequency = Duration.ofMinutes(it)
                }
            },
            PreferenceMigration(9, 10) { context, prefs ->
                if (prefs.getBoolean("pref_experimental_sea_level_calibration_v2") != true) {
                    val userPreferences = UserPreferences(context)
                    userPreferences.weather.pressureSmoothing = 15f
                }

                prefs.remove("pref_barometer_altitude_outlier")
                prefs.remove("pref_barometer_altitude_smoothing")
                prefs.remove("pref_experimental_sea_level_calibration_v2")
            },
            PreferenceMigration(10, 11) { _, prefs ->
                val date = prefs.getLocalDate("pref_astronomy_alerts_last_run_date")
                if (date != null) {
                    prefs.putLocalDate(
                        "pref_andromeda_daily_worker_last_run_date_${AstronomyDailyWorker.UNIQUE_ID}",
                        date
                    )
                }
                prefs.remove("pref_astronomy_alerts_last_run_date")
            },
            PreferenceMigration(11, 12) { _, prefs ->
                val elevation = prefs.getFloat(CustomGPS.LAST_ALTITUDE)
                if (elevation != null) {
                    prefs.putFloat(CachingAltimeterWrapper.LAST_ALTITUDE_KEY, elevation)
                }
            },
            PreferenceMigration(12, 13) { context, _ ->
                val userPrefs = UserPreferences(context)
                userPrefs.thermometer.resetThermometerCalibration()
            },
            PreferenceMigration(13, 14) { context, prefs ->
                val userPrefs = UserPreferences(context)
                val wasLegacyCompass = prefs.getBoolean("pref_use_legacy_compass_2") ?: false
                val sources = CompassProvider.getAvailableSources(context)
                if (wasLegacyCompass) {
                    userPrefs.compass.source = CompassSource.Orientation
                } else if (sources.contains(CompassSource.RotationVector)) {
                    // The rotation vector is accurate, no need for smoothing
                    userPrefs.compass.compassSmoothing = 1
                }
                userPrefs.compass.source = sources.firstOrNull() ?: CompassSource.CustomMagnetometer
                prefs.remove("pref_use_legacy_compass_2")
            },
            PreferenceMigration(14, 15){ context, prefs ->
                val userPrefs = UserPreferences(context)

                // By grabbing the preferences, it will solidify the defaults
                userPrefs.use24HourTime
                userPrefs.distanceUnits
                userPrefs.weightUnits
                userPrefs.pressureUnits
                userPrefs.temperatureUnits
            },
            PreferenceMigration(15, 16){ context, prefs ->
                if (prefs.getBoolean("cache_dialog_tool_cliff_height") != null){
                    // Enable the cliff height tool since it was previously used
                    val userPrefs = UserPreferences(context)
                    userPrefs.isCliffHeightEnabled = true
                }
            }
        )

        fun getInstance(): PreferenceMigrator {
            return synchronized(staticLock) {
                if (instance == null) {
                    instance = PreferenceMigrator()
                }
                instance!!
            }
        }


    }

}