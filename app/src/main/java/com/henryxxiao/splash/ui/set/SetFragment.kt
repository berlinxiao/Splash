package com.henryxxiao.splash.ui.set

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.transition.MaterialSharedAxis
import com.henryxxiao.splash.R
import com.henryxxiao.splash.databinding.FragmentSetBinding
import com.henryxxiao.splash.utils.PhotoCacheManager
import com.henryxxiao.splash.utils.settings.AppLanguage
import com.henryxxiao.splash.utils.settings.DownloadQuality
import com.henryxxiao.splash.utils.settings.LoadType
import com.henryxxiao.splash.utils.settings.PreviewQuality
import com.henryxxiao.splash.utils.settings.SettingsManager
import com.henryxxiao.splash.utils.settings.ThemeStyle
import com.henryxxiao.splash.utils.view.MenuItem
import com.henryxxiao.splash.utils.view.PopupWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.net.toUri

class SetFragment : Fragment() {
    private var _binding: FragmentSetBinding? = null
    private val binding get() = _binding!!
    private var popupWindow: PopupWindow? = null
    private var currentCacheSizeMB: Double = 0.0  // 当前的缓存大小，方便判断和做动画
    private val settingsManager by lazy { SettingsManager(requireContext()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = null //防止残影
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadCacheSize() // 异步加载并显示缓存大小
        setupTouchListeners()
        observeSettings()

        binding.clear.setOnClickListener {
            showConfirmSheet()
        }
        // 开启协程，异步获取初始状态
        viewLifecycleOwner.lifecycleScope.launch {
            // 异步等待拿到本地存储的最新主题状态
            val initialStyle = settingsManager.styleFlow.first()

            // 先摘掉监听器
            binding.switchDaynight.setOnCheckedChangeListener(null)

            binding.switchDaynight.isChecked = (initialStyle == ThemeStyle.DARK)

            // 斩断动画瞬间把 Switch 的内部 UI 强行拖拽到最终位置！
            binding.switchDaynight.jumpDrawablesToCurrentState()

            binding.switchDaynight.setOnCheckedChangeListener { _, isChecked ->
                val newStyle = if (isChecked) ThemeStyle.DARK else ThemeStyle.LIGHT
                viewLifecycleOwner.lifecycleScope.launch {
                    settingsManager.saveStyle(newStyle)
                }
            }
        }

        binding.version.setOnClickListener {
            if (parentFragmentManager.findFragmentByTag("LogSheetTag") == null) {
                val bottomSheet = VersionLogSheet()
                bottomSheet.show(parentFragmentManager, "LogSheetTag")
            }
        }

        binding.unsplash.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, "https://unsplash.com/".toUri())
            startActivity(intent)
        }

        binding.setBtnBack.setOnClickListener { findNavController().navigateUp() }
    }

    // ==========================================
    // 响应式 UI：监听流并自动更新UI状态和文字
    // ==========================================
    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 监听语言
                launch {
                    settingsManager.languageFlow.collectLatest { lang ->
                        binding.textLanguage.text = when (lang) {
                            AppLanguage.ENGLISH -> getString(R.string.set_english)
                            AppLanguage.JAPANESE -> getString(R.string.set_japanese)
                            AppLanguage.CHINESE_SIMPLE -> getString(R.string.set_chinese_simple)
                            AppLanguage.CHINESE_TW -> getString(R.string.set_chinese_tw)
                            AppLanguage.FOLLOW_SYSTEM -> getString(R.string.set_follow)
                        }
                    }
                }

                // 首页加载
                launch {
                    settingsManager.loadTypeFlow.collectLatest { load ->
                        binding.textLoad.text = when (load) {
                            LoadType.NEWEST -> getString(R.string.set_newest)
                            LoadType.POPULAR -> getString(R.string.set_popular)
                        }
                    }
                }

                // 监听预览画质
                launch {
                    settingsManager.previewFlow.collectLatest { preview ->
                        binding.textPreview.text = when (preview) {
                            PreviewQuality.FLUENT -> getString(R.string.set_fluent)
                            PreviewQuality.HIGH_DEF -> getString(R.string.set_hd)
                        }
                    }
                }

                // 监听下载画质
                launch {
                    settingsManager.downloadFlow.collectLatest { down ->
                        binding.textDownload.text = when (down) {
                            DownloadQuality.RAW -> getString(R.string.set_raw)
                            DownloadQuality.FULL -> getString(R.string.set_full)
                            DownloadQuality.REGULAR -> getString(R.string.set_regular)
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 触摸与弹窗触发逻辑
    // ==========================================
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListeners() {
        // 语言设置
        val languageOptions = listOf(
            AppLanguage.FOLLOW_SYSTEM to getString(R.string.set_follow),
            AppLanguage.ENGLISH to getString(R.string.set_english),
            AppLanguage.JAPANESE to getString(R.string.set_japanese),
            AppLanguage.CHINESE_SIMPLE to getString(R.string.set_chinese_simple),
            AppLanguage.CHINESE_TW to getString(R.string.set_chinese_tw)
        )
        binding.languages.setOnTouchListener { v, event ->
            showPopupOnTouch(v, event, settingsManager.languageFlow, languageOptions) { selectedEnum  ->
                settingsManager.saveLanguage(selectedEnum )
            }
        }

        // 首页加载类型
        val loadOptions = listOf(
            LoadType.NEWEST to getString(R.string.set_newest),
            LoadType.POPULAR to getString(R.string.set_popular)
        )
        binding.load.setOnTouchListener { v, event ->
            showPopupOnTouch(v, event, settingsManager.loadTypeFlow, loadOptions) { selectedEnum ->
                settingsManager.saveLoadType(selectedEnum)
            }
        }

        // 预览画质设置
        val previewOptions = listOf(
            PreviewQuality.HIGH_DEF to getString(R.string.set_hd),
            PreviewQuality.FLUENT to getString(R.string.set_fluent)
        )
        binding.preview.setOnTouchListener { v, event ->
            showPopupOnTouch(v, event, settingsManager.previewFlow, previewOptions) { selectedEnum ->
                settingsManager.savePreview(selectedEnum)
            }
        }

        // 下载画质设置
        val downloadOptions = listOf(
            DownloadQuality.RAW to  getString(R.string.set_raw),
            DownloadQuality.FULL to getString(R.string.set_full),
            DownloadQuality.REGULAR to getString(R.string.set_regular)
        )
        binding.download.setOnTouchListener { v, event ->
            showPopupOnTouch(v, event, settingsManager.downloadFlow, downloadOptions) { selectedEnum ->
                settingsManager.saveDownload(selectedEnum)
            }
        }
    }

    // ==========================================
    // 泛型万能弹窗触发器，通用的触摸事件拦截与弹窗触发器
    // ==========================================
    /**
     * @param T : Enum<T> 限制 T 必须是一个枚举类型
     */
    private fun <T : Enum<T>> showPopupOnTouch(
        anchor: View,
        event: MotionEvent,
        flow: Flow<T>,
        options: List<Pair<T, String>>,
        onSave: suspend (T) -> Unit
    ): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            anchor.performClick()
            viewLifecycleOwner.lifecycleScope.launch {
                // 瞬间抓取当前最新的枚举值
                val currentSelectedEnum = flow.first()

                popupWindow?.dismiss()

                // 映射成 MenuItem
                val items = options.mapIndexed { index, pair ->
                    MenuItem(
                        id = index, // ID 只是给 MenuItem 内部用的标识
                        title = pair.second,
                        isSelected = (currentSelectedEnum == pair.first),
                        showCheckWhenSelected = true
                    )
                }

                popupWindow = PopupWindow(
                    context = requireContext(),
                    items = items,
                    onItemClick = { clickedItem ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            val targetEnum = options[clickedItem.id].first
                            onSave(targetEnum)
                        }
                    }
                )
                popupWindow?.show(anchor, event.rawX, event.rawY)
            }
        }
        return false
    }

    // ==========================================
    //  Glide 缓存 和 数据缓存 的计算与清理
    // ==========================================

    @SuppressLint("DefaultLocale")
    private fun loadCacheSize() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 获取 Glide 缓存目录
                val glideCacheDir = Glide.getPhotoCacheDir(requireContext())
                val glideSizeBytes = getFolderSize(glideCacheDir)

                // B. 计算 PhotoDetail 详情 JSON 缓存大小
                val detailsSizeBytes = PhotoCacheManager.getCacheSizeBytes(requireContext())

                // C. 合计并转为 MB
                val totalBytes = glideSizeBytes + detailsSizeBytes
                currentCacheSizeMB = totalBytes / (1024.0 * 1024.0)

                // 切换回主线程更新 UI
                withContext(Dispatchers.Main) {
                    binding.textCache.text = String.format("%.2f MB", currentCacheSizeMB)
                }
            } catch (_: Exception) {
                currentCacheSizeMB = 0.0
                withContext(Dispatchers.Main) {
                    binding.textCache.text = getString(R.string.set_cache)
                }
            }
        }
    }

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

    private fun showConfirmSheet() {
        if (parentFragmentManager.findFragmentByTag("Confirm_sheet") != null) return
        // 智能拦截：毫无缓存时直接提示，不弹窗
        if (currentCacheSizeMB <= 0.01) {
            Toast.makeText(requireContext(), getString(R.string.set_no_cache), Toast.LENGTH_SHORT).show()
            return
        }

        // 核心修改：将 String.format 改成了直接传 Float 过去
        ConfirmSheet.newInstance(currentCacheSizeMB.toFloat()).apply {
            onConfirm = {
                // 收到回调，执行真正的清理
                clearCache()
            }
        }.show(parentFragmentManager, "Confirm_sheet")
    }

    private fun clearCache() {
        // 清理缓存必须慎重对待线程分配
        viewLifecycleOwner.lifecycleScope.launch {
            // 注意：此时屏幕上显示的是 ConfirmSheet 的动画。
            // 当这几行代码在后台执行完，ConfirmSheet 也刚好播完动画关闭了。

            // 1. 清理内存缓存 (强制要求在主线程执行)
            Glide.get(requireContext()).clearMemory()

            // 2. 清理磁盘缓存 (强制要求在后台 IO 线程执行)
            withContext(Dispatchers.IO) {
                // 清理图片磁盘缓存
                Glide.get(requireContext()).clearDiskCache()
                // 清理图片详情的缓存
                PhotoCacheManager.clearCache(requireContext())
            }

            // 3. 归零 UI
            currentCacheSizeMB = 0.0
            binding.textCache.text = getString(R.string.set_cache)
            Toast.makeText(requireContext(), getString(R.string.set_cache_clear), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 避免内存泄漏
        popupWindow?.dismiss()
        _binding = null
    }
}