package com.github.k1rakishou.chan.features.setup.epoxy.site

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.setup.data.ArchiveEnabledTotalCount
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.utils.AndroidUtils
import javax.inject.Inject


@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySiteArchivesGroupView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val toggleIndicator: AppCompatImageView
  private val archivesLabel: ColorizableTextView
  private val divider: View

  private var isExpanded = false

  init {
    inflate(context, R.layout.epoxy_site_archives_group_view, this)
    Chan.inject(this)

    toggleIndicator = findViewById(R.id.toggle_indicator_view)
    archivesLabel = findViewById(R.id.archives_label)
    divider = findViewById(R.id.divider)

    updateDividerColor()
  }

  @ModelProp
  fun setArchiveEnabledAndTotalCount(archiveEnabledTotalCount: ArchiveEnabledTotalCount) {
    val archivesInfoText = context.getString(
      R.string.controller_sites_setup_archives_group,
      archiveEnabledTotalCount.enabledCount,
      archiveEnabledTotalCount.totalCount,
    )

    archivesLabel.setText(archivesInfoText)
  }

  @ModelProp
  fun isExpanded(expanded: Boolean) {
    this.isExpanded = expanded

    updateToggleIndicator()
  }

  @CallbackProp
  fun clickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      setOnClickListener(null)
      return
    }

    setOnClickListener { listener.invoke() }
  }

  override fun onThemeChanged() {
    updateToggleIndicator()
    updateDividerColor()
  }

  private fun updateDividerColor() {
    val isDarkColor = AndroidUtils.isDarkColor(themeEngine.chanTheme.backColor)
    divider.setBackgroundColor(themeEngine.resolveTintColor(isDarkColor))
  }

  private fun updateToggleIndicator() {
    val isDarkColor = AndroidUtils.isDarkColor(themeEngine.chanTheme.backColor)

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