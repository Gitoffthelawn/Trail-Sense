package com.kylecorry.trail_sense.shared.dem

import androidx.test.platform.app.InstrumentationRegistry
import com.kylecorry.sol.math.statistics.Statistics
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.trail_sense.shared.AltitudeCorrection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class DigitalElevationModelTest {

    @Test
    fun getElevation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val errors = mutableListOf<Float>()
        val maximumError = 300f
        val maximumAverageError = 1.5f
        val maximumStdDevError = 2f

        val places = listOf(
            Place("New York", 41.714, -74.006, 11f),
            Place("Orlando", 28.538, -81.379, 25f),
            Place("Los Angeles", 34.052, -118.244, 93f),
            Place("Quito", -0.230, -78.525, 2850f),
            Place("London", 51.509, -0.126, 35f),
            Place("Anchorage", 61.218, -149.900, 31f),
            Place("Amesterdam", 52.374, 4.890, -2f),
            Place("Stockholm", 59.333, 18.065, 28f),
            Place("Rio de Janeiro", -22.903, -43.208, 380f),
            Place("Honolulu", 21.307, -157.858, 5f),
            Place("Tokyo", 35.689, 139.692, 40f),
            Place("Bangkok", 13.754, 100.501, 1.5f),
            Place("Sydney", -33.868, 151.207, 3f)
        )

        for (place in places) {
            val elevation =
                DigitalElevationModel.getElevation(context, Coordinate(place.latitude, place.longitude))
            assertEquals(place.elevation, elevation, maximumError)
            errors.add(elevation - place.elevation)
        }

        // Check the average error and standard deviation
        val absAverageError = Statistics.mean(errors.map { abs(it) })
        val standardDeviation = Statistics.stdev(errors.map { abs(it) })

        assertEquals("Average", 0f, absAverageError, maximumAverageError)
        assertEquals("Standard Deviation", 0f, standardDeviation, maximumStdDevError)
    }

    private class Place(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val elevation: Float
    )

}