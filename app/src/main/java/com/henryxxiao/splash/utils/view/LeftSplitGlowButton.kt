package com.henryxxiao.splash.utils.view

import android.R.attr.insetLeft
import android.R.attr.insetRight
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.graphics.toColorInt
import com.google.android.material.R
import com.google.android.material.button.MaterialButton
import kotlin.math.max
import kotlin.math.min

class LeftSplitGlowButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {

    private val borderWidth = 1.dpToPx()
    private val rightInnerCornerRadius = 4.dpToPx()

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
        strokeCap = Paint.Cap.ROUND
    }

    private val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth * 2f
        alpha = 255
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(4f.dpToPx(), BlurMaskFilter.Blur.NORMAL)
    }

    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth * 4f
        alpha = 180
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(14f.dpToPx(), BlurMaskFilter.Blur.NORMAL)
    }

    private val gradientColors = intArrayOf(
        Color.parseColor("#534AB7"),
        Color.parseColor("#EA4335"),
        Color.parseColor("#FBBC05"),
        Color.parseColor("#34A853"),
        Color.parseColor("#4285F4"),
        Color.parseColor("#534AB7")
    )

    private val bgBounds = RectF()
    private val borderBounds = RectF()
    private val outlinePath = Path()
    private val drawnPath = Path()
    private val gradientMatrix = Matrix()
    private val pathMeasure = PathMeasure()
    private var sweepGradient: SweepGradient? = null

    // 动画双指针引擎
    private var currentHead = 0f
    private var targetHead = 0f
    private var currentTail = 0f
    private var targetTail = 0f

    private var lastDrawTime = 0L
    private val traceDurationMs = 300f

    private val hideGlowRunnable = Runnable { hideGlow() }

    // 状态保护机制
    private var originalTranslationZ = 0f
    private var isElevatedForGlow = false

    private var originalAmbientShadowColor = 0
    private var originalSpotShadowColor = 0

    init {
        strokeWidth = 0
        // 【关键修复】：彻底删除 setLayerType(LAYER_TYPE_HARDWARE, null)
        // 允许 View 直接绘制在父画布上，释放发光边缘！
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val fw = w.toFloat()
        val fh = h.toFloat()
        if (fw <= 0f || fh <= 0f) return

        var left = insetLeft.toFloat()
        var top = insetTop.toFloat()
        var right = fw - insetRight.toFloat()
        var bottom = fh - insetBottom.toFloat()

        if (left >= right || top >= bottom) {
            left = 0f; top = 0f; right = fw; bottom = fh
        }

        bgBounds.set(left, top, right, bottom)
        borderBounds.set(bgBounds)

        val overlap = 3.dpToPx()
        val strokeInset = (borderWidth / 2f) + overlap
        borderBounds.inset(strokeInset, strokeInset)

        val horizontalExpand = 3.dpToPx()
        borderBounds.left -= horizontalExpand
        borderBounds.right += horizontalExpand

        if (borderBounds.left >= borderBounds.right) borderBounds.right = borderBounds.left + 1f
        if (borderBounds.top >= borderBounds.bottom) borderBounds.bottom = borderBounds.top + 1f

        val r = max(0f, borderBounds.height() / 2f)
        val innerR = max(0f, rightInnerCornerRadius)
        val radii = floatArrayOf(r, r, innerR, innerR, innerR, innerR, r, r)

        outlinePath.reset()
        outlinePath.addRoundRect(borderBounds, radii, Path.Direction.CW)

        pathMeasure.setPath(outlinePath, false)

        val cx = borderBounds.centerX()
        val cy = borderBounds.centerY()
        sweepGradient = SweepGradient(cx, cy, gradientColors, null)
    }

    override fun draw(canvas: Canvas) {
        if (!isShown) return
        if (width == 0 || height == 0 || sweepGradient == null) {
            super.draw(canvas)
            return
        }

        val currentTime = SystemClock.elapsedRealtime()
        if (lastDrawTime == 0L) lastDrawTime = currentTime
        val dt = min(currentTime - lastDrawTime, 32L).toFloat()
        lastDrawTime = currentTime

        val step = dt / traceDurationMs
        if (currentHead < targetHead) currentHead = min(targetHead, currentHead + step)
        if (currentTail < targetTail) currentTail = min(targetTail, currentTail + step)

        if (currentHead == 0f && currentTail == 0f) {
            super.draw(canvas)
            return
        }

        if (currentTail >= 1f && currentHead >= 1f) {
            currentHead = 0f
            currentTail = 0f
            targetHead = 0f
            targetTail = 0f

            post {
                if (isElevatedForGlow) {
                    translationZ = originalTranslationZ
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        outlineAmbientShadowColor = originalAmbientShadowColor
                        outlineSpotShadowColor = originalSpotShadowColor
                    }
                    isElevatedForGlow = false
                }
            }

            super.draw(canvas)
            return
        }

        val flowCycleMs = 2000L
        val modTime = currentTime % flowCycleMs
        val rotationAngle = (modTime.toFloat() / flowCycleMs.toFloat()) * 360f

        gradientMatrix.setRotate(rotationAngle, width / 2f, height / 2f)
        sweepGradient!!.setLocalMatrix(gradientMatrix)

        borderPaint.shader = sweepGradient
        innerGlowPaint.shader = sweepGradient
        outerGlowPaint.shader = sweepGradient

        val length = pathMeasure.length
        drawnPath.reset()

        if (length > 0f) {
            val startD = currentTail * length
            val stopD = currentHead * length

            if (stopD - startD > 0f) {
                pathMeasure.getSegment(startD, stopD, drawnPath, true)

                canvas.drawPath(drawnPath, outerGlowPaint)
                canvas.drawPath(drawnPath, innerGlowPaint)
                super.draw(canvas)
                canvas.drawPath(drawnPath, borderPaint)
            } else {
                super.draw(canvas)
            }
        } else {
            super.draw(canvas)
        }

        if (currentHead != targetHead || currentTail != targetTail || (currentHead > 0f && currentTail < 1f)) {
            postInvalidateOnAnimation()
        }
    }

    private inline fun runOnMainThread(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            post { action() }
        }
    }

    fun showGlow() {
        runOnMainThread {
            removeCallbacks(hideGlowRunnable)
            internalShowGlow()
        }
    }

    fun hideGlow() {
        runOnMainThread {
            removeCallbacks(hideGlowRunnable)
            internalHideGlow()
        }
    }

    fun playOneShotGlow(delayMs: Long = 300L) {
        runOnMainThread {
            removeCallbacks(hideGlowRunnable)
            currentHead = 0f
            targetHead = 0f
            currentTail = 0f
            targetTail = 0f

            internalShowGlow()
            postDelayed(hideGlowRunnable, delayMs)
        }
    }

    private fun internalShowGlow() {
        if (targetHead != 1f) {
            targetHead = 1f
            targetTail = 0f

            if (!isElevatedForGlow) {
                originalTranslationZ = translationZ
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    originalAmbientShadowColor = outlineAmbientShadowColor
                    originalSpotShadowColor = outlineSpotShadowColor
                    outlineAmbientShadowColor = Color.TRANSPARENT
                    outlineSpotShadowColor = Color.TRANSPARENT
                }
                translationZ = originalTranslationZ + 10f
                isElevatedForGlow = true
            }

            lastDrawTime = SystemClock.elapsedRealtime()
            invalidate()
        }
    }

    private fun internalHideGlow() {
        if (targetTail != 1f) {
            targetHead = 1f
            targetTail = 1f
            lastDrawTime = SystemClock.elapsedRealtime()
            invalidate()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE && (currentHead > 0f || targetHead > 0f)) {
            lastDrawTime = SystemClock.elapsedRealtime()
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // 恢复你原先验证没问题的 depth < 5，保护深层视图树的遍历性能
        var currentParent = parent
        var depth = 0
        while (currentParent is ViewGroup && depth < 5) {
            currentParent.clipChildren = false
            currentParent.clipToPadding = false
            currentParent = currentParent.parent
            depth++
        }
    }

    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }

    private fun Int.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }
}