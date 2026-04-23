package com.henryxxiao.splash.utils.view

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.henryxxiao.splash.R
import androidx.core.graphics.drawable.toDrawable
import com.google.android.material.color.MaterialColors

class PopupWindow(
    private val context: Context,
    private val items: List<MenuItem>,
    private val onItemClick: (MenuItem) -> Unit,
    // 菜单关闭时的回调
    private val onDismissListener: (() -> Unit)? = null
) {
    private var popup: PopupWindow? = null

    /**
     *  让菜单显示在点击坐标的下方
     */
    fun show(anchorView: View, rawTouchX: Float, rawTouchY: Float) {
        val inflater = LayoutInflater.from(context)
        // inflate 根布局（FrameLayout），再找到内部的 LinearLayout 容器
        val rootView = inflater.inflate(R.layout.popup_menu_main, null, false)
        val container = rootView.findViewById<LinearLayout>(R.id.menu_container)

        items.forEach { item ->
            val itemView = inflater.inflate(R.layout.popup_menu_item, container, false)

            val iconView = itemView.findViewById<ImageView>(R.id.item_icon)
            val titleView = itemView.findViewById<TextView>(R.id.item_title)

            when {
                // 已选中且需要显示勾——优先级最高
                item.isSelected && item.showCheckWhenSelected -> {
                    iconView.visibility = View.VISIBLE
                    iconView.setImageResource(R.drawable.ic_show_done)
                    iconView.setColorFilter(
                        MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.BLACK)
                    )
                }
                // 有普通图标
                item.icon != null -> {
                    iconView.visibility = View.VISIBLE
                    iconView.setImageResource(item.icon)
                    iconView.clearColorFilter()
                }
                // 既无图标也无勾选
                else -> {
                    iconView.visibility = View.INVISIBLE  // 保留占位保持对齐
                }
            }

            titleView.text = item.title

            itemView.setOnClickListener {
                onItemClick(item)
                dismiss()
            }

            container.addView(itemView)
        }
        popup = PopupWindow(
            rootView,
            dpToPx(220),
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            // 必须设置非 null 背景，点击外部才能关闭（Android 6 兼容关键）
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isFocusable = true
            isOutsideTouchable = true
            elevation = dpToPx(8).toFloat()
            animationStyle = R.style.PopupFadeAnim
        }

        // 直接用 rawTouchX/rawTouchY 作为屏幕坐标，不叠加 anchorView 位置
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val popupWidth = dpToPx(220)
        val estimatedHeight = items.size * dpToPx(48) + dpToPx(12) // 12 = 上下 padding

        var xOffset = rawTouchX.toInt()
        var yOffset = rawTouchY.toInt()

        // 超出右边界则左移
        if (xOffset + popupWidth > screenWidth) {
            xOffset = screenWidth - popupWidth - dpToPx(8)
        }
        // 超出下边界则上移
        if (yOffset + estimatedHeight > screenHeight) {
            yOffset -= estimatedHeight
        }
        // 保证不超出上边界
        if (yOffset < 0) yOffset = dpToPx(8)

        popup?.showAtLocation(anchorView, Gravity.NO_GRAVITY, xOffset, yOffset)
    }

    /**
     *  让菜单显示在目标按钮的正上方
     */
    fun showAboveAnchor(anchorView: View) {
        val inflater = LayoutInflater.from(context)
        val rootView = inflater.inflate(R.layout.popup_menu_main, null, false)
        val container = rootView.findViewById<LinearLayout>(R.id.menu_container)

        items.forEach { item ->
            val itemView = inflater.inflate(R.layout.popup_menu_item, container, false)

            val iconView = itemView.findViewById<ImageView>(R.id.item_icon)
            val titleView = itemView.findViewById<TextView>(R.id.item_title)

            when {
                // 已选中且需要显示勾——优先级最高
                item.isSelected && item.showCheckWhenSelected -> {
                    iconView.visibility = View.VISIBLE
                    iconView.setImageResource(R.drawable.ic_show_done)
                    iconView.setColorFilter(
                        MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.BLACK)
                    )
                }
                // 有普通图标
                item.icon != null -> {
                    iconView.visibility = View.VISIBLE
                    iconView.setImageResource(item.icon)
                    iconView.clearColorFilter()
                }
                // 既无图标也无勾选
                else -> {
                    iconView.visibility = View.INVISIBLE  // 保留占位保持对齐
                }
            }

            titleView.text = item.title

            itemView.setOnClickListener {
                onItemClick(item)
                dismiss()
            }

            container.addView(itemView)
        }

        popup = PopupWindow(
            rootView,
            dpToPx(220),
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isFocusable = true
            isOutsideTouchable = true
            elevation = dpToPx(8).toFloat()
            animationStyle = R.style.PopupFadeAnim

            // 绑定系统的关闭监听器，触发我们自己的回调
            setOnDismissListener {
                onDismissListener?.invoke()
            }
        }

        // ==========================================
        // 精准的相对坐标计算 (基于目标按钮)
        // ==========================================
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val anchorX = location[0]
        val anchorY = location[1]

        val popupWidth = dpToPx(220)
        // 估算菜单高度
        val estimatedHeight = items.size * dpToPx(48) + dpToPx(12)

        // X坐标：让菜单的右边缘和按钮的右边缘对齐
        var xOffset = anchorX + anchorView.width - popupWidth
        if (xOffset < dpToPx(8)) xOffset = dpToPx(8) // 屏幕左侧防出界保护

        // Y坐标：在按钮正上方
        var yOffset = anchorY - estimatedHeight - dpToPx(8)

        // 如果上方空间不够了，就改为显示在按钮正下方
        if (yOffset < 0) {
            yOffset = anchorY + anchorView.height + dpToPx(8)
        }

        popup?.showAtLocation(anchorView, Gravity.NO_GRAVITY, xOffset, yOffset)
    }

    fun dismiss() {
        if (popup?.isShowing == true) popup?.dismiss()
        popup = null
    }

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density + 0.5f).toInt()
}