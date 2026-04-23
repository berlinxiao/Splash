package com.henryxxiao.splash.ui.user

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.transition.MaterialContainerTransform
import com.henryxxiao.splash.R
import com.henryxxiao.splash.data.SplashUser
import com.henryxxiao.splash.databinding.FragmentUserBinding
import com.henryxxiao.splash.utils.SharedPhotoViewModel
import com.henryxxiao.splash.utils.animation.StaggerAnimator
import kotlin.getValue

class UserFragment : Fragment() {

    private var _binding: FragmentUserBinding? = null
    private val binding get() = _binding!!

    private var isAppBarExpanded = true // 保存 AppBar 的折叠状态 (由于实例不销毁，这个值会一直保留)

    private val staggerAnimator = StaggerAnimator()
    private val animTargets: List<View> by lazy { collectAnimTargets() } // 信息的进场动画，收集所有要参与动画的 View

    // 直接从宿主拿 SharedViewModel 取数据
    private val sharedViewModel: SharedPhotoViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 配置 Material 容器形变过渡动画
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            duration = 400
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            scrimColor = Color.TRANSPARENT
            drawingViewId = R.id.nav_host_fragment // 指定导航容器层级
        }

    }
    // 信息进场动画
    private fun scheduleEnterAnimation() {
        // 等共享元素过渡开始后再播放进场动画
        // postponeEnterTransition / startPostponedEnterTransition 在 ListFragment 中控制
        view?.doOnPreDraw {
            staggerAnimator.enter(
                views = animTargets,
                config = StaggerAnimator.Config(
                    translateYDp = 36f,  //初始向下偏移位置
                    startDelayMs = 150L,  // 首行延迟
                    staggerMs    = 48L,  //行间错开
                    durationMs   = 350L  //单行时长
                )
            )
        }
    }

    private fun collectAnimTargets(): List<View> {
        // 按视觉顺序收集行，同一行作为一个节奏单位一起进场
        return with(binding) {
            listOf(
                userTextViewName,
                userTextViewUsername,
                userTextViewLocation,
                userTextViewBio,
                userLinearLayout1,
                userTabLayout,
                userViewpager
            )
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // 注意把之前的 ActivityUserBinding 换成了 FragmentUserBinding
        _binding = FragmentUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 加入超时保护的暂停机制
        // 如果内部的 Paging3 或 Glide 出了问题一直没加载出来， 最多等 400 毫秒，强行放行动画，不能让 App卡死白屏
        postponeEnterTransition(400, java.util.concurrent.TimeUnit.MILLISECONDS)

        // 读取专属 ID，不管是从哪里跳过来的
        val currentUserId = arguments?.getString("ARG_USER_ID") ?: run {
            findNavController().navigateUp()
            return
        }
        // 提取专属对象，如果没有数据则安全撤退
        val user = sharedViewModel.getUserFromPool(currentUserId) ?: run {
            findNavController().navigateUp()
            return
        }

        // 绑定过渡动画的终点名称
        binding.userImageViewHead.transitionName = "avatar_${user.id}"

        // 恢复 AppBar 状态
        binding.userAppbar.setExpanded(isAppBarExpanded, false)
        // 实时监听并保存 AppBar 的状态
        binding.userAppbar.addOnOffsetChangedListener { _, verticalOffset ->
            // verticalOffset == 0 表示完全展开
            isAppBarExpanded = (verticalOffset == 0)
        }

        initHeader(user)
        initViewPager(user)

        // 物理和顶部返回按钮统一交给 NavController 管理
        binding.userBtnBack.setOnClickListener { findNavController().navigateUp() }

        // 前往用户主页
        binding.userBtnShare.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, user.links.html.toUri())
            startActivity(intent)
        }

        // 拦截系统返回，手动控制退场顺序
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    binding.userAppbar.setExpanded(true, true) // 有一个头像返回的动画，防止 AppBar 折叠
                    parentFragmentManager.popBackStack() // 动画结束后触发共享元素返回过渡
                }
            }
        )

        binding.root.doOnPreDraw {
            startPostponedEnterTransition()
        }
        // 播放进场动画
        scheduleEnterAnimation()
    }

    private fun initHeader(user: SplashUser) {
        binding.userTextViewName.text = user.name
        binding.userTextViewUsername.text = "@${user.username}"

        if (user.location.isNullOrEmpty()) {
            binding.userTextViewLocation.visibility = View.GONE
        } else {
            binding.userTextViewLocation.text = user.location
        }

        if (user.bio.isNullOrEmpty()) {
            binding.userTextViewBio.visibility = View.GONE
        } else {
            binding.userTextViewBio.text = user.bio
        }

        binding.userTextViewPhotosCount.text = formatCount(user.totalPhotos)
        binding.userTextViewLikesCount.text = formatCount(user.totalLikes)

        // 头像无缝过渡加载机制
        val avatarUrl = user.profileImage?.large ?: user.profileImage?.small
        Glide.with(this)
            .load(avatarUrl)
            .apply(com.bumptech.glide.request.RequestOptions.bitmapTransform(CircleCrop()))
            .onlyRetrieveFromCache(true) // 头像在上个页面一定加载过了，强行读内存缓存实现 0 毫秒加载
            .dontAnimate()
            .into(binding.userImageViewHead)

        // 头像点击放大
        binding.userImageViewHead.setOnClickListener {
            // 获取超高清原图和大图 URL
            val highResUrl = user.profileImage?.large ?: user.profileImage?.medium
            val lowResUrl = user.profileImage?.small

            // 呼出当前页面的沉浸式浮层，不再发生任何页面路由跳转
            showAvatarPopup(highResUrl, lowResUrl)
        }
    }

    private fun initViewPager(user: SplashUser) {
        val username = user.username

        // 交给底层的 Paging 3 去处理数据和空状态
        val fragments = listOf(
            UserListFragment.newInstance(
                endpoint = "users/$username/photos",
                emptyMessage = getString(R.string.user_not_photo) ,
                showUserInfo = false
            ),
            UserListFragment.newInstance(
                endpoint = "users/$username/likes",
                emptyMessage = getString( R.string.user_not_like),
                showUserInfo = true
            )
        )

        val titles = listOf(
            getString(R.string.user_photos),
            getString(R.string.user_likes)
        )

        // Fragment 嵌套 Fragment 必须使用 this (代表 childFragmentManager) 如果这里写成 requireActivity()，会导致内部 Fragment 生命周期错乱，且返回时页面会白屏
        binding.userViewpager.adapter = object : FragmentStateAdapter(this@UserFragment) {
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        binding.userViewpager.offscreenPageLimit = fragments.size

        TabLayoutMediator(binding.userTabLayout, binding.userViewpager) { tab, position ->
            tab.text = titles[position]
        }.attach()
    }

    /**
     * 弹出全屏沉浸式头像查看器
     */
    private fun showAvatarPopup(highResUrl: String?, lowResUrl: String?) {
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

    @SuppressLint("DefaultLocale")
    private fun formatCount(count: Int): String {
        return if (count >= 1000) {
            String.format("%.1fk", count / 1000.0)
        } else {
            count.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 取消所有进行中的动画，避免内存泄漏
        staggerAnimator.cancel(animTargets)
        // 置空 Binding 防内存泄漏
        _binding = null
    }
}