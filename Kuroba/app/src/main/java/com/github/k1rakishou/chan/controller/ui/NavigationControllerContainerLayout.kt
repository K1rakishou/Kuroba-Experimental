/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.controller.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import com.github.k1rakishou.chan.core.manager.GlobalViewStateManager
import com.github.k1rakishou.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder
import com.github.k1rakishou.chan.features.gesture_editor.ExclusionZone
import com.github.k1rakishou.chan.ui.controller.BrowseController
import com.github.k1rakishou.chan.ui.controller.ViewThreadController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.findControllerOrNull
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import java.util.*
import javax.inject.Inject

class NavigationControllerContainerLayout : FrameLayout {
  private var controllerTracker: ControllerTracker? = null

  @Inject
  lateinit var exclusionZonesHolder: Android10GesturesExclusionZonesHolder
  @Inject
  lateinit var globalViewStateManager: GlobalViewStateManager

  constructor(context: Context) : super(context) {
    preInit()
  }

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    preInit()
  }

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
    context,
    attrs,
    defStyleAttr
  ) {
    preInit()
  }

  private fun preInit() {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    exclusionZonesHolder.removeInvalidZones(context)
  }

  fun initThreadControllerTracking(
    navigationController: NavigationController
  ) {
    Logger.d(TAG, "initThreadControllerTracking()")

    if (controllerTracker is ThreadControllerTracker) {
      return
    }

    controllerTracker = ThreadControllerTracker(
      context = context,
      getWidthFunc = { this.width },
      getHeightFunc = { this.height },
      invalidateFunc = { invalidate() },
      postOnAnimationFunc = { runnable -> ViewCompat.postOnAnimation(this, runnable) },
      navigationController = navigationController
    )
  }

  fun initThreadDrawerOpenGestureControllerTracker(
    navigationController: NavigationController
  ) {
    Logger.d(TAG, "initThreadDrawerOpenGestureControllerTracker()")

    if (controllerTracker is ThreadDrawerOpenGestureControllerTracker) {
      return
    }

    controllerTracker = ThreadDrawerOpenGestureControllerTracker(
      context = context,
      findViewThreadControllerFunc = {
        return@ThreadDrawerOpenGestureControllerTracker navigationController
          .findControllerOrNull { c -> c is ViewThreadController } as? ViewThreadController
      },
      navigationController = navigationController
    )
  }

  fun initBrowseControllerTracker(
    browseController: BrowseController,
    navigationController: NavigationController
  ) {
    Logger.d(TAG, "initBrowseControllerTracker()")

    if (controllerTracker is BrowseControllerTracker) {
      return
    }

    controllerTracker = BrowseControllerTracker(
      context = context,
      browseController = browseController,
      navigationController = navigationController
    )
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    if (AndroidUtils.isAndroid10()) {
      // To trigger onLayout() which will call provideAndroid10GesturesExclusionZones()
      requestLayout()
    }
  }

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    return controllerTracker?.onInterceptTouchEvent(event) ?: false
  }

  override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
    if (disallowIntercept) {
      controllerTracker?.requestDisallowInterceptTouchEvent()
    }

    super.requestDisallowInterceptTouchEvent(disallowIntercept)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    return controllerTracker?.onTouchEvent(parent, event) ?: false
  }

  override fun dispatchDraw(canvas: Canvas) {
    super.dispatchDraw(canvas)

    if (controllerTracker is ThreadControllerTracker) {
      (controllerTracker as ThreadControllerTracker).dispatchDraw(canvas)
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

  companion object {
    private const val TAG = "NavigationControllerContainerLayout"
  }

}