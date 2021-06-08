package com.github.k1rakishou.chan.features.media_viewer

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.RequiresApi
import com.github.k1rakishou.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder
import com.github.k1rakishou.chan.features.gesture_editor.ExclusionZone
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayoutNoBackground
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AndroidUtils
import java.util.*
import javax.inject.Inject

class MediaViewerRootLayout @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : TouchBlockingFrameLayoutNoBackground(context, attributeSet) {

  @Inject
  lateinit var exclusionZonesHolder: Android10GesturesExclusionZonesHolder

  init {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(context)
        .inject(this)

      exclusionZonesHolder.removeInvalidZones(context)
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    if (AndroidUtils.isAndroid10()) {
      // To trigger onLayout() which will call provideAndroid10GesturesExclusionZones()
      requestLayout()
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)

    // We should check that changed is true, otherwise there will be way too may events, we don't
    // want that many.
    if (AndroidUtils.isAndroid10() && changed) {
      // This shouldn't be called very often (like once per configuration change or even
      // less often) so it's okay to allocate lists. Just to not use this method in onDraw
      provideAndroid10GesturesExclusionZones()
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.Q)
  private fun provideAndroid10GesturesExclusionZones() {
    val zonesMap: Map<Int, Set<ExclusionZone>> = exclusionZonesHolder.getZones()

    if (zonesMap.isEmpty()) {
      return
    }

    val orientation = context.resources.configuration.orientation
    val zones = zonesMap[orientation]

    if (zones != null && zones.isNotEmpty()) {
      val rects: MutableList<Rect> = zones.mapTo(ArrayList(), ExclusionZone::zoneRect)

      systemGestureExclusionRects = rects
    }
  }

}