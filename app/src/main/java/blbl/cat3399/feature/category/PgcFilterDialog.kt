package blbl.cat3399.feature.category

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import blbl.cat3399.R
import blbl.cat3399.databinding.DialogPgcFilterBinding

class PgcFilterDialog(
    context: Context,
    private val initialState: PgcFilterState,
    private val onApply: (PgcFilterState) -> Unit,
) : Dialog(context, R.style.Theme_BlBl_Dialog) {
    private lateinit var binding: DialogPgcFilterBinding

    private var currentState = initialState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogPgcFilterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAreaSpinner()
        setupYearSpinner()
        setupOrderSpinner()
        setupSeasonStatusSpinner()
        setupFinishSpinner()
        setupButtons()

        restoreState()
    }

    private fun setupAreaSpinner() {
        val areaItems =
            listOf(
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

        val adapter =
            object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, areaItems.map { it.first }) {
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getDropDownView(position, convertView, parent)
                    view.findViewById<TextView>(android.R.id.text1)?.apply {
                        textSize = 16f
                        setPadding(32, 24, 32, 24)
                    }
                    return view
                }
            }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerArea.adapter = adapter

        binding.spinnerArea.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    currentState = currentState.copy(area = areaItems[position].second)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
    }

    private fun setupYearSpinner() {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val yearItems =
            mutableListOf("全部" to "-1").apply {
                for (year in currentYear downTo 2010) {
                    add(year.toString() to year.toString())
                }
                add("2010年以前" to "[2010,2015)")
            }

        val adapter =
            object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, yearItems.map { it.first }) {
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getDropDownView(position, convertView, parent)
                    view.findViewById<TextView>(android.R.id.text1)?.apply {
                        textSize = 16f
                        setPadding(32, 24, 32, 24)
                    }
                    return view
                }
            }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerYear.adapter = adapter

        binding.spinnerYear.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    val value = yearItems[position].second
                    currentState =
                        if (currentState.seasonType in listOf(1, 4)) {
                            currentState.copy(year = value)
                        } else {
                            currentState.copy(releaseDate = value)
                        }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
    }

    private fun setupOrderSpinner() {
        val orderItems =
            listOf(
                "更新时间" to 0,
                "弹幕数量" to 1,
                "播放数量" to 2,
                "追剧人数" to 3,
                "最高评分" to 4,
                "开播时间" to 5,
                "上映时间" to 6,
            )

        val adapter =
            object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, orderItems.map { it.first }) {
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getDropDownView(position, convertView, parent)
                    view.findViewById<TextView>(android.R.id.text1)?.apply {
                        textSize = 16f
                        setPadding(32, 24, 32, 24)
                    }
                    return view
                }
            }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerOrder.adapter = adapter

        binding.spinnerOrder.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    currentState = currentState.copy(order = orderItems[position].second)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
    }

    private fun setupSeasonStatusSpinner() {
        val statusItems =
            listOf(
                "全部" to -1,
                "免费" to 1,
                "付费" to 2,
                "大会员" to 4,
            )

        val adapter =
            object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, statusItems.map { it.first }) {
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getDropDownView(position, convertView, parent)
                    view.findViewById<TextView>(android.R.id.text1)?.apply {
                        textSize = 16f
                        setPadding(32, 24, 32, 24)
                    }
                    return view
                }
            }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSeasonStatus.adapter = adapter

        binding.spinnerSeasonStatus.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    currentState = currentState.copy(seasonStatus = statusItems[position].second)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
    }

    private fun setupFinishSpinner() {
        val finishItems =
            listOf(
                "全部" to -1,
                "连载中" to 0,
                "已完结" to 1,
            )

        val adapter =
            object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, finishItems.map { it.first }) {
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getDropDownView(position, convertView, parent)
                    view.findViewById<TextView>(android.R.id.text1)?.apply {
                        textSize = 16f
                        setPadding(32, 24, 32, 24)
                    }
                    return view
                }
            }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFinish.adapter = adapter

        binding.spinnerFinish.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    currentState = currentState.copy(isFinish = finishItems[position].second)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnApply.setOnClickListener {
            onApply(currentState)
            dismiss()
        }
        binding.btnReset.setOnClickListener {
            currentState = PgcFilterState(seasonType = initialState.seasonType)
            restoreState()
        }
    }

    private fun restoreState() {
        val areaAdapter = binding.spinnerArea.adapter as? ArrayAdapter<*> ?: return
        val areaItems =
            listOf(
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
        val areaPosition = areaItems.indexOfFirst { it.second == currentState.area }
        if (areaPosition >= 0) binding.spinnerArea.setSelection(areaPosition, false)

        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val yearItems =
            mutableListOf("全部" to "-1").apply {
                for (year in currentYear downTo 2010) {
                    add(year.toString() to year.toString())
                }
                add("2010年以前" to "[2010,2015)")
            }
        val yearValue =
            if (currentState.seasonType in listOf(1, 4)) {
                currentState.year
            } else {
                currentState.releaseDate
            }
        val yearPosition = yearItems.indexOfFirst { it.second == yearValue }
        if (yearPosition >= 0) binding.spinnerYear.setSelection(yearPosition, false)

        val orderItems =
            listOf(
                "更新时间" to 0,
                "弹幕数量" to 1,
                "播放数量" to 2,
                "追剧人数" to 3,
                "最高评分" to 4,
                "开播时间" to 5,
                "上映时间" to 6,
            )
        val orderPosition = orderItems.indexOfFirst { it.second == currentState.order }
        if (orderPosition >= 0) binding.spinnerOrder.setSelection(orderPosition, false)

        val statusItems =
            listOf(
                "全部" to -1,
                "免费" to 1,
                "付费" to 2,
                "大会员" to 4,
            )
        val statusPosition = statusItems.indexOfFirst { it.second == currentState.seasonStatus }
        if (statusPosition >= 0) binding.spinnerSeasonStatus.setSelection(statusPosition, false)

        val finishItems =
            listOf(
                "全部" to -1,
                "连载中" to 0,
                "已完结" to 1,
            )
        val finishPosition = finishItems.indexOfFirst { it.second == currentState.isFinish }
        if (finishPosition >= 0) binding.spinnerFinish.setSelection(finishPosition, false)
    }
}
