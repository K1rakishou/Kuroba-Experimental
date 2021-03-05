package com.github.k1rakishou.chan.ui.controller.settings

import android.content.Context
import android.text.InputType
import android.text.TextWatcher
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.misc.ConstraintLayoutBiasPair
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCardView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableSlider
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView

class RangeSettingUpdaterController(
  context: Context,
  private val constraintLayoutBiasPair: ConstraintLayoutBiasPair = ConstraintLayoutBiasPair.TopRight,
  private val title: String,
  private val minValue: Int,
  private val maxValue: Int,
  private val currentValue: Int,
  private var resetClickedFunc: (() -> Unit)? = null,
  private var applyClickedFunc: ((Int) -> Unit)? = null
) : BaseFloatingController(context) {
  private lateinit var outsideArea: ConstraintLayout
  private lateinit var slider: ColorizableSlider
  private lateinit var minValueTextView: ColorizableTextView
  private lateinit var currentValueInput: ColorizableEditText
  private lateinit var maxValueTextView: ColorizableTextView
  private lateinit var cancel: ColorizableBarButton
  private lateinit var apply: ColorizableBarButton
  private lateinit var reset: ColorizableBarButton

  private var textWatcher: TextWatcher? = null

  override fun getLayoutId(): Int = R.layout.controller_range_setting_updater

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    outsideArea = view.findViewById(R.id.outside_area)
    slider = view.findViewById(R.id.controller_range_setting_updater_slider)
    minValueTextView = view.findViewById(R.id.controller_range_setting_updater_min_value_text_view)
    currentValueInput = view.findViewById(R.id.controller_range_setting_updater_current_value_input)
    maxValueTextView = view.findViewById(R.id.controller_range_setting_updater_max_value_text_view)
    cancel = view.findViewById(R.id.cancel_button)
    apply = view.findViewById(R.id.apply_button)
    reset = view.findViewById(R.id.reset_button)

    val titleTextView = view.findViewById<ColorizableTextView>(R.id.controller_range_setting_updater_title)
    titleTextView.text = title

    val cardView = view.findViewById<ColorizableCardView>(R.id.controller_range_setting_updater_card_view)
    cardView.updateLayoutParams<ConstraintLayout.LayoutParams> {
      horizontalBias = constraintLayoutBiasPair.horizontalBias
      verticalBias = constraintLayoutBiasPair.verticalBias
    }

    slider.valueFrom = minValue.toFloat()
    slider.valueTo = maxValue.toFloat()
    slider.value = currentValue.toFloat()
    slider.setLabelFormatter { value -> value.toInt().toString() }

    slider.addOnChangeListener { _, value, _ ->
      currentValueInput.mySetText(value.toInt().toString())
    }

    minValueTextView.text = minValue.toString()
    maxValueTextView.text = maxValue.toString()

    currentValueInput.mySetText(currentValue.toString())
    currentValueInput.inputType = InputType.TYPE_CLASS_NUMBER

    this.textWatcher = currentValueInput.doAfterTextChanged { editable ->
      val floatValue = editable?.toString()?.toFloatOrNull()
      if (floatValue == null || slider.value == floatValue) {
        return@doAfterTextChanged
      }

      if (floatValue < minValue) {
        return@doAfterTextChanged
      }

      if (floatValue > maxValue) {
        return@doAfterTextChanged
      }

      slider.value = floatValue
    }

    cancel.setOnClickListener { pop() }
    outsideArea.setOnClickListener { pop() }

    reset.setOnClickListener {
      resetClickedFunc?.invoke()
      pop()
    }

    apply.setOnClickListener {
      applyClickedFunc?.invoke(slider.value.toInt())
      pop()
    }
  }

  private fun ColorizableEditText.mySetText(text: CharSequence) {
    editableText.replace(0, editableText.length, text)
  }

  override fun onDestroy() {
    super.onDestroy()

    slider.clearOnChangeListeners()
    textWatcher?.let { tw -> currentValueInput.removeTextChangedListener(tw) }
    textWatcher = null

    applyClickedFunc = null
  }

}