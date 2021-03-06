package com.github.k1rakishou.chan.features.gesture_editor

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.View.inflate
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.RelativeLayout
import androidx.annotation.RequiresApi
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableButton
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getScreenOrientation
import javax.inject.Inject


@RequiresApi(Build.VERSION_CODES.Q)
class AdjustAndroid10GestureZonesController(context: Context) : Controller(context) {
  private lateinit var viewRoot: RelativeLayout
  private lateinit var adjustZonesView: AdjustAndroid10GestureZonesView
  private lateinit var addZoneButton: ColorizableButton

  private var presenting = false
  private var attachSide: AttachSide? = null
  private var skipZone: ExclusionZone? = null

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val globalLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
    override fun onGlobalLayout() {
      adjustZonesView.viewTreeObserver.removeOnGlobalLayoutListener(this)

      onViewLaidOut()
    }
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()
    presenting = true

    view = inflate(context, R.layout.controller_adjust_android_ten_gesture_zones, null) as ViewGroup
    viewRoot = view.findViewById(R.id.view_root)
    adjustZonesView = view.findViewById(R.id.adjust_gesture_zones_view)
    addZoneButton = view.findViewById(R.id.add_zone_button)
    addZoneButton.setOnClickListener { adjustZonesView.onAddZoneButtonClicked() }

    adjustZonesView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    adjustZonesView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
  }

  fun setAttachSide(side: AttachSide) {
    this.attachSide = side
  }

  fun setSkipZone(skipZone: ExclusionZone?) {
    this.skipZone = skipZone
  }

  private fun onViewLaidOut() {
    val side = checkNotNull(attachSide) { "Attach side was not provided! use setAttachSide()" }
    setButtonPosition(side)

    adjustZonesView.measure(
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )

    adjustZonesView.hide()
    adjustZonesView.show(
      side,
      getScreenOrientation(),
      skipZone,
      adjustZonesView.width,
      adjustZonesView.height
    )

    adjustZonesView.setOnZoneAddedCallback {
      showToast(R.string.setting_exclusion_zones_zone_added_message)
      stopPresenting()
    }
  }

  private fun setButtonPosition(attachSide: AttachSide) {
    val prevLayoutParams = addZoneButton.layoutParams as RelativeLayout.LayoutParams

    when (attachSide) {
      AttachSide.Top -> {
        prevLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        prevLayoutParams.topMargin = 0
        prevLayoutParams.bottomMargin = BOTTOM_BUTTON_MARGIN + globalWindowInsetsManager.bottom()
      }
      else -> {
        prevLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        prevLayoutParams.topMargin = TOP_BUTTON_MARGIN + globalWindowInsetsManager.top()
        prevLayoutParams.bottomMargin = 0
      }
    }

    addZoneButton.layoutParams = prevLayoutParams
  }

  override fun onBack(): Boolean {
    if (presenting) {
      presenting = false
      stopPresenting()
      return true
    }

    return super.onBack()
  }

  override fun onDestroy() {
    super.onDestroy()

    presenting = false
    adjustZonesView.hide()
  }

  companion object {
    private val TOP_BUTTON_MARGIN = dp(64f)
    private val BOTTOM_BUTTON_MARGIN = dp(32f)
  }
}