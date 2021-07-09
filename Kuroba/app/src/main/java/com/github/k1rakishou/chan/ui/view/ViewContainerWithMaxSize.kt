package com.github.k1rakishou.chan.ui.view

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.ui.layout.PostPopupContainer
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AndroidUtils
import javax.inject.Inject

class ViewContainerWithMaxSize @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : CoordinatorLayout(context, attrs, defStyleAttr) {
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private var displayWidth: Int = 0

  var desiredWidth: Int = PostPopupContainer.MAX_WIDTH
    set(value) {
      field = value
      requestLayout()
    }

  init {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(context)
        .inject(this)
    }

    calculateDisplaySize()
  }

  fun takeWholeWidth() {
    desiredWidth = 0
  }

  override fun onConfigurationChanged(newConfig: Configuration?) {
    calculateDisplaySize()
    requestLayout()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    var newWidth = MeasureSpec.getSize(widthMeasureSpec)

    if (isInEditMode) {
      newWidth = (newWidth / 1.2f).toInt()

      super.onMeasure(
        MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY),
        heightMeasureSpec
      )

      return
    }

    calculateDisplaySize()

    val horizontalPaddings = globalWindowInsetsManager.left() +
      globalWindowInsetsManager.right() +
      paddingStart +
      paddingEnd +
      marginStart +
      marginEnd

    val actualWidth = if (this.desiredWidth <= 0 || this.desiredWidth > displayWidth) {
      displayWidth
    } else {
      desiredWidth
    }

    newWidth = (actualWidth - horizontalPaddings).coerceIn(0, displayWidth)

    require(newWidth > 0) {
      "Bad newWidth: $newWidth (actualWidth: $actualWidth, " +
        "desiredWidth: $desiredWidth, displayWidth: $displayWidth)"
    }

    super.onMeasure(
      MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY),
      heightMeasureSpec
    )
  }

  private fun calculateDisplaySize() {
    if (this.displayWidth <= 0) {
      val (dispWidth, _) = AndroidUtils.getDisplaySize(context)
      this.displayWidth = dispWidth
    }
  }

}