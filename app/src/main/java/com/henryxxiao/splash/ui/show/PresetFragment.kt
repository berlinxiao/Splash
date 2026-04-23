package com.henryxxiao.splash.ui.show

import android.app.WallpaperManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import com.henryxxiao.splash.R
import com.henryxxiao.splash.data.SplashPhoto
import com.henryxxiao.splash.databinding.FragmentPresetBinding
import com.henryxxiao.splash.utils.SharedPhotoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PresetFragment : Fragment() {

    private var _binding: FragmentPresetBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedPhotoViewModel by activityViewModels()
    private lateinit var currentPhoto: SplashPhoto

    // 保存下载好的高清 Bitmap，供后续裁剪使用
    private var fullResBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPresetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 隐藏状态栏和导航栏
        //enterImmersiveMode()

        // 获取数据
        val photoId = arguments?.getString("ARG_PHOTO_ID") ?: run {
            findNavController().navigateUp()
            return
        }
        currentPhoto = sharedViewModel.getPhotoFromPool(photoId) ?: return

        binding.progress.indeterminateTintList =
            ColorStateList.valueOf((currentPhoto.color as String).toColorInt())

        // 加载大图
        loadHighResImage()

        // 绑定事件
        binding.fabApplyWallpaper.setOnClickListener { showWallpaperOptionsDialog() }
    }

    // Glide 原生加载机制，完美支持缩略图
    private fun loadHighResImage() {
        // 优先给中等画质，需要更高清让用户下载图片
        val targetUrl = currentPhoto.urls.regular ?: currentPhoto.urls.full

        Glide.with(this)
            .load(targetUrl)
            // 禁止 Glide 自动缩小图片
            // 强迫 Glide 把原本宽达 4000px 的大图原封不动地塞进内存。
            // 这样 PhotoView 拿到手后，才有超出的多余部分让你能够“上下左右”尽情滑动！
            .override(Target.SIZE_ORIGINAL)
            // 延长大图的下载超时时间到 15 秒
            .timeout(15000)
            .thumbnail(Glide.with(this).load(currentPhoto.urls.small).dontAnimate())
            .dontAnimate()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.loadingOverlay.visibility = View.GONE
                    // 打印真实的报错原因，方便调试 (比如是 Timeout 还是 OOM)
                    val errorMsg = e?.localizedMessage ?: "Unknown error"

                    showToast("Failed: $errorMsg")
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.loadingOverlay.visibility = View.GONE
                    binding.fabApplyWallpaper.isEnabled = true

                    // 强制唤醒 PhotoView 矩阵
                    binding.previewPhotoView.post {
                        binding.previewPhotoView.scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    return false
                }
            })
            .into(binding.previewPhotoView)
    }

    private fun showWallpaperOptionsDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            showToast(getString(R.string.show_wallpaper_low))
            return
        }

        val options = arrayOf(
            getString(R.string.show_wallpaper_home),
            getString(R.string.show_wallpaper_lock),
            getString(R.string.show_wallpaper_both)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.show_set_wallpaper))
            .setItems(options) { _, which ->
                val flag = when (which) {
                    0 -> WallpaperManager.FLAG_SYSTEM

                    1 -> WallpaperManager.FLAG_LOCK

                    else -> WallpaperManager.FLAG_SYSTEM or (WallpaperManager.FLAG_LOCK)
                }
                applyWallpaper(flag)
            }
            .show()
    }

    private fun applyWallpaper(flags: Int) {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.fabApplyWallpaper.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
//                currentPhoto.links.downloadLocation?.let { trackUrl ->
//                    sharedViewModel.trackDownload(trackUrl, {}, {})
//                }

                withContext(Dispatchers.IO) {
                    // 如果抠图失败，不能静默 return，须抛出异常阻断后续流程
                    val croppedBitmap = createCroppedBitmap()
                        ?: throw IllegalStateException("Cannot extract bitmap from PhotoView")

                    val wallpaperManager =
                        WallpaperManager.getInstance(requireContext().applicationContext)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(croppedBitmap, null, true, flags)
                    } else {
                        wallpaperManager.setBitmap(croppedBitmap)
                    }
                }

                // 只有上面的 IO 操作完完全全没有抛出异常，才会走到这里
                showToast(getString(R.string.show_wallpaper_success))
                findNavController().navigateUp()

            } catch (e: Exception) {
                // 任何失败（包括我们的抠图失败）都会走这里，不会有“假成功”
                showToast("Failed: ${e.localizedMessage}")
            } finally {
                binding.loadingOverlay.visibility = View.GONE
                binding.fabApplyWallpaper.isEnabled = true
            }
        }
    }

    /**
     * 精准裁剪算法：基于用户的拖动和缩放比例，提取屏幕可见区域。
     */
    private fun createCroppedBitmap(): Bitmap? {
        val drawable = binding.previewPhotoView.drawable ?: return null

        // 防线：确保拿到的绝对是 BitmapDrawable
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return null
        val displayRect = binding.previewPhotoView.displayRect ?: return null

        val scaleX = bitmap.width / displayRect.width()
        val scaleY = bitmap.height / displayRect.height()

        val cropLeft = (0f - displayRect.left) * scaleX
        val cropTop = (0f - displayRect.top) * scaleY
        val cropRight = (binding.previewPhotoView.width - displayRect.left) * scaleX
        val cropBottom = (binding.previewPhotoView.height - displayRect.top) * scaleY

        val safeLeft = cropLeft.coerceAtLeast(0f).toInt()
        val safeTop = cropTop.coerceAtLeast(0f).toInt()
        val safeRight = cropRight.coerceAtMost(bitmap.width.toFloat()).toInt()
        val safeBottom = cropBottom.coerceAtMost(bitmap.height.toFloat()).toInt()

        val safeWidth = (safeRight - safeLeft).coerceAtLeast(1)
        val safeHeight = (safeBottom - safeTop).coerceAtLeast(1)

        return Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
    }

//    private fun enterImmersiveMode() {
//        val window = requireActivity().window
//
//        // 允许壁纸画面完全穿透刘海屏/挖孔屏区域，达到 100% 真实预览
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            window.attributes.layoutInDisplayCutoutMode =
//                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
//        }
//
//        // 只隐藏系统栏，绝对不碰 setDecorFitsSystemWindows！
//        WindowInsetsControllerCompat(window, binding.root).apply {
//            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//            hide(WindowInsetsCompat.Type.systemBars())
//        }
//    }
//
//    private fun exitImmersiveMode() {
//        val window = requireActivity().window
//
//        // 退出时恢复刘海屏默认行为
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            window.attributes.layoutInDisplayCutoutMode =
//                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
//        }
//
//        // 只显示系统栏，维持 MainActivity 原有的 enableEdgeToEdge 状态
//        WindowInsetsControllerCompat(window, binding.root).apply {
//            show(WindowInsetsCompat.Type.systemBars())
//        }
//    }

    private fun showToast(str: String) {
        Toast.makeText(requireContext(), str, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 离开预览页时，必须恢复系统的状态栏！
        //exitImmersiveMode()

        fullResBitmap?.recycle()
        fullResBitmap = null
        _binding = null
    }
}