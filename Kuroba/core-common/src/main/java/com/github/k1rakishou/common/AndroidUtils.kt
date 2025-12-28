package com.github.k1rakishou.common

import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Dialog
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnShowListener
import android.content.SharedPreferences
import android.graphics.Point
import android.media.AudioManager
import android.os.Build
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.preference.PreferenceManager
import java.io.File
import kotlin.math.max
import kotlin.math.min

object AndroidUtils {
  private const val TAG = "AndroidUtils"
  const val CHAN_STATE_PREFS_NAME: String = "chan_state"
  const val MPV_PREFS_NAME: String = "mpv_prefs"

  @SuppressLint("StaticFieldLeak")
  private lateinit var application: Application

  fun init(application: Application) {
    if (!::application.isInitialized) {
      this.application = application
    }
  }

  @JvmStatic
  val appDir: File
    get() = application.filesDir.getParentFile()

  val filesDir: File
    get() = application.filesDir

  @JvmStatic
  val appContext: Context
    get() = application

  val applicationLabel: CharSequence
    get() = application.packageManager
      .getApplicationLabel(application.applicationInfo)

  val appFileProvider: String
    get() = application.packageName + ".fileprovider"

  val isNotMainProcess: Boolean
    get() = false

  @JvmStatic
  val appMainPreferences: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(application)

  val appState: SharedPreferences
    get() = appContext.getSharedPreferences(
      CHAN_STATE_PREFS_NAME,
      Context.MODE_PRIVATE
    )

  val mpvState: SharedPreferences
    get() = appContext.getSharedPreferences(
      MPV_PREFS_NAME,
      Context.MODE_PRIVATE
    )

  @JvmStatic
  fun requestKeyboardFocus(dialog: Dialog, view: View) {
    view.requestFocus()
    dialog.setOnShowListener(OnShowListener { dialog1: DialogInterface? -> requestKeyboardFocus(view) })
  }

  @JvmStatic
  fun requestKeyboardFocus(view: View?) {
    inputManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
  }

  @JvmStatic
  fun hideKeyboard(view: View?) {
    if (view != null) {
      inputManager.hideSoftInputFromWindow(view.windowToken, 0)
    }
  }

  fun requestViewAndKeyboardFocus(view: View) {
    view.setFocusable(false)
    view.setFocusableInTouchMode(true)
    if (view.requestFocus()) {
      inputManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
  }

  fun updatePaddings(view: View, left: Int, right: Int, top: Int, bottom: Int) {
    var newLeft = left
    if (newLeft < 0) {
      newLeft = view.getPaddingLeft()
    }

    var newRight = right
    if (newRight < 0) {
      newRight = view.getPaddingRight()
    }

    var newTop = top
    if (newTop < 0) {
      newTop = view.getPaddingTop()
    }

    var newBottom = bottom
    if (newBottom < 0) {
      newBottom = view.getPaddingBottom()
    }

    view.setPadding(newLeft, newTop, newRight, newBottom)
  }

  fun setBoundlessRoundRippleBackground(view: View) {
    val outValue = TypedValue()
    view.getContext().getTheme().resolveAttribute(
      R.attr.selectableItemBackgroundBorderless,
      outValue,
      true
    )

    view.setBackgroundResource(outValue.resourceId)
  }

  fun setRippleBackground(view: View) {
    val outValue = TypedValue()
    view.getContext().getTheme().resolveAttribute(
      R.attr.selectableItemBackground,
      outValue,
      true
    )

    view.setBackgroundResource(outValue.resourceId)
  }

  fun findViewsById(root: ViewGroup, id: Int): MutableList<View?> {
    val views: MutableList<View?> = ArrayList<View?>()
    val childCount = root.getChildCount()
    for (i in 0..<childCount) {
      val child = root.getChildAt(i)
      if (child is ViewGroup) {
        views.addAll(findViewsById(child, id))
      }

      if (child.getId() == id) {
        views.add(child)
      }
    }

    return views
  }

  @JvmStatic
  fun removeFromParentView(view: View): Boolean {
    if (view.getParent() is ViewGroup && (view.getParent() as ViewGroup).indexOfChild(view) >= 0) {
      (view.getParent() as ViewGroup).removeView(view)
      return true
    } else {
      return false
    }
  }

  @JvmStatic
  fun getDisplaySize(context: Context): Point {
    val displayMetrics = DisplayMetrics()
    val windowManager = (context as Activity).getWindowManager()
    windowManager.getDefaultDisplay().getMetrics(displayMetrics)

    return Point(displayMetrics.widthPixels, displayMetrics.heightPixels)
  }

  fun getRealDisplaySize(context: Context): Point {
    val displayMetrics = DisplayMetrics()
    val windowManager = (context as Activity).getWindowManager()
    windowManager.getDefaultDisplay().getRealMetrics(displayMetrics)

    return Point(displayMetrics.widthPixels, displayMetrics.heightPixels)
  }

  /**
   * These two methods get the screen size ignoring the current screen orientation.
   */
  fun getScreenWidth(context: Context): Int {
    val displaySize = getDisplaySize(context)
    return displaySize.x
  }

  fun getRealMinScreenSize(context: Context): Int {
    val displaySize = getRealDisplaySize(context)
    return min(displaySize.x, displaySize.y)
  }

  fun getRealMaxScreenSize(context: Context): Int {
    val displaySize = getRealDisplaySize(context)
    return max(displaySize.x, displaySize.y)
  }

  fun getWindow(context: Context?): Window? {
    if (context is Activity) {
      return context.getWindow()
    } else {
      return null
    }
  }

  @JvmStatic
  private val inputManager: InputMethodManager
    get() = application.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

  @JvmStatic
  val clipboardManager: ClipboardManager
    get() = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

  @JvmStatic
  val activityManager: ActivityManager
    get() = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

  val notificationManager: NotificationManager
    get() = (application.getSystemService(Context.NOTIFICATION_SERVICE)) as NotificationManager

  val jobScheduler: JobScheduler
    get() = application.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

  val audioManager: AudioManager
    get() = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  val clipboardContent: String
    get() {
      val primary: ClipData? = clipboardManager.getPrimaryClip()
      if (primary != null && primary.getItemCount() > 0) {
        val text = primary.getItemAt(0).getText()
        if (!TextUtils.isEmpty(text)) {
          return primary.getItemAt(0).getText().toString()
        }
      }

      return ""
    }

  fun setClipboardContent(label: String?, content: String?) {
    clipboardManager.setPrimaryClip(ClipData.newPlainText(label, content))
  }

  val apiLevel: Int
    get() = Build.VERSION.SDK_INT

  @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
  val isAndroid14: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

  @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
  val isAndroid13: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

  @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
  val isAndroid11: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

  @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
  val isAndroid10: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

  @JvmStatic
  @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
  val isAndroidO: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

  @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP_MR1)
  val isAndroidL_MR1: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1

  @JvmStatic
  @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
  val isAndroidP: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

  @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.M)
  val isAndroidM: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

  @JvmStatic
  @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
  val isAndroidN: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

  @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N_MR1)
  val isAndroidNMR1: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1

  enum class FlavorType(val tag: String) {
    Stable("Stable"),
    Beta("Beta"),
    Dev("Dev"),
    Fdroid("Fdroid")
  }

  enum class VerifiedBuildType {
    Debug,
    Release,
    Unknown
  }
}
