package com.github.k1rakishou.chan.ui.epoxy

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySimpleGroupView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {
  private val groupTitle: ColorizableTextView
  private val divider: View

  @Inject
  lateinit var themeEngine: ThemeEngine

  init {
    inflate(context, R.layout.epoxy_simple_group_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    groupTitle = findViewById(R.id.group_title)
    divider = findViewById(R.id.divider)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    themeEngine.addListener(this)

    updateDividerColor()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    updateDividerColor()
  }

  @ModelProp
  fun setGroupTitle(title: String) {
    groupTitle.setText(title)
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode])
  fun clickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      setOnClickListener(null)
      return
    }

    setOnClickListener { listener.invoke() }
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode])
  fun longClickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      setOnLongClickListener(null)
      return
    }

    setOnLongClickListener {
      listener.invoke()
      return@setOnLongClickListener true
    }
  }

  private fun updateDividerColor() {
    val isDarkColor = ThemeEngine.isDarkColor(themeEngine.chanTheme.backColor)
    divider.setBackgroundColor(ThemeEngine.resolveDrawableTintColor(isDarkColor))
  }

}