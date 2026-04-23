package com.henryxxiao.splash.utils.settings

// 主题模式枚举
enum class ThemeStyle {
    FOLLOW_SYSTEM, DARK, LIGHT
}

// 语言选项枚举 (保留 tag 用于API)
enum class AppLanguage(val tag: String) {
    FOLLOW_SYSTEM(""),
    ENGLISH("en-US"),
    JAPANESE("ja"),
    CHINESE_SIMPLE("zh-CN"),
    CHINESE_TW("zh-TW")
}

// 首页加载类型
enum class LoadType {
    POPULAR, NEWEST
}

// 预览画质枚举
enum class PreviewQuality {
    HIGH_DEF, FLUENT
}

// 下载画质枚举
enum class DownloadQuality {
    RAW, FULL, REGULAR
}