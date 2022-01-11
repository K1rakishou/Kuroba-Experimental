package com.github.k1rakishou.chan.ui.epoxy

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.isDarkColor
import javax.inject.Inject


@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyExpandableGroupView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val toggleIndicator: AppCompatImageView
  private val groupTitle: ColorizableTextView
  private val divider: View

  private var isExpanded = false

  init {
    inflate(context, R.layout.epoxy_expandable_group_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    toggleIndicator = findViewById(R.id.toggle_indicator_view)
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

  @ModelProp
  fun setGroupTitle(title: String) {
    groupTitle.setText(title)
  }

  @ModelProp
  fun isExpanded(expanded: Boolean) {
    this.isExpanded = expanded

    updateToggleIndicator()
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

  override fun onThemeChanged() {
    updateToggleIndicator()
    updateDividerColor()
  }

  private fun updateDividerColor() {
    val isDarkColor = isDarkColor(themeEngine.chanTheme.backColor)
    divider.setBackgroundColor(ThemeEngine.resolveDrawableTintColor(isDarkColor))
  }

  private fun updateToggleIndicator() {
    val isDarkColor = isDarkColor(themeEngine.chanTheme.backColor)

    val tintedDrawable = themeEngine.getDrawableTinted(
      context,
      R.drawable.ic_chevron_left_black_24dp,
      isDarkColor
    )

    if (isExpanded) {
      toggleIndicator.rotation = 270f
    } else {
      toggleIndicator.rotation = 180f
    }

    toggleIndicator.setImageDrawable(tintedDrawable)
  }

}