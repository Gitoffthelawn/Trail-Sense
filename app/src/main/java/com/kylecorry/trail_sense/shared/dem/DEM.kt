package com.kylecorry.trail_sense.shared.dem

import android.util.Size
import com.kylecorry.andromeda.core.cache.AppServiceRegistry
import com.kylecorry.andromeda.core.cache.GeospatialCache
import com.kylecorry.andromeda.core.coroutines.onIO
import com.kylecorry.andromeda.core.tryOrDefault
import com.kylecorry.andromeda.json.JsonConvert
import com.kylecorry.luna.streams.readText
import com.kylecorry.luna.streams.readUntil
import com.kylecorry.sol.math.SolMath.roundNearest
import com.kylecorry.sol.science.geology.CoordinateBounds
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.sol.units.Distance
import com.kylecorry.trail_sense.shared.ProguardIgnore
import com.kylecorry.trail_sense.shared.data.GeographicImageSource
import com.kylecorry.trail_sense.shared.io.FileSubsystem

class DigitalElevationModelFile(
    val filename: String,
    val width: Int,
    val height: Int,
    val a: Double,
    val b: Double,
    val longitude_start: Double,
    val longitude_end: Double,
    val latitude_start: Double,
    val latitude_end: Double,
) : ProguardIgnore

class DigitalElevationModelIndex(
    val resolution_arc_seconds: Int,
    val compression_method: String,
    val files: List<DigitalElevationModelFile>
) : ProguardIgnore

object DEM {
    private var cache = GeospatialCache<Distance>(Distance.meters(100f), size = 40)
    private var cachedIndex: DigitalElevationModelIndex? = null
    private var useDefaultModel = false

    suspend fun getElevation(location: Coordinate): Distance? = onIO {
        val files = AppServiceRegistry.get<FileSubsystem>()
        val index = cachedIndex ?: loadIndex() ?: return@onIO null

        val sources = index.files.map {
            it.filename to GeographicImageSource(
                Size(it.width, it.height),
                bounds = CoordinateBounds(
                    it.latitude_start,
                    it.longitude_end,
                    it.latitude_end,
                    it.longitude_start
                ),
                decoder = GeographicImageSource.scaledDecoder(it.a, it.b),
                precision = 4
            )
        }

        val resolutionArcMinutes = index.resolution_arc_seconds / 60.0
        val resolutionDegrees = resolutionArcMinutes / 60.0

        val rounded = location.copy(
            latitude = location.latitude.roundNearest(resolutionDegrees / 4.0),
            longitude = location.longitude.roundNearest(resolutionDegrees / 4.0)
        )
        cache.getOrPut(rounded) {
            val image =
                sources.firstOrNull { it.second.contains(rounded) }
                    ?: return@getOrPut Distance.meters(0f)
            tryOrDefault(Distance.meters(0f)) {
                val stream = if (useDefaultModel) {
                    files.streamAsset("dem/${image.first}")
                } else {
                    files.streamLocal("dem/${image.first}")
                }
                stream?.use {
                    Distance.meters(image.second.read(it, location).first())
                } ?: Distance.meters(0f)
            }
        }
    }

    fun invalidateCache() {
        cache = GeospatialCache(Distance.meters(100f), size = 40)
        cachedIndex = null
        useDefaultModel = true
    }

    fun isAvailable(): Boolean {
        return true
    }

    private suspend fun loadIndex() = onIO {
        val files = AppServiceRegistry.get<FileSubsystem>()
        val index = files.get("dem/index.json")
        var json = ""
        json = if (!index.exists()) {
            useDefaultModel = true
            files.streamAsset("dem/index.json")?.use { it.readUntil { false } } ?: ""
        } else {
            useDefaultModel = false
            index.readText()
        }
        val parsed =
            tryOrDefault(null) { JsonConvert.fromJson<DigitalElevationModelIndex>(json) }
        cachedIndex = parsed
        parsed
    }

}