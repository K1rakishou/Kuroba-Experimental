package com.github.k1rakishou.chan.features.reordering

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyHolder
import com.airbnb.epoxy.EpoxyModelClass
import com.airbnb.epoxy.EpoxyModelWithHolder
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

@EpoxyModelClass
abstract class EpoxyReorderableItemView : EpoxyModelWithHolder<EpoxyReorderableItemViewHolder>(),
  ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private var holder: EpoxyReorderableItemViewHolder? = null
  var dragIndicator: AppCompatImageView? = null

  @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
  var context: Context? = null
  @EpoxyAttribute
  var titleText: String? = null

  override fun getDefaultLayout(): Int = R.layout.epoxy_reorderable_item_view

  override fun bind(holder: EpoxyReorderableItemViewHolder) {
    super.bind(holder)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    this.holder = holder
    this.dragIndicator = holder.dragIndicator

    holder.setTitle(titleText)
    holder.onThemeChanged(themeEngine)

    themeEngine.addListener(id(), this)
  }

  override fun unbind(holder: EpoxyReorderableItemViewHolder) {
    super.unbind(holder)

    this.holder = null
    this.dragIndicator = null

    themeEngine.removeListener(id())
  }

  override fun onThemeChanged() {
    holder?.onThemeChanged(themeEngine)
  }

}

open class EpoxyReorderableItemViewHolder : EpoxyHolder() {
  private lateinit var titleView: AppCompatTextView
  lateinit var dragIndicator: AppCompatImageView

  override fun bindView(itemView: View) {
    titleView = itemView.findViewById(R.id.title)
    dragIndicator = itemView.findViewById(R.id.drag_indicator)

    val viewRoot = itemView.findViewById<LinearLayout>(R.id.reorderable_item_view_root)
    viewRoot.updatePaddings(left = dp(12f), right = dp(12f))
  }

  fun setTitle(title: String?) {
    titleView.text = title
  }

  fun onThemeChanged(themeEngine: ThemeEngine) {
    titleView.setTextColor(themeEngine.chanTheme.textColorPrimary)

    dragIndicator.setImageDrawable(
      themeEngine.tintDrawable(
        dragIndicator.drawable,
        themeEngine.chanTheme.isBackColorDark
      )
    )
  }


}