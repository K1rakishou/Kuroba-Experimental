package com.github.k1rakishou.chan.ui.layout

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.ui.compose.window.KurobaWindowWidthSizeClass
import com.github.k1rakishou.chan.ui.compose.window.WindowWidthSizeClass
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class FloatingControllerLinearContainer : LinearLayout {

  @Inject
  lateinit var appResources: AppResources
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalUiStateHolder: GlobalUiStateHolder

  private val coroutineScope = KurobaCoroutineScope()

  constructor(context: Context) : super(context) {
    init(context)
  }

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    init(context)
  }

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    init(context)
  }

  private fun init(context: Context) {
    AppModuleAndroidUtils.extractActivityComponent(getContext())
      .inject(this)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    coroutineScope.cancelChildren()
    coroutineScope.launch {
      globalUiStateHolder.mainUi.windowSizeClass
        .onEach { requestLayout() }
        .collect()
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    coroutineScope.cancelChildren()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val windowSizeClass = globalUiStateHolder.mainUi.windowSizeClass.value

    var maxWidth = with(appResources.composeDensity) {
      val maxWidthPx = when (windowSizeClass?.widthSizeClass?.asKuroba()) {
        null -> WindowWidthSizeClass.CompactWindowMaxWidth
        KurobaWindowWidthSizeClass.Compact -> WindowWidthSizeClass.CompactWindowMaxWidth
        KurobaWindowWidthSizeClass.Medium -> WindowWidthSizeClass.MediumWindowMaxWidth
        KurobaWindowWidthSizeClass.Expanded -> WindowWidthSizeClass.MediumWindowMaxWidth
      }

      return@with maxWidthPx.dp.roundToPx()
    }

    val minScreenSizeWithPaddings = AndroidUtils.getScreenWidth(context)

    if (maxWidth > minScreenSizeWithPaddings) {
      maxWidth = minScreenSizeWithPaddings
    }

    val parentMaxWidth = MeasureSpec.getSize(widthMeasureSpec)
    if (maxWidth > parentMaxWidth) {
      maxWidth = parentMaxWidth
    }

    val newWidthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY)
    super.onMeasure(newWidthSpec, heightMeasureSpec)
  }

}
