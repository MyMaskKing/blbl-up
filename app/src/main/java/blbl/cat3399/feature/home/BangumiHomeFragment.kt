package blbl.cat3399.feature.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.model.BangumiSeason
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.TabContentFocusTarget
import blbl.cat3399.core.ui.requestFocusAdapterPositionReliable
import blbl.cat3399.core.util.filterHiddenPgcAccess
import blbl.cat3399.databinding.FragmentBangumiHomeBinding
import blbl.cat3399.feature.category.PgcCategoryFragment
import blbl.cat3399.feature.my.BangumiDetailActivity
import blbl.cat3399.ui.RefreshKeyHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BangumiHomeFragment : Fragment(), RefreshKeyHandler, TabContentFocusTarget {
    private var _binding: FragmentBangumiHomeBinding? = null
    private val binding get() = _binding!!

    private data class BangumiSection(
        val title: String,
        val seasonType: Int,
        val items: MutableList<BangumiSeason>,
    )

    private var sections = listOf<BangumiSection>()

    private var hotItems = mutableListOf<BangumiSeason>()
    private var hotAdapter: PgcHorizontalAdapter? = null

    private var bangumiAdapter: PgcHorizontalAdapter? = null
    private var chineseAdapter: PgcHorizontalAdapter? = null
    // 避免初始加载和首页切 tab 自动刷新叠在一起，导致双请求和画面闪烁。
    private var activeLoadCount: Int = 0
    private var initialLoadTriggered: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBangumiHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupSectionClickListeners()
        binding.swipeRefresh.setOnRefreshListener { triggerRefresh() }
        binding.btnSideRefresh.setOnClickListener { triggerRefresh() }
        binding.btnSideRefresh.setOnKeyListener { _, keyCode, event ->
            if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> focusNearestRightContentItem(binding.btnSideRefresh)
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> (parentFragment as? HomeFragment)?.requestFocusHomeTabByOffset(1) == true
                else -> false
            }
        }
        setupFocusOrder()
        initAdapters()
    }

    override fun onResume() {
        super.onResume()
        maybeTriggerInitialLoad()
    }

    private fun setupFocusOrder() {
        binding.tvBangumiTitle.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                return@setOnKeyListener focusFirstInRecycler(binding.rvBangumi)
            }
            false
        }
        binding.btnBangumiMore.setOnKeyListener { _, keyCode, event ->
            if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> return@setOnKeyListener focusFirstInRecycler(binding.rvBangumi)
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> return@setOnKeyListener binding.btnSideRefresh.requestFocus()
            }
            false
        }
        binding.tvChineseTitle.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                return@setOnKeyListener focusFirstInRecycler(binding.rvChinese)
            }
            false
        }
        binding.btnChineseMore.setOnKeyListener { _, keyCode, event ->
            if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> return@setOnKeyListener focusFirstInRecycler(binding.rvChinese)
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> return@setOnKeyListener binding.btnSideRefresh.requestFocus()
            }
            false
        }
    }

    private fun initAdapters() {
        hotAdapter = PgcHorizontalAdapter(
            onItemClick = { season -> openBangumiDetail(season, 1) },
            onEdgeKey = { position, itemCount, keyCode -> handleHomeGridEdgeKey(position, itemCount, keyCode) },
        )
        binding.rvHot.adapter = hotAdapter
        binding.rvHot.layoutManager = GridLayoutManager(context, spanCountForPgc())
        binding.rvHot.setHasFixedSize(true)
        installRecyclerEdgeFallback(binding.rvHot)

        bangumiAdapter = PgcHorizontalAdapter(
            onItemClick = { season -> openBangumiDetail(season, 1) },
            onEdgeKey = { position, itemCount, keyCode -> handleHomeGridEdgeKey(position, itemCount, keyCode) },
        )
        binding.rvBangumi.adapter = bangumiAdapter
        binding.rvBangumi.layoutManager = GridLayoutManager(context, spanCountForPgc())
        binding.rvBangumi.setHasFixedSize(true)
        installRecyclerEdgeFallback(binding.rvBangumi)

        chineseAdapter = PgcHorizontalAdapter(
            onItemClick = { season -> openBangumiDetail(season, 4) },
            onEdgeKey = { position, itemCount, keyCode -> handleHomeGridEdgeKey(position, itemCount, keyCode) },
        )
        binding.rvChinese.adapter = chineseAdapter
        binding.rvChinese.layoutManager = GridLayoutManager(context, spanCountForPgc())
        binding.rvChinese.setHasFixedSize(true)
        installRecyclerEdgeFallback(binding.rvChinese)
    }

    private fun spanCountForPgc(): Int = BiliClient.prefs.pgcGridSpanCount.coerceIn(1, 6)

    private fun loadAllData() {
        sections = listOf(
            BangumiSection("番剧热播", 1, mutableListOf()),
            BangumiSection("国创热播", 4, mutableListOf()),
        )
        loadHotSection()
        sections.forEach { loadSection(it) }
    }

    private fun loadHotSection() {
        markLoadStarted()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    BiliApi.pgcSeasonIndex(
                        seasonType = 1,
                        page = 1,
                        pageSize = hotPageSize(),
                        order = 2,
                        sort = 0,
                    )
                }
                hotItems.clear()
                hotItems.addAll(result.items)
                hotAdapter?.submit(hotItems.filterHiddenPgcAccess())
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("BangumiHome", "load hot section failed", t)
            } finally {
                markLoadFinished()
            }
        }
    }

    private fun loadSection(section: BangumiSection) {
        markLoadStarted()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    BiliApi.pgcSeasonIndex(
                        seasonType = section.seasonType,
                        page = 1,
                        pageSize = 15,
                        order = 5,
                        sort = 0,
                    )
                }
                section.items.clear()
                section.items.addAll(result.items)
                updateSectionAdapter(section)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("BangumiHome", "load ${section.title} failed", t)
            } finally {
                markLoadFinished()
            }
        }
    }

    private fun updateSectionAdapter(section: BangumiSection) {
        val adapter = when (section.seasonType) {
            1 -> bangumiAdapter
            4 -> chineseAdapter
            else -> return
        }
        adapter?.submit(section.items.filterHiddenPgcAccess())
    }

    private fun hotPageSize(): Int = spanCountForPgc() * 4

    private fun setupSectionClickListeners() {
        binding.btnBangumiMore.setOnClickListener { openCategoryPage(1) }
        binding.btnChineseMore.setOnClickListener { openCategoryPage(4) }
        binding.tvBangumiTitle.setOnClickListener { openCategoryPage(1) }
        binding.tvChineseTitle.setOnClickListener { openCategoryPage(4) }
    }

    private fun openCategoryPage(seasonType: Int) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, PgcCategoryFragment().apply {
                arguments = Bundle().apply {
                    putInt("seasonType", seasonType)
                }
            })
            .addToBackStack(null)
            .commit()
    }

    private fun openBangumiDetail(season: BangumiSeason, seasonType: Int) {
        if (!isAdded) return
        startActivity(
            Intent(requireContext(), BangumiDetailActivity::class.java)
                .putExtra(BangumiDetailActivity.EXTRA_SEASON_ID, season.seasonId)
                .putExtra(BangumiDetailActivity.EXTRA_IS_DRAMA, seasonType in listOf(2, 3, 5, 7)),
        )
    }

    private fun refreshAll() {
        loadAllData()
    }

    private fun maybeTriggerInitialLoad() {
        if (initialLoadTriggered) return
        if (binding.swipeRefresh.isRefreshing) return
        binding.swipeRefresh.isRefreshing = true
        loadAllData()
        initialLoadTriggered = true
    }

    override fun handleRefreshKey(): Boolean {
        return triggerRefresh()
    }

    override fun requestFocusPrimaryItemFromTab(): Boolean {
        return focusFirstHotItem()
    }

    override fun requestFocusPrimaryItemFromContentSwitch(): Boolean {
        return focusFirstHotItem()
    }

    private fun focusFirstHotItem(): Boolean {
        val b = _binding ?: return false
        if (!isResumed) return false
        return focusFirstInRecycler(b.rvHot)
    }

    private fun focusFirstInRecycler(recycler: RecyclerView): Boolean {
        if ((recycler.adapter?.itemCount ?: 0) > 0) {
            recycler.requestFocusAdapterPositionReliable(
                position = 0,
                smoothScroll = false,
                isAlive = { _binding != null && isResumed },
                onFocused = {},
            )
        } else {
            recycler.requestFocus()
        }
        return true
    }

    private fun handleHomeGridEdgeKey(position: Int, itemCount: Int, keyCode: Int): Boolean {
        val spanCount = spanCountForPgc()
        return when {
            keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT && position % spanCount == 0 ->
                (parentFragment as? HomeFragment)?.requestFocusHomeTabByOffset(-1) == true
            keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT &&
                (position % spanCount == spanCount - 1 || position == itemCount - 1) ->
                binding.btnSideRefresh.requestFocus()
            else -> false
        }
    }

    private fun installRecyclerEdgeFallback(recycler: RecyclerView) {
        recycler.setOnKeyListener { _, keyCode, event ->
            if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (!recycler.isFocused) return@setOnKeyListener false
            // 空数据或加载中时焦点可能停在 RecyclerView 自身，也要保证右侧快捷按钮可进入。
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> (parentFragment as? HomeFragment)?.requestFocusHomeTabByOffset(-1) == true
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> binding.btnSideRefresh.requestFocus()
                else -> false
            }
        }
    }

    private fun focusNearestRightContentItem(anchor: View): Boolean {
        val b = _binding ?: return false
        val target = findNearestRightItem(
            anchor = anchor,
            recyclers = listOf(b.rvHot, b.rvBangumi, b.rvChinese),
        )
        if (target != null) return target.requestFocus()
        return focusFirstHotItem()
    }

    private fun findNearestRightItem(anchor: View, recyclers: List<RecyclerView>): View? {
        val anchorRect = android.graphics.Rect()
        if (!anchor.getGlobalVisibleRect(anchorRect)) return null
        val anchorCenterY = anchorRect.centerY()

        var best: View? = null
        var bestVerticalDistance = Int.MAX_VALUE
        var bestRight = Int.MIN_VALUE
        val childRect = android.graphics.Rect()

        for (recycler in recyclers) {
            for (i in 0 until recycler.childCount) {
                val child = recycler.getChildAt(i) ?: continue
                if (!child.isFocusable || !child.getGlobalVisibleRect(childRect)) continue
                val verticalDistance = kotlin.math.abs(childRect.centerY() - anchorCenterY)
                if (verticalDistance < bestVerticalDistance || (verticalDistance == bestVerticalDistance && childRect.right > bestRight)) {
                    best = child
                    bestVerticalDistance = verticalDistance
                    bestRight = childRect.right
                }
            }
        }
        return best
    }

    private fun triggerRefresh(): Boolean {
        if (!isAdded) return false
        if (activeLoadCount > 0) {
            binding.swipeRefresh.isRefreshing = false
            return true
        }
        if (binding.swipeRefresh.isRefreshing) return true
        binding.swipeRefresh.isRefreshing = true
        refreshAll()
        return true
    }

    private fun markLoadStarted() {
        activeLoadCount++
    }

    private fun markLoadFinished() {
        activeLoadCount = (activeLoadCount - 1).coerceAtLeast(0)
        if (activeLoadCount == 0) {
            _binding?.swipeRefresh?.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        activeLoadCount = 0
        initialLoadTriggered = false
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = BangumiHomeFragment()
    }
}
