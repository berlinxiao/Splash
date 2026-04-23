package com.henryxxiao.splash.ui.set

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.henryxxiao.splash.R
import com.henryxxiao.splash.utils.view.SmoothCardDrawable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConfirmSheet : BottomSheetDialogFragment() {

    var onConfirm: (() -> Unit)? = null  // 直接用函数类型，不需要定义接口

    companion object {
        // 不传 String，而是传具体的缓存大小 Float
        private const val ARG_CACHE_SIZE = "arg_cache_size"

        fun newInstance(sizeMB: Float) = ConfirmSheet().apply {
            arguments = bundleOf(ARG_CACHE_SIZE to sizeMB)
        }
    }

    @SuppressLint("DefaultLocale")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        // 在 Dialog 创建后立即设置 layout
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.sheet_confirm, null, false)

        // 取出传入的缓存大小数字
        val cacheSizeMB = arguments?.getFloat(ARG_CACHE_SIZE) ?: 0f

        val infoTextView = view.findViewById<TextView>(R.id.sheet_info)
        val confirmButton = view.findViewById<MaterialButton>(R.id.sheet_confirm_button)
        val cancelButton = view.findViewById<MaterialButton>(R.id.sheet_cancel_button)

        // 初始化显示原本的文字
        infoTextView.text = String.format("%.2f MB", cacheSizeMB)

        confirmButton.setOnClickListener {
            // 1. 禁用按钮，防止用户在动画播放期间疯狂连击
            confirmButton.isEnabled = false
            cancelButton.isEnabled = false

            // 2. 瞬间通知 SetFragment 开始在后台清理真正的缓存
            onConfirm?.invoke()

            // 3. 开启数字递减动画，使用协程接管动画引擎 (不要用 ValueAnimator)
            lifecycleScope.launch {
                val duration = 400L        // 动画总时长 400 毫秒
                val steps = 20             // 分 40 步执行（约等于 50fps 的刷新率，绝对丝滑）
                val delayPerStep = duration / steps
                val stepValue = cacheSizeMB / steps
                var current = cacheSizeMB

                // 循环递减数字
                for (i in 0 until steps) {
                    current -= stepValue
                    if (current < 0f) current = 0f
                    // 实时更新 UI
                    infoTextView.text = String.format("%.2f MB", current)
                    delay(delayPerStep) // 协程挂起，绝不阻塞主线程
                }

                // 确保最终精确归零，然后关闭弹窗
                infoTextView.text = "0.00 MB"
                dismiss()
            }
        }

        cancelButton.setOnClickListener {
            dismiss()
        }

        // 不再使用原生的 drawable 资源，换成我们自己写的平滑圆角
        // 获取背景色
        val bgColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val radiusPx = 32.dpToPx().toFloat() // 你想要的圆角大小，比如 24dp
        // 把圆角背景设置在你的内部 View 上，而不是外部容器上
        view.background = SmoothCardDrawable(bgColor, radiusPx)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            // 找到系统的底层容器
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener

            // 【关键步骤 2】：彻底扒掉系统容器的“外衣”
            // 设为透明，并且必须把 elevation 设为 0，否则哪怕透明了，依然会有个黑色的矩形阴影贴在底部！
            sheet.setBackgroundColor(Color.TRANSPARENT)
            sheet.elevation = 0f

            // 防止内部阴影被裁切（可选，为了更好的视觉效果）
            (sheet as? ViewGroup)?.clipChildren = false

            // 【关键步骤 3】：把 margin 加在你的内部 View 上
            // 这样底层的 sheet 依然贴着屏幕底部（顺应系统逻辑），但你的 View 被往上推了 16dp，实现完美悬浮
            val margin = 16.dpToPx()
            val layoutParams = view.layoutParams as FrameLayout.LayoutParams
            layoutParams.setMargins(margin, margin, margin, margin)
            view.layoutParams = layoutParams

            BottomSheetBehavior.from(sheet).apply {
                skipCollapsed = true
                isHideable = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        return dialog
    }

    // 扩展函数，dp 转 px
    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    // Fragment 销毁时清掉 listener 防止内存泄漏
    override fun onDestroyView() {
        super.onDestroyView()
        onConfirm = null
    }
}