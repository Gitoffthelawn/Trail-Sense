package com.kylecorry.trail_sense.tools.climate.infrastructure.temperatures.calculators

import com.kylecorry.sol.math.Vector2
import com.kylecorry.sol.math.analysis.Trigonometry
import com.kylecorry.sol.time.Time
import com.kylecorry.sol.units.Reading
import com.kylecorry.sol.units.Temperature
import java.time.Instant
import java.time.ZonedDateTime

internal class SineTemperatureCalculator(
    private val first: Reading<Temperature>,
    private val second: Reading<Temperature>
) : ITemperatureCalculator {

    private val wave by lazy {
        Trigonometry.connect(
            Vector2(
                getX(first.time),
                first.value.celsius().value
            ),
            Vector2(
                getX(second.time),
                second.value.celsius().value
            )
        )
    }

    override suspend fun calculate(time: ZonedDateTime): Temperature {
        return Temperature.celsius(wave.calculate(getX(time.toInstant())))
    }

    private fun getX(time: Instant): Float {
        return Time.hoursBetween(first.time, time)
    }
}