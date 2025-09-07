package com.kylecorry.trail_sense.shared.map_layers.ui.layers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.kylecorry.andromeda.canvas.ICanvasDrawer
import com.kylecorry.andromeda.core.cache.AppServiceRegistry
import com.kylecorry.andromeda.core.units.PixelCoordinate
import com.kylecorry.luna.coroutines.onMain
import com.kylecorry.sol.science.geology.CoordinateBounds
import com.kylecorry.sol.units.Bearing
import com.kylecorry.sol.units.CompassDirection
import com.kylecorry.trail_sense.main.errors.SafeMode
import com.kylecorry.trail_sense.shared.andromeda_temp.grow
import com.kylecorry.trail_sense.shared.map_layers.MapLayerBackgroundTask
import com.kylecorry.trail_sense.shared.device.DeviceSubsystem
import com.kylecorry.trail_sense.shared.map_layers.tiles.ITileSourceSelector
import com.kylecorry.trail_sense.shared.map_layers.tiles.TileLoader
import com.kylecorry.trail_sense.tools.map.map_layers.BaseMapMapLayerPreferences
import com.kylecorry.trail_sense.tools.photo_maps.map_layers.PhotoMapMapLayerPreferences
import kotlinx.coroutines.CancellationException

class TiledMapLayer : IAsyncLayer {

    private var shouldReloadTiles = true
    private var backgroundColor: Int = Color.WHITE
    var controlsPdfCache = false
    private var minZoom: Int = 0
    private val loader = TileLoader()
    private val tilePaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }
    private val taskRunner = MapLayerBackgroundTask()
    private var updateListener: (() -> Unit)? = null

    var sourceSelector: ITileSourceSelector? = null
        set(value) {
            field = value
            loader.clearCache()
            shouldReloadTiles = true
        }

    fun setPreferences(prefs: PhotoMapMapLayerPreferences) {
        _percentOpacity = prefs.opacity.get() / 100f
        invalidate()
    }

    fun setPreferences(prefs: BaseMapMapLayerPreferences) {
        _percentOpacity = prefs.opacity.get() / 100f
        invalidate()
    }

    fun setBackgroundColor(color: Int) {
        this.backgroundColor = color
        shouldReloadTiles = true
    }

    fun setMinZoom(minZoom: Int) {
        this.minZoom = minZoom
        shouldReloadTiles = true
    }

    override fun draw(drawer: ICanvasDrawer, map: IMapView) {
        // Avoid drawing while in safe mode
        if (SafeMode.isEnabled()) {
            return
        }

        // Load tiles if needed
        taskRunner.scheduleUpdate(
            map.mapBounds,
            map.metersPerPixel,
            shouldReloadTiles
        ) { bounds, metersPerPixel ->
            shouldReloadTiles = false
            try {
                sourceSelector?.let {
                    loader.loadTiles(
                        it,
                        bounds.grow(getGrowPercent()),
                        metersPerPixel,
                        minZoom,
                        backgroundColor,
                        controlsPdfCache
                    )
                }
                onMain {
                    updateListener?.invoke()
                }
            } catch (e: CancellationException) {
                System.gc()
                throw e
            } catch (e: Throwable) {
                e.printStackTrace()
                shouldReloadTiles = true
            }
        }

        // Render loaded tiles
        synchronized(loader.lock) {
            tilePaint.alpha = 255
            renderTiles(drawer.canvas, map)
        }
    }

    override fun drawOverlay(drawer: ICanvasDrawer, map: IMapView) {
        // Do nothing
    }

    override fun invalidate() {
        // Do nothing, invalidation is handled separately
    }

    override fun onClick(drawer: ICanvasDrawer, map: IMapView, pixel: PixelCoordinate): Boolean {
        return false
    }

    private fun getGrowPercent(): Float {
        val device = AppServiceRegistry.get<DeviceSubsystem>()
        val threshold = 50 * 1024 * 1024 // 50 MB
        return if (device.getAvailableBitmapMemoryBytes() < threshold) {
            0f
        } else {
            0.2f
        }
    }

    private fun renderTiles(canvas: Canvas, map: IMapView) {
        loader.tileCache.entries.sortedBy { it.key.z }.forEach { (tile, bitmaps) ->
            val tileBounds = tile.getBounds()
            bitmaps.reversed().forEach { bitmap ->
                val topLeftPixel = map.toPixel(tileBounds.northWest)
                val topRightPixel = map.toPixel(tileBounds.northEast)
                val bottomRightPixel = map.toPixel(tileBounds.southEast)
                val bottomLeftPixel = map.toPixel(tileBounds.southWest)
                canvas.drawBitmapMesh(
                    bitmap,
                    1,
                    1,
                    floatArrayOf(
                        // Intentionally inverted along the Y axis
                        bottomLeftPixel.x, bottomLeftPixel.y,
                        bottomRightPixel.x, bottomRightPixel.y,
                        topLeftPixel.x, topLeftPixel.y,
                        topRightPixel.x, topRightPixel.y
                    ),
                    0,
                    null,
                    0,
                    tilePaint
                )
            }
        }
    }

    override fun setHasUpdateListener(listener: (() -> Unit)?) {
        updateListener = listener
    }

    private var _percentOpacity: Float = 1f

    override val percentOpacity: Float
        get() = _percentOpacity
}