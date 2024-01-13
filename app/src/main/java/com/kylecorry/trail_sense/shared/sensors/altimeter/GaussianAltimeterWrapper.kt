package com.kylecorry.trail_sense.shared.sensors.altimeter

import com.kylecorry.andromeda.core.sensors.AbstractSensor
import com.kylecorry.andromeda.core.sensors.IAltimeter
import com.kylecorry.andromeda.core.sensors.Quality
import com.kylecorry.andromeda.sense.location.IGPS
import com.kylecorry.sol.math.RingBuffer
import com.kylecorry.sol.math.SolMath.positive
import com.kylecorry.sol.math.SolMath.real
import com.kylecorry.sol.math.statistics.GaussianDistribution
import com.kylecorry.sol.math.statistics.Statistics

class GaussianAltimeterWrapper(override val altimeter: IAltimeter, samples: Int = 4) :
    AbstractSensor(),
    AltimeterWrapper {

    // TODO: Add this to IAltimeter
    override var altitudeAccuracy: Float? = null
        private set

    private val buffer = RingBuffer<GaussianDistribution>(samples)

    private var lastDistribution: GaussianDistribution? = null

    private val defaultVariance = 10f

    override fun startImpl() {
        buffer.clear()
        altimeter.start(this::onReading)
    }

    override fun stopImpl() {
        altimeter.stop(this::onReading)
    }

    override var altitude: Float = altimeter.altitude
        private set


    override val hasValidReading: Boolean
        get() = altimeter.hasValidReading && buffer.isFull()

    override val quality: Quality
        get() = altimeter.quality

    private fun onReading(): Boolean {

        // Always populate the variance
        val variance = if (altimeter is IGPS) {
            altimeter.verticalAccuracy
                ?.real(defaultVariance)
                ?.positive(defaultVariance)
                ?: defaultVariance
        } else {
            defaultVariance
        }

        val distribution = GaussianDistribution(
            altimeter.altitude.real(0f),
            variance
        )

        // A new elevation reading was not received
        if (distribution == lastDistribution) {
            return true
        }

        lastDistribution = distribution

        buffer.add(distribution)
        val calculated = Statistics.joint(buffer.toList())
        if (calculated != null) {
            altitude = calculated.mean.real(0f)
            altitudeAccuracy =
                calculated.standardDeviation.real(defaultVariance).positive(defaultVariance)
            if (hasValidReading) {
                notifyListeners()
            }
        }
        return true
    }
}