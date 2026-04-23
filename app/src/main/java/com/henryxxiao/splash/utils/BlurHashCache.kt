package com.henryxxiao.splash.utils

import android.graphics.Bitmap
import android.util.LruCache
import com.vanniktech.blurhash.BlurHash

object BlurHashCache {
    // 缓存最多 100 个模糊占位图（因为分辨率极小，100个也才占用不到 1MB 内存）
    private val cache = LruCache<String, Bitmap>(100)

    fun getBitmap(blurHash: String, width: Int, height: Int): Bitmap? {
        // 如果内存里已经有了解码好的占位图，瞬间返回
        cache.get(blurHash)?.let { return it }

        // 如果没有，开始解码
        return try {
            // punch 参数 (默认 1f) 控制对比度，越大色彩越鲜艳
            val bitmap = BlurHash.decode(blurHash, width, height, punch = 1f)
            if (bitmap != null) {
                cache.put(blurHash, bitmap)
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}