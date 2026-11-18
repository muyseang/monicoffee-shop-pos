package com.monicoffee.pos.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * Shows a photo under a round frame — the shape of the profile picture — and lets the customer
 * drag it and pinch to zoom until the part they want fills the circle. [crop] returns that part.
 * The photo always covers the whole circle, so the result never has empty corners.
 */
@SuppressLint("ViewConstructor")
class PhotoCropView(context: Context, private var photo: Bitmap) : View(context) {
    private var scale = 1f
    private var minScale = 1f
    private var left = 0f
    private var top = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    private val drawMatrix = Matrix()
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2 * resources.displayMetrics.density }
    private val mask = Path()

    private val zoom = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomBy(detector.scaleFactor, detector.focusX, detector.focusY); return true
        }
    })

    private val cx get() = width / 2f
    private val cy get() = height / 2f
    /** Radius of the round frame. */
    private val radius get() = minOf(width, height) * 0.42f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        mask.reset()
        mask.fillType = Path.FillType.EVEN_ODD
        mask.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        mask.addCircle(w / 2f, h / 2f, minOf(w, h) * 0.42f, Path.Direction.CW)
        fit()
    }

    /** Smallest zoom that still covers the circle, centred. */
    private fun fit() {
        if (width == 0) return
        minScale = 2 * radius / minOf(photo.width, photo.height)
        scale = minScale
        left = cx - photo.width * scale / 2
        top = cy - photo.height * scale / 2
        invalidate()
    }

    private fun zoomBy(factor: Float, fx: Float, fy: Float) {
        val next = (scale * factor).coerceIn(minScale, minScale * MAX_ZOOM)
        // Zoom around the fingers: the point under them stays put.
        left = fx - (fx - left) * next / scale
        top = fy - (fy - top) * next / scale
        scale = next
        keepCovered(); invalidate()
    }

    /** Never let an edge of the photo come inside the circle. */
    private fun keepCovered() {
        left = left.coerceIn(cx + radius - photo.width * scale, cx - radius)
        top = top.coerceIn(cy + radius - photo.height * scale, cy - radius)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        zoom.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; dragging = true }
            // A second finger starts a pinch; dragging resumes from wherever one finger is left.
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> dragging = false
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !zoom.isInProgress) {
                    if (dragging) { left += event.x - lastX; top += event.y - lastY; keepCovered(); invalidate() }
                    lastX = event.x; lastY = event.y; dragging = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    /** Turns the photo a quarter turn clockwise (for a picture taken sideways). */
    fun rotate() {
        photo = Bitmap.createBitmap(photo, 0, 0, photo.width, photo.height, Matrix().apply { postRotate(90f) }, true)
        fit()
    }

    /** The part of the photo inside the circle, as a [size]×[size] square. */
    fun crop(size: Int): Bitmap {
        val side = (2 * radius / scale).toInt().coerceIn(1, minOf(photo.width, photo.height))
        val x = ((cx - radius - left) / scale).toInt().coerceIn(0, photo.width - side)
        val y = ((cy - radius - top) / scale).toInt().coerceIn(0, photo.height - side)
        val square = Bitmap.createBitmap(photo, x, y, side, side)
        return if (side > size) Bitmap.createScaledBitmap(square, size, size, true) else square
    }

    override fun onDraw(canvas: Canvas) {
        drawMatrix.setScale(scale, scale); drawMatrix.postTranslate(left, top)
        canvas.drawBitmap(photo, drawMatrix, photoPaint)
        canvas.drawPath(mask, shade)
        canvas.drawCircle(cx, cy, radius, ring)
    }

    private companion object {
        const val MAX_ZOOM = 5f
    }
}
