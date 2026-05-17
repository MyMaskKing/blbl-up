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
    val styleId: String = "-1",
    val spokenLanguageType: Int = -1,
    val seasonVersion: Int = -1,
    val isFinish: Int = -1,
    val seasonStatus: String = "-1",
    val year: String = "-1",
    val releaseDate: String = "-1",
    // 分类页默认按播放量排序，避免进入页面时落到按时间排序的列表。
    val order: Int = 2,
    val sort: Int = 0,
) {
    val displayName: String
        get() {
            val names = mutableListOf<String>()
            if (area != "-1") names.add(PgcConstants.getAreaName(seasonType, area) ?: "地区")
            if (styleId != "-1") names.add(PgcConstants.getStyleName(seasonType, styleId) ?: "题材")
            val selectedYear = if (seasonType in listOf(1, 4)) year else releaseDate
            if (selectedYear != "-1") names.add(PgcConstants.getYearName(seasonType, selectedYear) ?: selectedYear)
            names.add(PgcConstants.getOrderName(seasonType, order) ?: "排序")
            if (sort != 0) names.add(PgcConstants.getSortName(seasonType, order, sort) ?: "升序")
            return if (names.isEmpty()) "筛选" else names.joinToString(" · ")
        }

    fun getEffectiveOrder(): Int = order
}

object PgcConstants {
    // 下列筛选值按 B 站 PGC condition 接口维护，逗号组合值需要原样透传。
    fun getStyles(seasonType: Int): List<Pair<String, String>> {
        return when (seasonType) {
            1 -> listOf(
                "全部" to "-1",
                // 题材 ID 来自 B 站 PGC condition 接口，旧的 2/3/104 等 ID 已不再命中。
                "原创" to "10010", "漫画改" to "10011", "小说改" to "10012", "游戏改" to "10013",
                "特摄" to "10102", "布袋戏" to "10015", "热血" to "10016", "穿越" to "10017",
                "奇幻" to "10018", "战斗" to "10020", "搞笑" to "10021", "日常" to "10022",
                "科幻" to "10023", "萌系" to "10024", "治愈" to "10025", "校园" to "10026",
                "少儿" to "10027", "泡面" to "10028", "恋爱" to "10029", "少女" to "10030",
                "魔法" to "10031", "冒险" to "10032", "历史" to "10033", "架空" to "10034",
                "机战" to "10035", "神魔" to "10036", "声控" to "10037", "运动" to "10038",
                "励志" to "10039", "音乐" to "10040", "推理" to "10041", "社团" to "10042",
                "智斗" to "10043", "催泪" to "10044", "美食" to "10045", "偶像" to "10046",
                "乙女" to "10047", "职场" to "10048",
            )
            2 -> listOf(
                "全部" to "-1",
                "短片" to "10104", "剧情" to "10050", "喜剧" to "10051", "爱情" to "10052",
                "动作" to "10053", "恐怖" to "10054", "科幻" to "10023", "犯罪" to "10055",
                "惊悚" to "10056", "悬疑" to "10057", "奇幻" to "10018", "战争" to "10058",
                "动画" to "10059", "传记" to "10060", "家庭" to "10061", "歌舞" to "10062",
                "历史" to "10033", "冒险" to "10032", "纪实" to "10063", "灾难" to "10064",
                "漫画改" to "10011", "小说改" to "10012",
            )
            3 -> listOf(
                "全部" to "-1",
                "历史" to "10033", "美食" to "10045", "人文" to "10065", "科技" to "10066",
                "探险" to "10067", "宇宙" to "10068", "萌宠" to "10069", "社会" to "10070",
                "动物" to "10071", "自然" to "10072", "医疗" to "10073", "军事" to "10074",
                "灾难" to "10064", "罪案" to "10075", "神秘" to "10076", "旅行" to "10077",
                "运动" to "10038", "电影" to "-10",
            )
            4 -> listOf(
                "全部" to "-1",
                "原创" to "10010", "漫画改" to "10011", "小说改" to "10012", "游戏改" to "10013",
                "动态漫" to "10014", "布袋戏" to "10015", "热血" to "10016", "奇幻" to "10018",
                "玄幻" to "10019", "战斗" to "10020", "搞笑" to "10021", "武侠" to "10078",
                "日常" to "10022", "科幻" to "10023", "萌系" to "10024", "治愈" to "10025",
                "悬疑" to "10057", "校园" to "10026", "少儿" to "10027", "泡面" to "10028",
                "恋爱" to "10029", "少女" to "10030", "魔法" to "10031", "历史" to "10033",
                "机战" to "10035", "神魔" to "10036", "声控" to "10037", "运动" to "10038",
                "励志" to "10039", "音乐" to "10040", "推理" to "10041", "社团" to "10042",
                "智斗" to "10043", "催泪" to "10044", "美食" to "10045", "偶像" to "10046",
                "乙女" to "10047", "职场" to "10048", "古风" to "10049", "漫剧" to "50112",
            )
            5 -> listOf(
                "全部" to "-1",
                "剧情" to "10050", "情感" to "10084", "搞笑" to "10021", "悬疑" to "10057",
                "都市" to "10080", "家庭" to "10061", "古装" to "10081", "历史" to "10033",
                "奇幻" to "10018", "青春" to "10079", "战争" to "10058", "武侠" to "10078",
                "励志" to "10039", "短剧" to "10103", "科幻" to "10023",
                "其他" to "10086,10088,10089,10017,10083,10082,10087,10085",
            )
            7 -> listOf(
                "全部" to "-1",
                "音乐" to "10040", "访谈" to "10090", "脱口秀" to "10091", "真人秀" to "10092",
                "选秀" to "10094", "美食" to "10045", "旅游" to "10095", "晚会" to "10098",
                "演唱会" to "10096", "情感" to "10084", "喜剧" to "10051", "亲子" to "10097",
                "文化" to "10100", "职场" to "10048", "萌宠" to "10069", "养成" to "10099",
            )
            else -> listOf("全部" to "-1")
        }
    }

    fun getStyleName(seasonType: Int, styleId: String): String? {
        return getStyles(seasonType).find { it.second == styleId }?.first
    }

    fun getOrderItems(seasonType: Int): List<Pair<String, Int>> {
        return when (seasonType) {
            1, 4 -> listOf(
                "追番人数" to 3,
                "更新时间" to 0,
                "最高评分" to 4,
                "播放数量" to 2,
                "开播时间" to 5,
            )
            2 -> listOf(
                "播放数量" to 2,
                "更新时间" to 0,
                "上映时间" to 6,
                "最高评分" to 4,
            )
            3 -> listOf(
                "播放数量" to 2,
                "最高评分" to 4,
                "更新时间" to 0,
                "上映时间" to 6,
                "弹幕数量" to 1,
            )
            5 -> listOf(
                "播放数量" to 2,
                "更新时间" to 0,
                "弹幕数量" to 1,
                "最高评分" to 4,
                "追剧人数" to 3,
            )
            7 -> listOf(
                "最多播放" to 2,
                "最近更新" to 0,
                "最新上映" to 6,
                "最高评分" to 4,
                "弹幕数量" to 1,
            )
            else -> listOf("播放数量" to 2)
        }
    }

    fun getOrderName(seasonType: Int, order: Int): String? {
        return getOrderItems(seasonType).find { it.second == order }?.first
    }

    fun getSortItems(seasonType: Int, order: Int): List<Pair<String, Int>> {
        val supportsAscending =
            when (seasonType) {
                1, 4 -> order in setOf(0, 2, 3, 5)
                2 -> order in setOf(0, 2, 6)
                3 -> order in setOf(0, 1, 2, 6)
                5 -> order in setOf(0, 1, 2, 3)
                7 -> order in setOf(0, 1, 2, 6)
                else -> order in setOf(0, 1, 2, 3, 5, 6)
            }
        return if (!supportsAscending) {
            listOf("默认" to 0)
        } else {
            // B 站接口的 order.sort 返回 0,1；这里按字段语义展示，值原样透传。
            when (order) {
                0, 5, 6 -> listOf("从新到旧" to 0, "从旧到新" to 1)
                else -> listOf("从高到低" to 0, "从低到高" to 1)
            }
        }
    }

    fun getSortName(seasonType: Int, order: Int, sort: Int): String? {
        return getSortItems(seasonType, order).find { it.second == sort }?.first
    }

    fun getAreaItems(seasonType: Int): List<Pair<String, String>> {
        return when (seasonType) {
            1 -> listOf(
                "全部" to "-1",
                "日本" to "2",
                "美国" to "3",
                "其他" to "1,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70",
            )
            2 -> listOf(
                "全部" to "-1",
                "中国大陆" to "1",
                "中国港台" to "6,7",
                "美国" to "3",
                "日本" to "2",
                "韩国" to "8",
                "法国" to "9",
                "英国" to "4",
                "德国" to "15",
                "泰国" to "10",
                "意大利" to "35",
                "西班牙" to "13",
                "其他" to "5,11,12,14,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70",
            )
            5 -> listOf(
                "全部" to "-1",
                "中国" to "1,6,7",
                "日本" to "2",
                "美国" to "3",
                "英国" to "4",
                "其他" to "5,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70",
            )
            else -> listOf("全部" to "-1")
        }
    }

    fun getAreaName(seasonType: Int, area: String): String? {
        return getAreaItems(seasonType).find { it.second == area }?.first
    }

    fun getYearItems(seasonType: Int): List<Pair<String, String>> {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val isAnimeOrGc = seasonType in listOf(1, 4)
        val items = mutableListOf("全部" to "-1")
        for (year in currentYear downTo 2016) {
            if (isAnimeOrGc) {
                items += year.toString() to "[${year},${year + 1})"
            } else {
                items += year.toString() to "[${year}-01-01 00:00:00,${year + 1}-01-01 00:00:00)"
            }
        }
        return if (isAnimeOrGc) {
            items + listOf(
                "2015" to "[2015,2016)",
                "2014-2010" to "[2010,2015)",
                "2009-2005" to "[2005,2010)",
                "2004-2000" to "[2000,2005)",
                "90年代" to "[1990,2000)",
                "80年代" to "[1980,1990)",
                "更早" to "[,1980)",
            )
        } else {
            items + listOf(
                "2015-2010" to "[2010-01-01 00:00:00,2016-01-01 00:00:00)",
                "2009-2005" to "[2005-01-01 00:00:00,2010-01-01 00:00:00)",
                "2004-2000" to "[2000-01-01 00:00:00,2005-01-01 00:00:00)",
                "90年代" to "[1990-01-01 00:00:00,2000-01-01 00:00:00)",
                "80年代" to "[1980-01-01 00:00:00,1990-01-01 00:00:00)",
                "更早" to "[,1980-01-01 00:00:00)",
            )
        }
    }

    fun getYearName(seasonType: Int, year: String): String? {
        return getYearItems(seasonType).find { it.second == year }?.first
    }

    fun getStatusItems(seasonType: Int): List<Pair<String, String>> {
        return when (seasonType) {
            1, 2, 4 -> listOf(
                "全部" to "-1",
                "免费" to "1",
                "付费" to "2,6",
                "大会员" to "4,6",
            )
            3, 5, 7 -> listOf(
                "全部" to "-1",
                "免费" to "1",
                "大会员" to "4,6",
            )
            else -> listOf("全部" to "-1")
        }
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
        val initialSeasonType = arguments?.getInt("seasonType", 1) ?: 1
        filterState = PgcFilterState(seasonType = initialSeasonType)
        setupAdapter()
        setupTabs()
        setupRecyclerView()
        setupSwipeRefresh()
        setupFilterButton()
        selectTabForSeasonType(initialSeasonType)
        // selectTab 内部已触发 onTabSelected → resetAndLoad，无需再调 maybeTriggerInitialLoad
    }

    private fun selectTabForSeasonType(seasonType: Int) {
        val tabLayout = binding.tabLayout
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i)
            if (tab?.tag == seasonType) {
                initialLoadTriggered = true
                tab.select()
                return
            }
        }
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
                    filterState = PgcFilterState(seasonType = seasonType)
                    updateFilterButtonText()
                    initialLoadTriggered = true
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
        binding.btnSideFilter.setOnClickListener {
            showFilterDialog()
        }
        binding.btnSideRefresh.setOnClickListener {
            refreshFromShortcut()
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
                        styleId = filterState.styleId.takeIf { it != "-1" },
                        spokenLanguageType = filterState.spokenLanguageType.takeIf { it != -1 },
                        seasonVersion = filterState.seasonVersion.takeIf { it != -1 },
                        isFinish = filterState.isFinish.takeIf { it != -1 },
                        seasonStatus = filterState.seasonStatus.takeIf { it != "-1" },
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
        return refreshFromShortcut()
    }

    private fun refreshFromShortcut(): Boolean {
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
