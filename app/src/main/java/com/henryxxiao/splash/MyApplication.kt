package com.henryxxiao.splash

import android.app.Application
import com.google.android.material.color.DynamicColors

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 自动跟随系统的动态颜色，IfAvailable已经判断Android版本，12及以上可用
        //DynamicColors.applyToActivitiesIfAvailable(this)
    }
}