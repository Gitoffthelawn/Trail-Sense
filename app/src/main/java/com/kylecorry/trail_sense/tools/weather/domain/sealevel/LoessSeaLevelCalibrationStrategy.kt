package com.kylecorry.trail_sense.tools.weather.domain.sealevel

import com.kylecorry.sol.units.Pressure
import com.kylecorry.sol.units.Reading
import com.kylecorry.trail_sense.shared.data.DataUtils
import com.kylecorry.trail_sense.tools.weather.domain.RawWeatherObservation

class LoessSeaLevelCalibrationStrategy(
    private val baseStrategy: ISeaLevelCalibrationStrategy,
    private val smoothing: Float
) : ISeaLevelCalibrationStrategy {
    override fun calibrate(readings: List<Reading<RawWeatherObservation>>): List<Reading<Pressure>> {
        val seaLevel = baseStrategy.calibrate(readings)
        return DataUtils.smoothTemporal(
            seaLevel,
            smoothing,
            { it.value }
        ) { reading, value ->
            Pressure.from(value, reading.units)
        }
    }
}