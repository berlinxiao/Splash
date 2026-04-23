package com.henryxxiao.splash.utils.animation

import android.view.View
import android.widget.TextView
import com.henryxxiao.splash.utils.view.LoadingTextView

class LoadingTextManager {
    private val views = mutableSetOf<LoadingTextView>()

    fun register(vararg v: LoadingTextView) {
        v.forEach { views.add(it); it.startLoading() }
    }

    // 单独停止，传入最终要显示的 TextView 和文字
    fun stop(loading: LoadingTextView, target: TextView, text: String) {
        loading.stopLoading {
            loading.visibility = View.GONE
            target.apply {
                alpha = 0f
                this.text = text
                visibility = View.VISIBLE
                animate().alpha(1f).setDuration(250).start()
            }
        }
        views.remove(loading)
    }

    fun stopAll() {
        views.forEach { it.stopLoading() }
        views.clear()
    }
}