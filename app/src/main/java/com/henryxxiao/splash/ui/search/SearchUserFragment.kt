package com.henryxxiao.splash.ui.search

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.henryxxiao.splash.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.getValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.henryxxiao.splash.databinding.FragmentSearchListBinding
import com.henryxxiao.splash.utils.SharedPhotoViewModel
import com.henryxxiao.splash.utils.view.PagingLoadStateAdapter
import java.io.IOException

class SearchUserFragment : Fragment() {
    private var _binding: FragmentSearchListBinding? = null
    private val binding get() = _binding!!

    // 共享同一个 ViewModel
    private val searchViewModel: SharedSearchViewModel by activityViewModels()
    private lateinit var sessionId: String
    // 获取共享 ViewModel
    private val sharedViewModel: SharedPhotoViewModel by activityViewModels()

    // 初始化用户列表专用 Adapter
    private val userAdapter = SearchUserAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // 复用同一个 XML 布局
        _binding = FragmentSearchListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 拿到 Session ID
        sessionId = requireParentFragment().requireArguments().getString("ARG_SESSION_ID") ?: "MAIN_SEARCH"

        setupRecyclerView()
        observeData()
        observeLoadState()

        binding.searchBtnRetry.setOnClickListener {
            userAdapter.retry()
        }
    }

    private fun setupRecyclerView() {
        binding.searchRecyclerView.apply {
            // 用户列表通常是单列垂直滑动，所以用 LinearLayoutManager 体验更好
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            adapter = userAdapter.withLoadStateFooter(
                footer = PagingLoadStateAdapter { userAdapter.retry() }
            )
        }

        // 跳转到用户个人主页
        userAdapter.setOnItemClickListener { splashUser, imageView ->
            // 1. 把搜索到的 User 存入池中
            sharedViewModel.cacheUserToPool(splashUser)

            // 2. 绑定动画共享元素
            val extras = FragmentNavigatorExtras(imageView to imageView.transitionName)

            // 3. 传递 USER_ID 并跳转！
            val bundle = Bundle().apply {
                putString("ARG_USER_ID", splashUser.id)
            }

            // 执行跳转 (确保你的 nav_graph 里配置了从 Search 到 User 的 action)
            requireParentFragment().findNavController().navigate(
                R.id.action_nav_search_to_nav_user,
                bundle,
                null,
                extras
            )
        }
    }

    private fun observeData() {
        // 收集用户搜索结果数据流
        viewLifecycleOwner.lifecycleScope.launch {
            searchViewModel.getUsersFlow(sessionId).collectLatest { pagingData ->
                userAdapter.submitData(pagingData)
            }
        }
    }

    private fun observeLoadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            userAdapter.loadStateFlow.collectLatest { loadStates ->
                val refreshState = loadStates.refresh

                if (refreshState is LoadState.Loading) {
                    binding.searchLottieEmoji.visibility = View.GONE
                    binding.searchTextView.visibility = View.GONE
                    binding.searchErrorText.visibility = View.GONE
                    binding.searchBtnRetry.visibility = View.GONE
                    binding.searchRecyclerView.visibility = View.GONE
                    binding.searchGlowProgress.showGlow()
                }
                else if (refreshState is LoadState.NotLoading) {
                    binding.searchGlowProgress.hideGlow()

                    if (userAdapter.itemCount == 0) {
                        showEmptyState("", false)

                        // 兜底 空数据时，立刻解开动画死锁
                        binding.root.doOnPreDraw { parentFragment?.startPostponedEnterTransition() }
                    } else {
                        binding.searchRecyclerView.visibility = View.VISIBLE
                        // 通知父Fragment可以放行动画了！
                        // 等 Paging3 把数据塞进 Adapter，并且 RecyclerView 准备绘制时，
                        // 此时目标头像绝对已经在屏幕上了，返程动画成功
                        binding.searchRecyclerView.doOnPreDraw {
                            val parent = parentFragment as? SearchFragment
                            if (parent != null) {
                                // 只有当自己是第 1 个 Tab (Users) 时，才有资格放行
                                if (parent.getActiveTabIndex() == 1) {
                                    parent.startPostponedEnterTransition()
                                }
                            } else {
                                parentFragment?.startPostponedEnterTransition()
                            }
                        }
                    }
                }
                else if (refreshState is LoadState.Error) {
                    binding.searchGlowProgress.hideGlow()
                    binding.searchRecyclerView.visibility = View.GONE

                    val errorMsg = if (refreshState.error is IOException) {
                        getString(R.string.home_network_no)
                    } else {
                        getString(R.string.search_fail)
                    }
                    showEmptyState(errorMsg, true)

                    // 断网报错时，立刻解开动画死锁
                    binding.root.doOnPreDraw { parentFragment?.startPostponedEnterTransition() }
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