package com.henryxxiao.splash.utils.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.henryxxiao.splash.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

class PhotoDownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val KEY_URL = "key_url"
        const val KEY_PHOTO_ID = "key_photo_id"
        const val KEY_USER_NAME = "key_user_name"
        const val KEY_QUALITY = "key_quality"

        // 将下载进度和完成的通道分开，防止通知被降级拦截
        private const val CHANNEL_PROGRESS_ID = "download_progress"
        private const val CHANNEL_COMPLETE_ID = "download_complete"

        // 提升为单例，连接池、线程池复用，高频下载时内存和线程资源不会浪费。
        val client: OkHttpClient by lazy {
            OkHttpClient.Builder() //RAW 原图动辄 30MB，默认 10s 极易超时中断，放宽到 60s
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val photoId = inputData.getString(KEY_PHOTO_ID) ?: return Result.failure()
        val userName = inputData.getString(KEY_USER_NAME) ?: "Unknown"
        val quality = inputData.getString(KEY_QUALITY) ?: "REGULAR" // 取出画质

        // 利用 photoId 自身的唯一性生成稳定 ID
        val progressNotificationId = ((photoId + quality).hashCode() and 0x7FFFFFFF)
        // 完成通知必须用不同的 ID，防止被 WorkManager 销毁前台服务时一并误杀
        val completeNotificationId = progressNotificationId + 1

        createNotificationChannels()

        val fileName = "Splash_${photoId}_${quality}.jpg"
        // 外部存储在 Android 6 的低配机、部分双卡机型上随时可能不可用。
        // 防止externalCacheDir空指针崩溃，部存储被卸载时返回 null
        val cacheDir = context.externalCacheDir ?: context.cacheDir // 降级到内部缓存
        val cacheFile = File(cacheDir, fileName)

        var isRetrying = false // 用于标记本次异常是否触发了重试

        // 立刻启动前台服务，让进度条显示出来
        setForeground(createForegroundInfo(progressNotificationId, userName, quality, 0))

        return try {
            downloadFile(url, cacheFile, progressNotificationId, userName, quality)
            val finalUri = moveToPublicStorage(cacheFile, fileName)
            if (finalUri != null) {
                showSuccessNotification(completeNotificationId, userName, quality, finalUri)
                Result.success()
            } else {
                showErrorNotification(completeNotificationId,  context.getString(R.string.download_failed_gallery))
                Result.failure()
            }
        } catch (e: Exception) {
            // 针对网络瞬断进行最高 3 次的智能重试
            when (e) {
                // 捕获 downloadFile里抛出的CancellationException，用户点击的“取消操作”
                is CancellationException -> {
                    showCancelNotification(completeNotificationId, userName, quality)
                    throw e // 重要：在协程中捕获到取消异常后，必须重新抛出，才能让协程完美终止！
                }
                is java.net.SocketTimeoutException,
                is java.net.UnknownHostException -> {
                    // 纯网络问题，本地缓存可信，断点续传继续
                    if (runAttemptCount < 3) {
                        isRetrying = true // 标记为正在重试，保护断点续传文件
                        Result.retry()
                    }
                    else {
                        showErrorNotification(completeNotificationId,  context.getString(R.string.download_network_error_3))
                        Result.failure()
                    }
                }
                is java.io.IOException -> {
                    // IOException 可能是本地文件 I/O 问题，缓存文件不可信
                    // 清除后重试，让下次从头下载
                    cacheFile.delete()
                    if (runAttemptCount < 3) {
                        isRetrying = true  // 告知 finally 不需要再处理
                        Result.retry()  // isRetrying 保持 false，finally 不会二次 delete（已手动删除）
                    } else {
                        showErrorNotification(completeNotificationId,  context.getString(R.string.download_io_error_3))
                        Result.failure()
                    }
                }
                else -> {
                    showErrorNotification(completeNotificationId, e.localizedMessage ?: "Unknown Error")
                    Result.failure()
                }
            }
        } finally {
            // 如果成功，moveToPublicStorage 内部会删文件；
            // 如果发生了彻底失败（且不打算重试），我们才在这里删除文件，防止磁盘泄露。否则cacheFile里的残缺数据永远留在磁盘。多次失败重试会持续堆积。
            if (!isRetrying && cacheFile.exists()) {
                cacheFile.delete()
            }
        }
    }

    private suspend fun downloadFile(url: String, cacheFile: File, notificationId: Int, userName: String, quality: String) {
        withContext(Dispatchers.IO) {
            //  断点续传的唯一的事实来源，保持一致性
            val resumeFrom = if (cacheFile.exists()) cacheFile.length() else 0L
            val request = Request.Builder().url(url)
                .header("Range", "bytes=$resumeFrom-")
                .build()

            // 将 request 包装成单独的 call 变量
            val call = client.newCall(request)

            // ---- 进度上报管道 ----
            // CONFLATED：Channel 只保留最新一条，避免积压
            val progressChannel = Channel<Int>(Channel.CONFLATED)
            // 消费者协程：唯一可以调用 suspend setForeground 的地方
            val progressJob = launch(CoroutineExceptionHandler { _, _ -> /* 忽略通知失败 */ }) {
                for (progress in progressChannel) {
                    try {
                        setForeground(createForegroundInfo(notificationId, userName, quality, progress))
                    } catch (_: Exception) {
                        // setForeground 失败不应终止下载，静默忽略
                    }
                }
            }

            try {

                // runInterruptible：协程取消时自动 interrupt 当前线程
                // 被 interrupt 的线程在 OkHttp socket read() 处抛出 InterruptedIOException
                runInterruptible {
                    // 使用包装好的 call
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            // HTTP 416 表示范围越界（文件已下完），需要校验文件本地文件是否完整
                            if (response.code == 416){
                                // 用 HEAD 请求拿到服务端真实文件大小进行比对
                                val headRequest = Request.Builder().url(url).head().build()
                                val serverSize = client.newCall(headRequest).execute().use { headResponse ->
                                    if (!headResponse.isSuccessful) {
                                        cacheFile.delete()
                                        throw Exception("HEAD failed: ${headResponse.code}")
                                    }
                                    headResponse.header("Content-Length")?.toLongOrNull()
                                }
                                if (serverSize != null && cacheFile.length() == serverSize) {
                                    // 本地字节数与服务端完全一致 → 文件完整，视为已下完
                                    return@runInterruptible
                                } else {
                                    // 大小不一致 → 本地文件损坏或服务端文件已更新，删除重来
                                    cacheFile.delete()
                                    throw Exception( context.getString(R.string.download_error_416))
                                }
                            }
                            throw Exception("HTTP ${response.code}")
                        }

                        val body = response.body ?: throw Exception("Empty body")
                        // totalSize 计算用同一个 resumeFrom
                        val isResuming = response.code == 206

                        val totalSize = if (isResuming)
                            body.contentLength() + resumeFrom
                        else
                            body.contentLength()

                        var downloadedSoFar = if (isResuming) resumeFrom else 0L

                        body.byteStream().use { input ->
                            RandomAccessFile(cacheFile, "rw").use { raf ->
                                // 写入起点用同一个 resumeFrom，200 时无论 resumeFrom 是多少，都从头清空重写
                                if (isResuming) raf.seek(resumeFrom) else raf.setLength(0)
                                val buffer = ByteArray(65536) // 64KB 高吞吐
                                var bytesRead: Int
                                var lastPercent = -1
                                // 用整除跨越判断
                                var lastChunk = resumeFrom / (512 * 1024)

                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    raf.write(buffer, 0, bytesRead)
                                    downloadedSoFar += bytesRead

                                    // totalSize 已知，百分比模式
                                    if (totalSize > 0) {
                                        val percent = (downloadedSoFar * 100 / totalSize)
                                            .toInt().coerceIn(0, 100)
                                        if (percent != lastPercent && percent % 10 == 0) {
                                            lastPercent = percent
                                            // trySend 非挂起，可在普通 lambda 内安全调用
                                            progressChannel.trySend(percent)
                                        }
                                    } else {
                                        // totalSize 未知，每下载 512KB 刷新一次不定态进度
                                        val currentChunk = downloadedSoFar / (512L * 1024)
                                        if (currentChunk > lastChunk) {
                                            lastChunk = currentChunk
                                            progressChannel.trySend(-1) //  进度不可知，触发 indeterminate 模式（进度条来回跑）
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: InterruptedIOException) {
                // 线程被 interrupt → OkHttp 抛出 InterruptedIOException
                // 包装为 CancellationException，外层 doWork 的 catch 块可以正确识别
                throw CancellationException(context.getString(R.string.download_cancel_by_user), e)
            } finally {
                progressChannel.close() // 关闭 Channel，for 循环自然耗尽后退出

                if (coroutineContext.isActive) {
                    progressJob.join()         // 正常结束：等待最后一帧进度处理完
                } else {
                    progressJob.cancelAndJoin() // 取消/异常：不需要等，立即终止
                }

                call.cancel() // 无论如何都调用，彻底释放 TCP 连接
            }
        }
    }



    // ==========================================
    // 写入 MediaStore 与相册刷新机制
    // ==========================================
    private suspend fun moveToPublicStorage(source: File, fileName: String): android.net.Uri? {
        return withContext(Dispatchers.IO) {
            try {
                // android 10+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "Splash")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext null

                    try {
                        val outputStream = resolver.openOutputStream(uri)
                            ?: throw androidx.datastore.core.IOException("openOutputStream returned null")

                        source.inputStream().use { input ->
                            outputStream.use { output -> input.copyTo(output) }
                        }

                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        val rows = resolver.update(uri, values, null, null)

                        if (rows <= 0) {
                            resolver.delete(uri, null, null)
                            throw Exception(context.getString(R.string.download_failed_media))
                        }

                        source.delete()
                        uri

                    } catch (e: Exception) {
                        // 任何失败都清除 Pending 记录，杜绝僵尸文件
                        resolver.delete(uri, null, null)
                        throw e // 继续往外抛，由 doWork 的 catch 处理
                    }
                } else {
                    // android 6 - 9
                    val destDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Splash")
                    destDir.mkdirs()
                    val dest = File(destDir, fileName)
                    try {
                        // copy 中断 → dest 已存在但不完整
                        if (!source.renameTo(dest)) {
                            FileInputStream(source).use { input ->
                                FileOutputStream(dest).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            source.delete()
                        }
                    } catch (e: Exception) {
                        dest.delete() // 关键补偿
                        throw e
                    }
                    // 触发系统级媒体扫描，秒出图库
                    // suspendCancellableCoroutine 将异步 scanFile 桥接为挂起函数
                    // 只有扫描真正完成，才会 resume 并返回 MediaStore content:// URI
                    val scannedUri = withTimeoutOrNull(10_000L){ // 10 秒超时
                        suspendCancellableCoroutine<Uri?> { continuation ->
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(dest.absolutePath),
                                arrayOf("image/jpeg")
                            ) { _, uri ->
                                // Result.success 包装，兼容新版 API，无弃用警告
                                // 检查 isActive，防止超时后延迟回调导致的 Already resumed 崩溃
                                if (continuation.isActive) {
                                    continuation.resumeWith(kotlin.Result.success(uri))
                                }
                            }
                            // scanFile 本身无法中止，取消时忽略回调结果即可
                            continuation.invokeOnCancellation { /* no-op */ }
                        }
                    } // 超时返回 null，降级为 FileProvider URI
                    // scannedUri 极少数情况下为 null（媒体数据库写入失败）
                    // 降级为 FileProvider URI 作为兜底，保证函数不返回 null
                    scannedUri ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    // ==========================================
    // 双通道通知栏管理机制
    // ==========================================
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 下载中的通道：LOW (不发出声音，不会弹出悬浮窗)
            val progressChannel = NotificationChannel(CHANNEL_PROGRESS_ID,  context.getString(R.string.download_downloading), NotificationManager.IMPORTANCE_LOW)
            // 下载完的通道：HIGH (会弹出横幅或发出提示音，提醒用户去相册看)
            val completeChannel = NotificationChannel(CHANNEL_COMPLETE_ID,  context.getString(R.string.download_complete), NotificationManager.IMPORTANCE_HIGH)

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(completeChannel)
        }
    }

    private fun createForegroundInfo(notificationId: Int, userName: String, quality: String, progress: Int): ForegroundInfo {
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setContentTitle("Downloading ($quality) - $userName")
            .setSmallIcon(R.drawable.ic_show_download)
            .setProgress(100, if (progress < 0) 0 else progress, progress < 0) //-1 → indeterminate 转圈，0~100 → 正常百分比
            .setOngoing(true)
            // 加上取消按钮
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,  context.getString(R.string.download_cancel),WorkManager.getInstance(context).createCancelPendingIntent(id))
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, builder.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, builder.build())
        }
    }

    // 用户点击取消后的反馈提示
    private fun showCancelNotification(notificationId: Int, userName: String, quality: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE_ID)
            .setContentTitle(context.getString(R.string.download_download_canceled))
            .setContentText(context.getString(R.string.download_canceled, userName, quality))
            .setSmallIcon(R.drawable.ic_show_download)
            .setAutoCancel(true)
        notificationManager.notify(notificationId, builder.build())
    }

    private fun showSuccessNotification(notificationId: Int, userName: String, quality: String, uri: android.net.Uri) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pendingIntent = PendingIntent.getActivity(context, notificationId, viewIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE_ID)
            .setContentTitle(context.getString(R.string.download_saved,userName, quality))
            .setContentText(context.getString(R.string.download_tap))
            .setSmallIcon(R.drawable.ic_show_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun showErrorNotification(notificationId: Int, error: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE_ID)
            .setContentTitle(context.getString(R.string.download_failed))
            .setContentText(error)
            .setSmallIcon(R.drawable.ic_show_close)
            .setAutoCancel(true)
        notificationManager.notify(notificationId, builder.build())
    }
}