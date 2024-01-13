package com.kylecorry.trail_sense.tools.convert.ui

import com.kylecorry.sol.units.Distance
import com.kylecorry.sol.units.DistanceUnits
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.FormatService
import kotlin.math.absoluteValue

class FragmentDistanceConverter :
    SimpleConvertFragment<DistanceUnits>(DistanceUnits.Meters, DistanceUnits.Feet) {

    private val formatService by lazy { FormatService.getInstance(requireContext()) }

    override val units = listOf(
        DistanceUnits.Centimeters,
        DistanceUnits.Meters,
        DistanceUnits.Kilometers,
        DistanceUnits.Inches,
        DistanceUnits.Feet,
        DistanceUnits.Yards,
        DistanceUnits.Miles,
        DistanceUnits.NauticalMiles,
    )


    override fun getUnitName(unit: DistanceUnits): String {
        return when (unit) {
            DistanceUnits.Meters -> getString(R.string.unit_meters)
            DistanceUnits.Kilometers -> getString(R.string.unit_kilometers)
            DistanceUnits.Feet -> getString(R.string.unit_feet)
            DistanceUnits.Miles -> getString(R.string.unit_miles)
            DistanceUnits.NauticalMiles -> getString(R.string.unit_nautical_miles)
            DistanceUnits.Centimeters -> getString(R.string.unit_centimeters)
            DistanceUnits.Inches -> getString(R.string.unit_inches)
            DistanceUnits.Yards -> getString(R.string.unit_yards)
        }
    }

    override fun convert(amount: Float, from: DistanceUnits, to: DistanceUnits): String {
        val converted = Distance(amount.absoluteValue, from).convertTo(to)
        return formatService.formatDistance(converted, 4, false)
    }

}