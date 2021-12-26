package com.github.k1rakishou.chan.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.updatePaddings
import javax.inject.Inject

class InsetAwareLinearLayout @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : LinearLayout(context, attributeSet), WindowInsetsListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    orientation = LinearLayout.VERTICAL
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  override fun onFinishInflate() {
    super.onFinishInflate()

    onInsetsChanged()
  }

  override fun onViewAdded(child: View?) {
    onInsetsChanged()
  }

  override fun onViewRemoved(child: View?) {
    onInsetsChanged()
  }

  override fun onInsetsChanged() {
    updatePaddings(
      left = globalWindowInsetsManager.left(),
      right = globalWindowInsetsManager.right(),
      bottom = globalWindowInsetsManager.bottom()
    )
  }
}