package blbl.cat3399.feature.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
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
    private val loadedSeasonTypes = mutableSetOf<Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBangumiHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupSections()
        setupSectionClickListeners()
        binding.swipeRefresh.setOnRefreshListener { refreshAll() }
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
            if (!loadedSeasonTypes.contains(section.seasonType)) {
                loadSection(section)
            }
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
                loadedSeasonTypes.add(section.seasonType)
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
                1 -> setupHorizontalList(b.rvBangumi, section)
                4 -> setupHorizontalList(b.rvChinese, section)
            }
        }
    }

    private fun setupHorizontalList(recyclerView: RecyclerView, section: BangumiSection) {
        if (recyclerView.adapter != null) return

        val adapter = PgcHorizontalAdapter { season ->
            openBangumiDetail(season, section.seasonType)
        }
        adapter.submit(section.items)

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.setHasFixedSize(true)
    }

    private fun setupSectionClickListeners() {
        binding.btnBangumiFilter.setOnClickListener { openCategoryPage(1) }
        binding.btnChineseFilter.setOnClickListener { openCategoryPage(4) }

        binding.tvBangumiTitle.setOnClickListener { openCategoryPage(1) }
        binding.tvChineseTitle.setOnClickListener { openCategoryPage(4) }
    }

    private fun openCategoryPage(seasonType: Int) {
        (parentFragment as? HomeFragment)?.let { homeFragment ->
            homeFragment.childFragmentManager.beginTransaction()
                .replace(R.id.main_container, PgcCategoryFragment().apply {
                    arguments = Bundle().apply {
                        putInt("seasonType", seasonType)
                    }
                })
                .addToBackStack(null)
                .commit()
        }
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
        loadedSeasonTypes.clear()
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
