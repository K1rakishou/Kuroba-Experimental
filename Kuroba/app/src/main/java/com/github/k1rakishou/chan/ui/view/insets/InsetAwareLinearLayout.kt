package com.github.k1rakishou.chan.ui.view.insets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.updatePaddings
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class InsetAwareLinearLayout @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : LinearLayout(context, attributeSet), WindowInsetsListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var globalUiStateHolder: GlobalUiStateHolder
  @Inject
  lateinit var appResources: AppResources

  private val coroutineScope = KurobaCoroutineScope()

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    orientation = LinearLayout.VERTICAL
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    globalWindowInsetsManager.addInsetsUpdatesListener(this)

    coroutineScope.cancelChildren()
    coroutineScope.launch {
      combine(
        globalUiStateHolder.toolbar.toolbarHeight,
        globalUiStateHolder.bottomPanel.bottomPanelHeight
      ) { t1, t2 -> t1 to t2 }
        .onEach { onInsetsChanged() }
        .collect()
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    coroutineScope.cancelChildren()
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
    val bottomPadding = with(appResources.composeDensity) {
      maxOf(
        globalWindowInsetsManager.bottom(),
        globalUiStateHolder.bottomPanel.bottomPanelHeight.value.roundToPx()
      )
    }

    val topPadding = with(appResources.composeDensity) {
      maxOf(
        globalWindowInsetsManager.top(),
        globalUiStateHolder.toolbar.toolbarHeight.value.roundToPx()
      )
    }

    updatePaddings(
      left = globalWindowInsetsManager.left(),
      right = globalWindowInsetsManager.right(),
      top = topPadding,
      bottom = bottomPadding
    )
  }
}