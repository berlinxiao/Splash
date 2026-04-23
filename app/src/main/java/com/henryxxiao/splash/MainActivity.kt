package com.henryxxiao.splash

import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.henryxxiao.splash.utils.settings.SettingsManager
import com.henryxxiao.splash.utils.settings.ThemeStyle
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ✅ 在这里获取，确保拿到的是当前 Activity 关联的动态色
        // 1. 手动获取系统动态主色（Android 12+）
//        val systemPrimaryColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            // system_accent1_600 是浅色标准的 Primary 颜色 system_accent1_200是深色，这里取中间值500
//            getColor(android.R.color.system_accent1_500)
//        } else {
//            // 12以下使用你原本定义的固定颜色
//            MaterialColors.getColor(this, com.github.chrisbanes.photoview.R.attr.colorPrimary, getColor(R.color.colorPrimary))
//        }


        observeGlobalSettings()

        // 开启沉浸式全屏，Android15开始系统强制开启，这里统一为了统一体验全部开启。
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 关闭导航栏的半透明遮罩
            window.isNavigationBarContrastEnforced = false
        }

        // 必须通过 supportFragmentManager 获取 NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
    }

    /**
     * 监听配置：暗黑模式与多语言
     */
    private fun observeGlobalSettings() {
        val settingsManager = SettingsManager(applicationContext)

        // 深色模式
        lifecycleScope.launch {
            settingsManager.styleFlow.collect { style ->
                when (style) {
                    ThemeStyle.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    ThemeStyle.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    ThemeStyle.FOLLOW_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            }
        }

        // 多语言
        lifecycleScope.launch {
            settingsManager.languageFlow.collect { language ->
                val localeList = if (language.tag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(language.tag)
                }
                // 只要一调用这个，Android 系统会自动帮你无缝重启 UI 树应用新语言
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        }
    }


    override fun onSupportNavigateUp(): Boolean {
        // 配合 NavController 的官方返回逻辑
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}