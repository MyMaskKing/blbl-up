package blbl.cat3399.feature.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.model.BangumiSeason
import blbl.cat3399.databinding.FragmentBangumiHomeBinding
import blbl.cat3399.databinding.ItemPgcHorizontalBinding
import blbl.cat3399.feature.category.PgcCategoryFragment
import blbl.cat3399.feature.my.BangumiDetailActivity
import blbl.cat3399.ui.RefreshKeyHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class BangumiHomeFragment : Fragment(), RefreshKeyHandler {
    private var _binding: FragmentBangumiHomeBinding? = null
    private val binding get() = _binding!!

    private data class BangumiSection(
        val title: String,
        val seasonType: Int,
        val items: MutableList<BangumiSeason>,
    )

    private var sections = listOf<BangumiSection>()
    
    // 正在热播数据
    private var hotItems = mutableListOf<BangumiSeason>()
    private var hotAdapter: PgcHorizontalAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBangumiHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupSectionClickListeners()
        binding.swipeRefresh.setOnRefreshListener { refreshAll() }
        
        // 延迟加载数据，确保视图完全初始化
        view.post {
            setupHotSection()
            setupSections()
        }
    }

    private fun setupHotSection() {
        hotAdapter = PgcHorizontalAdapter { season ->
            openBangumiDetail(season, 1) // 默认当作番剧类型打开
        }
        binding.rvHot.adapter = hotAdapter
        binding.rvHot.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvHot.setHasFixedSize(true)
        loadHotSection()
    }

    private fun loadHotSection() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 加载番剧热播内容
                val result = BiliApi.pgcSeasonIndex(
                    seasonType = 1,
                    page = 1,
                    pageSize = 15,
                    order = 2, // 播放数量排序
                    sort = 0,
                )
                hotItems.clear()
                hotItems.addAll(result.items)
                hotAdapter?.submit(hotItems)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("BangumiHome", "load hot section failed", t)
            }
        }
    }

    private fun setupSections() {
        sections = listOf(
            BangumiSection("番剧热播", 1, mutableListOf()),
            BangumiSection("国创热播", 4, mutableListOf()),
        )
        loadSections()
    }

    private fun loadSections() {
        sections.forEach { section ->
            loadSection(section)
        }
    }

    private fun loadSection(section: BangumiSection) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = BiliApi.pgcSeasonIndex(
                    seasonType = section.seasonType,
                    page = 1,
                    pageSize = 15,
                    order = 5, // 开播时间排序
                    sort = 0,
                )
                section.items.clear()
                section.items.addAll(result.items)
                updateSectionView(section)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("BangumiHome", "load ${section.title} failed", t)
            }
        }
    }

    private fun updateSectionView(section: BangumiSection) {
        _binding?.let { b ->
            when (section.seasonType) {
                1 -> setupVerticalGridList(b.rvBangumi, section)
                4 -> setupVerticalGridList(b.rvChinese, section)
            }
        }
    }

    private fun setupVerticalGridList(recyclerView: RecyclerView, section: BangumiSection) {
        if (recyclerView.adapter != null) return

        val adapter = PgcHorizontalAdapter { season ->
            openBangumiDetail(season, section.seasonType)
        }
        adapter.submit(section.items)

        recyclerView.adapter = adapter
        // 使用4列网格布局
        recyclerView.layoutManager = GridLayoutManager(context, 4)
        recyclerView.setHasFixedSize(true)
    }

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
        hotItems.clear()
        hotAdapter?.submit(hotItems)
        loadHotSection()
        
        sections.forEach { section ->
            section.items.clear()
        }
        binding.rvBangumi.adapter = null
        binding.rvChinese.adapter = null
        loadSections()
        binding.swipeRefresh.isRefreshing = false
    }

    override fun handleRefreshKey(): Boolean {
        if (binding.swipeRefresh.isRefreshing) return true
        binding.swipeRefresh.isRefreshing = true
        refreshAll()
        return true
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = BangumiHomeFragment()
    }
}
