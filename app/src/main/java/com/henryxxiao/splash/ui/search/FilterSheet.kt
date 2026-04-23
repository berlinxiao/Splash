package com.henryxxiao.splash.ui.search

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.henryxxiao.splash.R
import com.henryxxiao.splash.utils.view.SmoothCardDrawable

class FilterSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_ID = ""

        // 使用静态工厂方法创建实例
        fun newInstance(id: String): FilterSheet {
            return FilterSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_ID, id)
                }
            }
        }
    }

    private val sessionId by lazy { arguments?.getString(ARG_ID) ?: "" }

    // 获取 SearchFragment 中的共享 ViewModel
    private val searchViewModel: SharedSearchViewModel by activityViewModels()

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        // 在 Dialog 创建后立即设置 layout
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.sheet_filter, null, false)

        val confirmButton = view.findViewById<MaterialButton>(R.id.filter_confirm_button)
        val cancelButton = view.findViewById<MaterialButton>(R.id.filter_cancel_button)

        val groupOrderBy = view.findViewById<MaterialButtonToggleGroup>(R.id.filter_group_order)
        val groupOrientation = view.findViewById<MaterialButtonToggleGroup>(R.id.filter_group_orientation)
        val groupColor = view.findViewById<ChipGroup>(R.id.filter_group_color)

        confirmButton.setOnClickListener {
            // 禁用按钮
            confirmButton.isEnabled = false
            cancelButton.isEnabled = false

            // 1. 收集 orderBy (因为设了 selectionRequired，必定有一个被选中)
            val orderBy = when (groupOrderBy.checkedButtonId) {
                R.id.filter_latest -> "latest"
                else -> "relevant"
            }

            // 2. 收集 orientation (View.NO_ID 代表用户没选任何按钮)
            val orientation = when (groupOrientation.checkedButtonId) {
                R.id.filter_landscape -> "landscape"
                R.id.filter_portrait -> "portrait"
                R.id.filter_squarish -> "squarish"
                else -> null // 没选用 null
            }

            // 3. 收集 color
            val color = when (groupColor.checkedChipId) {
                R.id.filter_color_bw -> "black_and_white"
                R.id.filter_color_black -> "black"
                R.id.filter_color_white -> "white"
                R.id.filter_color_yellow -> "yellow"
                R.id.filter_color_orange -> "orange"
                R.id.filter_color_red -> "red"
                R.id.filter_color_purple -> "purple"
                R.id.filter_color_magenta -> "magenta"
                R.id.filter_color_green -> "green"
                R.id.filter_color_teal -> "teal"
                R.id.filter_color_blue -> "blue"
                else -> null // 没选颜色，传 null
            }

            // 更新筛选条件，如果 query 为空，它只会被静静地保存在 ViewModel 里。 如果 query 不为空，这行代码会瞬间触发底层的 flatMapLatest 执行网络请求并刷新列表！
            searchViewModel.updateFilters(sessionId, orderBy, color, orientation)

            dismiss()
        }

        cancelButton.setOnClickListener {
            dismiss()
        }

        // 状态回显：下次打开弹窗时，把刚才选的按钮重新高亮
        val params = searchViewModel.getParamsFlow(sessionId).value
        // 恢复 orderBy
        if (params.orderBy == "latest") {
            groupOrderBy.check(R.id.filter_latest)
        }

        // 恢复 orientation
        when (params.orientation) {
            "landscape" -> groupOrientation.check(R.id.filter_landscape)
            "portrait" -> groupOrientation.check(R.id.filter_portrait)
            "squarish" -> groupOrientation.check(R.id.filter_squarish)
        }

        // 恢复 color
        when (params.color) {
            "black_and_white" -> groupColor.check(R.id.filter_color_bw)
            "black" -> groupColor.check(R.id.filter_color_black)
            "white" -> groupColor.check(R.id.filter_color_white)
            "yellow" -> groupColor.check(R.id.filter_color_yellow)
            "orange" -> groupColor.check(R.id.filter_color_orange)
            "red" -> groupColor.check(R.id.filter_color_red)
            "purple" -> groupColor.check(R.id.filter_color_purple)
            "magenta" -> groupColor.check(R.id.filter_color_magenta)
            "green" -> groupColor.check(R.id.filter_color_green)
            "teal" -> groupColor.check(R.id.filter_color_teal)
            "blue" -> groupColor.check(R.id.filter_color_blue)
        }
        // 不再使用原生的 drawable 资源，换成我们自己写的平滑圆角
        // 获取背景色
        val bgColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val radiusPx = 32.dpToPx().toFloat() // 圆角大小，24dp
        // 把圆角背景设置在你的内部 View 上，而不是外部容器上
        view.background = SmoothCardDrawable(bgColor, radiusPx)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            // 找到系统的底层容器
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener

            // 【关键步骤】：彻底扒掉系统容器的“外衣”
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
    }
}