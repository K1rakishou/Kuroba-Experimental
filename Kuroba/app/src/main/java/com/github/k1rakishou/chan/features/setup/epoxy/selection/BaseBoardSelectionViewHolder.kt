package com.github.k1rakishou.chan.features.setup.epoxy.selection

import android.text.SpannableString
import android.view.View
import android.widget.TextView
import com.airbnb.epoxy.EpoxyHolder
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.SpannableHelper
import com.github.k1rakishou.chan.utils.setBackgroundColorFast
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.google.android.material.textview.MaterialTextView
import javax.inject.Inject

class BaseBoardSelectionViewHolder : EpoxyHolder(), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private lateinit var backgroundView: View
  private lateinit var rootView: View
  private lateinit var topTitle: MaterialTextView
  private lateinit var bottomTitle: MaterialTextView
  private var boardSeparator: MaterialTextView? = null

  private var searchQuery: String? = null
  private var isCurrentlySelected: Boolean = false
  var catalogDescriptor: ChanDescriptor.ICatalogDescriptor? = null

  override fun bindView(itemView: View) {
    AppModuleAndroidUtils.extractActivityComponent(itemView.context)
      .inject(this)

    backgroundView = itemView.findViewById(R.id.background_view)
    rootView = itemView.findViewById(R.id.root_view)
    topTitle = itemView.findViewById(R.id.top_title)
    bottomTitle = itemView.findViewById(R.id.bottom_title)
    boardSeparator = itemView.findViewById(R.id.board_separator)

    themeEngine.addListener(this)
    onThemeChanged()

    afterPropsSet()
  }

  override fun onThemeChanged() {
    topTitle.setTextColor(themeEngine.chanTheme.textColorPrimary)
    boardSeparator?.setTextColor(themeEngine.chanTheme.textColorPrimary)
    bottomTitle.setTextColor(themeEngine.chanTheme.textColorPrimary)

    if (isCurrentlySelected) {
      backgroundView.setBackgroundColorFast(themeEngine.chanTheme.postHighlightedColor)
    } else {
      backgroundView.setBackgroundColorFast(0)
    }
  }

  fun afterPropsSet() {
    val query = searchQuery
    if (query.isNullOrEmpty()) {
      SpannableHelper.cleanSearchSpans(this.topTitle.text)
      SpannableHelper.cleanSearchSpans(this.bottomTitle.text)
    } else {
      SpannableHelper.findAllQueryEntriesInsideSpannableStringAndMarkThem(
        inputQueries = listOf(query),
        spannableString = this.topTitle.text as SpannableString,
        bgColor = themeEngine.chanTheme.accentColor,
        minQueryLength = 1
      )

      SpannableHelper.findAllQueryEntriesInsideSpannableStringAndMarkThem(
        inputQueries = listOf(query),
        spannableString = this.bottomTitle.text as SpannableString,
        bgColor = themeEngine.chanTheme.accentColor,
        minQueryLength = 1
      )
    }
  }

  fun unbind() {
    themeEngine.removeListener(this)
    SpannableHelper.cleanSearchSpans(this.topTitle.text)
    SpannableHelper.cleanSearchSpans(this.bottomTitle.text)
  }

  fun bindTopTitle(title: String?) {
    if (title == null) {
      this.topTitle.setText(null, TextView.BufferType.SPANNABLE)
    } else {
      this.topTitle.setText(SpannableString.valueOf(title), TextView.BufferType.SPANNABLE)
    }

    onThemeChanged()
  }

  fun bindBottomTitle(title: CharSequence?) {
    if (title == null) {
      this.bottomTitle.setText(null, TextView.BufferType.SPANNABLE)
    } else {
      this.bottomTitle.setText(SpannableString.valueOf(title), TextView.BufferType.SPANNABLE)
    }

    onThemeChanged()
  }

  fun bindQuery(query: String?) {
    this.searchQuery = query
  }

  fun bindCurrentlySelected(selected: Boolean) {
    this.isCurrentlySelected = selected
    onThemeChanged()
  }

  fun bindRowClickCallback(callback: (() -> Unit)?) {
    if (callback == null) {
      rootView.setOnClickListener(null)
      return
    }

    rootView.setOnClickListener {
      callback.invoke()
    }
  }
}