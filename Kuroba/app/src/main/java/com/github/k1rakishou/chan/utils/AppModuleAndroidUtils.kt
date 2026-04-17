package com.github.k1rakishou.chan.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.Chan.Companion.getComponent
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.features.view.media.MediaViewerActivity
import com.github.k1rakishou.chan.ui.activity.SharingActivity
import com.github.k1rakishou.chan.ui.activity.StartActivity
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarManager
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.utils.HashingUtil.byteArrayHashSha256HexString
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AndroidUtils.BuildType
import com.github.k1rakishou.common.AndroidUtils.VerifiedBuildType
import com.github.k1rakishou.common.AndroidUtils.appContext
import com.github.k1rakishou.common.AndroidUtils.isAndroidP
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger.d
import com.github.k1rakishou.core_logger.Logger.e
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.NetworkContentAutoLoadMode
import java.io.File
import java.util.Locale

object AppModuleAndroidUtils {
  private const val TAG = "AppModuleAndroidUtils"

  private const val ReleaseSignature = "86242978CF53C34361A8C962D0A57107AEB70E10631AE13EB5B006C0CF673FA9"
  private const val DebugSignature = "DC5195CC40E42B95267D500B6E93E46EC51028C67BDD3D09BBB9C208BF20C8FE"

  @SuppressLint("StaticFieldLeak")
  private lateinit var application: Application

  const val SITE_PREFS_FILE_PREFIX: String = "site_preferences_"

  fun init(application: Application) {
    if (!::application.isInitialized) {
      this.application = application
    }
  }

  fun checkDontKeepActivitiesSettingEnabledForWarningDialog(context: Context, kurobaSettings: KurobaSettings): Boolean {
    if (kurobaSettings.internal.dontKeepActivitiesWarningShown.readBlocking()) {
      return false
    }

    val settingEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.ALWAYS_FINISH_ACTIVITIES, 0) == 1
    if (settingEnabled) {
      kurobaSettings.internal.dontKeepActivitiesWarningShown.writeAsync(true)
    }

    return settingEnabled
  }

  fun verifiedBuildType(): VerifiedBuildType {
    try {
      @SuppressLint("PackageManagerGetSignatures") val sig = applicationSignature()

      val signatureHexString =
        byteArrayHashSha256HexString(sig.toByteArray()).uppercase(Locale.getDefault())

      val isOfficialRelease = ReleaseSignature == signatureHexString
      if (isOfficialRelease) {
        return VerifiedBuildType.Release
      }

      val isOfficialBeta = DebugSignature == signatureHexString
      if (isOfficialBeta) {
        return VerifiedBuildType.Debug
      }

      return VerifiedBuildType.Unknown
    } catch (error: Throwable) {
      e(TAG, "getVerifiedBuildType() error: " + error.errorMessageOrClassName())
      return VerifiedBuildType.Unknown
    }
  }

  @Throws(PackageManager.NameNotFoundException::class)
  private fun applicationSignature(): Signature {
    @SuppressLint("PackageManagerGetSignatures") val sig: Signature

    if (isAndroidP) {
      sig = application.packageManager.getPackageInfo(
        AppModuleAndroidUtils.application.getPackageName(),
        PackageManager.GET_SIGNING_CERTIFICATES
      ).signingInfo!!.getApkContentsSigners()[0]
    } else {
      sig = application.packageManager.getPackageInfo(
        AppModuleAndroidUtils.application.getPackageName(),
        PackageManager.GET_SIGNATURES
      ).signatures!![0]
    }
    return sig
  }

  val isStableBuild: Boolean
    get() = buildType == BuildType.Stable

  val isDevBuild: Boolean
    get() = buildType == BuildType.Dev

  val isBetaBuild: Boolean
    get() = buildType == BuildType.Beta

  val isFdroidBuild: Boolean
    get() = false

  fun isDevOrBetaBuild(): Boolean {
    return isDevBuild || isBetaBuild
  }

  val buildType: BuildType
    get() = when (BuildConfig.BUILD_TYPE) {
      "Stable" -> BuildType.Stable
      "Beta" -> BuildType.Beta
      "Dev" -> BuildType.Dev
      else -> error("Unknown build type '${BuildConfig.BUILD_TYPE}'")
    }

  val obsoleteApplicationIdFromBuildType: String
    get() {
      return when (buildType) {
        BuildType.Stable -> "com.github.k1rakishou.chan"
        BuildType.Beta -> "com.github.k1rakishou.chan-beta"
        BuildType.Dev -> "com.github.k1rakishou.chan-dev"
      }
    }

  /**
   * Tries to open an app that can open the specified URL.<br></br>
   * If this app will open the link then show a chooser to the user without this app.<br></br>
   * Else allow the default logic to run with startActivity.
   *
   * @param link url to open
   */
  @JvmStatic
  fun openLink(link: String?) {
    if (link == null || TextUtils.isEmpty(link)) {
      d(TAG, "openLink() link is empty")
      showToast(application, getString(R.string.open_link_failed_url, link), Toast.LENGTH_LONG)
      return
    }

    val pm = application.getPackageManager()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))

    val resolvedActivity = intent.resolveActivity(pm)
    if (resolvedActivity == null) {
      d(TAG, "openLink() resolvedActivity == null")

      try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivitySafe(intent)
      } catch (e: Throwable) {
        e(TAG, "openLink() application.startActivity() error, intent = " + intent, e)

        val message = getString(R.string.open_link_failed_url_additional_info, link, e.message)
        showErrorToast(message, Toast.LENGTH_SHORT)
      }

      return
    }

    val thisAppIsDefault = (resolvedActivity.packageName == application.packageName)
    if (!thisAppIsDefault) {
      d(TAG, "openLink() thisAppIsDefault == false")
      openIntent(intent)
      return
    }

    // Get all intents that match, and filter out this app
    val resolveInfos = pm.queryIntentActivities(intent, 0)
    val filteredIntents: MutableList<Intent?> = ArrayList<Intent?>(resolveInfos.size)

    for (info in resolveInfos) {
      if (info.activityInfo.packageName != application.packageName) {
        val i = Intent(Intent.ACTION_VIEW, link.toUri())
        i.setPackage(info.activityInfo.packageName)
        filteredIntents.add(i)
      }
    }

    if (filteredIntents.isEmpty()) {
      d(TAG, "openLink() filteredIntents.size() <= 0")
      val message = getString(
        R.string.open_link_failed_url_additional_info,
        link,
        "filteredIntents count <= 0"
      )

      showToast(application, message, Toast.LENGTH_LONG)
      return
    }

    if (filteredIntents.size == 1) {
      d(TAG, "openLink() filteredIntents.size() == 1")
      AppModuleAndroidUtils.openIntent(filteredIntents.get(0)!!)

      return
    }

    // Create a chooser for the last app in the list, and add the rest with
    // EXTRA_INITIAL_INTENTS that get placed above
    val chooser = Intent.createChooser(
      filteredIntents.removeAt(filteredIntents.size - 1),
      null
    )

    chooser.putExtra(
      Intent.EXTRA_INITIAL_INTENTS,
      filteredIntents.toTypedArray<Intent?>()
    )

    d(TAG, "openLink() success")
    openIntent(chooser)
  }

  fun shareLink(link: String?) {
    val intent = Intent(Intent.ACTION_SEND)
    intent.setType("text/plain")
    intent.putExtra(Intent.EXTRA_TEXT, link)
    val chooser = Intent.createChooser(intent, getString(R.string.action_share))
    openIntent(chooser)
  }

  @JvmStatic
  fun openIntent(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
      application.startActivitySafe(intent)
    } catch (e: Throwable) {
      e(TAG, "openIntent() application.startActivity() error, intent = " + intent, e)

      val message: String = getString(R.string.open_intent_failed, intent.toString())
      showErrorToast(message, Toast.LENGTH_SHORT)
      return
    }

    d(TAG, "openIntent() success")
  }

  @JvmStatic
  fun inflate(context: Context?, resId: Int, root: ViewGroup?): View? {
    return LayoutInflater.from(context).inflate(resId, root)
  }

  @JvmStatic
  fun inflate(context: Context?, resId: Int, root: ViewGroup?, attachToRoot: Boolean): View {
    return LayoutInflater.from(context).inflate(resId, root, attachToRoot)
  }

  @JvmStatic
  fun inflate(context: Context?, resId: Int): ViewGroup {
    return LayoutInflater.from(context).inflate(resId, null) as ViewGroup
  }

  val res: Resources
    get() = application.resources!!

  @JvmStatic
  fun dp(dp: Float): Int {
    return (dp * res.getDisplayMetrics().density).toInt()
  }

  fun pxToDp(px: Float): Int {
    return (px / res.getDisplayMetrics().density).toInt()
  }

  fun pxToDp(px: Int): Int {
    return (px / res.getDisplayMetrics().density).toInt()
  }

  @JvmStatic
  fun dp(context: Context, dp: Float): Int {
    return (dp * context.getResources().getDisplayMetrics().density).toInt()
  }

  @JvmStatic
  fun sp(sp: Int): Int {
    return sp(sp.toFloat())
  }

  @JvmStatic
  fun sp(sp: Float): Int {
    return (sp * res.getDisplayMetrics().scaledDensity).toInt()
  }

  @JvmStatic
  fun getString(res: Int): String {
    return AppModuleAndroidUtils.res.getString(res)
  }

  @JvmStatic
  fun getString(res: Int, vararg formatArgs: Any?): String {
    return AppModuleAndroidUtils.res.getString(res, *formatArgs)
  }

  fun getQuantityString(res: Int, quantity: Int): String {
    return AppModuleAndroidUtils.res.getQuantityString(res, quantity)
  }

  fun getQuantityString(res: Int, quantity: Int, vararg formatArgs: Any?): String {
    return AppModuleAndroidUtils.res.getQuantityString(res, quantity, *formatArgs)
  }

  fun getDrawable(@DrawableRes res: Int): Drawable {
    return ContextCompat.getDrawable(appContext, res)!!
  }

  @JvmStatic
  val isTablet: Boolean
    get() = res.getBoolean(R.bool.is_tablet)

  fun getDimen(dimen: Int): Int {
    return res.getDimensionPixelSize(dimen)
  }

  fun shouldLoadForNetworkType(networkType: NetworkContentAutoLoadMode?): Boolean {
    if (networkType == NetworkContentAutoLoadMode.None) {
      return false
    } else if (networkType == NetworkContentAutoLoadMode.Unmetered) {
      return isConnectionUnmetered
    } else {
      return networkType == NetworkContentAutoLoadMode.All
    }
  }

  val isConnectionUnmetered: Boolean
    get() {
      val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

      val networkInfo = connectivityManager.getActiveNetworkInfo()
      if (networkInfo == null) {
        return false
      }

      if (!networkInfo.isConnected()) {
        return false
      }

      if (connectivityManager.isActiveNetworkMetered()) {
        return false
      }

      return true
    }

  @JvmStatic
  val screenOrientation: Int
    get() {
      val screenOrientation = appContext.getResources().getConfiguration().orientation
      check(!(screenOrientation != Configuration.ORIENTATION_LANDSCAPE && screenOrientation != Configuration.ORIENTATION_PORTRAIT)) {
        "Illegal screen orientation value! value = " + screenOrientation
      }

      return screenOrientation
    }

  /**
   * Change to ConnectivityManager#registerDefaultNetworkCallback when minSdk == 24, basically never
   */
  fun networkClass(connectivityManager: ConnectivityManager): String {
    val info = connectivityManager.getActiveNetworkInfo()
    if (info == null || !info.isConnected()) {
      return "No connected" // not connected
    }

    if (info.getType() == ConnectivityManager.TYPE_WIFI) {
      return "WIFI"
    }

    if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
      val networkType = info.getSubtype()
      when (networkType) {
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN,
        TelephonyManager.NETWORK_TYPE_GSM -> return "2G"
        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> return "3G"
        TelephonyManager.NETWORK_TYPE_LTE,
        TelephonyManager.NETWORK_TYPE_IWLAN, 19 -> return "4G"
        TelephonyManager.NETWORK_TYPE_NR -> return "5G"
      }
    }

    return "Unknown"
  }

  fun availableSpaceInBytes(file: File): Long {
    val stat = StatFs(file.getPath())

    return stat.getAvailableBlocksLong() * stat.getBlockSizeLong()
  }

  /**
   * Always registers an onpredrawlistener.
   * **Warning: the view you give must be attached to the view root!**
   */
  fun waitForLayout(view: View, callback: OnMeasuredCallback) {
    if (view.getWindowToken() == null) {
      // See comment above
      view.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
          waitForLayoutInternal(true, view.getViewTreeObserver(), view, callback)
          view.removeOnAttachStateChangeListener(this)
        }

        override fun onViewDetachedFromWindow(v: View) {
          view.removeOnAttachStateChangeListener(this)
        }
      })
      return
    }

    waitForLayoutInternal(false, view.getViewTreeObserver(), view, callback)
  }

  private fun waitForLayoutInternal(
    returnIfNotZero: Boolean,
    viewTreeObserver: ViewTreeObserver,
    view: View,
    callback: OnMeasuredCallback
  ) {
    val width = view.getWidth()
    val height = view.getHeight()

    if (returnIfNotZero && width > 0 && height > 0) {
      callback.onMeasured(view)
      return
    }

    viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
      private var usingViewTreeObserver = viewTreeObserver

      override fun onPreDraw(): Boolean {
        if (usingViewTreeObserver != view.getViewTreeObserver()) {
          e(
            TAG, "view.getViewTreeObserver() is another viewtreeobserver! " +
              "replacing with the new one"
          )

          usingViewTreeObserver = view.getViewTreeObserver()
        }

        if (usingViewTreeObserver.isAlive()) {
          usingViewTreeObserver.removeOnPreDrawListener(this)
        } else {
          e(
            TAG, "ViewTreeObserver not alive, could not remove onPreDrawListener! " +
              "This will probably not end well"
          )
        }

        val ret: Boolean
        try {
          ret = callback.onMeasured(view)
        } catch (e: Exception) {
          e(TAG, "Exception in onMeasured", e)
          throw e
        }

        if (!ret) {
          d(TAG, "waitForLayout requested a re-layout by returning false")
        }

        return ret
      }
    })
  }

  private val snackbarManagerLazy = lazy<SnackbarManager> {
    val applicationComponent = getComponent()
    applicationComponent.snackbarManagerFactory.snackbarManager(SnackbarScope.Global())
  }

  @JvmStatic
  fun showToast(context: Context?, resId: Int, duration: Int) {
    AppModuleAndroidUtils.showToast(context, AppModuleAndroidUtils.getString(resId)!!, duration)
  }

  @JvmStatic
  fun showToast(context: Context?, resId: Int) {
    showToast(context, AppModuleAndroidUtils.getString(resId)!!)
  }

  @JvmStatic
  @JvmOverloads
  fun showToast(context: Context?, message: String, duration: Int = Toast.LENGTH_SHORT) {
    val app = application
    if (app == null) {
      return
    }

    snackbarManagerLazy.value.globalToast(message, duration)
  }

  fun showErrorToast(resId: Int, duration: Int) {
    AppModuleAndroidUtils.showErrorToast(AppModuleAndroidUtils.getString(resId)!!, duration)
  }

  fun showErrorToast(resId: Int) {
    showErrorToast(AppModuleAndroidUtils.getString(resId)!!)
  }

  @JvmOverloads
  fun showErrorToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    val app = application
    if (app == null) {
      return
    }

    snackbarManagerLazy.value.globalErrorToast(message, duration)
  }

  fun getPreferencesForSite(siteDescriptor: SiteDescriptor): SharedPreferences? {
    val preferencesFileName = SITE_PREFS_FILE_PREFIX + siteDescriptor.siteName

    return application.getSharedPreferences(
      preferencesFileName,
      Context.MODE_PRIVATE
    )
  }

  @JvmStatic
  fun extractActivityComponent(context: Context?): ActivityComponent {
    if (context is StartActivity) {
      return context.activityComponent
    } else if (context is SharingActivity) {
      return context.activityComponent
    } else if (context is MediaViewerActivity) {
      return context.activityComponent
    } else if (context is ContextWrapper) {
      val baseContext = context.baseContext
      if (baseContext != null) {
        return extractActivityComponent(baseContext)
      }
    } else if (context == null) {
      error("Context is null")
    }

    throw IllegalStateException("Unknown context wrapper " + context.javaClass.getName())
  }

  fun interface OnMeasuredCallback {
    /**
     * Called when the layout is done.
     *
     * @param view same view as the argument.
     * @return true to continue with rendering, false to cancel and redo the layout.
     */
    fun onMeasured(view: View): Boolean
  }

  fun hasPostNotificationsPermission(context: Context): Boolean {
    if (!AndroidUtils.isAndroidT) {
      return true
    }

    return ActivityCompat.checkSelfPermission(
      context,
      Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
  }
}
