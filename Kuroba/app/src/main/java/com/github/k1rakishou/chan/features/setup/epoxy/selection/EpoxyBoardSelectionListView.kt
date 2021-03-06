package com.github.k1rakishou.chan.features.setup.epoxy.selection

import android.content.Context
import android.text.SpannableString
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import com.airbnb.epoxy.AfterPropsSet
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.airbnb.epoxy.OnViewRecycled
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.SpannableHelper
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.android.material.textview.MaterialTextView
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyBoardSelectionListView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val boardCode: MaterialTextView
  private val boardSeparator: MaterialTextView
  private val boardName: MaterialTextView

  private var searchQuery: String? = null

  init {
    inflate(context, R.layout.epoxy_board_selection_list_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    boardCode = findViewById(R.id.board_code)
    boardSeparator = findViewById(R.id.board_separator)
    boardName = findViewById(R.id.board_name)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    updateBoardNameColor()
  }

  @AfterPropsSet
  fun afterPropsSet() {
    val query = searchQuery
    if (query.isNullOrEmpty()) {
      SpannableHelper.cleanSearchSpans(this.boardCode.text)
      SpannableHelper.cleanSearchSpans(this.boardName.text)
      return
    }

    SpannableHelper.findAllQueryEntriesInsideSpannableStringAndMarkThem(
      inputQueries = listOf(query),
      spannableString = this.boardCode.text as SpannableString,
      color = themeEngine.chanTheme.accentColor,
      minQueryLength = 1
    )

    SpannableHelper.findAllQueryEntriesInsideSpannableStringAndMarkThem(
      inputQueries = listOf(query),
      spannableString = this.boardName.text as SpannableString,
      color = themeEngine.chanTheme.accentColor,
      minQueryLength = 1
    )
  }

  @OnViewRecycled
  fun onRecycled() {
    SpannableHelper.cleanSearchSpans(this.boardCode.text)
    SpannableHelper.cleanSearchSpans(this.boardName.text)
  }

  @ModelProp
  fun bindBoardCode(boardCode: String) {
    this.boardCode.setText(SpannableString.valueOf(boardCode), TextView.BufferType.SPANNABLE)
    updateBoardNameColor()
  }

  @ModelProp
  fun bindBoardName(boardName: String) {
    this.boardName.setText(SpannableString.valueOf(boardName), TextView.BufferType.SPANNABLE)
    updateBoardNameColor()
  }

  @ModelProp
  fun bindQuery(query: String?) {
    this.searchQuery = query
  }

  @CallbackProp
  fun bindRowClickCallback(callback: (() -> Unit)?) {
    if (callback == null) {
      setOnClickListener(null)
      return
    }

    setOnClickListener {
      callback.invoke()
    }
  }

  private fun updateBoardNameColor() {
    boardCode.setTextColor(themeEngine.chanTheme.textColorPrimary)
    boardSeparator.setTextColor(themeEngine.chanTheme.textColorPrimary)
    boardName.setTextColor(themeEngine.chanTheme.textColorPrimary)
  }

}