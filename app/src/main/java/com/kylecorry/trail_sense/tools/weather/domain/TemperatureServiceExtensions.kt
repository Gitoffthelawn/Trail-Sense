package com.kylecorry.trail_sense.tools.weather.domain

import android.util.Log
import com.kylecorry.sol.units.Temperature
import com.kylecorry.trail_sense.tools.weather.domain.forecasting.temperatures.ITemperatureService
import java.time.ZonedDateTime

internal suspend fun ITemperatureService.getTemperaturePrediction(time: ZonedDateTime): TemperaturePrediction? {
    return try {
        val range = getTemperatureRange(time.toLocalDate())
        val low = range.start
        val high = range.end
        val current = getTemperature(time)
        val average = Temperature.from((low.value + high.value) / 2f, low.units)
        TemperaturePrediction(
            average,
            low,
            high,
            current
        )
    } catch (e: Exception) {
        Log.e(javaClass.simpleName, "Unable to lookup temperature", e)
        null
    }
}