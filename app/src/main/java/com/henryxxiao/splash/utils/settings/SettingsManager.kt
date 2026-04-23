package com.henryxxiao.splash.utils.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 1. 全局单例委托：确保整个 App 只有一个 DataStore 实例
val Context.dataStore by preferencesDataStore(name = "Settings_Data")

class SettingsManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        // String 类型的 Key
        val KEY_PREVIEW = stringPreferencesKey("preview_quality")
        val KEY_DOWNLOAD = stringPreferencesKey("download_quality")
        val KEY_LOAD = stringPreferencesKey("load_type")
        val KEY_STYLE = stringPreferencesKey("theme_style")
        val KEY_LANGUAGE = stringPreferencesKey("app_language")
    }

    // ==========================================
    // 读取：字符串无缝转枚举
    // 使用 runCatching 防止未来修改枚举名导致旧应用崩溃，安全回退到默认值
    // ==========================================
    val previewFlow: Flow<PreviewQuality> = dataStore.data.map { prefs ->
        runCatching { enumValueOf<PreviewQuality>(prefs[KEY_PREVIEW] ?: "") }
            .getOrDefault(PreviewQuality.FLUENT)
    }

    val downloadFlow: Flow<DownloadQuality> = dataStore.data.map { prefs ->
        runCatching { enumValueOf<DownloadQuality>(prefs[KEY_DOWNLOAD] ?: "") }
            .getOrDefault(DownloadQuality.REGULAR)
    }

    val loadTypeFlow: Flow<LoadType> = dataStore.data.map { prefs ->
        runCatching { enumValueOf<LoadType>(prefs[KEY_LOAD] ?: "") }
            .getOrDefault(LoadType.POPULAR)
    }

    val styleFlow: Flow<ThemeStyle> = dataStore.data.map { prefs ->
        runCatching { enumValueOf<ThemeStyle>(prefs[KEY_STYLE] ?: "") }
            .getOrDefault(ThemeStyle.FOLLOW_SYSTEM)
    }

    val languageFlow: Flow<AppLanguage> = dataStore.data.map { prefs ->
        runCatching { enumValueOf<AppLanguage>(prefs[KEY_LANGUAGE] ?: "") }
            .getOrDefault(AppLanguage.FOLLOW_SYSTEM)
    }

    // ==========================================
    // 直接存入枚举的 .name (如 "DARK", "RAW")
    // ==========================================
    suspend fun savePreview(quality: PreviewQuality) {
        dataStore.edit { it[KEY_PREVIEW] = quality.name }
    }

    suspend fun saveDownload(quality: DownloadQuality) {
        dataStore.edit { it[KEY_DOWNLOAD] = quality.name }
    }

    suspend fun saveLoadType(type: LoadType) {
        dataStore.edit { it[KEY_LOAD] = type.name }
    }

    suspend fun saveStyle(style: ThemeStyle) {
        dataStore.edit { it[KEY_STYLE] = style.name }
    }

    suspend fun saveLanguage(language: AppLanguage) {
        dataStore.edit { it[KEY_LANGUAGE] = language.name }
    }
}