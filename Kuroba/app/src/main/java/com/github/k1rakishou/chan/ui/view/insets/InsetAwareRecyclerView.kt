package com.github.k1rakishou.chan.ui.view.insets

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView
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

class InsetAwareRecyclerView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyleAttr: Int = androidx.recyclerview.R.attr.recyclerViewStyle
) : RecyclerView(context, attributeSet, defStyleAttr), WindowInsetsListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var globalUiStateHolder: GlobalUiStateHolder
  @Inject
  lateinit var appResources: AppResources

  private val coroutineScope = KurobaCoroutineScope()

  private val _additionalTopPaddingDp = mutableStateOf(0.dp)
  private val _additionalBottomPaddingDp = mutableStateOf(0.dp)

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)
  }

  fun additionalTopPadding(padding: Dp) {
    _additionalTopPaddingDp.value = padding
    onInsetsChanged()
  }

  fun additionalBottomPadding(padding: Dp) {
    _additionalBottomPaddingDp.value = padding
    onInsetsChanged()
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

    val additionalTopPadding = with(appResources.composeDensity) { _additionalTopPaddingDp.value.roundToPx() }
    val additionalBottomPadding = with(appResources.composeDensity) { _additionalBottomPaddingDp.value.roundToPx() }

    updatePaddings(
      top = topPadding + additionalTopPadding,
      bottom = bottomPadding + additionalBottomPadding
    )
  }

}