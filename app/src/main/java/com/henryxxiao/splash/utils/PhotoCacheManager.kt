package com.henryxxiao.splash.utils

import android.content.Context
import com.henryxxiao.splash.data.SplashPhoto
import com.henryxxiao.splash.data.SplashTopic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

object PhotoCacheManager {
    // 专门用于存放详情数据的文件夹名
    private const val CACHE_DIR_NAME = "photo_details_cache"
    private const val TOPICS_FILE_NAME = "topics_cache.json"

    // 配置专用的 Json 解析器（必须开启忽略未知键，防止未来增删字段导致崩溃）
    private val cacheJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 写入磁盘：将对象转为 JSON 字符串，保存为 photoId.json
     */
    suspend fun savePhotoDetail(context: Context, photo: SplashPhoto) = withContext(Dispatchers.IO) {
        try {
            val file = File(getCacheDir(context), "${photo.id}.json")
            val jsonString = cacheJson.encodeToString(photo)
            file.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 读取磁盘：读取 JSON 文件并还原为 Kotlin 对象
     */
    suspend fun getPhotoDetail(context: Context, photoId: String): SplashPhoto? = withContext(Dispatchers.IO) {
        try {
            val file = File(getCacheDir(context), "$photoId.json")
            if (file.exists()) {
                val jsonString = file.readText()
                return@withContext cacheJson.decodeFromString<SplashPhoto>(jsonString)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    // 存入 Topics (序列化 List)
    suspend fun saveTopics(context: Context, topics: List<SplashTopic>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, TOPICS_FILE_NAME)
            file.writeText(cacheJson.encodeToString(topics))
        } catch (_: Exception) {}
    }

    // 读取 Topics
    suspend fun getTopics(context: Context): List<SplashTopic>? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, TOPICS_FILE_NAME)
            if (file.exists()) {
                return@withContext cacheJson.decodeFromString<List<SplashTopic>>(file.readText())
            }
        } catch (_: Exception) {}
        return@withContext null
    }

    /**
     * 计算详情缓存占用的总字节数 (Bytes)
     */
    suspend fun getCacheSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        var totalSize = 0L

        // A. 累加详情文件夹的大小
        val detailsDir = File(context.cacheDir, CACHE_DIR_NAME)
        totalSize += getFolderSize(detailsDir)

        // B. 累加 Topics 缓存文件的大小
        val topicsFile = File(context.cacheDir, TOPICS_FILE_NAME)
        if (topicsFile.exists()) {
            totalSize += topicsFile.length()
        }

        return@withContext totalSize
    }

    /**
     * 彻底清空详情缓存文件夹
     */
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        // A. 删掉详情文件夹
        val detailsDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (detailsDir.exists()) {
            detailsDir.deleteRecursively()
        }

        // B. 删掉 Topics 缓存文件
        val topicsFile = File(context.cacheDir, TOPICS_FILE_NAME)
        if (topicsFile.exists()) {
            topicsFile.delete()
        }
    }

    // 递归计算文件夹大小
    private fun getFolderSize(file: File?): Long {
        var size: Long = 0
        if (file != null && file.exists()) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child -> size += getFolderSize(child) }
            } else {
                size = file.length()
            }
        }
        return size
    }
}