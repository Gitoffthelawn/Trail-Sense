package com.kylecorry.trail_sense.tools.navigation.ui.markers

import android.graphics.Color
import androidx.annotation.ColorInt
import com.kylecorry.andromeda.canvas.ICanvasDrawer
import com.kylecorry.andromeda.core.units.PixelCoordinate
import com.kylecorry.sol.units.Coordinate

class CircleMapMarker(
    override val location: Coordinate,
    @ColorInt private val color: Int,
    @ColorInt private val strokeColor: Int? = null,
    private val opacity: Int = 255,
    override val size: Float = 12f,
    private val strokeWeight: Float = 0.5f,
    private val isSizeInDp: Boolean = true,
    private val useScale: Boolean = true,
    private val onClickFn: () -> Boolean = { false }
) : MapMarker {
    override fun draw(
        drawer: ICanvasDrawer,
        anchor: PixelCoordinate,
        scale: Float,
        rotation: Float
    ) {
        val actualScale = if (useScale) scale else 1f
        val size = if (isSizeInDp) drawer.dp(this.size) else this.size
        drawer.noTint()
        if (strokeColor != null && strokeColor != Color.TRANSPARENT) {
            drawer.stroke(strokeColor)
            drawer.strokeWeight(drawer.dp(strokeWeight) * actualScale)
        } else {
            drawer.noStroke()
        }
        if (color != Color.TRANSPARENT) {
            drawer.fill(color)
            drawer.opacity(opacity)
            drawer.circle(anchor.x, anchor.y, size * actualScale)
            drawer.opacity(255)
        }
    }

    override fun onClick(): Boolean {
        return onClickFn()
    }
}