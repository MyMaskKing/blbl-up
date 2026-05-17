package blbl.cat3399.feature.category

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.model.BangumiSeason
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.DpadGridController
import blbl.cat3399.core.ui.postIfAlive
import blbl.cat3399.core.ui.requestFocusAdapterPositionReliable
import blbl.cat3399.core.ui.requestFocusFirstItemOrSelfAfterRefresh
import blbl.cat3399.databinding.FragmentPgcCategoryBinding
import blbl.cat3399.feature.my.BangumiFollowAdapter
import blbl.cat3399.feature.my.BangumiDetailActivity
import blbl.cat3399.ui.RefreshKeyHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class PgcFilterState(
    val seasonType: Int = 1,
    val area: String = "-1",
    val styleId: Int = -1,
    val spokenLanguageType: Int = -1,
    val seasonVersion: Int = -1,
    val isFinish: Int = -1,
    val seasonStatus: Int = -1,
    val year: String = "-1",
    val releaseDate: String = "-1",
    val order: Int = -1,
    val sort: Int = 0,
) {
    val displayName: String
        get() {
            val names = mutableListOf<String>()
            if (area != "-1") names.add(PgcConstants.PGC_AREA_NAMES[area] ?: "地区")
            if (styleId != -1) names.add(PgcConstants.getStyleName(seasonType, styleId) ?: "题材")
            if (year != "-1") names.add(year)
            if (order != -1) names.add(PgcConstants.PGC_ORDER_NAMES.getOrNull(order) ?: "排序")
            return if (names.isEmpty()) "筛选" else names.joinToString(" · ")
        }

    fun getEffectiveOrder(): Int {
        if (order != -1) return order
        return if (seasonType in listOf(1, 4)) 5 else 6
    }
}

object PgcConstants {
    val PGC_AREA_NAMES = mapOf(
        "1" to "中国大陆",
        "2" to "日本",
        "3" to "美国",
        "4" to "英国",
        "6" to "中国香港",
        "7" to "中国台湾",
        "8" to "韩国",
        "9" to "法国",
        "10" to "泰国",
    )
    val PGC_ORDER_NAMES = mapOf(
        0 to "更新时间",
        1 to "弹幕数量",
        2 to "播放数量",
        3 to "追剧人数",
        4 to "最高评分",
        5 to "开播时间",
        6 to "上映时间",
    )

    fun getStyles(seasonType: Int): List<Pair<String, Int>> {
        return when (seasonType) {
            1 -> listOf(
                "全部" to -1,
                "热血" to 2,
                "战斗" to 3,
                "青春" to 5,
                "搞笑" to 6,
                "日常" to 7,
                "治愈" to 8,
                "励志" to 10,
                "恋爱" to 11,
                "科幻" to 12,
                "奇幻" to 13,
                "悬疑" to 15,
                "冒险" to 16,
                "校园" to 17,
                "魔法" to 20,
                "动作" to 21,
                "百合" to 24,
                "运动" to 25,
                "推理" to 26,
                "偶像" to 27,
                "音乐" to 28,
                "竞技" to 30,
            )
            2, 3, 5, 7 -> listOf(
                "全部" to -1,
                "动作" to 104,
                "喜剧" to 105,
                "爱情" to 106,
                "科幻" to 107,
                "奇幻" to 108,
                "悬疑" to 109,
                "犯罪" to 110,
                "恐怖" to 111,
                "冒险" to 112,
                "战争" to 113,
                "动画" to 114,
                "纪录片" to 115,
                "历史" to 116,
                "古装" to 117,
                "运动" to 118,
                "音乐" to 119,
            )
            4 -> listOf(
                "全部" to -1,
                "搞笑" to 51,
                "日常" to 52,
                "励志" to 53,
                "治愈" to 54,
                "古风" to 55,
                "治愈" to 56,
            )
            else -> listOf("全部" to -1)
        }
    }

    fun getStyleName(seasonType: Int, styleId: Int): String? {
        return getStyles(seasonType).find { it.second == styleId }?.first
    }

    fun getOrderItems(seasonType: Int): List<Pair<String, Int>> {
        val base = listOf(
            "更新时间" to 0,
            "弹幕数量" to 1,
            "播放数量" to 2,
            "追剧人数" to 3,
            "最高评分" to 4,
        )
        return if (seasonType in listOf(1, 4)) {
            base + listOf("开播时间" to 5)
        } else {
            base + listOf("上映时间" to 6)
        }
    }

    fun getAreaItems(): List<Pair<String, String>> {
        return listOf(
            "全部" to "-1",
            "中国大陆" to "1",
            "日本" to "2",
            "美国" to "3",
            "英国" to "4",
            "中国香港" to "6",
            "中国台湾" to "7",
            "韩国" to "8",
            "法国" to "9",
            "泰国" to "10",
        )
    }

    fun getYearItems(): List<Pair<String, String>> {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return mutableListOf("全部" to "-1").apply {
            for (year in currentYear downTo 2010) {
                add(year.toString() to year.toString())
            }
            add("2010年以前" to "[2010,2015)")
        }
    }

    fun getStatusItems(): List<Pair<String, Int>> {
        return listOf(
            "全部" to -1,
            "免费" to 1,
            "付费" to 2,
            "大会员" to 4,
        )
    }

    fun getFinishItems(): List<Pair<String, Int>> {
        return listOf(
            "全部" to -1,
            "连载中" to 0,
            "已完结" to 1,
        )
    }
}

class PgcCategoryFragment : Fragment(), RefreshKeyHandler {
    private var _binding: FragmentPgcCategoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: BangumiFollowAdapter

    private var currentPage = 1
    private var hasNext = true
    private var isLoading = false
    private var isLoadingMore = false
    private var initialLoadTriggered = false

    private var pendingRestorePosition: Int? = null
    private var pendingFocusFirstCardAfterRefresh = false

    private var lastFocusedAdapterPosition: Int? = null
    private var dpadGridController: DpadGridController? = null

    private var filterState = PgcFilterState()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPgcCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupAdapter()
        setupTabs()
        setupRecyclerView()
        setupSwipeRefresh()
        setupFilterButton()
        maybeTriggerInitialLoad()
    }

    private fun setupAdapter() {
        if (!::adapter.isInitialized) {
            adapter = BangumiFollowAdapter { _, season -> openBangumiDetail(season) }
        }
    }

    private fun setupTabs() {
        val tabLayout = binding.tabLayout
        tabLayout.addTab(tabLayout.newTab().setText("番剧").setTag(1))
        tabLayout.addTab(tabLayout.newTab().setText("电影").setTag(2))
        tabLayout.addTab(tabLayout.newTab().setText("纪录片").setTag(3))
        tabLayout.addTab(tabLayout.newTab().setText("国创").setTag(4))
        tabLayout.addTab(tabLayout.newTab().setText("电视剧").setTag(5))
        tabLayout.addTab(tabLayout.newTab().setText("综艺").setTag(7))

        tabLayout.addOnTabSelectedListener(
            object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                    val seasonType = tab?.tag as? Int ?: 1
                    filterState = filterState.copy(seasonType = seasonType)
                    resetAndLoad()
                }

                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            },
        )
    }

    private fun setupRecyclerView() {
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), pgcGridSpanCount())
        (binding.recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.recyclerView.clearOnScrollListeners()

        dpadGridController?.release()
        dpadGridController =
            DpadGridController(
                recyclerView = binding.recyclerView,
                callbacks =
                    object : DpadGridController.Callbacks {
                        override fun onTopEdge(): Boolean {
                            binding.tabLayout.getTabAt(0)?.view?.requestFocus()
                            return true
                        }

                        override fun onLeftEdge(): Boolean {
                            return false
                        }

                        override fun onRightEdge() {
                        }

                        override fun canLoadMore(): Boolean = hasNext && !isLoadingMore

                        override fun loadMore() {
                            loadNextPage()
                        }
                    },
                config = DpadGridController.Config(isEnabled = { _binding != null && isResumed }),
            ).also { it.install() }

        binding.recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    if (isLoadingMore || !hasNext) return
                    val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                    val last = lm.findLastVisibleItemPosition()
                    val total = adapter.itemCount
                    if (total <= 0) return
                    if (total - last - 1 <= 8) loadNextPage()
                }
            },
        )
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { resetAndLoad(fromUserRefresh = true) }
    }

    private fun pgcGridSpanCount(): Int = BiliClient.prefs.pgcGridSpanCount.coerceIn(1, 6)

    private fun setupFilterButton() {
        binding.btnFilter.setOnClickListener {
            showFilterDialog()
        }
        updateFilterButtonText()
    }

    private fun showFilterDialog() {
        val oldState = filterState
        val dialog = PgcFilterDialog(requireContext(), filterState) { newState ->
            filterState = newState
            if (newState != oldState) {
                updateFilterButtonText()
                resetAndLoad()
            }
        }
        dialog.show()
    }

    private fun updateFilterButtonText() {
        binding.btnFilter.text = filterState.displayName
    }

    private fun maybeTriggerInitialLoad() {
        if (initialLoadTriggered) return
        if (!::adapter.isInitialized) return
        if (adapter.itemCount != 0) {
            initialLoadTriggered = true
            return
        }
        if (binding.swipeRefresh.isRefreshing) return
        binding.swipeRefresh.isRefreshing = true
        resetAndLoad(fromUserRefresh = false)
        initialLoadTriggered = true
    }

    private fun resetAndLoad(fromUserRefresh: Boolean = false) {
        isLoading = false
        isLoadingMore = false
        currentPage = 1
        hasNext = true
        pendingRestorePosition = null
        if (fromUserRefresh) {
            pendingFocusFirstCardAfterRefresh = true
            dpadGridController?.parkFocusForDataSetReset()
        }
        adapter.submit(emptyList())
        loadNextPage(isRefresh = true)
    }

    private fun loadNextPage(isRefresh: Boolean = false) {
        if (isLoading) return
        if (!isRefresh && (isLoadingMore || !hasNext)) return

        isLoading = true
        if (!isRefresh) isLoadingMore = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result =
                    BiliApi.pgcSeasonIndex(
                        seasonType = filterState.seasonType,
                        page = currentPage,
                        pageSize = 20,
                        area = filterState.area.takeIf { it != "-1" },
                        styleId = filterState.styleId.takeIf { it != -1 },
                        spokenLanguageType = filterState.spokenLanguageType.takeIf { it != -1 },
                        seasonVersion = filterState.seasonVersion.takeIf { it != -1 },
                        isFinish = filterState.isFinish.takeIf { it != -1 },
                        seasonStatus = filterState.seasonStatus.takeIf { it != -1 },
                        year = filterState.year.takeIf { it != "-1" },
                        releaseDate = filterState.releaseDate.takeIf { it != "-1" },
                        order = filterState.getEffectiveOrder(),
                        sort = filterState.sort,
                    )

                hasNext = result.hasNext
                currentPage++

                if (isRefresh) {
                    adapter.submit(result.items)
                } else {
                    adapter.append(result.items)
                }

                _binding?.let { b ->
                    b.recyclerView.postIfAlive(isAlive = { _binding === b && isResumed }) {
                        if (isRefresh && pendingFocusFirstCardAfterRefresh) {
                            pendingFocusFirstCardAfterRefresh = false
                            pendingRestorePosition = null
                            lastFocusedAdapterPosition = adapter.itemCount.takeIf { it > 0 }?.let { 0 }
                            val recycler = b.recyclerView
                            recycler.requestFocusFirstItemOrSelfAfterRefresh(
                                itemCount = adapter.itemCount,
                                smoothScroll = false,
                                isAlive = { _binding === b && isResumed },
                                onDone = { focusedFirstItem ->
                                    if (focusedFirstItem) lastFocusedAdapterPosition = 0
                                    dpadGridController?.unparkFocusAfterDataSetReset()
                                },
                            )
                        } else {
                            dpadGridController?.consumePendingFocusAfterLoadMore()
                        }
                    }
                }

                pendingRestorePosition?.let { restoreFocusIfNeeded() }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("PgcCategory", "load failed", t)
                context?.let { AppToast.show(it, "加载失败，可查看 Logcat(标签 BLBL)") }
            } finally {
                isLoading = false
                isLoadingMore = false
                _binding?.swipeRefresh?.isRefreshing = false
            }
        }
    }

    private fun openBangumiDetail(season: BangumiSeason) {
        if (!isAdded) return
        startActivity(
            Intent(requireContext(), BangumiDetailActivity::class.java)
                .putExtra(BangumiDetailActivity.EXTRA_SEASON_ID, season.seasonId)
                .putExtra(BangumiDetailActivity.EXTRA_IS_DRAMA, filterState.seasonType in listOf(2, 3, 5, 7)),
        )
    }

    private fun restoreFocusIfNeeded() {
        val pos = pendingRestorePosition ?: return
        if (_binding == null) return
        if (pos < 0 || pos >= adapter.itemCount) return
        val recycler = binding.recyclerView
        recycler.postIfAlive(isAlive = { _binding != null }) {
            recycler.scrollToPosition(pos)
            recycler.postIfAlive(isAlive = { _binding != null }) {
                recycler.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
                pendingRestorePosition = null
            }
        }
    }

    override fun handleRefreshKey(): Boolean {
        val b = _binding ?: return false
        if (!isResumed) return false
        if (b.swipeRefresh.isRefreshing) return true
        b.swipeRefresh.isRefreshing = true
        resetAndLoad(fromUserRefresh = true)
        return true
    }

    override fun onResume() {
        super.onResume()
        (binding.recyclerView.layoutManager as? GridLayoutManager)?.spanCount = pgcGridSpanCount()
    }

    override fun onDestroyView() {
        initialLoadTriggered = false
        pendingRestorePosition = null
        dpadGridController?.release()
        dpadGridController = null
        _binding = null
        super.onDestroyView()
    }
}
