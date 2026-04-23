package com.henryxxiao.splash.utils

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlin.math.min
import androidx.core.graphics.withClip
import com.henryxxiao.splash.R
import androidx.core.content.withStyledAttributes

class SquircleFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // 完美保留你原有的 Double 比例逻辑
    var aspectRatio: Double = -1.0
        set(value) {
            field = value
            requestLayout()
        }

    var squircleRadius: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            updatePath()
            invalidate()
        }

    private val clipPath = Path()
    private val drawFilter = PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)

        // 支持 XML 属性解析 (记得在 attrs.xml 声明 SquircleFrameLayout_squircleRadius)
//        if (attrs != null) {
//            context.withStyledAttributes(attrs, R.styleable.SquircleFrameLayout) {
//                squircleRadius = getDimension(R.styleable.SquircleFrameLayout_squircleRadius, 0f)
//            }
//        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (aspectRatio == -1.0) return
        val width = measuredWidth
        val height = (width * aspectRatio).toInt()
        if (height == measuredHeight) return
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePath()
    }

    private fun updatePath() {
        clipPath.reset()
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val maxRadius = min(w, h) / 2f
        val r = min(squircleRadius, maxRadius)

        if (r <= 0f) {
            clipPath.addRect(0f, 0f, w, h, Path.Direction.CW)
            return
        }

        val control = r * 0.552284749831f
        clipPath.moveTo(r, 0f)
        clipPath.lineTo(w - r, 0f)
        clipPath.cubicTo(w - r + control, 0f, w, r - control, w, r)
        clipPath.lineTo(w, h - r)
        clipPath.cubicTo(w, h - r + control, w - r + control, h, w - r, h)
        clipPath.lineTo(r, h)
        clipPath.cubicTo(r - control, h, 0f, h - r + control, 0f, h - r)
        clipPath.lineTo(0f, r)
        clipPath.cubicTo(0f, r - control, r - control, 0f, r, 0f)
        clipPath.close()
    }

    // 【核心关键】：在这里裁剪内部所有的子 View (包括图片、遮罩、文字)
    override fun dispatchDraw(canvas: Canvas) {
        canvas.drawFilter = drawFilter
        canvas.withClip(clipPath) {
            super.dispatchDraw(canvas) // 绘制子 View
        }
    }
}