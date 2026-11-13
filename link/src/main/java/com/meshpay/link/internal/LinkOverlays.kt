package com.meshpay.link.internal

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils

/*
 * The native ✕ and loader drawn over the WebView while Link loads. Both match
 * react-native-sdk's `CloseButton.tsx` / `Loader.tsx` so every SDK looks the same:
 * sizes are in dp, and the faint circle/track uses the foreground color at 8 %.
 */

/** Opacity of the close button's circle and the loader's track (RN `opacity: 0.08`). */
internal const val OVERLAY_TRACK_ALPHA: Float = 0.08f

/** Edge inset of the close button, matching Link's own header close (RN `top/right: 16`). */
internal const val CLOSE_BUTTON_INSET_DP: Float = 16f

/** Extra touch area around the close circle on every side (RN `hitSlop={12}`). */
internal const val CLOSE_BUTTON_HIT_SLOP_DP: Float = 12f
private const val CLOSE_BUTTON_SIZE_DP = 40f
private const val CLOSE_LINE_LENGTH_DP = 16f
private const val CLOSE_LINE_WIDTH_DP = 1f

private const val LOADER_SIZE_DP = 24f
private const val LOADER_STROKE_DP = 2f
private const val LOADER_ARC_DEGREES = 90f
private const val LOADER_START_ANGLE = -135f
private const val FULL_TURN = 360f

/** One full turn of the loader (RN `duration: 900`, linear). */
internal const val LOADER_SPIN_MS: Long = 900L

private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

private fun withAlpha(
    color: Int,
    alpha: Float,
): Int = ColorUtils.setAlphaComponent(color, (Color.alpha(color) * alpha).toInt())

/**
 * A 40 dp circle (foreground at 8 %) with a thin ✕ of two 16 dp × 1 dp lines.
 * The view is padded by the hit slop, so the touch target is 64 dp while the
 * drawn circle stays 40 dp.
 */
@SuppressLint("ViewConstructor")
internal class LinkCloseButton(
    context: Context,
    foreground: Int,
) : View(context) {
    private val slop = context.dp(CLOSE_BUTTON_HIT_SLOP_DP)
    private val radius = context.dp(CLOSE_BUTTON_SIZE_DP) / 2
    private val halfLine = context.dp(CLOSE_LINE_LENGTH_DP) / 2

    private val circlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(foreground, OVERLAY_TRACK_ALPHA)
            style = Paint.Style.FILL
        }
    private val linePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foreground
            style = Paint.Style.STROKE
            strokeWidth = context.dp(CLOSE_LINE_WIDTH_DP)
            strokeCap = Paint.Cap.ROUND
        }

    /** Total edge length of the view, circle plus hit slop on both sides. */
    val sizePx: Int = (2 * (radius + slop)).toInt()

    init {
        contentDescription = "Close"
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, radius, circlePaint)
        // Two lines rotated ±45°: their end points sit on the diagonals.
        val d = halfLine * DIAGONAL
        canvas.drawLine(cx - d, cy - d, cx + d, cy + d, linePaint)
        canvas.drawLine(cx - d, cy + d, cx + d, cy - d, linePaint)
    }

    private companion object {
        /** cos(45°): a line of half-length L rotated 45° spans L·cos45 on each axis. */
        const val DIAGONAL = 0.70710678f
    }
}

/**
 * A 24 dp ring with a 2 dp stroke: a faint full track (foreground at 8 %) and a
 * quarter arc spinning once every 900 ms. It never takes touches, and it only
 * animates while visible.
 */
@SuppressLint("ViewConstructor")
internal class LinkLoader(
    context: Context,
    foreground: Int,
) : View(context) {
    private val size = context.dp(LOADER_SIZE_DP)
    private val stroke = context.dp(LOADER_STROKE_DP)
    private val oval = RectF()

    private val trackPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(foreground, OVERLAY_TRACK_ALPHA)
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
    private val arcPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foreground
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }

    /** Current rotation of the arc, in degrees. */
    internal var angle: Float = 0f
        private set

    internal val spinner: ValueAnimator =
        ValueAnimator.ofFloat(0f, FULL_TURN).apply {
            duration = LOADER_SPIN_MS
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                angle = it.animatedValue as Float
                invalidate()
            }
        }

    init {
        contentDescription = "Loading"
        isClickable = false
        isFocusable = false
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val px = size.toInt()
        setMeasuredDimension(px, px)
    }

    override fun onDraw(canvas: Canvas) {
        // RN draws a 24 dp box with a 2 dp border; the stroke sits inside it.
        val inset = stroke / 2
        oval.set(inset, inset, width - inset, height - inset)
        canvas.drawOval(oval, trackPaint)
        canvas.drawArc(oval, LOADER_START_ANGLE + angle, LOADER_ARC_DEGREES, false, arcPaint)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            if (!spinner.isStarted) spinner.start()
        } else {
            spinner.cancel()
        }
    }

    override fun onDetachedFromWindow() {
        spinner.cancel()
        super.onDetachedFromWindow()
    }
}
