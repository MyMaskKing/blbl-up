package blbl.cat3399.feature.category

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.updatePadding
import blbl.cat3399.R
import blbl.cat3399.databinding.DialogPgcFilterBinding

class PgcFilterDialog(
    context: Context,
    private val initialState: PgcFilterState,
    private val onApply: (PgcFilterState) -> Unit,
) : Dialog(context, R.style.ThemeOverlay_Blbl_TransparentDialog) {
    private lateinit var binding: DialogPgcFilterBinding

    private var currentState = initialState

    private val allAreaButtons = mutableListOf<Button>()
    private val allStyleButtons = mutableListOf<Button>()
    private val allYearButtons = mutableListOf<Button>()
    private val allOrderButtons = mutableListOf<Button>()
    private val allStatusButtons = mutableListOf<Button>()
    private val allFinishButtons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setBackgroundDrawableResource(R.color.blbl_surface)
            // 设置对话框尺寸为全屏，适合TV版显示，手机上也使用全屏
            val width = context.resources.displayMetrics.widthPixels
            val height = context.resources.displayMetrics.heightPixels
            setLayout(width, height)
            setGravity(Gravity.CENTER)
        }
        binding = DialogPgcFilterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAllOptions()
        setupButtons()
        restoreState()
    }

    private fun setupAllOptions() {
        setupAreaOptions()
        setupStyleOptions()
        setupYearOptions()
        setupOrderOptions()
        setupStatusOptions()
        setupFinishOptions()
    }

    private fun setupAreaOptions() {
        val container = binding.areaContainer
        val items = PgcConstants.getAreaItems()
        allAreaButtons.clear()

        items.forEach { (label, value) ->
            val button = createOptionButton(label)
            button.setOnClickListener {
                currentState = currentState.copy(area = value)
                updateAreaButtons(value)
            }
            container.addView(button)
            allAreaButtons.add(button)
        }
    }

    private fun setupStyleOptions() {
        val container = binding.styleContainer
        val items = PgcConstants.getStyles(currentState.seasonType)
        allStyleButtons.clear()

        items.forEach { (label, value) ->
            val button = createOptionButton(label)
            button.setOnClickListener {
                currentState = currentState.copy(styleId = value)
                updateStyleButtons(value)
            }
            container.addView(button)
            allStyleButtons.add(button)
        }
    }

    private fun setupYearOptions() {
        val container = binding.yearContainer
        val items = PgcConstants.getYearItems()
        allYearButtons.clear()

        items.forEach { (label, value) ->
            val button = createOptionButton(label)
            button.setOnClickListener {
                if (initialState.seasonType in listOf(1, 4)) {
                    currentState = currentState.copy(year = value)
                } else {
                    currentState = currentState.copy(releaseDate = value)
                }
                updateYearButtons(value)
            }
            container.addView(button)
            allYearButtons.add(button)
        }
    }

    private fun setupOrderOptions() {
        val container = binding.orderContainer
        val items = PgcConstants.getOrderItems(initialState.seasonType)
        allOrderButtons.clear()

        items.forEach { (label, value) ->
            val button = createOptionButton(label)
            button.setOnClickListener {
                currentState = currentState.copy(order = value)
                updateOrderButtons(value)
            }
            container.addView(button)
            allOrderButtons.add(button)
        }
    }

    private fun setupStatusOptions() {
        val container = binding.statusContainer
        val items = PgcConstants.getStatusItems()
        allStatusButtons.clear()

        items.forEach { (label, value) ->
            val button = createOptionButton(label)
            button.setOnClickListener {
                currentState = currentState.copy(seasonStatus = value)
                updateStatusButtons(value)
            }
            container.addView(button)
            allStatusButtons.add(button)
        }
    }

    private fun setupFinishOptions() {
        val container = binding.finishContainer
        val items = PgcConstants.getFinishItems()
        allFinishButtons.clear()

        items.forEach { (label, value) ->
            val button = createOptionButton(label)
            button.setOnClickListener {
                currentState = currentState.copy(isFinish = value)
                updateFinishButtons(value)
            }
            container.addView(button)
            allFinishButtons.add(button)
        }
    }

    private fun createOptionButton(text: String): Button {
        val button = Button(context)
        val params = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
        params.rightMargin = 12
        params.bottomMargin = 16
        button.layoutParams = params
        button.text = text
        button.textSize = 24f
        button.setPadding(20, 24, 20, 24)
        button.isAllCaps = false
        button.background = context.getDrawable(R.drawable.blbl_button_selector)
        button.isFocusable = true
        button.isFocusableInTouchMode = true
        return button
    }

    private fun updateAreaButtons(selectedValue: String) {
        val items = PgcConstants.getAreaItems()
        items.forEachIndexed { index, (_, value) ->
            allAreaButtons[index].apply {
                isSelected = value == selectedValue
                setTextColor(
                    if (value == selectedValue) {
                        context.getColor(R.color.blbl_purple)
                    } else {
                        context.getColor(R.color.blbl_text_secondary)
                    }
                )
            }
        }
    }

    private fun updateStyleButtons(selectedValue: Int) {
        val items = PgcConstants.getStyles(currentState.seasonType)
        items.forEachIndexed { index, (_, value) ->
            allStyleButtons[index].apply {
                isSelected = value == selectedValue
                setTextColor(
                    if (value == selectedValue) {
                        context.getColor(R.color.blbl_purple)
                    } else {
                        context.getColor(R.color.blbl_text_secondary)
                    }
                )
            }
        }
    }

    private fun updateYearButtons(selectedValue: String) {
        val items = PgcConstants.getYearItems()
        items.forEachIndexed { index, (_, value) ->
            allYearButtons[index].apply {
                isSelected = value == selectedValue
                setTextColor(
                    if (value == selectedValue) {
                        context.getColor(R.color.blbl_purple)
                    } else {
                        context.getColor(R.color.blbl_text_secondary)
                    }
                )
            }
        }
    }

    private fun updateOrderButtons(selectedValue: Int) {
        val items = PgcConstants.getOrderItems(initialState.seasonType)
        items.forEachIndexed { index, (_, value) ->
            allOrderButtons[index].apply {
                isSelected = value == selectedValue
                setTextColor(
                    if (value == selectedValue) {
                        context.getColor(R.color.blbl_purple)
                    } else {
                        context.getColor(R.color.blbl_text_secondary)
                    }
                )
            }
        }
    }

    private fun updateStatusButtons(selectedValue: Int) {
        val items = PgcConstants.getStatusItems()
        items.forEachIndexed { index, (_, value) ->
            allStatusButtons[index].apply {
                isSelected = value == selectedValue
                setTextColor(
                    if (value == selectedValue) {
                        context.getColor(R.color.blbl_purple)
                    } else {
                        context.getColor(R.color.blbl_text_secondary)
                    }
                )
            }
        }
    }

    private fun updateFinishButtons(selectedValue: Int) {
        val items = PgcConstants.getFinishItems()
        items.forEachIndexed { index, (_, value) ->
            allFinishButtons[index].apply {
                isSelected = value == selectedValue
                setTextColor(
                    if (value == selectedValue) {
                        context.getColor(R.color.blbl_purple)
                    } else {
                        context.getColor(R.color.blbl_text_secondary)
                    }
                )
            }
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
        updateAreaButtons(currentState.area)
        updateStyleButtons(currentState.styleId)

        val yearValue =
            if (currentState.seasonType in listOf(1, 4)) {
                currentState.year
            } else {
                currentState.releaseDate
            }
        updateYearButtons(yearValue)

        updateOrderButtons(currentState.order)
        updateStatusButtons(currentState.seasonStatus)
        updateFinishButtons(currentState.isFinish)
    }
}
