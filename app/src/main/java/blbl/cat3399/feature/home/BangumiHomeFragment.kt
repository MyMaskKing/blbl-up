package blbl.cat3399.feature.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.model.BangumiSeason
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.databinding.FragmentBangumiHomeBinding
import blbl.cat3399.feature.category.PgcCategoryFragment
import blbl.cat3399.feature.my.BangumiDetailActivity
import blbl.cat3399.ui.RefreshKeyHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BangumiHomeFragment : Fragment(), RefreshKeyHandler {
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBangumiHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupSectionClickListeners()
        binding.swipeRefresh.setOnRefreshListener { triggerRefresh() }
        binding.btnSideRefresh.setOnClickListener { triggerRefresh() }
        initAdapters()
        loadAllData()
    }

    private fun initAdapters() {
        hotAdapter = PgcHorizontalAdapter(
            onItemClick = { season -> openBangumiDetail(season, 1) },
        )
        binding.rvHot.adapter = hotAdapter
        binding.rvHot.layoutManager = GridLayoutManager(context, spanCountForPgc())
        binding.rvHot.setHasFixedSize(true)

        bangumiAdapter = PgcHorizontalAdapter(
            onItemClick = { season -> openBangumiDetail(season, 1) })
        binding.rvBangumi.adapter = bangumiAdapter
        binding.rvBangumi.layoutManager = GridLayoutManager(context, spanCountForPgc())
        binding.rvBangumi.setHasFixedSize(true)

        chineseAdapter = PgcHorizontalAdapter(
            onItemClick = { season -> openBangumiDetail(season, 4) })
        binding.rvChinese.adapter = chineseAdapter
        binding.rvChinese.layoutManager = GridLayoutManager(context, spanCountForPgc())
        binding.rvChinese.setHasFixedSize(true)
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
                hotAdapter?.submit(hotItems)
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
        adapter?.submit(section.items)
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

    override fun handleRefreshKey(): Boolean {
        return triggerRefresh()
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
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = BangumiHomeFragment()
    }
}
