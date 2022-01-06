package com.github.k1rakishou.chan.features.media_viewer.strip

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet
import com.github.k1rakishou.chan.utils.setVisibilityFast

class MediaViewerLeftActionStrip @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : MediaViewerActionStrip(context, attributeSet) {

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_viewer_left_action_strip, this)
    super.init()

    if (isTablet()) {
      setVisibilityFast(View.VISIBLE)
    } else {
      setVisibilityFast(View.GONE)
    }
  }

}