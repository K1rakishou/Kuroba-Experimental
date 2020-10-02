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
package com.github.k1rakishou.chan

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.github.k1rakishou.chan.core.cache.downloader.FileCacheException
import com.github.k1rakishou.chan.core.cache.downloader.FileCacheException.FileNotFoundOnTheServerException
import com.github.k1rakishou.chan.core.di.*
import com.github.k1rakishou.chan.core.manager.*
import com.github.k1rakishou.chan.core.net.DnsSelector
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.features.bookmarks.watcher.BookmarkWatcherCoordinator
import com.github.k1rakishou.chan.ui.service.SavingNotification
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.feather2.Feather
import com.github.k1rakishou.model.DatabaseModuleInjector.build
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import kotlinx.coroutines.*
import okhttp3.Dns
import okhttp3.Protocol
import org.greenrobot.eventbus.EventBus
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

class Chan : Application(), ActivityLifecycleCallbacks {
  private var activityForegroundCounter = 0
  private val job = SupervisorJob(null)
  private var applicationScope: CoroutineScope? = null

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var reportManager: ReportManager
  @Inject
  lateinit var bookmarkWatcherCoordinator: BookmarkWatcherCoordinator
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var chanFilterManager: ChanFilterManager
  @Inject
  lateinit var settingsNotificationManager: SettingsNotificationManager
  @Inject
  lateinit var applicationVisibilityManager: ApplicationVisibilityManager
  @Inject
  lateinit var historyNavigationManager: HistoryNavigationManager
  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var themeEngine: ThemeEngine

  private val okHttpDns: Dns
    get() {
      if (ChanSettings.okHttpAllowIpv6.get()) {
        Logger.d(AppModule.DI_TAG, "Using DnsSelector.Mode.SYSTEM")
        return DnsSelector(DnsSelector.Mode.SYSTEM)
      }

      Logger.d(AppModule.DI_TAG, "Using DnsSelector.Mode.IPV4_ONLY")
      return DnsSelector(DnsSelector.Mode.IPV4_ONLY)
    }

  private val okHttpProtocols: OkHttpProtocols
    get() {
      if (ChanSettings.okHttpAllowHttp2.get()) {
        Logger.d(AppModule.DI_TAG, "Using HTTP_2 and HTTP_1_1")
        return OkHttpProtocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
      }

      Logger.d(AppModule.DI_TAG, "Using HTTP_1_1")
      return OkHttpProtocols(listOf(Protocol.HTTP_1_1))
    }

  private val isEmulator: Boolean
    get() = (Build.MODEL.contains("google_sdk")
      || Build.MODEL.contains("Emulator")
      || Build.MODEL.contains("Android SDK"))


  val applicationInForeground: Boolean
    get() = activityForegroundCounter > 0

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    AndroidUtils.init(this)

    // spit out the build hash to the log
    AndroidUtils.getVerifiedBuildType()

    // remove this if you need to debug some sort of event bus issue
    EventBus.builder()
      .logNoSubscriberMessages(false)
      .installDefaultEventBus()
  }

  override fun onCreate() {
    super.onCreate()

    val start = System.currentTimeMillis()
    onCreateInternal()
    val diff = System.currentTimeMillis() - start

    Logger.d(TAG, "Application initialization took " + diff + "ms")
  }

  private fun onCreateInternal() {
    registerActivityLifecycleCallbacks(this)

    val isDev = AndroidUtils.isDevBuild()
    val isBeta = AndroidUtils.isBetaBuild()

    System.setProperty(
      "kotlinx.coroutines.debug",
      if (isDev) "on" else "off"
    )

    job.cancelChildren()
    applicationScope = CoroutineScope(job + Dispatchers.Main + CoroutineName("Chan"))

    val appConstants = AppConstants(applicationContext, isDev)
    logAppConstants(appConstants)
    SavingNotification.setupChannel()

    val okHttpDns = okHttpDns
    val okHttpProtocols = okHttpProtocols

    val modelMainComponent = build(
      this,
      okHttpDns,
      okHttpProtocols.protocols,
      Logger.TAG_PREFIX,
      isDev,
      isDev || isBeta,
      ChanSettings.verboseLogs.get(),
      appConstants,
      applicationScope!!
    )

    feather = Feather.with(
      AppModule(this, applicationScope, okHttpDns, okHttpProtocols, appConstants),
      UseCaseModule(),
      ExecutorsModule(),
      // TODO: change to a normal dagger implementation when we get rid of Feather
      RoomDatabaseModule(modelMainComponent),
      NetModule(),
      GsonModule(),
      RepositoryModule(),
      SiteModule(),
      LoaderModule(),
      ManagerModule()
    )

    feather.injectFields(this)

    themeEngine.initialize(this)
    siteManager.initialize()
    boardManager.initialize()
    bookmarksManager.initialize()
    historyNavigationManager.initialize()
    bookmarkWatcherCoordinator.initialize()
    archivesManager.initialize()
    chanFilterManager.initialize()

    setupErrorHandlers()

    // TODO(KurobaEx): move to background thread!
    if (ChanSettings.collectCrashLogs.get()) {
      if (reportManager.hasCrashLogs()) {
        settingsNotificationManager.notify(SettingNotificationType.CrashLog)
      }
    }
  }

  private fun setupErrorHandlers() {
    RxJavaPlugins.setErrorHandler { e: Throwable? ->
      var error = e

      if (error is UndeliverableException) {
        error = error.cause
      }

      if (error == null) {
        return@setErrorHandler
      }

      if (error is IOException) {
        // fine, irrelevant network problem or API that throws on cancellation
        return@setErrorHandler
      }

      if (error is InterruptedException) {
        // fine, some blocking code was interrupted by a dispose call
        return@setErrorHandler
      }

      if (error is RuntimeException && error.cause is InterruptedException) {
        // fine, DB synchronous call (via runTask) was interrupted when a reactive stream
        // was disposed of.
        return@setErrorHandler
      }

      if (error is FileCacheException.CancellationException
        || error is FileNotFoundOnTheServerException) {
        // fine, sometimes they get through all the checks but it doesn't really matter
        return@setErrorHandler
      }

      if (error is NullPointerException || error is IllegalArgumentException) {
        // that's likely a bug in the application
        Thread.currentThread().uncaughtExceptionHandler!!.uncaughtException(Thread.currentThread(), error)
        return@setErrorHandler
      }

      if (error is IllegalStateException) {
        // that's a bug in RxJava or in a custom operator
        Thread.currentThread().uncaughtExceptionHandler!!.uncaughtException(Thread.currentThread(), error)
        return@setErrorHandler
      }

      Logger.e(TAG, "RxJava undeliverable exception", error)
      onUnhandledException(error, exceptionToString(true, error))
    }

    Thread.setDefaultUncaughtExceptionHandler { _, e ->
      // if there's any uncaught crash stuff, just dump them to the log and exit immediately
      Logger.e(TAG, "Unhandled exception", e)
      onUnhandledException(e, exceptionToString(false, e))
      System.exit(999)
    }
  }

  private fun logAppConstants(appConstants: AppConstants) {
    Logger.d(TAG, "maxPostsCountInPostsCache = " + appConstants.maxPostsCountInPostsCache)
    Logger.d(TAG, "maxAmountOfPostsInDatabase = " + appConstants.maxAmountOfPostsInDatabase)
    Logger.d(TAG, "maxAmountOfThreadsInDatabase = " + appConstants.maxAmountOfThreadsInDatabase)
  }

  private fun exceptionToString(isCalledFromRxJavaHandler: Boolean, e: Throwable): String {
    try {
      StringWriter().use { sw ->
        PrintWriter(sw).use { pw ->
          e.printStackTrace(pw)
          val stackTrace = sw.toString()

          return if (isCalledFromRxJavaHandler) {
            "Called from RxJava onError handler.\n$stackTrace"
          } else {
            "Called from unhandled exception handler.\n$stackTrace"
          }
        }
      }
    } catch (ex: IOException) {
      throw RuntimeException("Error while trying to convert exception to string!", ex)
    }
  }

  private fun onUnhandledException(exception: Throwable, errorText: String) {
    Logger.e("UNCAUGHT", errorText)
    Logger.e("UNCAUGHT", "------------------------------")
    Logger.e("UNCAUGHT", "END OF CURRENT RUNTIME MESSAGES")
    Logger.e("UNCAUGHT", "------------------------------")
    Logger.e("UNCAUGHT", "Android API Level: " + Build.VERSION.SDK_INT)
    Logger.e("UNCAUGHT", "App Version: " + BuildConfig.VERSION_NAME + "." + BuildConfig.BUILD_NUMBER)
    Logger.e("UNCAUGHT", "Development Build: " + AndroidUtils.getVerifiedBuildType().name)
    Logger.e("UNCAUGHT", "Phone Model: " + Build.MANUFACTURER + " " + Build.MODEL)

    // don't upload debug crashes
    if ("Debug crash" == exception.message) {
      return
    }

    if (isEmulator) {
      return
    }

    if (ChanSettings.collectCrashLogs.get()) {
      reportManager.storeCrashLog(exception.message, errorText)
    }
  }

  private fun activityEnteredForeground() {
    val lastForeground = applicationInForeground
    activityForegroundCounter++

    if (applicationInForeground != lastForeground) {
      Logger.d(TAG, "^^^ App went foreground ^^^")

      applicationVisibilityManager.onEnteredForeground()
      AndroidUtils.postToEventBus(ForegroundChangedMessage(applicationInForeground))
    }
  }

  private fun activityEnteredBackground() {
    val lastForeground = applicationInForeground
    activityForegroundCounter--

    if (activityForegroundCounter < 0) {
      activityForegroundCounter = 0
    }

    if (applicationInForeground != lastForeground) {
      Logger.d(TAG, "vvv App went background vvv")

      applicationVisibilityManager.onEnteredBackground()
      AndroidUtils.postToEventBus(ForegroundChangedMessage(applicationInForeground))
    }
  }

  class ForegroundChangedMessage(var inForeground: Boolean)

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
  override fun onActivityStarted(activity: Activity) {
    activityEnteredForeground()
  }

  override fun onActivityResumed(activity: Activity) {}
  override fun onActivityPaused(activity: Activity) {}
  override fun onActivityStopped(activity: Activity) {
    activityEnteredBackground()
  }

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle?) {}
  override fun onActivityDestroyed(activity: Activity) {}

  class OkHttpProtocols(val protocols: List<Protocol>)

  companion object {
    private const val TAG = "Chan"
    private lateinit var feather: Feather

    /**
     * Only ever use this method in cases when there is no other way around it (like when normal
     * injection will create a circular dependency)
     * TODO(dependency-cycles): get rid of this method once all dependency cycles are resolved
     */
    fun <T> instance(tClass: Class<T>?): T {
      return feather.instance(tClass)
    }

    @JvmStatic
    fun <T> inject(instance: T): T {
      feather.injectFields(instance)
      return instance
    }
  }
}