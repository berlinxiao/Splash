package com.henryxxiao.splash.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.transition.MaterialElevationScale
import com.henryxxiao.splash.R
import com.henryxxiao.splash.databinding.FragmentHomeBinding
import com.henryxxiao.splash.utils.PhotoAdapter
import com.henryxxiao.splash.utils.SharedPhotoViewModel
import com.henryxxiao.splash.utils.view.PagingLoadStateAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException

class HomeFragment : Fragment() {

    // ViewBinding 的规范写法，确保在 onDestroyView 中置空防止内存泄漏
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val sharedViewModel: SharedPhotoViewModel by activityViewModels()

    // Kotlin 委托属性：获取 ViewModel，无需再写 ViewModelProvider
    private val photoViewModel: PhotoViewModel by viewModels()

    // 初始化 Adapter (注意：它必须继承自 PagingDataAdapter)
    private val photoAdapter = PhotoAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 【退场】：当详情页打开时，列表微微缩小并淡出，像被推向深处
//        exitTransition = MaterialElevationScale(false).apply {
//            duration = 250L
//            interpolator = PathInterpolatorCompat.create(0.3f, 0.0f, 0.8f, 0.15f)
//        }


        // 【返场】：当从详情页返回时，列表从深处放大恢复并淡入
        reenterTransition = MaterialElevationScale(true).apply {
            duration = 300L
            interpolator = PathInterpolatorCompat.create(0.05f, 0.7f, 0.1f, 1f)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 【返回动画防闪烁】 当返回时，暂停动画，等待瀑布流把图片重新画好再放行
        postponeEnterTransition()

        setupRecyclerView()
        setupSwipeRefresh()
        observeData()
        observeLoadState()

        binding.homeSearch.setOnClickListener {
            val bundle = Bundle().apply {
                putString("ARG_SESSION_ID", "MAIN_SEARCH") // 🌟 赋予主会话 ID
            }
            // 跳转到 SearchFragment
            findNavController().navigate(R.id.action_nav_home_to_nav_search, bundle)
        }

        binding.homeSet.setOnClickListener {
            // 跳转到 SettingsFragment
            findNavController().navigate(R.id.action_nav_home_to_nav_set)
        }

        // 设置转圈的颜色
        binding.SwipeRefreshLayout.setColorSchemeResources(
            R.color.brand_400,
            R.color.success_400,
            R.color.warning_400,
            R.color.error_400,
            R.color.info_400,
            R.color.coral_400
        )

        // View 准备好时放行动画
        view.doOnPreDraw { startPostponedEnterTransition() }
    }

    private fun setupRecyclerView() {
        // 设置瀑布流布局
        binding.homeRecyclerview.apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            setHasFixedSize(true) //item的改变不会影响RecyclerView宽高的时候，可以设置为true让RecyclerView避免重新计算大小。
            itemAnimator = null // 防止item出现重新排列
            //adapter = photoAdapter
            // 挂载底部状态
            adapter = photoAdapter.withLoadStateFooter(
                footer = PagingLoadStateAdapter { photoAdapter.retry() }
            )
        }

        // Adapter 点击事件跳转，以及过渡动画
        photoAdapter.setOnItemClickListener { splashPhoto, imageView ->
            // 将对象直接存入共享内存 ViewModel 中
            sharedViewModel.cachePhotoToPool(splashPhoto)
            // 动态绑定共享元素名称
            val extras = FragmentNavigatorExtras(imageView to imageView.transitionName)
            // Bundle 中只传字符串
            val bundle = Bundle().apply {
                putString("ARG_PHOTO_ID", splashPhoto.id)
                //putString("ARG_MEM_CACHE_URL", splashPhoto.urls.small)
            }
            // 跳转
            findNavController().navigate(
                R.id.action_nav_home_to_nav_show,
                bundle, // 传入轻量级 ID
                null,
                extras
            )
            // 绕过 Navigation，使用原生 FragmentManager 强行挂载
//            val showFragment = ShowFragment()
//            requireActivity().supportFragmentManager.beginTransaction()
//                // 关键点：android.R.id.content 是 Activity 的绝对底层 FrameLayout
//                // 把 Fragment 加在这里，它会覆盖在整个 MainActivity 的上方
//                .add(android.R.id.content, showFragment)
//                .addToBackStack(null) // 允许按返回键退回
//                .commit()

        }

        // 当点击网络重试按钮时，Paging 3 原生支持一键重试
        binding.homeButtonRetry.setOnClickListener {
            photoAdapter.retry()
        }
    }

    private fun setupSwipeRefresh() {
        // 下拉刷新时，直接调用 Paging 3 adapter 自带的刷新方法
        binding.SwipeRefreshLayout.setOnRefreshListener {
            photoAdapter.refresh()
        }
    }

    private fun observeData() {
        // 监听 ViewModel 中传来的分页数据
        // 因为 ViewModel 里使用了 flatMapLatest 监听 DataStore，
        // 所以当用户在设置里修改了壁纸类型，这里的 collectLatest 会自动收到新数据并刷新 UI
        viewLifecycleOwner.lifecycleScope.launch {
            photoViewModel.photosFlow.collectLatest { pagingData ->
                // 提交数据到 Adapter
                photoAdapter.submitData(pagingData)
            }
        }
    }

    private fun observeLoadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            photoAdapter.loadStateFlow.collectLatest { loadStates ->
                // 获取当前的刷新状态
                // 1. 是否正在加载？如果是，显示下拉刷新的圈圈
//                val isLoading = refreshState is LoadState.Loading
//                //binding.progressBar.visibility = if (isLoading && !binding.SwipeRefreshLayout.isRefreshing) View.VISIBLE else View.GONE
//                binding.SwipeRefreshLayout.isRefreshing = isLoading
//
//                // 2. 加载成功且不处于加载状态
//                if (refreshState !is LoadState.Loading) {
//                    binding.SwipeRefreshLayout.isRefreshing = false // 关闭下拉圈圈
//                }

                // 3. 处理错误和成功状态的 UI 切换
                when (val refreshState = loadStates.refresh) {
                    is LoadState.NotLoading -> {
                        // 成功：显示列表，隐藏所有错误提示
                        binding.homeRecyclerview.visibility = View.VISIBLE
                        binding.homeLottieEmoji.visibility = View.GONE
                        binding.homeTextView.visibility = View.GONE
                        binding.homeTextViewTitle.visibility = View.GONE
                        binding.homeButtonRetry.visibility = View.GONE
                        // 加载成功就可以开启下拉刷新了
                        binding.SwipeRefreshLayout.isEnabled = true
                        binding.SwipeRefreshLayout.isRefreshing = false // 关闭加载的圈圈
                    }

                    is LoadState.Loading -> {
                        binding.SwipeRefreshLayout.isRefreshing = true // 显示加载圈圈
                        binding.homeLottieEmoji.visibility = View.GONE
                        binding.homeTextView.visibility = View.GONE
                        binding.homeTextViewTitle.visibility = View.GONE
                        binding.homeButtonRetry.visibility = View.GONE
                    }

                    is LoadState.Error -> {
                        binding.SwipeRefreshLayout.isRefreshing = false // 关闭加载的圈圈
                        // 失败：隐藏列表，显示错误提示
                        binding.homeRecyclerview.visibility = View.GONE
                        binding.homeLottieEmoji.visibility = View.VISIBLE
                        binding.homeTextViewTitle.visibility = View.VISIBLE
                        binding.homeTextView.visibility = View.VISIBLE
                        binding.homeButtonRetry.visibility = View.VISIBLE
                        // 播放 Lottie 动画
                        binding.homeLottieEmoji.playAnimation()

                        // 区分是否是网络异常 (IOException 代表无网络/超时)
                        if (refreshState.error is IOException) {
                            binding.homeTextViewTitle.text =
                                getString(R.string.home_network_no_title)
                            binding.homeTextView.text = getString(R.string.home_network_no)
                            binding.SwipeRefreshLayout.isEnabled = false // 无网络时禁止下拉刷新
                        } else {
                            binding.homeTextViewTitle.text =
                                getString(R.string.home_network_error_title)
                            binding.homeTextView.text = getString(R.string.home_network_error)
                        }
                    }
                }
            }
        }
    }

    /* fragment 的生命周期与 activity 的生命周期不同，并且该fragment可以超出其视图的生命周期，
    因此如果不将其设置为null，则可能会发生内存泄漏。*/
    override fun onDestroyView() {
        super.onDestroyView()
        // 防止内存泄漏，必须置空
        _binding = null
    }
}