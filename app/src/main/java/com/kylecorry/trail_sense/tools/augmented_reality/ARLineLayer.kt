package com.kylecorry.trail_sense.tools.augmented_reality

import android.graphics.Path
import com.kylecorry.andromeda.canvas.ICanvasDrawer
import com.kylecorry.andromeda.canvas.StrokeCap
import com.kylecorry.andromeda.canvas.StrokeJoin
import com.kylecorry.andromeda.core.units.PixelCoordinate
import com.kylecorry.sol.math.SolMath
import com.kylecorry.sol.math.SolMath.normalizeAngle
import com.kylecorry.sol.math.geometry.Geometry
import com.kylecorry.sol.math.geometry.Rectangle
import com.kylecorry.trail_sense.shared.getBounds
import com.kylecorry.trail_sense.shared.toPixelCoordinate
import com.kylecorry.trail_sense.shared.toVector2
import com.kylecorry.trail_sense.tools.augmented_reality.position.ARPoint
import com.kylecorry.trail_sense.tools.augmented_reality.position.AugmentedRealityCoordinate
import kotlin.math.hypot
import kotlin.math.roundToInt

class ARLineLayer : ARLayer {

    private val path = Path()

    private val lines = mutableListOf<ARLine>()
    private val lineLock = Any()

    fun setLines(lines: List<ARLine>) {
        synchronized(lineLock) {
            this.lines.clear()
            this.lines.addAll(lines)
        }
    }

    fun clearLines() {
        synchronized(lineLock) {
            lines.clear()
        }
    }

    override fun draw(drawer: ICanvasDrawer, view: AugmentedRealityView) {
        val maxAngle = hypot(view.fov.width, view.fov.height) * 1.5f
        val resolutionDegrees = (maxAngle / 10f).roundToInt().coerceIn(1, 5)



        drawer.noFill()
        drawer.strokeJoin(StrokeJoin.Round)
        drawer.strokeCap(StrokeCap.Round)

        val lines = synchronized(lineLock) {
            lines.toList()
        }

        // Draw horizontal lines
        for (line in lines) {
            path.reset()
            drawer.stroke(line.color)
            val thicknessPx = when (line.thicknessUnits) {
                ARLine.ThicknessUnits.Dp -> drawer.dp(line.thickness)
                ARLine.ThicknessUnits.Angle -> view.sizeToPixel(line.thickness)
            }
            drawer.strokeWeight(thicknessPx)

            if (line.curved) {
                // TODO: Only calculate this higher resolution path once or only for the visible portion
                val pixels = getLinePixels(
                    view,
                    line.points,
                    resolutionDegrees.toFloat()
                )
                render(pixels, view, path)
            } else {
                render(line.points.map { it.getAugmentedRealityCoordinate(view) }, view, path)
            }

            drawer.path(path)
        }

        drawer.noStroke()
    }

    /**
     * Given a line it returns the pixels that make up the line.
     * It will split long lines into smaller chunks, using the resolutionDegrees.
     * It will also clip the line to the view.
     */
    private fun getLinePixels(
        view: AugmentedRealityView,
        line: List<ARPoint>,
        resolutionDegrees: Float,
    ): List<AugmentedRealityCoordinate> {
        val pixels = mutableListOf<AugmentedRealityCoordinate>()
        var previousCoordinate: AugmentedRealityCoordinate? = null
        for (point in line) {
            val coord = point.getAugmentedRealityCoordinate(view)
            pixels.addAll(
                if (previousCoordinate != null) {
                    splitLine(previousCoordinate, coord, resolutionDegrees)
                } else {
                    listOf(coord)
                }
            )

            previousCoordinate = coord
        }

        return pixels
    }

    // TODO: Should this operate on pixels or coordinates? - if it is pixels, it will be linear, if it is coordinates it will be curved
    private fun splitLine(
        start: AugmentedRealityCoordinate,
        end: AugmentedRealityCoordinate,
        resolutionDegrees: Float
    ): List<AugmentedRealityCoordinate> {
        val coordinates = mutableListOf<AugmentedRealityCoordinate>()
        coordinates.add(start)
        val bearingDelta = if (start.hasBearing && end.hasBearing) {
            SolMath.deltaAngle(start.bearing, end.bearing)
        } else {
            0f
        }
        val elevationDelta = end.elevation - start.elevation
        val distanceDelta = end.distance - start.distance
        val distance = hypot(bearingDelta, elevationDelta)
        val steps = (distance / resolutionDegrees).roundToInt()
        val bearingStep = bearingDelta / steps
        val elevationStep = elevationDelta / steps
        val distanceStep = distanceDelta / steps
        for (i in 1..steps) {
            coordinates.add(
                AugmentedRealityCoordinate.fromSpherical(
                    normalizeAngle((if (start.hasBearing) start.bearing else end.bearing) + bearingStep * i),
                    start.elevation + elevationStep * i,
                    start.distance + distanceStep * i
                )
            )
        }

        // Add the end point
        coordinates.add(end)

        return coordinates
    }

    override fun invalidate() {
        // Do nothing
    }

    override fun onClick(
        drawer: ICanvasDrawer,
        view: AugmentedRealityView,
        pixel: PixelCoordinate
    ): Boolean {
        return false
    }

    override fun onFocus(drawer: ICanvasDrawer, view: AugmentedRealityView): Boolean {
        return false
    }

    private fun render(
        points: List<AugmentedRealityCoordinate>,
        view: AugmentedRealityView,
        path: Path
    ) {
        val bounds = view.getBounds()
        val pixels = points.map { view.toPixel(it) }
        var previous: PixelCoordinate? = null

        val multiplier = 1.5f

        val minX = view.width * -multiplier
        val maxX = view.width * (1 + multiplier)
        val minY = view.height * -multiplier
        val maxY = view.height * (1 + multiplier)


        for (pixel in pixels) {

            val isLineInvalid = previous != null &&
                    (pixel.x < minX && previous.x > maxX ||
                            pixel.x > maxX && previous.x < minX ||
                            pixel.y < minY && previous.y > maxY ||
                            pixel.y > maxY && previous.y < minY)

            if (previous != null && !isLineInvalid) {
                drawLine(bounds, PixelCoordinate(0f, 0f), previous, pixel, path)
            } else {
                path.moveTo(pixel.x, pixel.y)
            }
            previous = pixel
        }
    }

    private fun drawLine(
        bounds: Rectangle,
        origin: PixelCoordinate,
        start: PixelCoordinate,
        end: PixelCoordinate,
        path: Path
    ) {

        val a = start.toVector2(bounds.top)
        val b = end.toVector2(bounds.top)

        // Both are in
        if (bounds.contains(a) && bounds.contains(b)) {
            path.lineTo(end.x - origin.x, end.y - origin.y)
            return
        }

        val intersection =
            Geometry.getIntersection(a, b, bounds).map { it.toPixelCoordinate(bounds.top) }

        // A is in, B is not
        if (bounds.contains(a)) {
            if (intersection.any()) {
                path.lineTo(intersection[0].x - origin.x, intersection[0].y - origin.y)
            }
            path.moveTo(end.x - origin.x, end.y - origin.y)
            return
        }

        // B is in, A is not
        if (bounds.contains(b)) {
            if (intersection.any()) {
                path.moveTo(intersection[0].x - origin.x, intersection[0].y - origin.y)
            }
            path.lineTo(end.x - origin.x, end.y - origin.y)
            return
        }

        // Both are out, but may intersect
        if (intersection.size == 2) {
            path.moveTo(intersection[0].x - origin.x, intersection[0].y - origin.y)
            path.lineTo(intersection[1].x - origin.x, intersection[1].y - origin.y)
        }
        path.moveTo(end.x - origin.x, end.y - origin.y)
    }
}