package com.henryxxiao.splash.utils.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class TopGlowLightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val lineWidth = 2.dpToPx()
    private val maxGlowRadius = 20f.dpToPx()

    // 离屏预烘焙画笔 (仅用于生成 Alpha 遮罩)
    private val outerMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = lineWidth * 3f
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
        alpha = 150
        maskFilter = BlurMaskFilter(maxGlowRadius, BlurMaskFilter.Blur.NORMAL)
    }

    private val innerMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = lineWidth
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
        alpha = 255
        maskFilter = BlurMaskFilter(2f.dpToPx(), BlurMaskFilter.Blur.NORMAL)
    }

    // GPU 实时渲染画笔
    private val renderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ALPHA_8 格式缓存
    private var glowMaskBitmap: Bitmap? = null
    private var maskCenterY = 0f
    private var maskPadding = 0f

    private val aiGlowColors = intArrayOf(
        Color.parseColor("#4285F4"),
        Color.parseColor("#EA4335"),
        Color.parseColor("#FBBC05"),
        Color.parseColor("#34A853"),
        Color.parseColor("#4285F4")
    )

    private var glowShader: LinearGradient? = null
    private val gradientMatrix = Matrix()

    private var lastDrawTime = 0L

    private var isGlowVisible = false

    private var currentProgress = 0f
    private var targetProgress = 0f
    private val animDurationMs = 500f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // 【优化 3：把耗时操作移出 onDraw，彻底解放渲染管线首帧性能】
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val fw = w.toFloat()
        if (fw > 0f) {
            createGlowMaskCache(fw)
            glowShader = LinearGradient(0f, 0f, fw, 0f, aiGlowColors, null, Shader.TileMode.REPEAT)
            renderPaint.shader = glowShader
        }
    }

    private fun createGlowMaskCache(w: Float) {
        glowMaskBitmap?.recycle()

        maskPadding = maxGlowRadius * 2f
        val bmpWidth = (w + maskPadding * 2).toInt()
        val bmpHeight = (maxGlowRadius * 4f).toInt()

        glowMaskBitmap = createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ALPHA_8)
        val c = Canvas(glowMaskBitmap!!)
        maskCenterY = bmpHeight / 2f

        c.drawLine(maskPadding, maskCenterY, maskPadding + w, maskCenterY, outerMaskPaint)
        c.drawLine(maskPadding, maskCenterY, maskPadding + w, maskCenterY, innerMaskPaint)
    }

    override fun onDraw(canvas: Canvas) {
        if (!isShown) return
        val w = width.toFloat()
        val bmp = glowMaskBitmap
        if (w <= 0f || bmp == null || glowShader == null) return

        val currentTime = SystemClock.elapsedRealtime()
        if (lastDrawTime == 0L) lastDrawTime = currentTime
        val dt = min(currentTime - lastDrawTime, 32L).toFloat()
        lastDrawTime = currentTime

        // 更新升降进度
        val step = dt / animDurationMs
        if (currentProgress < targetProgress) {
            currentProgress = min(targetProgress, currentProgress + step)
        } else if (currentProgress > targetProgress) {
            currentProgress = max(targetProgress, currentProgress - step)
        }

        // 退场完毕后，严格停机休眠
        if (currentProgress <= 0f && targetProgress <= 0f) return

        val flowCycleMs = 2000L
        val modTime = currentTime % flowCycleMs
        val dx = (modTime.toFloat() / flowCycleMs.toFloat()) * w

        gradientMatrix.setTranslate(dx, 0f)
        glowShader!!.setLocalMatrix(gradientMatrix)

        canvas.withSave {

            // 计算物理升降坐标
            val easeProgress = cubicEaseInOut(currentProgress)
            val hiddenY = -(maxGlowRadius + lineWidth * 2f)
            val visibleY = lineWidth / 2f
            val currentY = hiddenY + (visibleY - hiddenY) * easeProgress
            val drawY = currentY - maskCenterY

            // 【优化 4：抛弃超大浮点数，使用精准的真实边界进行刀切】
            // 允许向左、向右扩展 maskPadding，向下最大扩展到 drawY + bmpHeight
            val bottomBound = drawY + bmp.height
            clipRect(-maskPadding, 0f, w + maskPadding, bottomBound)

            // GPU 极速贴图渲染
            drawBitmap(bmp, -maskPadding, drawY, renderPaint)

        }

        // 持续渲染条件
        if (currentProgress > 0f || targetProgress > 0f) {
            postInvalidateOnAnimation()
        }
    }

    private fun cubicEaseInOut(t: Float): Float {
        return if (t < 0.5f) {
            4f * t * t * t
        } else {
            1f - (-2f * t + 2f).pow(3) / 2f
        }
    }

    // ================= 安全的 API =================

    // 【优化 2：强制切换到主线程，防御后台异常调用】
    fun showGlow() {
        post {
            if (!isGlowVisible) {
                isGlowVisible = true
                targetProgress = 1f
                resumeAnimation()
            }
        }
    }

    fun hideGlow() {
        post {
            if (isGlowVisible) {
                isGlowVisible = false
                targetProgress = 0f
                resumeAnimation()
            }
        }
    }

    private fun resumeAnimation() {
        lastDrawTime = SystemClock.elapsedRealtime()
        invalidate()
    }

    // 【优化 1：彻底修复时序 Bug，哪怕 View 先前被隐藏，一旦显示立即追赶进度】
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE && (isGlowVisible || currentProgress > 0f)) {
            resumeAnimation()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE && (isGlowVisible || currentProgress > 0f)) {
            resumeAnimation()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // 向上最多掀开 5 层父约束，完美平衡渲染范围与遍历性能
        var currentParent = parent
        var depth = 0
        while (currentParent is ViewGroup && depth < 5) {
            currentParent.clipChildren = false
            currentParent.clipToPadding = false
            currentParent = currentParent.parent
            depth++
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowMaskBitmap?.recycle()
        glowMaskBitmap = null
    }

    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }

    private fun Int.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }
}