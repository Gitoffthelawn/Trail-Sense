package com.kylecorry.trail_sense.tools.climate.infrastructure.temperatures.calculators

import com.kylecorry.sol.math.SolMath.square
import com.kylecorry.sol.time.Time
import com.kylecorry.sol.units.Reading
import com.kylecorry.sol.units.Temperature
import java.time.Instant
import java.time.ZonedDateTime

internal class QuadraticTemperatureCalculator(
    private val low: Reading<Temperature>,
    high: Reading<Temperature>
) : ITemperatureCalculator {

    private val b = low.value.celsius().value
    private val a = (high.value.celsius().value - b) / square(getX(high.time))

    override suspend fun calculate(time: ZonedDateTime): Temperature {
        val x = getX(time.toInstant())
        return Temperature.celsius(a * square(x) + b)
    }

    private fun getX(time: Instant): Float {
        return Time.hoursBetween(low.time, time)
    }
}