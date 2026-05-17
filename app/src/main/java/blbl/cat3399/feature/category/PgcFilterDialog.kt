package blbl.cat3399.feature.category

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R

private const val FILTER_GRID_COLUMNS = 5

private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_OPTION = 1

private sealed class FilterItem {
    data class Header(val title: String) : FilterItem()
    data class Option(
        val category: FilterCategory,
        val label: String,
        val value: String,
        var isSelected: Boolean,
    ) : FilterItem()
}

private enum class FilterCategory { AREA, STYLE, YEAR, ORDER, STATUS, FINISH }

private class FilterGridAdapter(
    private val onOptionClick: (FilterCategory, String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<FilterItem>()

    fun submit(newItems: List<FilterItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position] is FilterItem.Header) VIEW_TYPE_HEADER else VIEW_TYPE_OPTION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_filter_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_filter_option, parent, false)
            OptionViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is FilterItem.Header -> (holder as HeaderViewHolder).bind(item)
            is FilterItem.Option -> (holder as OptionViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(header: FilterItem.Header) {
            (itemView as TextView).text = header.title
        }
    }

    inner class OptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tv = view.findViewById<TextView>(R.id.tvOption)
        private var currentCategory: FilterCategory = FilterCategory.AREA
        private var currentValue: String = ""

        init {
            view.setOnClickListener {
                onOptionClick(currentCategory, currentValue)
            }
        }

        fun bind(option: FilterItem.Option) {
            currentCategory = option.category
            currentValue = option.value
            tv.text = option.label
            tv.isActivated = option.isSelected
            tv.setTextColor(
                if (option.isSelected) {
                    tv.context.getColor(R.color.blbl_purple)
                } else {
                    Color.WHITE
                }
            )
        }
    }
}

class PgcFilterDialog(
    context: Context,
    private val initialState: PgcFilterState,
    private val onApply: (PgcFilterState) -> Unit,
) : Dialog(context, R.style.ThemeOverlay_Blbl_TransparentDialog) {

    private lateinit var rvOptions: RecyclerView
    private lateinit var adapter: FilterGridAdapter
    private var currentState: PgcFilterState = initialState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setBackgroundDrawableResource(R.color.blbl_surface)
            // 沉浸式全屏，隐藏状态栏和导航栏
            decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
            )
            setGravity(Gravity.CENTER)
        }
        setContentView(R.layout.dialog_pgc_filter)

        rvOptions = findViewById(R.id.rvFilterOptions)
        initRecyclerView()
        buildItems()
        setupButtons()
    }

    private fun initRecyclerView() {
        val gridLayout = GridLayoutManager(context, FILTER_GRID_COLUMNS)
        gridLayout.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter.getItemViewType(position) == VIEW_TYPE_HEADER) {
                    FILTER_GRID_COLUMNS
                } else {
                    1
                }
            }
        }
        rvOptions.layoutManager = gridLayout
        adapter = FilterGridAdapter { category, value ->
            onFilterOptionClick(category, value)
        }
        rvOptions.adapter = adapter
        rvOptions.itemAnimator = null
    }

    private fun onFilterOptionClick(category: FilterCategory, value: String) {
        currentState = when (category) {
            FilterCategory.AREA -> currentState.copy(area = value)
            FilterCategory.STYLE -> currentState.copy(styleId = value)
            FilterCategory.YEAR -> {
                if (currentState.seasonType in listOf(1, 4)) {
                    currentState.copy(year = value)
                } else {
                    currentState.copy(releaseDate = value)
                }
            }
            FilterCategory.ORDER -> currentState.copy(order = value.toIntOrNull() ?: 0)
            FilterCategory.STATUS -> currentState.copy(seasonStatus = value.toIntOrNull() ?: -1)
            FilterCategory.FINISH -> currentState.copy(isFinish = value.toIntOrNull() ?: -1)
        }
        buildItems()
    }

    private fun buildItems() {
        val items = mutableListOf<FilterItem>()

        items += FilterItem.Header("地区")
        items += PgcConstants.getAreaItems().map { (label, value) ->
            FilterItem.Option(FilterCategory.AREA, label, value, currentState.area == value)
        }

        items += FilterItem.Header("题材")
        items += PgcConstants.getStyles(currentState.seasonType).map { (label, value) ->
            FilterItem.Option(FilterCategory.STYLE, label, value, currentState.styleId == value)
        }

        items += FilterItem.Header("年份")
        val yearValue = if (currentState.seasonType in listOf(1, 4)) {
            currentState.year
        } else {
            currentState.releaseDate
        }
        items += PgcConstants.getYearItems(currentState.seasonType).map { (label, value) ->
            FilterItem.Option(FilterCategory.YEAR, label, value, yearValue == value)
        }

        items += FilterItem.Header("排序")
        items += PgcConstants.getOrderItems(initialState.seasonType).map { (label, value) ->
            FilterItem.Option(FilterCategory.ORDER, label, value.toString(), currentState.order == value)
        }

        items += FilterItem.Header("付费状态")
        items += PgcConstants.getStatusItems().map { (label, value) ->
            FilterItem.Option(FilterCategory.STATUS, label, value.toString(), currentState.seasonStatus == value)
        }

        items += FilterItem.Header("完结状态")
        items += PgcConstants.getFinishItems().map { (label, value) ->
            FilterItem.Option(FilterCategory.FINISH, label, value.toString(), currentState.isFinish == value)
        }

        adapter.submit(items)
    }

    private fun setupButtons() {
        findViewById<View>(R.id.btnCancel).setOnClickListener { dismiss() }
        findViewById<View>(R.id.btnApply).setOnClickListener {
            onApply(currentState)
            dismiss()
        }
        findViewById<View>(R.id.btnReset).setOnClickListener {
            currentState = PgcFilterState(seasonType = initialState.seasonType)
            buildItems()
        }
    }
}
