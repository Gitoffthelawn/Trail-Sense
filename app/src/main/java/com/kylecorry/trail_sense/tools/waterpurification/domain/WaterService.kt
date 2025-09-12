package com.kylecorry.trail_sense.tools.waterpurification.domain

import com.kylecorry.sol.units.Distance
import com.kylecorry.sol.units.DistanceUnits
import java.time.Duration

class WaterService : IWaterService {

    override fun getPurificationTime(altitude: Distance?): Duration {
        if (altitude == null || altitude.convertTo(DistanceUnits.Meters).value >= 1000f){
            return Duration.ofMinutes(3)
        }

        return Duration.ofMinutes(1)
    }

}