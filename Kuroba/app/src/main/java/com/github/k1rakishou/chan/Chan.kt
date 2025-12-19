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
import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.os.SystemClock
import com.github.k1rakishou.BookmarkGridViewInfo
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettingsInfo
import com.github.k1rakishou.MpvSettings
import com.github.k1rakishou.PersistableChanStateInfo
import com.github.k1rakishou.chan.core.AppDependenciesInitializer
import com.github.k1rakishou.chan.core.cache.downloader.MediaDownloadException
import com.github.k1rakishou.chan.core.di.component.application.ApplicationComponent
import com.github.k1rakishou.chan.core.di.component.application.DaggerApplicationComponent
import com.github.k1rakishou.chan.core.di.module.application.AppModule
import com.github.k1rakishou.chan.core.di.module.application.JsonParserModule
import com.github.k1rakishou.chan.core.di.module.application.LoaderModule
import com.github.k1rakishou.chan.core.di.module.application.ManagerModule
import com.github.k1rakishou.chan.core.di.module.application.NetModule
import com.github.k1rakishou.chan.core.di.module.application.ParserModule
import com.github.k1rakishou.chan.core.di.module.application.RepositoryModule
import com.github.k1rakishou.chan.core.di.module.application.RoomDatabaseModule
import com.github.k1rakishou.chan.core.di.module.application.SiteModule
import com.github.k1rakishou.chan.core.di.module.application.UseCaseModule
import com.github.k1rakishou.chan.core.helper.ImageLoaderFileManagerWrapper
import com.github.k1rakishou.chan.core.helper.ImageSaverFileManagerWrapper
import com.github.k1rakishou.chan.core.helper.ThreadDownloaderFileManagerWrapper
import com.github.k1rakishou.chan.core.manager.ApplicationCrashNotifier
import com.github.k1rakishou.chan.core.manager.ApplicationMigrationManager
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.ReportManager
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager
import com.github.k1rakishou.chan.ui.activity.CrashReportActivity
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.TimeUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.dns.DnsOverHttpsSelector
import com.github.k1rakishou.common.dns.DnsOverHttpsSelectorFactory
import com.github.k1rakishou.common.dns.NormalDnsSelector
import com.github.k1rakishou.common.dns.NormalDnsSelectorFactory
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.SpannableModuleInjector
import com.github.k1rakishou.core_themes.ThemesModuleInjector
import com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.manager.base_directory.DirectoryManager
import com.github.k1rakishou.model.ModelModuleInjector
import com.github.k1rakishou.persist_state.PersistableChanState
import dagger.Lazy
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_OFF
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import org.joda.time.Duration
import org.joda.time.format.PeriodFormatterBuilder
import java.io.IOException
import java.net.InetAddress
import java.util.*
import javax.inject.Inject

class Chan : Application(), ActivityLifecycleCallbacks {
  private var activityForegroundCounter = 0
  private var startTime = 0L

  // Delay job creation here because we need to first set the kotlinx.coroutines.DEBUG_PROPERTY_NAME
  private val job by lazy { SupervisorJob(null) }
  private lateinit var applicationScope: CoroutineScope

  private val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
    Logger.e(TAG, "Coroutine unhandled exception exception", exception)
    onUnhandledException(exception)
  }

  private val tagPrefix by lazy { AndroidUtils.applicationLabel.toString() + " | " }

  private val applicationMigrationManager = ApplicationMigrationManager()

  @Inject
  lateinit var appDependenciesInitializer: AppDependenciesInitializer
  @Inject
  lateinit var settingsNotificationManager: Lazy<SettingsNotificationManager>
  @Inject
  lateinit var applicationVisibilityManager: Lazy<ApplicationVisibilityManager>
  @Inject
  lateinit var reportManager: ReportManager
  @Inject
  lateinit var appConstants: Lazy<AppConstants>
  @Inject
  lateinit var applicationCrashNotifier: ApplicationCrashNotifier

  class NormalDnsSelectorFactoryImpl : NormalDnsSelectorFactory {
    override fun createDnsSelector(okHttpClient: OkHttpClient): NormalDnsSelector {
      Logger.deps("NormalDnsSelectorFactory")

      if (ChanSettings.okHttpAllowIpv6.get()) {
        Logger.d(Logger.DI_TAG, "Using DnsSelector.Mode.SYSTEM")
        return NormalDnsSelector(NormalDnsSelector.Mode.SYSTEM)
      }

      Logger.d(Logger.DI_TAG, "Using DnsSelector.Mode.IPV4_ONLY")
      return NormalDnsSelector(NormalDnsSelector.Mode.IPV4_ONLY)
    }
  }

  class DnsOverHttpsSelectorFactoryImpl : DnsOverHttpsSelectorFactory {
    override fun createDnsSelector(okHttpClient: OkHttpClient): DnsOverHttpsSelector {
      Logger.deps("DnsOverHttpsSelectorFactory")

      val selector = DnsOverHttps.Builder()
        .includeIPv6(ChanSettings.okHttpAllowIpv6.get())
        .client(okHttpClient)
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
          listOf(
            InetAddress.getByName("162.159.36.1"),
            InetAddress.getByName("162.159.46.1"),
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("1.0.0.1"),
            InetAddress.getByName("162.159.132.53"),
            InetAddress.getByName("2606:4700:4700::1111"),
            InetAddress.getByName("2606:4700:4700::1001"),
            InetAddress.getByName("2606:4700:4700::0064"),
            InetAddress.getByName("2606:4700:4700::6400")
          )
        )
        .build()

      return DnsOverHttpsSelector(selector)
    }
  }

  val applicationInForeground: Boolean
    get() = activityForegroundCounter > 0
  val appRunningTime: Long
    get() {
      if (startTime == 0L) {
        return 0
      }

      return SystemClock.elapsedRealtime() - startTime
    }

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)

    val isDev = AppModuleAndroidUtils.isDevBuild
    System.setProperty(
      DEBUG_PROPERTY_NAME,
      if (isDev) DEBUG_PROPERTY_VALUE_ON else DEBUG_PROPERTY_VALUE_OFF
    )

    AndroidUtils.init(this)
    AppModuleAndroidUtils.init(this)
    ChanSettings.init(createChanSettingsInfo())
    Logger.init(tagPrefix, AppModuleAndroidUtils.isDevBuild, ChanSettings.verboseLogs.get(), this)
    PersistableChanState.init(createPersistableChanStateInfo())
    MpvSettings.init()
  }

  override fun onCreate() {
    super.onCreate()

    startTime = SystemClock.elapsedRealtime()

    val start = System.currentTimeMillis()
    onCreateInternal()
    val diff = System.currentTimeMillis() - start

    Logger.d(TAG, "Application initialization took " + diff + "ms")
  }

  private fun onCreateInternal() {
    registerActivityLifecycleCallbacks(this)
    applicationScope = CoroutineScope(job + Dispatchers.Main + CoroutineName("Chan") + coroutineExceptionHandler)

    val isDev = AppModuleAndroidUtils.isDevBuild
    val flavorType = AppModuleAndroidUtils.flavorType

    if (isDev && ENABLE_STRICT_MODE) {
      StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .penaltyFlashScreen()
          .build()
      )

      StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .build()
      )
    }

    val kurobaExUserAgent = buildString {
      append(AndroidUtils.applicationLabel)
      append(" ")
      append(BuildConfig.VERSION_NAME)
    }

    val appConstants = AppConstants(
      context = applicationContext,
      flavorType = flavorType,
      isLowRamDevice = ChanSettings.isLowRamDevice(),
      kurobaExCustomUserAgent = kurobaExUserAgent,
      overrideUserAgent = { ChanSettings.customUserAgent.get() },
      maxPostsInDatabaseSettingValue = 75000,
      maxThreadsInDatabaseSettingValue = 12500
    )

    // Preload user-agent on a background thread
    applicationScope.launch(Dispatchers.IO) { appConstants.userAgent }

    applicationMigrationManager.performMigration(this)

    val fileManager = provideApplicationFileManager()
    val imageSaverFileManagerWrapper =  provideImageSaverFileManagerWrapper()
    val threadDownloaderFileManagerWrapper =  provideThreadDownloaderFileManagerWrapper()
    val imageLoaderFileManagerWrapper =  provideImageLoaderFileManagerWrapper()

    val themeEngine = ThemesModuleInjector.build(
      application = this,
      scope = applicationScope,
      fileManager = fileManager
    ).getThemeEngine()

    themeEngine.initialize(this, TimeUtils.isHalloweenToday())
    SpannableModuleInjector.initialize(themeEngine)

    val modelComponent = ModelModuleInjector.build(
      application = this,
      scope = applicationScope,
      normalDnsSelectorFactory = NormalDnsSelectorFactoryImpl(),
      dnsOverHttpsSelectorFactory = DnsOverHttpsSelectorFactoryImpl(),
      verboseLogs = ChanSettings.verboseLogs.get(),
      isDevFlavor = isDev,
      isLowRamDevice = ChanSettings.isLowRamDevice(),
      okHttpUseDnsOverHttps = ChanSettings.okHttpUseDnsOverHttps.get(),
      appConstants = appConstants
    )

    // We need to start initializing ChanPostRepository first because it deletes old posts during
    // the initialization.
    modelComponent.getChanPostRepository().initialize()

    applicationComponent = DaggerApplicationComponent.builder()
      .application(this)
      .appContext(this)
      .themeEngine(themeEngine)
      .fileManager(fileManager)
      .imageSaverFileManagerWrapper(imageSaverFileManagerWrapper)
      .threadDownloaderFileManagerWrapper(threadDownloaderFileManagerWrapper)
      .imageLoaderFileManagerWrapper(imageLoaderFileManagerWrapper)
      .applicationCoroutineScope(applicationScope)
      .normalDnsSelectorFactory(NormalDnsSelectorFactoryImpl())
      .dnsOverHttpsSelectorFactory(DnsOverHttpsSelectorFactoryImpl())
      .appConstants(appConstants)
      .modelMainComponent(modelComponent)
      .appModule(AppModule())
      .roomDatabaseModule(RoomDatabaseModule())
      .gsonModule(JsonParserModule())
      .loaderModule(LoaderModule())
      .managerModule(ManagerModule())
      .netModule(NetModule())
      .repositoryModule(RepositoryModule())
      .siteModule(SiteModule())
      .parserModule(ParserModule())
      .useCaseModule(UseCaseModule())
      .build()
      .also { component -> component.inject(this) }

    appDependenciesInitializer.init()
    setupErrorHandlers()
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

      if (error is MediaDownloadException.FileDownloadCanceled
        || error is MediaDownloadException.FileNotFoundOnTheServerException
      ) {
        // fine, sometimes they get through all the checks but it doesn't really matter
        return@setErrorHandler
      }

      if (error is NullPointerException || error is IllegalArgumentException) {
        // that's likely a bug in the application
        Thread.currentThread().uncaughtExceptionHandler!!.uncaughtException(
          Thread.currentThread(),
          error
        )
        return@setErrorHandler
      }

      if (error is IllegalStateException) {
        // that's a bug in RxJava or in a custom operator
        Thread.currentThread().uncaughtExceptionHandler!!.uncaughtException(
          Thread.currentThread(),
          error
        )
        return@setErrorHandler
      }

      Logger.e(TAG, "RxJava unhandled exception exception", error)
      onUnhandledException(error)
    }

    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
      // if there's any uncaught crash stuff, just dump them to the log and exit immediately
      Logger.e(TAG, "Unhandled exception in thread: ${thread.name}", e)
      onUnhandledException(e)
    }
  }

  private fun onUnhandledException(exception: Throwable) {
    // Apparently there is no other way around it because when trying to deserialize the stacktrace as a string it crashes.
    CrashReportActivity.exception = exception

    val bundle = Bundle()
      .apply {
        putString(CrashReportActivity.USER_AGENT_KEY, appConstants.get().userAgent)
        putString(CrashReportActivity.APP_LIFE_TIME_KEY, formatAppRunningTime())
      }

    val intent = Intent(this, CrashReportActivity::class.java)
    intent.putExtra(CrashReportActivity.EXCEPTION_BUNDLE_KEY, bundle)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)

    applicationCrashNotifier.onApplicationCrashed()
  }

  private fun activityEnteredForeground() {
    val lastForeground = applicationInForeground
    activityForegroundCounter++

    if (applicationInForeground != lastForeground) {
      Logger.d(TAG, "^^^ App went foreground ^^^")

      applicationVisibilityManager.get().onEnteredForeground()
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

      applicationVisibilityManager.get().onEnteredBackground()
    }
  }

  private fun createPersistableChanStateInfo(): PersistableChanStateInfo {
    return PersistableChanStateInfo(
      versionCode = BuildConfig.VERSION_CODE,
      commitHash = BuildConfig.COMMIT_HASH
    )
  }

  private fun createChanSettingsInfo(): ChanSettingsInfo {
    return ChanSettingsInfo(
      applicationId = BuildConfig.APPLICATION_ID,
      isTablet = AppModuleAndroidUtils.isTablet,
      defaultFilterOrderName = PostsFilter.CatalogSortingOrder.BUMP.orderName,
      isDevBuild = AppModuleAndroidUtils.isDevBuild,
      isBetaBuild = AppModuleAndroidUtils.isBetaBuild,
      bookmarkGridViewInfo = BookmarkGridViewInfo(
        getDimen(R.dimen.thread_grid_bookmark_view_default_width),
        getDimen(R.dimen.thread_grid_bookmark_view_min_width),
        getDimen(R.dimen.thread_grid_bookmark_view_max_width)
      )
    )
  }

  /**
   * This is the main instance of FileManager that is used by the most of the app.
   * */
  private fun provideApplicationFileManager(): FileManager {
    val directoryManager = DirectoryManager(this)

    return FileManager(
      appContext = this,
      badPathSymbolResolutionStrategy = BadPathSymbolResolutionStrategy.ReplaceBadSymbols,
      directoryManager = directoryManager
    )
  }

  /**
   * This is a separate copy of FileManager that exist for the sole purpose of only being used by
   * ImageSaver. That's because all public methods of FileManager are globally locked and the SAF
   * version of the FileManager is slow as fuck so when you download albums the rest of the app will
   * HANG because of the FileManager methods will be locked. To avoid this situation we use a
   * second, separate, instance of FileManager that will only be used in ImageSaver so the other file
   * manager that is used by the app is not getting locked while the user downloads something.
   * */
  private fun provideImageSaverFileManagerWrapper(): ImageSaverFileManagerWrapper {
    val directoryManager = DirectoryManager(this)

    val fileManager = FileManager(
      appContext = this,
      badPathSymbolResolutionStrategy = BadPathSymbolResolutionStrategy.ReplaceBadSymbols,
      directoryManager = directoryManager
    )

    return ImageSaverFileManagerWrapper(fileManager)
  }

  private fun provideThreadDownloaderFileManagerWrapper(): ThreadDownloaderFileManagerWrapper {
    val directoryManager = DirectoryManager(this)

    val fileManager = FileManager(
      appContext = this,
      badPathSymbolResolutionStrategy = BadPathSymbolResolutionStrategy.ReplaceBadSymbols,
      directoryManager = directoryManager
    )

    return ThreadDownloaderFileManagerWrapper(fileManager)
  }

  private fun provideImageLoaderFileManagerWrapper(): ImageLoaderFileManagerWrapper {
    val directoryManager = DirectoryManager(this)

    val fileManager = FileManager(
      appContext = this,
      badPathSymbolResolutionStrategy = BadPathSymbolResolutionStrategy.ReplaceBadSymbols,
      directoryManager = directoryManager
    )

    return ImageLoaderFileManagerWrapper(fileManager)
  }

  fun formatAppRunningTime(): String {
    val time = appRunningTime
    if (time <= 0) {
      return "Unknown (appContext=${this::class.java.simpleName}), time ms: $time"
    }

    return appRunningTimeFormatter.print(Duration.millis(time).toPeriod())
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
  override fun onActivityStarted(activity: Activity) {
    activityEnteredForeground()
  }

  override fun onActivityResumed(activity: Activity) {}
  override fun onActivityPaused(activity: Activity) {}
  override fun onActivityStopped(activity: Activity) {
    activityEnteredBackground()
  }

  override fun onActivityDestroyed(activity: Activity) {}
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

  companion object {
    private const val TAG = "Chan"
    private const val ENABLE_STRICT_MODE = false

    private val appRunningTimeFormatter = PeriodFormatterBuilder()
      .printZeroAlways()
      .minimumPrintedDigits(2)
      .appendHours()
      .appendSuffix(":")
      .appendMinutes()
      .appendSuffix(":")
      .appendSeconds()
      .appendSuffix(".")
      .appendMillis3Digit()
      .toFormatter()

    private lateinit var applicationComponent: ApplicationComponent

    @JvmStatic
    fun getComponent(): ApplicationComponent {
      return applicationComponent
    }
  }
}