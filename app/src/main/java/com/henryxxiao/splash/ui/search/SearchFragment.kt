package com.henryxxiao.splash.ui.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.carousel.CarouselLayoutManager
import com.google.android.material.carousel.MultiBrowseCarouselStrategy
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.transition.MaterialSharedAxis
import com.henryxxiao.splash.R
import com.henryxxiao.splash.databinding.FragmentSearchBinding
import com.henryxxiao.splash.utils.SharedPhotoViewModel
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    // 由于内部有 ViewPager2，ViewModel 由当前 SearchFragment 实例化，
    // 内部的 SearchPhotoFragment 可以通过 requireParentFragment().viewModels() 共享它。
    private val searchViewModel: SharedSearchViewModel by activityViewModels()
    private val sharedViewModel: SharedPhotoViewModel by activityViewModels()

    // 当前页面的专属身份证
    private lateinit var sessionId: String


    /**
     * Topics 点击位置恢复。记录最后一次点击的卡片索引 (实例不销毁，数据永存)
     */
    private var lastClickedTopicPosition = 0
    private var clickedCarouse = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = null //防止残影
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 获取专属 Session ID
        sessionId = arguments?.getString("ARG_SESSION_ID") ?: "MAIN_SEARCH"
        // 判断是“从后台返回重建”还是“全新进入”
        val currentQuery = searchViewModel.getParamsFlow(sessionId).value.query
        // 恢复场景或处理 Tag 传递的新词
        val passedQuery = arguments?.getString("QUERY_KEYWORD")


        // 判断是否需要网络请求
        // 如果是从 Tag 带了新词进来，或者是毫无缓存的第一次打开首页搜索
        val isNewTagSearch = !passedQuery.isNullOrEmpty()
        val isFreshHomeSearch = currentQuery.isEmpty() && searchViewModel.topics.value.isNullOrEmpty()

        // 只有“从后台返回重建”时，才暂停动画去等内存里的数据画完
        // 如果是全新的网络请求，绝不暂停，让跳转动画瞬间播放！
        if (!isNewTagSearch && !isFreshHomeSearch) {
            postponeEnterTransition()
        }

        setupButton()
        setupInput()
        setupViewPager()
        // 配置 M3 轮播图
        setupCarousel()

        if (isNewTagSearch && passedQuery != currentQuery && !clickedCarouse) {
            // 场景 A：从 ShowFragment 的 Tag 点进来，且带了一个【全新的搜索词】

            // 设置输入框文字
            binding.searchEditText.setText(passedQuery)
            hideKeyboard()
            // 触发全新搜索
            searchViewModel.searchByQuery(sessionId, passedQuery) // 带上 ID
            showResultsUI()

            // (参数消费)： 触发完立刻把这个词从 arguments 里抹除。 防止将来按返回键回到这个页面时，再次触发这段逻辑导致状态错乱。
            arguments?.remove("QUERY_KEYWORD")

        } else if (currentQuery.isNotEmpty() && !clickedCarouse) {
            // 场景 B：普通的返回重建（比如从详情页按返回键回来）

            // 恢复之前的搜索词
            binding.searchEditText.setText(currentQuery)
            showResultsUI()
        }
//        else {
            // 监听数据
            //observeTopicsAndSearchState()
            // 场景 C：搜索词为空，显示的是 Carousel 轮播图。/ 全新进入 SearchFragment，啥也没搜过
            // 此时底下没有子 Fragment 来帮忙放行动画，必须由父自己放行，否则会白屏卡死
            // 如果是初始场景，不需要等 Paging3，让根视图直接放行动画
//            if (!isWaitingForReenter) {
//                binding.root.doOnPreDraw { startPostponedEnterTransition() }
//            }
//        }


        observeDataFlows()
        // 触发 ViewModel 去抓取 Topics (ViewModel 内部有缓存判断，不怕重复调)
        searchViewModel.fetchTopics(requireContext().applicationContext)
    }

    // 告诉子 Fragment，谁才是当前被选中的 Tab
    fun getActiveTabIndex(): Int {
        return binding.searchViewpager.currentItem
    }

    private fun setupInput() {
        binding.searchEditText.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString().trim()
                if (query.isNotEmpty()) {
                    // 执行搜索 (这里只传 query，筛选条件参数会在 ViewModel 带上)
                    searchViewModel.searchByQuery(sessionId,query)
                    hideKeyboard()
                    showResultsUI()
                }
                true
            } else false
        }
    }

    private fun setupButton() {
        // 设置返回键逻辑
        binding.searchBtnBack.setOnClickListener {
            if (binding.searchTopicLayout.isVisible) {
                findNavController().navigateUp()
            } else {
                hideResultsUI()
            }
        }

        // 搜索筛选
        binding.searchBtnFilter.setOnClickListener {
            hideKeyboard()
            if (parentFragmentManager.findFragmentByTag("FilterSheetTag") == null) {
                // 这里使用了 childFragmentManager
                FilterSheet.newInstance(sessionId).show(childFragmentManager, "FilterSheetTag")
            }
        }
    }

    private fun setupViewPager() {
        val fragments = listOf(SearchPhotoFragment(), SearchUserFragment())

        // Fragment 嵌套 ViewPager2，须用 childFragmentManager
        binding.searchViewpager.adapter =
            object : FragmentStateAdapter(childFragmentManager, viewLifecycleOwner.lifecycle) {
                override fun getItemCount() = fragments.size
                override fun createFragment(position: Int): Fragment = fragments[position]
            }

        TabLayoutMediator(binding.searchTabs, binding.searchViewpager) { tab, position ->
            tab.text =
                if (position == 0) getString(R.string.search_photos) else getString(R.string.search_users)
        }.attach()
    }

//    private fun observeTotals() {
//        viewLifecycleOwner.lifecycleScope.launch {
//            searchViewModel.getTotalPhotoCount(sessionId).collect { total ->
//                binding.searchTabs.getTabAt(0)?.text = getString(R.string.search_photos, total)
//            }
//        }
//        viewLifecycleOwner.lifecycleScope.launch {
//            searchViewModel.getTotalUserCount(sessionId).collect { total ->
//                binding.searchTabs.getTabAt(1)?.text = getString(R.string.search_users, total)
//            }
//        }
//    }

    private fun setupCarousel() {
        // 赋予 M3 轮播布局管理器. MultiBrowseCarouselStrategy 会自动处理一大、一中、一小露出一半的经典视觉效果
        binding.carouselRecyclerView.layoutManager = CarouselLayoutManager(MultiBrowseCarouselStrategy())

        val cachedTopics = searchViewModel.topics.value
        if (!cachedTopics.isNullOrEmpty()) {
            // 如果内存里有数据，直接提交并使用真实 Adapter
            realCarouselAdapter.submitList(cachedTopics)
            binding.carouselRecyclerView.adapter = realCarouselAdapter
        } else {
            binding.carouselRecyclerView.adapter = SkeletonCarouselAdapter(5)

            if (binding.searchTopicLayout.isVisible) {
                binding.carouselRecyclerView.doOnPreDraw {
                    startPostponedEnterTransition()
                }
            }
        }
    }

    // 把 Adapter 提升为全局懒加载实例，并开启状态保护！
    private val realCarouselAdapter by lazy {
        TopicCarouselAdapter { clickedTopic, view, position ->
            // 记住位置
            lastClickedTopicPosition = position

            clickedCarouse = true
            // 将大对象存入内存池
            sharedViewModel.cacheTopicToPool(clickedTopic)
            // 配置共享元素动画 (将卡片变形为详情页)
            val extras = FragmentNavigatorExtras(view to view.transitionName)
            val bundle = Bundle().apply { putString("ARG_TOPIC_ID", clickedTopic.id) }

            findNavController().navigate(
                R.id.action_nav_search_to_nav_topic,
                bundle,
                null,
                extras
            )
        }.apply {
            // 关键：告诉 RecyclerView，没拿到数据前绝对不准重置滑动位置！
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
    }

    // ---------------------------
    // 数据收集与响应
    // ---------------------------
    private fun observeDataFlows() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 采用生命周期感知收集
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 观察 Topics 数据的变化
                launch {
                    searchViewModel.topics.collect { topicsList ->
                        if (!topicsList.isNullOrEmpty()) {
                            realCarouselAdapter.submitList(topicsList)
                            // 数据拿到后，瞬间替换为真实的 Adapter
                            // 如果当前还是骨架屏，才替换为真实 Adapter
                            if (binding.carouselRecyclerView.adapter !is TopicCarouselAdapter) {
                                binding.carouselRecyclerView.adapter = realCarouselAdapter
                            }
                            // 在动画放行前，强行命令轮播图滚回目标卡片. 这样系统去抓取共享元素时，目标卡片绝对在屏幕上.
                            binding.carouselRecyclerView.scrollToPosition(lastClickedTopicPosition)
                            clickedCarouse = false
                            // 如果当前是 Topic 显示模式，且在等待返回动画，就由轮播图负责放行！
                            if (binding.searchTopicLayout.isVisible) {
                                binding.carouselRecyclerView.doOnPreDraw {
                                    startPostponedEnterTransition()
                                }
                            }
                        }
                    }
                }

                launch {
                    searchViewModel.getTotalPhotoCount(sessionId).collect { total ->
                        binding.searchTabs.getTabAt(0)?.text = getString(R.string.search_photos, total)
                    }
                }

                launch {
                    searchViewModel.getTotalUserCount(sessionId).collect { total ->
                        binding.searchTabs.getTabAt(1)?.text = getString(R.string.search_users, total)
                    }
                }
            }
        }
    }

    private fun observeTopicsAndSearchState() {
        // 观察当前搜索词，决定显示 轮播图 还是 搜索结果
//        viewLifecycleOwner.lifecycleScope.launch {
//            searchViewModel.getParamsFlow(sessionId).collect { params ->
//                if (params.query.isNotEmpty()) {
//                    // 如果有搜索词，隐藏轮播图，显示 ViewPager 结果
//                    showResultsUI()
//                } else {
//                    // 如果搜索词为空，显示轮播图，隐藏 ViewPager
//                    hideResultsUI()
//                }
//            }
//        }
    }

    private fun showResultsUI() {
        binding.searchTopicLayout.visibility = View.GONE
        binding.searchViewpager.visibility = View.VISIBLE
        binding.searchTabs.visibility = View.VISIBLE
    }

    private fun hideResultsUI() {
        binding.searchViewpager.visibility = View.GONE
        binding.searchTabs.visibility = View.GONE
        binding.searchTopicLayout.visibility = View.VISIBLE
    }

    private fun showKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.searchEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
        binding.searchEditText.clearFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}