package com.henryxxiao.splash.utils.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.view.View
import android.view.animation.PathInterpolator
import androidx.annotation.MainThread

class StaggerAnimator {

    private val INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f) // Material 标准曲线

    // 纯主线程访问，普通 Long 足够，无需 AtomicLong / @Volatile
    private var exitSeq = 0L

    data class Config(
        val translateYDp: Float = 28f,   // 初始向下偏移
        val startDelayMs: Long  = 120L,  // 第一个元素开始前等待（共享元素过渡期间）
        val staggerMs: Long     = 50L,   // 每个元素之间的错开间隔
        val durationMs: Long    = 280L,  // 单个元素动画时长
        val alphaFrom: Float    = 0f
    )

    /**
     * 进场：将 views 从偏移+透明 逐步动画到最终位置
     */
    @MainThread
    fun enter(views: List<View>, config: Config = Config()) {
        if (views.isEmpty()) return

        // 进场时作废所有待触发的退场回调
        exitSeq++

        val offsetPx = views.first().context.dpToPx(config.translateYDp)

        // 单次遍历：清理旧动画 + 设初始状态 + 启动新动画
        views.forEachIndexed { index, view ->
            view.animate().setListener(null).cancel()

            view.alpha = config.alphaFrom
            view.translationY = offsetPx

            if (!view.isAttachedToWindow) return@forEachIndexed

            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(config.startDelayMs + index * config.staggerMs)
                .setDuration(config.durationMs)
                .setInterpolator(INTERPOLATOR)
                .setListener(null)
                .start()
        }
    }

    /**
     * 退场：反向播放（在 popBackStack 之前调用，等动画完成再退）
     */
    @MainThread
    fun exit(
        views: List<View>,
        config: Config = Config(),
        onEnd: () -> Unit
    ) {
        if (views.isEmpty()) {
            onEnd()
            return
        }

        // 递增序列号，本轮退场的唯一身份
        val mySeq = ++exitSeq

        val offsetPx = views.first().context.dpToPx(config.translateYDp)
        val exitDuration = (config.durationMs * 0.8f).toLong()
        val exitStagger = (config.staggerMs * 0.6f).toLong()

        // 先停进场、归位状态，单次遍历完成
        views.forEach { view ->
            view.animate().setListener(null).cancel()
            view.alpha = 1f
            view.translationY = 0f
        }

        val reversed = views.asReversed()
        val total = reversed.count { it.isAttachedToWindow }

        if (total == 0) {
            onEnd()
            return
        }

        // 纯主线程计数器，普通 Int 足够
        var remaining = total

        reversed.forEachIndexed { index, view ->
            if (!view.isAttachedToWindow) return@forEachIndexed

            view.animate()
                .alpha(0f)
                .translationY(offsetPx * 0.6f)
                .setStartDelay(index * exitStagger)
                .setDuration(exitDuration)
                .setInterpolator(INTERPOLATOR)
                .setListener(object : AnimatorListenerAdapter() {
                    // 用局部 Boolean 防止同一个 listener 的 End 和 Cancel 都触发
                    private var counted = false

                    override fun onAnimationEnd(animation: Animator) = countOnce()
                    override fun onAnimationCancel(animation: Animator) = countOnce()

                    private fun countOnce() {
                        if (counted) return
                        counted = true
                        // 序列号不匹配说明这是旧一轮的回调，直接丢弃
                        if (mySeq != exitSeq) return
                        if (--remaining == 0) onEnd()
                    }
                })
                .start()
        }
    }

    @MainThread
    fun cancel(views: Collection<View>, reset: Boolean = false) {
        exitSeq++ // 作废所有待触发的退场回调

        views.forEach { view ->
            view.animate().setListener(null).cancel()
            if (reset) {
                view.alpha = 1f
                view.translationY = 0f
            }
        }
    }

    private fun Context.dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density
}