package com.github.adamantcheese.chan.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.core.manager.GlobalWindowInsetsManager
import com.github.adamantcheese.chan.core.manager.WindowInsetsListener
import javax.inject.Inject

class ViewContainerWithMaxSize @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), WindowInsetsListener {
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  var maxWidth: Int = 0
  var maxHeight: Int = 0

  init {
    Chan.inject(this)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  override fun onInsetsChanged() {
    requestLayout()
    invalidate()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    var width = MeasureSpec.getSize(widthMeasureSpec)
    var height = MeasureSpec.getSize(heightMeasureSpec)

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

    val maxWidthWithPaddings = if (maxWidth <= 0) {
      0
    } else {
      maxWidth - horizontalPaddings
    }

    val maxHeightWithPaddings = if (maxHeight <= 0) {
      0
    } else {
      maxHeight - verticalPaddings
    }

    if (maxWidthWithPaddings in 1 until width) {
      width = maxWidthWithPaddings
    }

    if (maxHeightWithPaddings in 1 until height) {
      height = maxHeightWithPaddings
    }

    setMeasuredDimension(
      MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
    )
  }

}