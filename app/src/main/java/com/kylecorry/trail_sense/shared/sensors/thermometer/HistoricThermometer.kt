package com.kylecorry.trail_sense.shared.sensors.thermometer

import android.content.Context
import com.kylecorry.andromeda.core.sensors.IThermometer
import com.kylecorry.trail_sense.shared.sensors.CoroutineIntervalSensor
import com.kylecorry.trail_sense.shared.sensors.LocationSubsystem
import com.kylecorry.trail_sense.tools.weather.infrastructure.subsystem.WeatherSubsystem
import java.time.Duration
import java.time.ZonedDateTime

class HistoricThermometer(
    context: Context,
    frequency: Duration = Duration.ofSeconds(1)
) :
    CoroutineIntervalSensor(frequency), IThermometer {

    private val location = LocationSubsystem.getInstance(context)
    private val weather = WeatherSubsystem.getInstance(context)

    override var temperature: Float = 0f
        private set(value) {
            field = value
            hasValidReading = true
        }

    override var hasValidReading: Boolean = false

    override suspend fun update() {
        // Purposely not calibrated because there's a wrapper for that
        temperature = weather.getTemperature(
            ZonedDateTime.now(),
            location.location,
            location.elevation,
            false
        ).value.value
        super.update()
    }

}