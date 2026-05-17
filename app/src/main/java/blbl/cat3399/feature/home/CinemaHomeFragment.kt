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
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.databinding.FragmentCinemaHomeBinding
import blbl.cat3399.databinding.ItemPgcHorizontalBinding
import blbl.cat3399.feature.category.PgcCategoryFragment
import blbl.cat3399.feature.my.BangumiDetailActivity
import blbl.cat3399.ui.RefreshKeyHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class CinemaHomeFragment : Fragment(), RefreshKeyHandler {
    private var _binding: FragmentCinemaHomeBinding? = null
    private val binding get() = _binding!!

    private data class CinemaSection(
        val title: String,
        val seasonType: Int,
        val items: MutableList<BangumiSeason>,
    )

    private var sections = listOf<CinemaSection>()
    private val loadedSeasonTypes = mutableSetOf<Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCinemaHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupSections()
        setupSectionClickListeners()
        binding.swipeRefresh.setOnRefreshListener { refreshAll() }
    }

    private fun setupSections() {
        sections = listOf(
            CinemaSection("电影热播", 2, mutableListOf()),
            CinemaSection("电视剧热播", 5, mutableListOf()),
            CinemaSection("纪录片热播", 3, mutableListOf()),
            CinemaSection("综艺热播", 7, mutableListOf()),
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

    private fun loadSection(section: CinemaSection) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = BiliApi.pgcSeasonIndex(
                    seasonType = section.seasonType,
                    page = 1,
                    pageSize = 15,
                    order = 6, // 上映时间排序
                    sort = 0,
                )
                loadedSeasonTypes.add(section.seasonType)
                section.items.clear()
                section.items.addAll(result.items)
                updateSectionView(section)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("CinemaHome", "load ${section.title} failed", t)
            }
        }
    }

    private fun updateSectionView(section: CinemaSection) {
        _binding?.let { b ->
            when (section.seasonType) {
                2 -> setupHorizontalList(b.rvMovies, section)
                5 -> setupHorizontalList(b.rvTv, section)
                3 -> setupHorizontalList(b.rvDocumentary, section)
                7 -> setupHorizontalList(b.rvVariety, section)
            }
        }
    }

    private fun setupHorizontalList(recyclerView: RecyclerView, section: CinemaSection) {
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
        binding.btnMoviesFilter.setOnClickListener { openCategoryPage(2) }
        binding.btnTvFilter.setOnClickListener { openCategoryPage(5) }
        binding.btnDocumentaryFilter.setOnClickListener { openCategoryPage(3) }
        binding.btnVarietyFilter.setOnClickListener { openCategoryPage(7) }

        binding.tvMoviesTitle.setOnClickListener { openCategoryPage(2) }
        binding.tvTvTitle.setOnClickListener { openCategoryPage(5) }
        binding.tvDocumentaryTitle.setOnClickListener { openCategoryPage(3) }
        binding.tvVarietyTitle.setOnClickListener { openCategoryPage(7) }
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
        binding.rvMovies.adapter = null
        binding.rvTv.adapter = null
        binding.rvDocumentary.adapter = null
        binding.rvVariety.adapter = null
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
        fun newInstance() = CinemaHomeFragment()
    }
}

class PgcHorizontalAdapter(
    private val onItemClick: (BangumiSeason) -> Unit,
) : RecyclerView.Adapter<PgcHorizontalAdapter.ViewHolder>() {

    private var items = emptyList<BangumiSeason>()

    fun submit(newItems: List<BangumiSeason>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPgcHorizontalBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemPgcHorizontalBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(items[adapterPosition])
                }
            }
        }

        fun bind(season: BangumiSeason) {
            binding.tvTitle.text = season.title
            binding.tvDesc.text = season.badge ?: ""
        }
    }
}
