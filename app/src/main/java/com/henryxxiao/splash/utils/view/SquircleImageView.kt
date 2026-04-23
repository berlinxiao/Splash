package com.henryxxiao.splash.utils.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.Path
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withClip
import com.henryxxiao.splash.R
import kotlin.math.min

class SquircleImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    // 1. 恢复为 Double，完美对接你的 SplashPhoto 数据
    var aspectRatio: Double = -1.0
        set(value) {
            field = value
            requestLayout() // 比例改变时，请求重新测量排版
        }

    // 2. 圆角大小依然用 Float，因为 Android 屏幕像素 (px/dp) 本质是 Float
    var cornerRadius: Float = 0f
        set(value) {
            field = value
            updatePath()
            invalidate() // 圆角改变时，请求重新绘制
        }

    private val clipPath = Path()
    private val drawFilter =
        PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        // 关闭硬件加速中不支持 clipPath 的部分边缘情况，防止黑屏或锯齿
        setLayerType(LAYER_TYPE_HARDWARE, null)

        // 支持 XML 属性解析 (记得在 attrs.xml 声明 SquircleFrameLayout_squircleRadius)
        if (attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.SquircleImageView) {
                cornerRadius = getDimension(R.styleable.SquircleImageView_squircleRadius, 0f)
            }
        }
    }

    // 3. 原汁原味保留你原本极其稳妥的测量逻辑！100% 兼容现有 XML
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

    // 4. Apple 级平滑曲线 (Squircle) 裁剪路径计算
    private fun updatePath() {
        clipPath.reset()
        val w = width.toFloat()
        val h = height.toFloat()

        if (w == 0f || h == 0f) return

        val maxRadius = min(w, h) / 2f
        val r = min(cornerRadius, maxRadius)

        if (r <= 0f) {
            clipPath.addRect(0f, 0f, w, h, Path.Direction.CW)
            return
        }

        // 常数 0.55228... 用于三阶贝塞尔曲线完美拟合圆弧
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

    override fun onDraw(canvas: Canvas) {
        // 开启抗锯齿，保证圆角边缘极其顺滑
        canvas.drawFilter = drawFilter

        canvas.withClip(clipPath) {
            super.onDraw(canvas)      // 画出图片本体
        }
    }
}