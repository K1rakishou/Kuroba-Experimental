package com.github.k1rakishou.chan.ui.view

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
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
  private var displayHeight: Int = 0

  var desiredWidth: Int = PostPopupContainer.MAX_WIDTH
    set(value) {
      field = value
      requestLayout()
    }

  var desiredHeight: Int = 0
    set(value) {
      field = value
      requestLayout()
    }

  init {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(context)
        .inject(this)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    if (!isInEditMode) {
      calculateSizes()
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration?) {
    calculateSizes()
    requestLayout()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    var newWidth = MeasureSpec.getSize(widthMeasureSpec)
    var newHeight = MeasureSpec.getSize(heightMeasureSpec)

    if (isInEditMode) {
      newWidth = (newWidth / 1.2f).toInt()
      newHeight = (newHeight / 1.2f).toInt()
    } else {
      val horizontalPaddings = globalWindowInsetsManager.left() +
        globalWindowInsetsManager.right() +
        paddingStart +
        paddingEnd +
        marginStart +
        marginEnd

      val verticalPaddings = globalWindowInsetsManager.top() +
        globalWindowInsetsManager.bottom() +
        paddingTop +
        paddingBottom +
        marginTop +
        marginBottom

      newWidth = desiredWidth.coerceIn(0, displayWidth) - horizontalPaddings
      newHeight = desiredHeight.coerceIn(0, displayHeight) - verticalPaddings

      require(newWidth > 0) { "Bad newWidth: $newWidth" }
      require(newHeight > 0) { "Bad newHeight: $newHeight" }
    }

    super.onMeasure(
      MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY),
      heightMeasureSpec
    )
  }

  private fun calculateSizes() {
    val (dispWidth, dispHeight) = AndroidUtils.getDisplaySize(context)
    this.displayWidth = dispWidth
    this.displayHeight = dispHeight

    if (this.desiredWidth <= 0 || this.desiredWidth > dispWidth) {
      this.desiredWidth = dispWidth
    }

    if (this.desiredHeight <= 0 || this.desiredHeight > dispHeight) {
      this.desiredHeight = dispHeight
    }
  }

}