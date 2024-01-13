package com.kylecorry.trail_sense.shared.sensors

import com.kylecorry.andromeda.core.sensors.AbstractSensor
import com.kylecorry.andromeda.core.time.CoroutineTimer
import java.time.Duration

abstract class IntervalSensor(private val frequency: Duration) : AbstractSensor() {

    private val timer = CoroutineTimer { update() }

    override val hasValidReading: Boolean
        get() = true

    override fun startImpl() {
        timer.interval(frequency)
    }

    override fun stopImpl() {
        timer.stop()
    }

    protected open fun update() {
        notifyListeners()
    }

}