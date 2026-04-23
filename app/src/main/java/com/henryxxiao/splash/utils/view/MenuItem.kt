package com.henryxxiao.splash.utils.view

data class MenuItem(
    val id: Int,
    val icon: Int ? = null, // 普通状态图标 drawable res，传入null时不显示
    val title: String,
    val isSelected: Boolean = false,  // 勾选效果
    val showCheckWhenSelected: Boolean = true  // 无图标时是否显示勾
)
