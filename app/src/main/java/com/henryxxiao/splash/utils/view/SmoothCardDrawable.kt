package com.henryxxiao.splash.utils.view

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * 模拟 iOS Squircle (G2 连续平滑) 的自定义圆角背景
 */
class SmoothCardDrawable(
    private val backgroundColor: Int,
    private val cornerRadius: Float,
    private val smoothing: Float = 0.6f
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
    }
    private val path = Path()

    // 绘制极其轻量化：没有任何对象创建和数学计算，全速渲染不掉帧
    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    // 核心优化点：只有当 View 的大小发生变化时，才重新计算路径
    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        buildSmoothCornerPath(bounds)
    }

    private fun buildSmoothCornerPath(bounds: Rect) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()

        // 健壮性优化：防止给定的圆角过大，导致曲线交叉错乱。最大不超过短边的一半。
        val r = cornerRadius.coerceAtMost(w / 2f).coerceAtMost(h / 2f)

        // 计算贝塞尔控制点距离
        val c = r * smoothing

        path.reset()

        // 重新推演的坐标轴（假设从左上角顺时针绘制）

        // 左上角
        path.moveTo(0f, r)
        path.cubicTo(0f, r - c, r - c, 0f, r, 0f)

        // 右上角
        path.lineTo(w - r, 0f)
        path.cubicTo(w - r + c, 0f, w, r - c, w, r)

        // 右下角
        path.lineTo(w, h - r)
        path.cubicTo(w, h - r + c, w - r + c, h, w - r, h)

        // 左下角
        path.lineTo(r, h)
        path.cubicTo(r - c, h, 0f, h - r + c, 0f, h - r)

        path.close()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}