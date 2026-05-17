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
    
    // 正在热播数据
    private var hotItems = mutableListOf<BangumiSeason>()
    private var hotAdapter: PgcHorizontalAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCinemaHomeBinding.inflate(inflater, container, false)
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
            openBangumiDetail(season, 2) // 默认当作电影类型打开
        }
        binding.rvHot.adapter = hotAdapter
        binding.rvHot.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvHot.setHasFixedSize(true)
        loadHotSection()
    }

    private fun loadHotSection() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 加载综合热播内容（包含电影、电视剧等）
                val result = BiliApi.pgcSeasonIndex(
                    seasonType = 2, // 电影作为主要来源
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
                AppLog.e("CinemaHome", "load hot section failed", t)
            }
        }
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
                2 -> setupVerticalGridList(b.rvMovies, section)
                5 -> setupVerticalGridList(b.rvTv, section)
                3 -> setupVerticalGridList(b.rvDocumentary, section)
                7 -> setupVerticalGridList(b.rvVariety, section)
            }
        }
    }

    private fun setupVerticalGridList(recyclerView: RecyclerView, section: CinemaSection) {
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
        binding.btnMoviesMore.setOnClickListener { openCategoryPage(2) }
        binding.btnTvMore.setOnClickListener { openCategoryPage(5) }
        binding.btnDocumentaryMore.setOnClickListener { openCategoryPage(3) }
        binding.btnVarietyMore.setOnClickListener { openCategoryPage(7) }

        binding.tvMoviesTitle.setOnClickListener { openCategoryPage(2) }
        binding.tvTvTitle.setOnClickListener { openCategoryPage(5) }
        binding.tvDocumentaryTitle.setOnClickListener { openCategoryPage(3) }
        binding.tvVarietyTitle.setOnClickListener { openCategoryPage(7) }
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
            ImageLoader.loadInto(binding.ivCover, ImageUrl.poster(season.coverUrl))
        }
    }
}
