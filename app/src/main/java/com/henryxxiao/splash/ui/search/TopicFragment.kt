package com.henryxxiao.splash.ui.search

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.transition.MaterialContainerTransform
import com.henryxxiao.splash.R
import com.henryxxiao.splash.data.SplashTopic
import com.henryxxiao.splash.databinding.FragmentTopicBinding
import com.henryxxiao.splash.ui.user.UserListFragment
import com.henryxxiao.splash.utils.BlurHashCache
import com.henryxxiao.splash.utils.SharedPhotoViewModel
import kotlin.getValue
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnPreDraw

class TopicFragment : Fragment() {

    private var _binding: FragmentTopicBinding? = null
    private val binding get() = _binding!!

    // 依然共享全局的 ViewModel
    private val sharedViewModel: SharedPhotoViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🌟 1. 挂载极其顺滑的容器变换动画
        val transform = MaterialContainerTransform().apply {
            duration = 450
            scrimColor = Color.TRANSPARENT
            drawingViewId = R.id.nav_host_fragment
            // 保持圆角变直角的完美贴合
            shapeMaskProgressThresholds = MaterialContainerTransform.ProgressThresholds(0.85f, 1.0f)
        }
        sharedElementEnterTransition = transform
        sharedElementReturnTransition = transform
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTopicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postponeEnterTransition()
        // 2. 暂停动画，最高等待 500ms
        //postponeEnterTransition(500, java.util.concurrent.TimeUnit.MILLISECONDS)

        // 3. 从 Bundle 获取 ID，并去内存池取回 Topic 对象
        val topicId = arguments?.getString("ARG_TOPIC_ID") ?: run {
            findNavController().navigateUp()
            return
        }
        val topic = sharedViewModel.getTopicFromPool(topicId) ?: return

        // 4. 给当前页面的根布局绑定目标名称，完成动画闭环！
        binding.topicRootLayout.transitionName = "topic_card_${topic.id}"

        setupHeader(topic)
        injectListFragment(topic)

        binding.root.doOnPreDraw { startPostponedEnterTransition() }
        //binding.topicBack.setOnClickListener { findNavController().navigateUp() }
    }

    private fun setupHeader(topic: SplashTopic) {
        binding.topicTitle.text = topic.title
        binding.topicDescription.text = topic.description

        // 动态计算并设置 AppBarLayout 的高度
//        topic.coverPhoto?.let { cover ->
//            if (cover.width > 0 && cover.height > 0) {
//                // 1. 获取当前屏幕宽度
//                val screenWidth = resources.displayMetrics.widthPixels
//
//                // 2. 计算图片的真实宽高比
//                val ratio = cover.height.toFloat() / cover.width.toFloat()
//
//                // 3. 计算等比例放大到全屏宽度后的高度
//                var targetHeight = (screenWidth * ratio).toInt()
//
//                // 4. 安全限制：最高不能超过屏幕高度的 60%，防止大长图霸屏
//                val maxHeight = (resources.displayMetrics.heightPixels * 0.6f).toInt()
//                targetHeight = targetHeight.coerceAtMost(maxHeight)
//
//                // 5. 应用新高度到 AppBarLayout
//                val params = binding.appBarLayout.layoutParams
//                params.height = targetHeight
//                binding.appBarLayout.layoutParams = params
//                binding.topicCoverImage.layoutParams = params
//            }
//        }

        val cover = topic.coverPhoto
        val ratio =
            if (cover != null && cover.width > 0) cover.height.toFloat() / cover.width.toFloat() else 1f
        val fallbackColor = try {
            (cover?.color ?: "#333333").toColorInt()
        } catch (_: Exception) {
            "#333333".toColorInt()
        }
        val colorDrawable = fallbackColor.toDrawable()
        var placeholderDrawable: Drawable = colorDrawable

        if (!cover?.blurHash.isNullOrEmpty()) {
            val blurBitmap =
                BlurHashCache.getBitmap(cover.blurHash, 30, (30 * ratio).toInt().coerceAtLeast(1))
            if (blurBitmap != null) placeholderDrawable = blurBitmap.toDrawable(resources)
        }

        // 0毫秒加载占位图
        val coverUrl = topic.coverPhoto?.urls?.regular ?: topic.coverPhoto?.urls?.small
        Glide.with(this)
            .load(coverUrl)
            .placeholder(placeholderDrawable)
            //.onlyRetrieveFromCache(true) // 从轮播图的内存里瞬间抠出来
            .dontAnimate()
//            .listener(object : RequestListener<Drawable> {
//                override fun onLoadFailed(
//                    e: GlideException?,
//                    model: Any?,
//                    target: Target<Drawable>?,
//                    isFirstResource: Boolean
//                ): Boolean {
//                    startPostponedEnterTransition()
//                    return false
//                }
//
//                override fun onResourceReady(
//                    resource: Drawable?,
//                    model: Any?,
//                    target: Target<Drawable>?,
//                    dataSource: DataSource?,
//                    isFirstResource: Boolean
//                ): Boolean {
//                    // 封面一旦就绪，立刻放行动画，卡片开始起飞变大！
//                    startPostponedEnterTransition()
//                    return false
//                }
//            })
            .into(binding.topicCoverImage)
    }

    private fun injectListFragment(topic: SplashTopic) {
        // 架构复用：动态塞入 UserListFragment！
        // 避免重复添加：如果容器里已经有了，就不再添加
        if (childFragmentManager.findFragmentById(R.id.topic_list_container) == null) {

            // 通过 newInstance 构建，传入专属的 Topic 路由
            val listFragment = UserListFragment.newInstance(
                endpoint = "topics/${topic.slug}/photos",
                emptyMessage = getString(R.string.search_no_result),
                showUserInfo = true // 显示头像和名字
            )

            // 使用 childFragmentManager 把万能列表嵌进当前页面底部！
            childFragmentManager.beginTransaction()
                .replace(R.id.topic_list_container, listFragment)
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}