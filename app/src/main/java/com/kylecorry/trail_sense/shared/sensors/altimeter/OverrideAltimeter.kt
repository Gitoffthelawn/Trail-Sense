package com.kylecorry.trail_sense.shared.sensors.altimeter

import android.content.Context
import com.kylecorry.andromeda.core.sensors.AbstractSensor
import com.kylecorry.andromeda.core.sensors.IAltimeter
import com.kylecorry.andromeda.core.time.CoroutineTimer
import com.kylecorry.trail_sense.shared.UserPreferences

class OverrideAltimeter(context: Context, private val updateFrequency: Long = 20L) :
    AbstractSensor(),
    IAltimeter {

    override val hasValidReading: Boolean
        get() = gotReading
    private var gotReading = true

    private val userPrefs by lazy { UserPreferences(context) }
    private val intervalometer = CoroutineTimer { notifyListeners() }

    override val altitude: Float
        get() = userPrefs.altitudeOverride

    override fun startImpl() {
        intervalometer.interval(updateFrequency)
    }

    override fun stopImpl() {
        intervalometer.stop()
    }
}