package com.henryxxiao.splash.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.henryxxiao.splash.R
import com.henryxxiao.splash.databinding.FragmentSearchListBinding
import com.henryxxiao.splash.utils.PhotoAdapter
import com.henryxxiao.splash.utils.SharedPhotoViewModel
import com.henryxxiao.splash.utils.view.PagingLoadStateAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.getValue

class SearchPhotoFragment : Fragment() {

    private var _binding: FragmentSearchListBinding? = null
    private val binding get() = _binding!!

    // 向上一级索要 ViewModel
    private val searchViewModel: SharedSearchViewModel by activityViewModels()

    private lateinit var sessionId: String

    // 获取宿主共享的 ViewModel
    private val sharedPhotoViewModel: SharedPhotoViewModel by activityViewModels()

    // 初始化 Adapter，传入 true 显示作者头像和名字
    private val photoAdapter = PhotoAdapter(showUserInfo = true)

    // 标记：是否需要回到顶部
    private var pendingScrollToTop = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 拿到 Session ID
        sessionId = requireParentFragment().requireArguments().getString("ARG_SESSION_ID") ?: "MAIN_SEARCH"

        setupRecyclerView()
        observeDataFlows()

        // Paging 3 原生重试机制
        binding.searchBtnRetry.setOnClickListener {
            photoAdapter.retry()
        }
    }

    private fun setupRecyclerView() {
        binding.searchRecyclerView.apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            setHasFixedSize(true)
            itemAnimator = null // 防止刷新闪烁
            adapter = photoAdapter.withLoadStateFooter(
                footer = PagingLoadStateAdapter { photoAdapter.retry() }
            )
        }

        // 跳转到大图预览页，携带共享元素动画
        photoAdapter.setOnItemClickListener {splashPhoto, imageView ->
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
                R.id.action_nav_search_to_nav_show,
                bundle,
                null,
                extras
            )
        }
    }

    private fun observeDataFlows() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // 监听 Paging 列表数据
                launch {
                    searchViewModel.getPhotosFlow(sessionId).collectLatest { pagingData ->
                        photoAdapter.submitData(pagingData)
                    }
                }

                // 监听 ViewModel 发出的“新搜索/改筛选”信号枪
                launch {
                    searchViewModel.getScrollEvent(sessionId).collect {
                        // 做个记号！(不要在这里直接 scrollToPosition)
                        pendingScrollToTop = true
                    }
                }

                // 监听 Paging 的加载状态
                launch {
                    photoAdapter.loadStateFlow.collectLatest { loadStates ->
                        val refreshState = loadStates.refresh

                        if (refreshState is LoadState.Loading) {
                            //[加载中]：显示顶部 Glow，隐藏错误信息。不要隐藏 RecyclerView
                            binding.searchGlowProgress.showGlow()
                            binding.searchLottieEmoji.visibility = View.GONE
                            binding.searchTextView.visibility = View.GONE
                            binding.searchErrorText.visibility = View.GONE
                            binding.searchBtnRetry.visibility = View.GONE
                        }
                        else if (refreshState is LoadState.NotLoading) {
                            //[加载完成]
                            binding.searchGlowProgress.hideGlow()

                            if (photoAdapter.itemCount == 0) {
                                // 没数据：显示空状态，隐藏列表，并兜底解开动画锁
                                showEmptyState("", false)
                                binding.searchRecyclerView.visibility = View.GONE
                                binding.root.doOnPreDraw {
                                    (parentFragment as? SearchFragment)?.startPostponedEnterTransition()
                                }
                            } else {
                                // 有数据：显示列表
                                binding.searchRecyclerView.visibility = View.VISIBLE

                                // 新数据：回到顶部
                                if (pendingScrollToTop) {
                                    binding.searchRecyclerView.smoothScrollToPosition(0)
                                    pendingScrollToTop = false // 消费掉记号
                                }

                                // 通知父亲：放行动画
                                binding.searchRecyclerView.doOnPreDraw {
                                    val parent = parentFragment as? SearchFragment
                                    if (parent != null && parent.getActiveTabIndex() == 0) {
                                        parent.startPostponedEnterTransition()
                                    } else {
                                        parentFragment?.startPostponedEnterTransition()
                                    }
                                }
                            }
                        }
                        else if (refreshState is LoadState.Error) {
                            // [加载报错]
                            binding.searchGlowProgress.hideGlow()
                            binding.searchRecyclerView.visibility = View.GONE

                            val errorMsg = if (refreshState.error is IOException) {
                                getString(R.string.home_network_no)
                            } else {
                                getString(R.string.search_fail)
                            }
                            showEmptyState(errorMsg, true)

                            // 断网也要兜底解开动画锁
                            binding.root.doOnPreDraw {
                                (parentFragment as? SearchFragment)?.startPostponedEnterTransition()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showEmptyState(message: String, showRetry: Boolean) {
        binding.searchErrorText.text = message
        binding.searchBtnRetry.visibility = if (showRetry) View.VISIBLE else View.GONE
        binding.searchLottieEmoji.visibility = View.VISIBLE
        binding.searchTextView.visibility = View.VISIBLE
        binding.searchErrorText.visibility = View.VISIBLE
        binding.searchLottieEmoji.playAnimation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}