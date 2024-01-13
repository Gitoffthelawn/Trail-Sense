package com.kylecorry.trail_sense.shared.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.annotation.ColorInt
import com.kylecorry.andromeda.canvas.CanvasView
import com.kylecorry.andromeda.canvas.ImageMode
import com.kylecorry.andromeda.core.bitmap.BitmapUtils.resizeToFit
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.units.PixelCoordinate
import com.kylecorry.sol.math.geometry.Size
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.CustomUiUtils.getPrimaryColor
import com.kylecorry.trail_sense.shared.colors.AppColor
import com.kylecorry.trail_sense.shared.io.FileSubsystem
import com.kylecorry.trail_sense.tools.maps.domain.ImageMagnifier
import com.kylecorry.trail_sense.tools.maps.domain.PercentBounds
import com.kylecorry.trail_sense.tools.maps.domain.PercentCoordinate
import com.kylecorry.trail_sense.tools.maps.domain.PixelBounds
import com.kylecorry.trail_sense.tools.maps.infrastructure.fixPerspective
import kotlin.math.max
import kotlin.math.min


// TODO: Extend subsampling image view and disable scrolling
class PerspectiveCorrectionView : CanvasView {

    private var image: Bitmap? = null
    private var imagePath: String? = null

    private var linesLoaded = false
    private var scale = 0.9f
    private var topLeft = PixelCoordinate(0f, 0f)
    private var topRight = PixelCoordinate(0f, 0f)
    private var bottomLeft = PixelCoordinate(0f, 0f)
    private var bottomRight = PixelCoordinate(0f, 0f)
    private var movingCorner: Corner? = null
    private var sourceMatrix = Matrix()
    var mapRotation: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var hasChanges = false

    var isPreview = false
        set(value) {
            field = value
            invalidate()
        }

    @ColorInt
    private var primaryColor = Color.BLACK

    private var imageX = 0f
    private var imageY = 0f

    private val files = FileSubsystem.getInstance(context)

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    init {
        runEveryCycle = false
    }

    override fun setup() {
        primaryColor = Resources.getPrimaryColor(context)
    }

    override fun draw() {
        if (image == null && imagePath != null && width > 0 && height > 0) {
            imagePath?.let { loadImage(it) }
        }

        val bitmap = image ?: return

        if (!linesLoaded) {
            resetLines()
        }

        // Center the image
        imageX = (width - bitmap.width * scale) / 2f
        imageY = (height - bitmap.height * scale) / 2f

        push()
        rotate(mapRotation)
        translate(imageX, imageY)
        scale(scale)
        if (isPreview) {
            drawPreviewCanvas()
        } else {
            drawEditCanvas()
            drawMagnify()
        }
        pop()
    }

    private fun loadImage(path: String) {

        val border = dp(48f).toInt()

        val w = if (mapRotation == 0f || mapRotation == 180f) {
            width
        } else {
            height
        } - border

        val h = if (mapRotation == 0f || mapRotation == 180f) {
            height
        } else {
            width
        } - border

        val bitmap = files.bitmap(path, w, h) ?: return
        image = bitmap.resizeToFit(w, h)
        if (image != bitmap) {
            bitmap.recycle()
        }
    }

    private fun drawMagnify() {
        val image = image ?: return
        val corner = movingCorner ?: return
        val center = when (corner) {
            Corner.TopLeft -> topLeft
            Corner.TopRight -> topRight
            Corner.BottomLeft -> bottomLeft
            Corner.BottomRight -> bottomRight
        }

        val magnifierSize = min(image.width, image.height) / 4f
        val magnifier = ImageMagnifier(
            Size(image.width.toFloat(), image.height.toFloat()),
            Size(magnifierSize, magnifierSize)
        )
        val pos = magnifier.getMagnifierPosition(center)
        val magCenter = PixelCoordinate(pos.x + magnifierSize / 2f, pos.y + magnifierSize / 2f)

        val magnifierImage = magnifier.magnify(image, center)

        imageMode(ImageMode.Center)
        image(magnifierImage, magCenter.x, magCenter.y)
        magnifierImage.recycle()
        imageMode(ImageMode.Corner)
        stroke(primaryColor)
        noFill()
        strokeWeight(dp(2f))
        val plusSize = dp(8f)
        line(magCenter.x - plusSize / 2f, magCenter.y, magCenter.x + plusSize / 2f, magCenter.y)
        line(magCenter.x, magCenter.y - plusSize / 2f, magCenter.x, magCenter.y + plusSize / 2f)
    }

    private fun drawPreviewCanvas() {
        val bitmap = image ?: return
        val warped = bitmap.fixPerspective(getBounds(), false, Color.WHITE)
        push()
        translate(-imageX, -imageY)
        val newImageX = (width - warped.width * scale) / 2f
        val newImageY = (height - warped.height * scale) / 2f
        translate(newImageX, newImageY)
        image(warped, 0f, 0f)
        pop()
        if (warped != bitmap) {
            warped.recycle()
        }
    }

    private fun drawEditCanvas() {
        val bitmap = image ?: return

        image(bitmap, 0f, 0f)

        noStroke()
        fill(primaryColor)
        circle(topLeft.x, topLeft.y, dp(10f))
        circle(topRight.x, topRight.y, dp(10f))
        circle(bottomLeft.x, bottomLeft.y, dp(10f))
        circle(bottomRight.x, bottomRight.y, dp(10f))

        stroke(primaryColor)
        noFill()
        strokeWeight(dp(2f))
        line(topLeft.x, topLeft.y, topRight.x, topRight.y)
        line(topRight.x, topRight.y, bottomRight.x, bottomRight.y)
        line(bottomRight.x, bottomRight.y, bottomLeft.x, bottomLeft.y)
        line(bottomLeft.x, bottomLeft.y, topLeft.x, topLeft.y)
    }

    private fun resetLines() {
        val image = image ?: return
        topLeft = PixelCoordinate(0f, 0f)
        topRight = PixelCoordinate(image.width.toFloat(), 0f)
        bottomLeft = PixelCoordinate(0f, image.height.toFloat())
        bottomRight = PixelCoordinate(image.width.toFloat(), image.height.toFloat())
        linesLoaded = true
    }

    private fun getBounds(): PixelBounds {
        return PixelBounds(topLeft, topRight, bottomLeft, bottomRight)
    }

    fun getPercentBounds(): PercentBounds? {
        val image = image ?: return null
        return PercentBounds(
            PercentCoordinate(topLeft.x / image.width, topLeft.y / image.height),
            PercentCoordinate(topRight.x / image.width, topRight.y / image.height),
            PercentCoordinate(bottomLeft.x / image.width, bottomLeft.y / image.height),
            PercentCoordinate(bottomRight.x / image.width, bottomRight.y / image.height),
        )
    }

    fun setImage(path: String) {
        imagePath = path
        image = null
        linesLoaded = false
        invalidate()
    }

    fun clearImage() {
        imagePath = null
        val oldImage = image
        image = null
        oldImage?.recycle()
        linesLoaded = false
        invalidate()
    }

    private fun toSource(pixel: PixelCoordinate): PixelCoordinate {
        sourceMatrix.reset()
        sourceMatrix.postRotate(mapRotation, width / 2f, height / 2f)
        sourceMatrix.invert(sourceMatrix)
        val point = floatArrayOf(pixel.x, pixel.y)
        sourceMatrix.mapPoints(point)
        val rotated = PixelCoordinate(point[0], point[1])
        return PixelCoordinate(
            rotated.x / scale - imageX / scale,
            rotated.y / scale - imageY / scale
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val position = toSource(PixelCoordinate(event.x, event.y))
        val radius = dp(16f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                when {
                    topLeft.distanceTo(position) <= radius -> {
                        movingCorner = Corner.TopLeft
                    }
                    topRight.distanceTo(position) <= radius -> {
                        movingCorner = Corner.TopRight
                    }
                    bottomLeft.distanceTo(position) <= radius -> {
                        movingCorner = Corner.BottomLeft
                    }
                    bottomRight.distanceTo(position) <= radius -> {
                        movingCorner = Corner.BottomRight
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (movingCorner) {
                    Corner.TopLeft -> {
                        topLeft = constrain(position, null, bottomLeft.y, null, topRight.x, radius)
                        hasChanges = true
                    }
                    Corner.TopRight -> {
                        topRight = constrain(position, null, bottomRight.y, topLeft.x, null, radius)
                        hasChanges = true
                    }
                    Corner.BottomLeft -> {
                        bottomLeft =
                            constrain(position, topLeft.y, null, null, bottomRight.x, radius)
                        hasChanges = true
                    }
                    Corner.BottomRight -> {
                        bottomRight =
                            constrain(position, topRight.y, null, bottomLeft.x, null, radius)
                        hasChanges = true
                    }
                    null -> {}
                }
            }
            MotionEvent.ACTION_UP -> {
                movingCorner = null
            }
        }
        invalidate()
        return true
    }

    private fun constrain(
        pixel: PixelCoordinate,
        top: Float?,
        bottom: Float?,
        left: Float?,
        right: Float?,
        radius: Float
    ): PixelCoordinate {
        var x = pixel.x
        var y = pixel.y

        if (top != null && y < top) {
            y = top
        }

        if (bottom != null && y > bottom) {
            y = bottom
        }

        if (left != null && x < left) {
            x = left
        }

        if (right != null && x > right) {
            x = right
        }

        val start = toSource(PixelCoordinate(radius, radius))
        val end = toSource(PixelCoordinate(width.toFloat() - radius, height.toFloat() - radius))

        return PixelCoordinate(
            x.coerceIn(min(start.x, end.x), max(start.x, end.x)),
            y.coerceIn(min(start.y, end.y), max(start.y, end.y))
        )
    }


    private enum class Corner {
        TopLeft,
        TopRight,
        BottomLeft,
        BottomRight
    }

}