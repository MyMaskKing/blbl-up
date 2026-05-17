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
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.model.BangumiSeason
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.TabContentFocusTarget
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.core.ui.requestFocusAdapterPositionReliable
import blbl.cat3399.databinding.FragmentCinemaHomeBinding
import blbl.cat3399.databinding.ItemPgcHorizontalBinding
import blbl.cat3399.feature.category.PgcCategoryFragment
import blbl.cat3399.feature.my.BangumiDetailActivity
import blbl.cat3399.ui.RefreshKeyHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CinemaHomeFragment : Fragment(), RefreshKeyHandler, TabContentFocusTarget {
    private var _binding: FragmentCinemaHomeBinding? = null
    private val binding get() = _binding!!

    private data class CinemaSection(
        val title: String,
        val seasonType: Int,
        val items: MutableList<BangumiSeason>,
    )

    private var sections = listOf<CinemaSection>()

    private var hotItems = mutableListOf<BangumiSeason>()
    private var hotAdapter: PgcHorizontalAdapter? = null

    private var moviesAdapter: PgcHorizontalAdapter? = null
    private var tvAdapter: PgcHorizontalAdapter? = null
    private var documentaryAdapter: PgcHorizontalAdapter? = null
    private var varietyAdapter: PgcHorizontalAdapter? = null
    // 避免初始加载和首页切 tab 自动刷新叠在一起，导致双请求和画面闪烁。
    private var activeLoadCount: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCinemaHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupSectionClickListeners()
        binding.swipeRefresh.setOnRefreshListener { triggerRefresh() }
        binding.btnSideRefresh.setOnClickListener { triggerRefresh() }
        setupFocusOrder()
        initAdapters()
        loadAllData()
    }

    private fun setupFocusOrder() {
        fun focusOnDown(target: RecyclerView): View.OnKeyListener = View.OnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                return@OnKeyListener focusFirstInRecycler(target)
            }
            false
        }
        binding.btnMoviesMore.setOnKeyListener(focusOnDown(binding.rvMovies))
        binding.btnTvMore.setOnKeyListener(focusOnDown(binding.rvTv))
        binding.btnDocumentaryMore.setOnKeyListener(focusOnDown(binding.rvDocumentary))
        binding.btnVarietyMore.setOnKeyListener(focusOnDown(binding.rvVariety))
    }

    private fun initAdapters() {
        hotAdapter = PgcHorizontalAdapter(
            onItemClick = { season -> openBangumiDetail(season, 2) },
        )
        binding.rvHot.adapter = hotAdapter
        binding.rvHot.layoutManager = GridLayoutManager(context, spanCountForPgc())
        binding.rvHot.setHasFixedSize(true)

        moviesAdapter = PgcHorizontalAdapter(
            onItemClick = { season -> openBangumiDetail(season, 2) })
        binding.rvMovies.adapter = moviesAdapter
        binding.rvMovies.layoutManager = GridLayoutManager(context, spanCountForPgc())
        binding.rvMovies.setHasFixedSize(true)

        tvAdapter = PgcHorizontalAdapter(
            onItemClick = { season -> openBangumiDetail(season, 5) })
        binding.rvTv.adapter = tvAdapter
        binding.rvTv.layoutManager = GridLayoutManager(context, spanCountForPgc())
        binding.rvTv.setHasFixedSize(true)

        documentaryAdapter = PgcHorizontalAdapter(
            onItemClick = { season -> openBangumiDetail(season, 3) })
        binding.rvDocumentary.adapter = documentaryAdapter
        binding.rvDocumentary.layoutManager = GridLayoutManager(context, spanCountForPgc())
        binding.rvDocumentary.setHasFixedSize(true)

        varietyAdapter = PgcHorizontalAdapter(
            onItemClick = { season -> openBangumiDetail(season, 7) })
        binding.rvVariety.adapter = varietyAdapter
        binding.rvVariety.layoutManager = GridLayoutManager(context, spanCountForPgc())
        binding.rvVariety.setHasFixedSize(true)
    }

    private fun spanCountForPgc(): Int = BiliClient.prefs.pgcGridSpanCount.coerceIn(1, 6)

    private fun loadAllData() {
        sections = listOf(
            CinemaSection("电影热播", 2, mutableListOf()),
            CinemaSection("电视剧热播", 5, mutableListOf()),
            CinemaSection("纪录片热播", 3, mutableListOf()),
            CinemaSection("综艺热播", 7, mutableListOf()),
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
                        seasonType = 2,
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
                AppLog.e("CinemaHome", "load hot section failed", t)
            } finally {
                markLoadFinished()
            }
        }
    }

    private fun loadSection(section: CinemaSection) {
        markLoadStarted()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    BiliApi.pgcSeasonIndex(
                        seasonType = section.seasonType,
                        page = 1,
                        pageSize = 15,
                        order = 6,
                        sort = 0,
                    )
                }
                section.items.clear()
                section.items.addAll(result.items)
                updateSectionAdapter(section)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("CinemaHome", "load ${section.title} failed", t)
            } finally {
                markLoadFinished()
            }
        }
    }

    private fun updateSectionAdapter(section: CinemaSection) {
        val adapter = when (section.seasonType) {
            2 -> moviesAdapter
            5 -> tvAdapter
            3 -> documentaryAdapter
            7 -> varietyAdapter
            else -> return
        }
        adapter?.submit(section.items)
    }

    private fun hotPageSize(): Int = spanCountForPgc() * 4

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
        loadAllData()
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
        fun newInstance() = CinemaHomeFragment()
    }
}

class PgcHorizontalAdapter(
    private val onItemClick: (BangumiSeason) -> Unit,
    private val itemWidth: Int = 0,
) : RecyclerView.Adapter<PgcHorizontalAdapter.ViewHolder>() {

    private var items = emptyList<BangumiSeason>()

    fun submit(newItems: List<BangumiSeason>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPgcHorizontalBinding.inflate(
            LayoutInflater.from(parent.context).cloneInUserScale(parent.context), parent, false,
        )
        if (itemWidth > 0) {
            binding.root.layoutParams = RecyclerView.LayoutParams(
                itemWidth, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
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
