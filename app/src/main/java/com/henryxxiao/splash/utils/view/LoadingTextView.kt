package com.henryxxiao.splash.utils.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import com.henryxxiao.splash.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoadingTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val loadingText = context.getString(R.string.show_text_load)
    companion object {
        //private const val LOADING_TEXT  =  "loading"
        private const val TYPE_INTERVAL  = 300L
        private const val DELETE_INTERVAL = 120L
        private const val PAUSE_FULL     = 800L
        private const val PAUSE_EMPTY    = 500L
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // 不设置 typeface，使用系统默认字体
        textAlign = Paint.Align.CENTER  // 始终居中绘制
    }

    private var displayText = ""
    private var baseline = 0f
    private var centerX = 0f            // onSizeChanged 时确定，不在 onDraw 里计算

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var animJob: Job? = null

    var textSizePx: Float = 36f
        set(value) {
            field = value
            paint.textSize = value
            // 字号变了才需要重新计算 baseline
            val fm = paint.fontMetrics
            baseline = -fm.ascent
            requestLayout()
        }

    var textColor: Int = "#D3D3D3".toColorInt()
        set(value) {
            field = value
            invalidate()
        }

    init {
        paint.textSize = textSizePx
        val fm = paint.fontMetrics
        baseline = -fm.ascent
    }

    // ---- 测量：宽度固定为完整 "loading..." 避免父布局抖动 ----
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        //paint.textSize = textSizePx
        val fm = paint.fontMetrics
        //baseline = -fm.ascent
        val w = (paint.measureText(loadingText) + paddingLeft + paddingRight).toInt()
        val h = (fm.bottom - fm.top + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        centerX = w / 2f
    }

    // ---- 绘制：Center 对齐，文字始终以 centerX 为轴扩展 ----
    override fun onDraw(canvas: Canvas) {
        paint.color = textColor
        canvas.drawText(displayText, centerX, paddingTop + baseline, paint)
    }

    // ---- 动画：while(true) + delay() 作为取消点，无需判断 isActive ----
    fun startLoading() {
        animJob?.cancel()  // 取消旧的，直接重建
        animJob = scope.launch {
            while (true) {
                // 逐字打出
                for (i in 1..loadingText.length) {
                    displayText = loadingText.substring(0, i)
                    invalidate()
                    delay(TYPE_INTERVAL)   // 取消点：cancel() 后从这里抛 CancellationException 退出
                }
                delay(PAUSE_FULL)

                // 逐字删除
                for (i in loadingText.length - 1 downTo 0) {
                    displayText = loadingText.substring(0, i)
                    invalidate()
                    delay(DELETE_INTERVAL)
                }
                delay(PAUSE_EMPTY)
            }
        }
    }

    fun stopLoading(onStopped: (() -> Unit)? = null) {
        animJob?.cancel()
        animJob = null
        displayText = ""
        invalidate()
        onStopped?.invoke()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()  // 取消 scope 下所有协程，无泄漏
    }
}