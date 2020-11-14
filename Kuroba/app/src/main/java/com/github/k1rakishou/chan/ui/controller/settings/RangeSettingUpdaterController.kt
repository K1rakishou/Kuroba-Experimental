package com.github.k1rakishou.chan.ui.controller.settings

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.StartActivityComponent
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.misc.ConstraintLayoutBiasPair
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCardView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableSlider
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView

class RangeSettingUpdaterController(
  context: Context,
  private val constraintLayoutBiasPair: ConstraintLayoutBiasPair = ConstraintLayoutBiasPair.TopRight,
  private val titleStringId: Int,
  private val minValue: Float,
  private val maxValue: Float,
  private val currentValue: Float,
  private var applyClickedFunc: ((Int) -> Unit)? = null
) : BaseFloatingController(context) {
  private lateinit var outsideArea: ConstraintLayout
  private lateinit var slider: ColorizableSlider
  private lateinit var minValueTextView: ColorizableTextView
  private lateinit var maxValueTextView: ColorizableTextView
  private lateinit var cancel: ColorizableBarButton
  private lateinit var apply: ColorizableBarButton

  override fun getLayoutId(): Int = R.layout.controller_range_setting_updater

  override fun injectDependencies(component: StartActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    outsideArea = view.findViewById(R.id.outside_area)
    slider = view.findViewById(R.id.controller_range_setting_updater_slider)
    minValueTextView = view.findViewById(R.id.controller_range_setting_updater_min_value_text_view)
    maxValueTextView = view.findViewById(R.id.controller_range_setting_updater_max_value_text_view)
    cancel = view.findViewById(R.id.cancel_button)
    apply = view.findViewById(R.id.apply_button)

    val title = view.findViewById<ColorizableTextView>(R.id.controller_range_setting_updater_title)
    title.setText(titleStringId)

    val cardView = view.findViewById<ColorizableCardView>(R.id.controller_range_setting_updater_card_view)
    cardView.updateLayoutParams<ConstraintLayout.LayoutParams> {
      horizontalBias = constraintLayoutBiasPair.horizontalBias
      verticalBias = constraintLayoutBiasPair.verticalBias
    }

    slider.valueFrom = minValue
    slider.valueTo = maxValue
    slider.value = currentValue

    minValueTextView.text = minValue.toString()
    maxValueTextView.text = maxValue.toString()

    cancel.setOnClickListener { pop() }
    outsideArea.setOnClickListener { pop() }

    apply.setOnClickListener {
      applyClickedFunc?.invoke(slider.value.toInt())
      pop()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    applyClickedFunc = null
  }

}