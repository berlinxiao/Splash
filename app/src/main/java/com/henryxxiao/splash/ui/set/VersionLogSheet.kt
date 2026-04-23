package com.henryxxiao.splash.ui.set

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.henryxxiao.splash.R
import com.henryxxiao.splash.utils.view.SmoothCardDrawable

class VersionLogSheet : BottomSheetDialogFragment() {

    @SuppressLint("DefaultLocale")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        // 在 Dialog 创建后立即设置 layout
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.sheet_log, null, false)

        // 获取背景色
        val bgColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val radiusPx = 32.dpToPx().toFloat() // 你想要的圆角大小，比如 24dp

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

    private fun Float.dpToPx() =
        (this * resources.displayMetrics.density)
    // 扩展函数，dp 转 px
    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()
}