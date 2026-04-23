package com.henryxxiao.splash.ui.show

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.PathParser
import androidx.core.graphics.toColorInt
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.transition.Transition
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialContainerTransform
import com.henryxxiao.splash.R
import com.henryxxiao.splash.data.SplashPhoto
import com.henryxxiao.splash.databinding.FragmentShowBinding
import com.henryxxiao.splash.utils.SharedPhotoViewModel
import com.henryxxiao.splash.utils.animation.LoadingTextManager
import com.henryxxiao.splash.utils.animation.StaggerAnimator
import com.henryxxiao.splash.utils.download.PhotoDownloadWorker
import com.henryxxiao.splash.utils.settings.DownloadQuality
import com.henryxxiao.splash.utils.settings.PreviewQuality
import com.henryxxiao.splash.utils.settings.PreviewQuality.*
import com.henryxxiao.splash.utils.settings.SettingsManager
import com.henryxxiao.splash.utils.view.MenuItem
import com.henryxxiao.splash.utils.view.PopupWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShowFragment : Fragment() {

    private var _binding: FragmentShowBinding? = null
    private val binding get() = _binding!!

    // 用于存储当前页面的临时下载画质 ， 返回页面时不会重复获取
    private var tempDownloadQuality: DownloadQuality? = null
    private var tempPreviewQuality: PreviewQuality? = null

    // 用于保存自定义弹窗实例，方便在页面销毁时关闭
    private var customPopupWindow: PopupWindow? = null // 引用自定义 PopupWindow 类

    // 全局唯一实例，懒加载。传入 applicationContext 安全。整个 Fragment 生命周期内只创建一次
    private val settingsManager by lazy { SettingsManager(requireContext().applicationContext) }

    private val sharedViewModel: SharedPhotoViewModel by activityViewModels() // 使用 activityViewModels() 获取宿主共享内存
    private lateinit var currentPhotoId: String
    private lateinit var currentPhoto: SplashPhoto // 数据共享，直接引用
//    private var memCacheUrl: String? = null

    private var isFirstEntry = true // Fragment 实例存活期间只有首次进入是 true
    private var shouldPlayEnterAnim = false // 由 onViewCreated 控制本次是否需要播

    private val staggerAnimator = StaggerAnimator()
    private val animTargets: List<View> by lazy { collectAnimTargets() } // 其余信息的进场动画，收集所有要参与动画的 View

    // 权限请求启动器
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                checkNotifyAndChannel()
            } else {
                showPermissionSnackbar(getString(R.string.show_per_storage))
            }
        }

    // 成员变量：Context 就绪后懒加载一次，之后复用
    private val qualityOptions: List<Pair<DownloadQuality, String>> by lazy {
        QUALITY_ENTRIES.map { quality ->
            quality to getString(
                when (quality) {
                    DownloadQuality.RAW -> R.string.set_raw
                    DownloadQuality.FULL -> R.string.set_full
                    DownloadQuality.REGULAR -> R.string.set_regular
                }
            )
        }
    }

    companion object {
        // 过渡动画曲线 只解析一次，整个 App 生命周期复用
        private val SHARED_TRANSITION: MaterialContainerTransform by lazy {
            val path = PathParser.createPathFromPathData(
                "M 0,0 C 0.05, 0, 0.133333, 0.06, 0.166666, 0.4 C 0.208333, 0.82, 0.25, 1, 1, 1"
            )
            MaterialContainerTransform().apply {
                drawingViewId = R.id.nav_host_fragment
                duration = 450L
                interpolator = PathInterpolator(path)
                scrimColor = Color.TRANSPARENT
            }
        }

        // 枚举顺序固定，只需定义一次
        private val QUALITY_ENTRIES = listOf(
            DownloadQuality.RAW,
            DownloadQuality.FULL,
            DownloadQuality.REGULAR
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 轨道一：共享元素（主图卡片）的飞行
//        val transform = MaterialContainerTransform().apply {
//
//            drawingViewId = R.id.nav_host_fragment
//            duration = 450L //过渡动画时长
//            interpolator = ENTER_INTERPOLATOR
//            scrimColor = Color.TRANSPARENT
//            //setAllContainerColors(com.google.android.material.R.attr.colorOnPrimary)
//            // 保持圆角变直角的完美贴合
//            //shapeMaskProgressThresholds = MaterialContainerTransform.ProgressThresholds(0.85f, 1.0f)
//        }

        // 进入和返回须同时赋值
        sharedElementEnterTransition = SHARED_TRANSITION
        sharedElementReturnTransition = SHARED_TRANSITION

        // 详情页其他内容：不用系统 enterTransition，我们自己控制
        // 设为 null 避免系统 Fade 干扰我们的手动动画
        enterTransition = null
        returnTransition = null
    }

    private fun scheduleEnterAnimation() {
        // 等共享元素过渡开始后再播放进场动画
        // postponeEnterTransition / startPostponedEnterTransition 在 ListFragment 中控制
        view?.doOnPreDraw {
            staggerAnimator.enter(
                views = animTargets,
                config = StaggerAnimator.Config(
                    translateYDp = 36f,  //初始向下偏移位置
                    startDelayMs = 150L,  // 首行延迟
                    staggerMs = 48L,  //行间错开
                    durationMs = 350L  //单行时长
                )
            )
        }
    }

    private fun collectAnimTargets(): List<View> {
        // 按视觉顺序收集行，同一行作为一个节奏单位一起进场
        return with(binding) {
            listOf(
                showLinearLayout1,
                showLinearLayout2,
                showCard1, showTextViewInfo,
                showCard2, showTextViewStatistics,
                showCard3, showTextViewDetails
            )
        }
    }

    private fun playExitAndPop() {
        // 先播退场动画，结束后再 pop（这样返回过渡和内容退场能协调）
        staggerAnimator.exit(
            views = animTargets,
            config = StaggerAnimator.Config(
                translateYDp = 10f,
                staggerMs = 20L,
                durationMs = 80L
            )
        ) {
            // 动画结束后触发共享元素返回过渡
            parentFragmentManager.popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 1. 暂停动画，等待低清图加载完毕再播
        postponeEnterTransition()

        // 1. 从 Bundle 中读取当前 Fragment 专属的身份证（ID）
        currentPhotoId = arguments?.getString("ARG_PHOTO_ID") ?: run {
            findNavController().navigateUp()
            return
        }
        //memCacheUrl = arguments?.getString("ARG_MEM_CACHE_URL")

        // 2. 拿着 ID 去全局内存池里取回“我的”大对象
        val photo = sharedViewModel.getPhotoFromPool(currentPhotoId)
        if (photo == null) {
            // 极限边缘情况防崩溃：如果 App 在后台被杀导致内存池清空，直接退出或重新请求网络
            findNavController().navigateUp()
            return
        }
        currentPhoto = photo

        // 配置视图过渡名称（和 HomeFragment 保持一致）动画名称一致，返回再多次也不会错乱
        binding.showImageView.transitionName = "photo_${currentPhoto.id}"

        // 【放行动画的时机】 等待整个根视图（包括上面的占位图）完成精准的尺寸测量
        binding.root.doOnPreDraw {
            startPostponedEnterTransition()
            if (shouldPlayEnterAnim) {
                shouldPlayEnterAnim = false
                scheduleEnterAnimation() // 其余内容的进场动画
                hdImageTransitionListener.onTransitionEnd(SHARED_TRANSITION)
                // 批量开启 Loading 动画
                loadingManager.register(
                    binding.loadingView1, binding.loadingView2, binding.loadingView3,
                    binding.loadingView4, binding.loadingView5, binding.loadingView6,
                    binding.loadingView7, binding.loadingView8, binding.loadingView9
                )
            }
        }

        // ---- 仅首次进入执行 ----
        if (isFirstEntry) {
            isFirstEntry = false
            shouldPlayEnterAnim = true

            // 在页面初始化时，统一开一个协程预取所需的默认设置
            viewLifecycleOwner.lifecycleScope.launch {
                // 瞬间抓取全局默认下载画质，赋值给临时变量
                tempDownloadQuality = settingsManager.downloadFlow.first()
                tempPreviewQuality = settingsManager.previewFlow.first() // 一并预取
            }
        }

        // 4. 初始化基础 UI 和两段式无缝动画
        setupBasicUI()
        initInstantTransitionImage()
        // 触发并监听详细数据 (EXIF, Tags)
        observeDetailedData()

        // 拦截系统返回，手动控制退场顺序
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    playExitAndPop()
                }
            }
        )

    }

    private fun setupBasicUI() {
        // 设置宽高比防形变
        val ratio = currentPhoto.height / currentPhoto.width
        binding.showImageView.aspectRatio = ratio

        // 安全解析背景色
        try {
            binding.showImageView.setBackgroundColor((currentPhoto.color ?: "#333333").toColorInt())
        } catch (_: Exception) {
            binding.showImageView.setBackgroundColor("#333333".toColorInt())
        }

        binding.showTextViewName.text = currentPhoto.user.name

//        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
//        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
//        // 转换回普通字符串
//        val date = try {
//            parser.parse(currentPhoto.createdAt ?: "")
//        } catch (_: Exception) { null }
//
//        binding.showTextDate.text = if (date != null) {
//            formatter.format(date)
//        } else {
//            currentPhoto.createdAt ?: ""  // 降级展示原始字符串
//        }
        val dateText = currentPhoto.createdAt
            ?.take(19)          // 取前19个字符 "2016-05-03T11:00:28"
            ?.replace('T', ' ') // 把 T 替换成空格
            ?: ""
        binding.showTextDate.text = dateText

        // 加载头像
        Glide.with(this) // 使用 Fragment 级别的 this
            .load(currentPhoto.user.profileImage?.large ?: currentPhoto.user.profileImage?.small)
            .apply(com.bumptech.glide.request.RequestOptions.bitmapTransform(CircleCrop()))
            .into(binding.showImageViewHead)

        // 返回事件
        //binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        // 动态分配唯一的过渡名称
        val avatarTransitionName = "avatar_${currentPhoto.user.id}"
        binding.showImageViewHead.transitionName = avatarTransitionName
        // 设置点击跳转事件
        binding.showImageViewHead.setOnClickListener {
            // 把 User 存入池中
            sharedViewModel.cacheUserToPool(currentPhoto.user)
            // 创建 Navigation 专属的共享元素 Extras
            // 语法：视图对象 to "过渡名称"
            val extras = FragmentNavigatorExtras(
                binding.showImageViewHead to avatarTransitionName,
            )
            val bundle = Bundle().apply {
                putString("ARG_USER_ID", currentPhoto.user.id)
            }
            // 跳转执行
            findNavController().navigate(
                resId = R.id.action_nav_show_to_nav_user,
                args = bundle,
                navOptions = null,
                navigatorExtras = extras // 把共享动画配置塞进去
            )
        }
        binding.showTextViewName.setOnClickListener { binding.showImageViewHead.callOnClick() }

        binding.showImageView.setOnClickListener {
            showPhotoPopup(currentPhoto.urls.regular, currentPhoto.urls.small)
        }

        binding.showBtnPreset.setOnClickListener {
            val bundle = Bundle().apply {
                putString("ARG_PHOTO_ID", currentPhotoId)
            }
            findNavController().navigate(R.id.action_nav_show_to_nav_preset,bundle)
        }
        binding.showBtnDownload.setOnClickListener { checkPermissions() }


        // [右侧] 下拉按钮
        binding.showBtnQuality.setOnClickListener { view ->
            // 获取当前应该打勾的选中项 (如果还没加载出来则默认使用 REGULAR)
            val currentSelected = tempDownloadQuality ?: DownloadQuality.REGULAR

            // 映射成你的自定义 MenuItem 列表
            val items = qualityOptions.map { (quality, title) ->
                MenuItem(
                    id = quality.ordinal,
                    title = title,
                    isSelected = (currentSelected == quality),
                    showCheckWhenSelected = true
                )
            }

            // 构建弹窗并注册关闭回调
            val popup = PopupWindow(
                requireContext(), items,
                onItemClick = { item ->
                    val selectedQuality = DownloadQuality.entries.find { it.ordinal == item.id }
                    if (selectedQuality != null && selectedQuality != tempDownloadQuality) {
                        tempDownloadQuality = selectedQuality
                        if (!binding.showBtnDownload.isEnabled) {
                            binding.showBtnDownload.text = getString(R.string.show_download)
                            binding.showBtnDownload.isEnabled = true // 恢复可点击
                            binding.showBtnDownload.playOneShotGlow()
                            binding.showBtnDownload.isClickable = true
                        }
                    }
                },
                onDismissListener = {
                    // 弹窗关闭时，不管有没有点选项，箭头必须翻回来！
                    binding.showBtnQuality.isChecked = false
                }
            )

            // 4. 使用专门的固定锚点方法弹出
            popup.showAboveAnchor(view)
        }
    }

    // ==========================================
    // 两段式动画黑科技：第一段 (读取低清缓存放行动画)
    // ==========================================
    private fun initInstantTransitionImage() {
        // 我们假设在 HomeFragment 的 Adapter 中使用的是 urls.small 作为封面
        val memCacheUrl = currentPhoto.urls.small

        Glide.with(this) // Fragment 生命周期绑定
            .load(memCacheUrl)
            .onlyRetrieveFromCache(true) // 强行只读内存
            .dontAnimate()               // 禁用内部渐变，防冲突
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    startPostponedEnterTransition()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    startPostponedEnterTransition()
                    return false
                }
            })
            .into(binding.showImageView)
    }

    // ==========================================
    // 两段式动画黑科技：第二段 (动画结束替换高清)
    // ==========================================
    // listener 提取为成员变量，只创建一次
    abstract class SimpleTransitionListener : Transition.TransitionListener {
        override fun onTransitionStart(transition: Transition) {}
        override fun onTransitionCancel(transition: Transition) {}
        override fun onTransitionPause(transition: Transition) {}
        override fun onTransitionResume(transition: Transition) {}
    }

    private val hdImageTransitionListener = object : SimpleTransitionListener() {
        override fun onTransitionEnd(transition: Transition) {
            // onTransitionEnd 早于最后一帧渲染，加一帧延迟对齐真实落定时机
            binding.root.post {
                loadHighResImage()
            }
            transition.removeListener(this)
        }

        override fun onTransitionCancel(transition: Transition) {
            transition.removeListener(this)
        }
    }
//    private fun listenToTransitionEndAndLoadHD() {
//        // Fragment 的共享过渡是 Any? 类型，必须强转为 androidx.transition.Transition
//        val transition = sharedElementEnterTransition as? Transition
//
//        if (transition != null) {
//            transition.addListener(object : Transition.TransitionListener {
//                override fun onTransitionEnd(transition: Transition) {
//                    loadHighResImage()
//                    transition.removeListener(this)
//                }
//
//                override fun onTransitionStart(transition: Transition) {}
//                override fun onTransitionCancel(transition: Transition) {
//                    transition.removeListener(this)
//                }
//
//                override fun onTransitionPause(transition: Transition) {}
//                override fun onTransitionResume(transition: Transition) {}
//            })
//        } else {
//            loadHighResImage()
//        }
//    }

    private fun loadHighResImage() {
        val hdUrl = when (tempPreviewQuality ?: HIGH_DEF) {
            HIGH_DEF -> currentPhoto.urls.regular ?: currentPhoto.urls.full
            FLUENT -> null // 直接赋值null让其不必往下执行
        }
        if (!hdUrl.isNullOrEmpty()) {
            Glide.with(this@ShowFragment)
                .load(hdUrl)
                .dontAnimate()
                .thumbnail(Glide.with(this@ShowFragment).load(currentPhoto.urls.small))
                .transition(DrawableTransitionOptions.withCrossFade(300))
                .into(binding.showImageView)
        }
    }

    // ==========================================
    // 监听与渲染详情数据 (Tags/Exif)
    // ==========================================
    private val loadingManager = LoadingTextManager()
    private fun observeDetailedData() {
        // 空字符，避免在协程中重复读取 Resources
        val nullText = getString(R.string.show_text_null)

        viewLifecycleOwner.lifecycleScope.launch {
            // 挂起等待结果：直接根据当前的 currentPhotoId 去拿专属数据！
            // 这里不会阻塞主线程，拿到结果后会自动继续往下走
            val detailPhoto = sharedViewModel.fetchPhotoDetailsNow(currentPhotoId)
            if (detailPhoto != null) {
                // --- 地理位置 ---
                val locationStr =
                    listOfNotNull(detailPhoto.location?.city, detailPhoto.location?.country)
                        .joinToString(", ")
                        .takeIf { it.isNotEmpty() } ?: nullText
                loadingManager.stop(binding.loadingView1, binding.showTextLocation, locationStr)

                // --- 统计数据 ---
                loadingManager.stop(
                    binding.loadingView2,
                    binding.showTextViews,
                    formatCount(detailPhoto.views ?: 0)
                )
                loadingManager.stop(
                    binding.loadingView3,
                    binding.showTextDownloads,
                    formatCount(detailPhoto.downloads ?: 0)
                )

                // --- EXIF 相机数据 ---
                val exif = detailPhoto.exif
                val model = exif?.make ?: exif?.name ?: exif?.model ?: nullText
                loadingManager.stop(binding.loadingView4, binding.showTextCameraModel, model)
                loadingManager.stop(
                    binding.loadingView5,
                    binding.showTextAperture,
                    exif?.aperture ?: nullText
                )
                loadingManager.stop(
                    binding.loadingView6,
                    binding.showTextFocalLength,
                    exif?.focalLength ?: nullText
                )
                loadingManager.stop(
                    binding.loadingView8,
                    binding.showTextIso,
                    exif?.iso?.toString() ?: nullText
                )

                // 专门抽离的快门智能格式化方法
                loadingManager.stop(
                    binding.loadingView7,
                    binding.showTextShutter,
                    formatExposureTime(exif?.exposureTime, nullText)
                )

                // --- 图像尺寸 (直接使用 Kotlin 字符串模板) ---
                val dimensions = "${currentPhoto.width.toInt()} x ${currentPhoto.height.toInt()}"
                loadingManager.stop(
                    binding.loadingView9,
                    binding.showTextDimensions,
                    dimensions
                )

                // tag 信息
                detailPhoto.tags?.let { tags ->
                    binding.chipGroupTags.removeAllViews()
                    tags.forEach { tag ->
                        if (!tag.title.isNullOrEmpty()) {
                            val chip = Chip(requireContext()).apply {
                                text = tag.title
                                isClickable = true
                                setOnClickListener {
                                    // 打包参数，加入“路由来源”标识
                                    val bundle = Bundle().apply {
                                        putString("QUERY_KEYWORD", tag.title)
                                        // 🌟 赋予随机的临时会话 ID，并带上搜索词
                                        putString("ARG_SESSION_ID", "TAG_SEARCH_${tag.title}")
                                    }
                                    //  跳转到搜索页
                                    findNavController().navigate(
                                        R.id.action_nav_show_to_nav_search,
                                        bundle
                                    )
                                }
                            }
                            binding.chipGroupTags.addView(chip)
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 权限与合规下载逻辑 (兼容 Android 6 - 16)
    // ==========================================
    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        // 1. 通知权限 (Android 13 / API 33 及以上需要)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // 2. 存储权限 (Android 9 / API 28 及以下才需要！Android 10+ 写入公共目录免权限)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // 如果都已经授权，检查系统级通知开关和渠道是否被用户手动关掉
            checkNotifyAndChannel()
        }
    }

    // 检查系统级开关与深层通道 (Channel) 状态
    private fun checkNotifyAndChannel() {
        val context = requireContext()
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 检查 App 的总通知开关是否被用户在系统设置里硬关闭了 (兼容所有版本)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            showPermissionSnackbar(getString(R.string.show_per_notify))
            return
        }

        // 检查"下载通道" 是否被用户单独静音/关闭了 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val requiredChannels =
                listOf("download_progress", "download_complete")  // 须和 Worker 里创建通道时的 ID 一样
            for (channelId in requiredChannels) {
                val channel = notificationManager.getNotificationChannel(channelId)
                // 重要性为 NONE 代表用户手动关闭了该特定通道
                if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                    showChannelSnackbar(channelId)
                    return // 阻断执行，等待用户开启后再下
                }
            }
        }

        // 启动后台下载
        startDownloadProcess()
    }

    private fun showPermissionSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.show_snackbar_permission)) {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().packageName, null)
                )
                startActivity(intent)
            }.show()
    }

    // 精准跳转到那个被关闭的具体“通知通道”设置页！
    private fun showChannelSnackbar(channelId: String) {
        Snackbar.make(binding.root, getString(R.string.show_per_channel), Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.show_snackbar_permission)) {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                }
                startActivity(intent)
            }.show()
    }

    private fun startDownloadProcess() {
        binding.showBtnDownload.text = getString(R.string.show_download_track)
        binding.showBtnDownload.isClickable =
            false // 如果这里用isEnabled = false会导致接下来的按钮描边动画冲突，isEnabled会让按钮变色
        binding.showBtnDownload.showGlow()
        val trackUrl = currentPhoto.links.downloadLocation ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            // 优先使用临时画质，如果为空（理论上不会），则去抓取一次全局设置兜底
            val downloadQuality = tempDownloadQuality ?: settingsManager.downloadFlow.first()
            val targetUrl = when (downloadQuality) {
                DownloadQuality.RAW -> currentPhoto.urls.raw ?: currentPhoto.urls.full
                DownloadQuality.FULL -> currentPhoto.urls.full ?: currentPhoto.urls.regular
                DownloadQuality.REGULAR -> currentPhoto.urls.regular
            } ?: currentPhoto.urls.regular ?: return@launch

            // 调用合规的追踪 API
            sharedViewModel.trackDownload(
                trackUrl,
                onTrackSuccess = {
                    // 追踪成功，将下载任务委托给 Jetpack WorkManager！
                    enqueueDownloadWork(
                        targetUrl,
                        currentPhoto.id,
                        currentPhoto.user.name,
                        downloadQuality.name
                    )
                    binding.showBtnDownload.hideGlow()
                    binding.showBtnDownload.isEnabled = false
                    binding.showBtnDownload.text = getString(R.string.show_download_start)
                    //Toast.makeText(requireContext(), "Downloading in background...", Toast.LENGTH_SHORT).show()
                },
                onTrackError = {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.show_url_error),
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.showBtnDownload.hideGlow()
                    binding.showBtnDownload.isEnabled = true
                    binding.showBtnDownload.isClickable = true
                    binding.showBtnDownload.text = getString(R.string.show_download)
                }
            )
        }
    }

    /**
     * 组装并派发后台任务
     */
    private fun enqueueDownloadWork(
        url: String,
        photoId: String,
        userName: String,
        quality: String
    ) {
        // 构建任务参数
        val inputData = Data.Builder()
            .putString(PhotoDownloadWorker.KEY_URL, url)
            .putString(PhotoDownloadWorker.KEY_PHOTO_ID, photoId)
            .putString(PhotoDownloadWorker.KEY_USER_NAME, userName)
            .putString(PhotoDownloadWorker.KEY_QUALITY, quality)
            .build()

        // 设定执行约束：必须有网络连接才执行
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 创建一次性工作请求
        val downloadWorkRequest = OneTimeWorkRequestBuilder<PhotoDownloadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            // 给重试加上指数退避策略 (默认 10 秒，下一次 20 秒，40 秒...)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()

        // 把任务扔给系统，之后哪怕 App 被彻底杀死，它也会稳稳执行完！
        WorkManager.getInstance(requireContext().applicationContext)
            .enqueueUniqueWork(
                "download_${photoId}_${quality}",     // 同一质量的图片只允许一个任务存在
                ExistingWorkPolicy.KEEP, //同一张图片如果被触发两次下载，两个 Worker 会写同一个 cacheFile，RandomAccessFile 会交错写入导致文件损坏。
                downloadWorkRequest
            )
    }

    /**
     * 弹出全屏沉浸式图片查看器
     */
    private fun showPhotoPopup(highResUrl: String?, lowResUrl: String?) {
        if (highResUrl == null && lowResUrl == null) return

        // 1. 创建一个自带透明背景和无标题栏的主题 Dialog
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(R.layout.popup_photo_viewer)

        val photoView = dialog.findViewById<PhotoView>(R.id.photo_view)

        // 2. 极致的加载体验：先瞬间显示已经在内存里的小头像，后台默默加载高清大头像
        Glide.with(this)
            .load(highResUrl ?: lowResUrl)
            .thumbnail(Glide.with(this).load(lowResUrl))
            .into(photoView)

        // 3. 交互黑科技：点击图片任意区域，瞬间关闭弹窗（符合用户直觉）
        photoView.setOnPhotoTapListener { _, _, _ ->
            dialog.dismiss()
        }

        // 4. 显示浮层！
        dialog.show()
    }

    // 转换数字1000 -> 1k
    @SuppressLint("DefaultLocale")
    private fun formatCount(count: Int): String {
        return if (count >= 1000) {
            String.format("%.1fk", count / 1000.0)
        } else {
            count.toString()
        }
    }

    /**
     * 格式化快门速度 (曝光时间)
     * @param exposureTime 原始快门数据 (可能为 "1/100" 或 "0.01" 或 "2")
     * @param fallback 缺省文字
     */
    private fun formatExposureTime(exposureTime: String?, fallback: String): String {
        if (exposureTime.isNullOrEmpty()) return fallback

        // 已经是分数格式了，直接返回 (如 "1/100")
        if (exposureTime.contains("/")) return exposureTime

        val timeDouble = exposureTime.toDoubleOrNull() ?: return exposureTime

        return if (timeDouble > 0 && timeDouble < 1.0) {
            // 小于 1 秒的，转换为分数 (如 0.02 -> 1/50)
            val denominator = Math.round(1.0 / timeDouble)
            "1/$denominator"
        } else {
            // 大于等于 1 秒的长曝光，直接加 "s" (如 2 -> 2s)
            "${exposureTime}s"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 取消所有进行中的动画，避免内存泄漏
        staggerAnimator.cancel(animTargets)
        loadingManager.stopAll()
        // 关闭自定义弹窗防泄漏
        customPopupWindow?.dismiss()
        customPopupWindow = null
        _binding = null
    }
}