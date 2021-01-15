package com.github.k1rakishou.chan.ui.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.ViewUtils.setEditTextCursorColor
import com.github.k1rakishou.chan.utils.ViewUtils.setHandlesColors
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.android.material.textview.MaterialTextView
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyPostLink @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val postLinkView: MaterialTextView
  private val postLinkOpenButton: ImageView

  init {
    inflate(context, R.layout.epoxy_post_link, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    postLinkView = findViewById(R.id.post_link_view)
    postLinkOpenButton = findViewById(R.id.post_link_open_button)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    // this is a hack to make sure text is selectable
    postLinkView.isEnabled = false
    postLinkView.isEnabled = true
    postLinkView.setTextIsSelectable(true)

    themeEngine.addListener(this)

    onThemeChanged()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    postLinkView.setEditTextCursorColor(themeEngine.chanTheme)
    postLinkView.setHandlesColors(themeEngine.chanTheme)

    postLinkOpenButton.setImageDrawable(
      themeEngine.getDrawableTinted(
        context,
        R.drawable.ic_baseline_navigate_next_24,
        ThemeEngine.isDarkColor(themeEngine.chanTheme.backColor)
      )
    )
  }

  @ModelProp
  fun setLinkText(link: CharSequence) {
    postLinkView.setText(link, TextView.BufferType.SPANNABLE)
  }

  @CallbackProp
  fun onOpenLinkClicked(listener: (() -> Unit)?) {
    if (listener == null) {
      postLinkOpenButton.setOnClickListener(null)
      return
    }

    postLinkOpenButton.setOnClickListener { listener.invoke() }
  }

}