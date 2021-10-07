package com.github.k1rakishou.chan.features.setup.epoxy

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
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCheckBox
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.SpannableHelper
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.android.material.textview.MaterialTextView
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySelectableBoardView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val clickableArea: LinearLayout
  private val boardName: MaterialTextView
  private val boardDescription: MaterialTextView
  private val boardCheckbox: ColorizableCheckBox

  private var searchQuery: String? = null

  init {
    inflate(context, R.layout.epoxy_selectable_board_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    clickableArea = findViewById(R.id.clickable_area)
    boardName = findViewById(R.id.board_name)
    boardDescription = findViewById(R.id.board_description)
    boardCheckbox = findViewById(R.id.board_selection_checkbox)
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
    updateBoardDescriptionColor()
    updateBoardNameColor()
  }

  @AfterPropsSet
  fun afterPropsSet() {
    val query = searchQuery
    if (query.isNullOrEmpty()) {
      SpannableHelper.cleanSearchSpans(this.boardName.text)
      SpannableHelper.cleanSearchSpans(this.boardDescription.text)
      return
    }

    SpannableHelper.findAllQueryEntriesInsideSpannableStringAndMarkThem(
      inputQueries = listOf(query),
      spannableString = this.boardName.text as SpannableString,
      bgColor = themeEngine.chanTheme.accentColor,
      minQueryLength = 1
    )

    SpannableHelper.findAllQueryEntriesInsideSpannableStringAndMarkThem(
      inputQueries = listOf(query),
      spannableString = this.boardDescription.text as SpannableString,
      bgColor = themeEngine.chanTheme.accentColor,
      minQueryLength = 1
    )
  }

  @OnViewRecycled
  fun onRecycled() {
    SpannableHelper.cleanSearchSpans(this.boardName.text)
    SpannableHelper.cleanSearchSpans(this.boardDescription.text)
  }

  @ModelProp
  fun setBoardName(boardName: String) {
    this.boardName.setText(SpannableString.valueOf(boardName), TextView.BufferType.SPANNABLE)
    updateBoardNameColor()
  }

  @ModelProp
  fun setBoardDescription(boardDescription: String) {
    this.boardDescription.setText(SpannableString.valueOf(boardDescription), TextView.BufferType.SPANNABLE)
    updateBoardDescriptionColor()
  }

  @ModelProp
  fun bindQuery(query: String?) {
    this.searchQuery = query
  }

  @ModelProp
  fun setBoardSelected(selected: Boolean) {
    this.boardCheckbox.isChecked = selected
  }

  @CallbackProp
  fun setOnClickedCallback(callback: ((Boolean) -> Unit)?) {
    if (callback == null) {
      clickableArea.setOnClickListener(null)
      return
    }

    clickableArea.setOnClickListener {
      if (callback == null) {
        return@setOnClickListener
      }

      boardCheckbox.isChecked = !boardCheckbox.isChecked
      callback.invoke(boardCheckbox.isChecked)
    }
  }

  private fun updateBoardDescriptionColor() {
    boardDescription.setTextColor(themeEngine.chanTheme.textColorSecondary)
  }

  private fun updateBoardNameColor() {
    boardName.setTextColor(themeEngine.chanTheme.textColorPrimary)
  }

}