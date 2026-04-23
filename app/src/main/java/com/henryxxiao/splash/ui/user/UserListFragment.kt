package com.henryxxiao.splash.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.henryxxiao.splash.R
import com.henryxxiao.splash.databinding.FragmentListBinding
import com.henryxxiao.splash.utils.PhotoAdapter
import com.henryxxiao.splash.utils.SharedListViewModel
import com.henryxxiao.splash.utils.SharedPhotoViewModel
import com.henryxxiao.splash.utils.view.PagingLoadStateAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.getValue

class UserListFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    // 注意：因为这里的 Fragment 是动态生成的多个实例，每个 Fragment 都会有专属自己的 ViewModel，
    // 所以它们彼此之间的 endpoint 互不干扰！
    private val listViewModel: SharedListViewModel by activityViewModels()

    private var showUserInfo: Boolean = false // 新增变量
    private lateinit var listAdapter: PhotoAdapter // 延迟初始化 Adapter
    // 复用之前写好的 PhotoAdapter，传入 false 隐藏头像
    //private val userAdapter = PhotoAdapter(showUserInfo = false)

    // 获取宿主共享的 ViewModel
    private val sharedPhotoViewModel: SharedPhotoViewModel by activityViewModels()

    private var endpoint: String = ""
    private var emptyMessage: String = ""

    // 静态工厂方法 (替代 Java 的 newInstance)
    companion object {
        private const val ARG_ENDPOINT = "endpoint"
        private const val ARG_EMPTY_MESSAGE = "emptyMessage"
        private const val ARG_SHOW_USER_INFO = "showUserInfo"

        fun newInstance(endpoint: String, emptyMessage: String, showUserInfo: Boolean) = UserListFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ENDPOINT, endpoint)
                putString(ARG_EMPTY_MESSAGE, emptyMessage)
                putBoolean(ARG_SHOW_USER_INFO, showUserInfo) // 是否显示用户头像
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            endpoint = it.getString(ARG_ENDPOINT) ?: ""
            emptyMessage = it.getString(ARG_EMPTY_MESSAGE) ?: ""
            // 取出开关，默认为 false
            showUserInfo = it.getBoolean(ARG_SHOW_USER_INFO, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 动态初始化 Adapter，把开关传给它！
        listAdapter = PhotoAdapter(showUserInfo = showUserInfo)

        setupRecyclerView()
        observeData()
        observeLoadState()

        binding.listRetry.setOnClickListener { listAdapter.retry() }
    }

    private fun setupRecyclerView() {
        binding.listRecyclerview.apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            setHasFixedSize(true)
            itemAnimator = null
            adapter = listAdapter.withLoadStateFooter(
                footer = PagingLoadStateAdapter { listAdapter.retry() }
            )
        }

        // 跳转到 ShowFragment
        listAdapter.setOnItemClickListener { splashPhoto, imageView ->
            // 将被点击的图片对象存入共享内存
            sharedPhotoViewModel.cachePhotoToPool(splashPhoto)
            // 绑定共享元素动画名称 (和 ShowFragment 里面保持一致)
            val extras = FragmentNavigatorExtras(imageView to imageView.transitionName)
            val bundle = Bundle().apply {
                putString("ARG_PHOTO_ID", splashPhoto.id)
                putString("ARG_MEM_CACHE_URL", splashPhoto.urls.small)
            }
            // 跨越嵌套层级！
            // 必须让父 Fragment 去执行跳转，Navigation 才能正确抓取到共享元素！
            requireParentFragment().findNavController().navigate(
                R.id.nav_show,
                bundle,
                null,
                extras
            )
        }
    }

    /**
     * 收集纯 Kotlin Flow 的图片数据
     */
    private fun observeData() {
        if (endpoint.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 🌟 从全局池子里索要这条专属的流
                listViewModel.getPhotosFlowByEndpoint(endpoint).collectLatest { pagingData ->
                    listAdapter.submitData(pagingData)
                }
            }
        }
    }

    /**
     * 收集 Paging 3 原生的加载状态流
     */
    private fun observeLoadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            listAdapter.loadStateFlow.collectLatest { loadStates ->
                when (val refreshState = loadStates.refresh) {
                    is LoadState.Loading -> {
                        binding.listGlowProgress.showGlow()
                        binding.listEmptyView.visibility = View.GONE
                        binding.listRetry.visibility = View.GONE
                    }
                    is LoadState.NotLoading -> {
                        binding.listGlowProgress.hideGlow()
                        if (listAdapter.itemCount == 0) {
                            showEmptyState(emptyMessage)
                        } else {
                            binding.listEmptyView.visibility = View.GONE
                            binding.listRetry.visibility = View.GONE
                        }
                    }
                    is LoadState.Error -> {
                        binding.listGlowProgress.hideGlow()
                        // 判断是否是因为无网络引发的异常
                        val errorMsg = if (refreshState.error is IOException) {
                            getString(R.string.home_network_error_title)
                        } else {
                            refreshState.error.localizedMessage ?: "Load Failed"
                        }
                        showEmptyState(errorMsg)
                        binding.listRetry.visibility = View.VISIBLE // 显示重试按钮，仅在网络错误下显示
                    }
                }
                if (loadStates.refresh is LoadState.NotLoading) {
                    // RecyclerView 异步恢复
                    // 当 Paging 3 数据加载完毕后，此时视图还没有开始绘制 (Layout)
                    // 使用 doOnPreDraw 告诉底层渲染引擎：等下一次 UI 帧即将绘制时，
                    // 此时照片的卡片绝对已经渲染在屏幕上了，立刻通知父 Fragment 放行返回动画！
                    binding.listRecyclerview.doOnPreDraw {
                        parentFragment?.startPostponedEnterTransition()
                    }
                }
            }
        }
    }

    private fun showEmptyState(msg: String) {
        binding.listEmptyView.visibility = View.VISIBLE
        binding.listTextViewOnly.text = msg
        binding.listEmptyEmoji.playAnimation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // 视图销毁时必须置空 binding，防止内存泄漏
    }
}