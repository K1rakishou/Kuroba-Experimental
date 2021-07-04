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
    var newHeight = MeasureSpec.getSize(heightMeasureSpec)

    if (isInEditMode) {
      newWidth = (newWidth / 1.2f).toInt()
      newHeight = (newHeight / 1.2f).toInt()
    } else {
      calculateDisplaySize()

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

      val actualWidth = if (this.desiredWidth <= 0 || this.desiredWidth > displayWidth) {
        displayWidth
      } else {
        desiredWidth
      }

      val actualHeight = if (this.desiredHeight <= 0 || this.desiredHeight > displayHeight) {
        displayHeight
      } else {
        desiredHeight
      }

      newWidth = (actualWidth - horizontalPaddings).coerceIn(0, displayWidth)
      newHeight = (actualHeight - verticalPaddings).coerceIn(0, displayHeight)

      require(newWidth > 0) { "Bad newWidth: $newWidth" }
      require(newHeight > 0) { "Bad newHeight: $newHeight" }
    }

    super.onMeasure(
      MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY),
      heightMeasureSpec
    )
  }

  private fun calculateDisplaySize() {
    if (this.displayWidth == 0 && this.displayHeight == 0) {
      val (dispWidth, dispHeight) = AndroidUtils.getDisplaySize(context)
      this.displayWidth = dispWidth
      this.displayHeight = dispHeight
    }
  }

}