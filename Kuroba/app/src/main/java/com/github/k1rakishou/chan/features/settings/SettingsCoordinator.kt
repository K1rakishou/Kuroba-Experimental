package com.github.k1rakishou.chan.features.settings

import android.content.Context
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.base.LazySuspend
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.core.manager.*
import com.github.k1rakishou.chan.core.repository.ImportExportRepository
import com.github.k1rakishou.chan.core.usecase.InstallMpvNativeLibrariesFromGithubUseCase
import com.github.k1rakishou.chan.core.usecase.InstallMpvNativeLibrariesFromLocalDirectoryUseCase
import com.github.k1rakishou.chan.core.usecase.TwoCaptchaCheckBalanceUseCase
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder
import com.github.k1rakishou.chan.features.settings.screens.*
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingDelegate
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.helper.AppSettingsUpdateAppRefreshHelper
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.RecyclerUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository
import com.github.k1rakishou.model.repository.SeenPostRepository
import com.github.k1rakishou.persist_state.IndexAndTop
import dagger.Lazy
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.reactive.asFlow
import java.util.*
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class SettingsCoordinator(
  private val context: Context,
  private val navigationController: NavigationController,
  private val mainControllerCallbacks: MainControllerCallbacks
): SettingsCoordinatorCallbacks {

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var fileCacheV2: FileCacheV2
  @Inject
  lateinit var cacheHandler: Lazy<CacheHandler>
  @Inject
  lateinit var seenPostRepository: SeenPostRepository
  @Inject
  lateinit var mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository
  @Inject
  lateinit var reportManager: ReportManager
  @Inject
  lateinit var settingsNotificationManager: SettingsNotificationManager
  @Inject
  lateinit var exclusionZonesHolder: Android10GesturesExclusionZonesHolder
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var fileManager: FileManager
  @Inject
  lateinit var applicationVisibilityManager: ApplicationVisibilityManager
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var postHideManager: PostHideManager
  @Inject
  lateinit var chanFilterManager: ChanFilterManager
  @Inject
  lateinit var chanPostRepository: ChanPostRepository
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var proxyStorage: ProxyStorage
  @Inject
  lateinit var importExportRepository: ImportExportRepository
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var twoCaptchaCheckBalanceUseCase: TwoCaptchaCheckBalanceUseCase
  @Inject
  lateinit var appSettingsUpdateAppRefreshHelper: AppSettingsUpdateAppRefreshHelper
  @Inject
  lateinit var threadDownloadingDelegate: ThreadDownloadingDelegate
  @Inject
  lateinit var updateManager: Lazy<UpdateManager>
  @Inject
  lateinit var installMpvNativeLibrariesFromGithubUseCase: InstallMpvNativeLibrariesFromGithubUseCase
  @Inject
  lateinit var installMpvNativeLibrariesFromLocalDirectoryUseCase: InstallMpvNativeLibrariesFromLocalDirectoryUseCase

  private val scope = KurobaCoroutineScope()
  private val settingBuilderExecutor = SerializedCoroutineExecutor(scope)

  private val mainSettingsScreen by lazy {
    MainSettingsScreen(
      context,
      mainControllerCallbacks,
      chanFilterManager,
      siteManager,
      updateManager.get(),
      reportManager,
      navigationController,
      dialogFactory
    )
  }

  private val threadWatcherSettingsScreen by lazy {
    WatcherSettingsScreen(
      context,
      applicationVisibilityManager,
      themeEngine,
      dialogFactory
    )
  }

  private val appearanceSettingsScreen by lazy {
    AppearanceSettingsScreen(
      context,
      navigationController,
      themeEngine
    )
  }

  private val behaviorSettingsScreen by lazy {
    BehaviourSettingsScreen(
      context,
      navigationController,
      postHideManager,
      appSettingsUpdateAppRefreshHelper,
      exclusionZonesHolder,
      globalWindowInsetsManager,
      dialogFactory
    )
  }

  private val captchaSolversSettingsScreen by lazy {
    CaptchaSolversScreen(
      context,
      navigationController,
      dialogFactory,
      twoCaptchaCheckBalanceUseCase
    )
  }

  private val experimentalSettingsScreen by lazy {
    ExperimentalSettingsScreen(context)
  }

  private val developerSettingsScreen by lazy {
    DeveloperSettingsScreen(
      context,
      navigationController,
      themeEngine,
    )
  }

  private val databaseSummaryScreen by lazy {
    DatabaseSettingsSummaryScreen(
      context,
      appConstants,
      mediaServiceLinkExtraContentRepository,
      seenPostRepository,
      chanPostRepository
    )
  }

  private val importExportSettingsScreen by lazy {
    ImportExportSettingsScreen(
      context,
      scope,
      navigationController,
      fileChooser,
      fileManager,
      dialogFactory,
      importExportRepository,
      threadDownloadingDelegate
    )
  }

  private val mediaSettingsScreen by lazy { MediaSettingsScreen(context) }

  private val securitySettingsScreen by lazy {
    SecuritySettingsScreen(
      context,
      navigationController,
      proxyStorage,
      mainControllerCallbacks
    )
  }

  private val cachingSettingsScreen by lazy {
    CachingSettingsScreen(
      context,
      cacheHandler,
      fileCacheV2,
      appConstants,
      dialogFactory
    )
  }

  private val pluginSettingsScreen by lazy {
    PluginSettingsScreen(
      context,
      appConstants,
      dialogFactory,
      fileChooser,
      globalWindowInsetsManager,
      navigationController,
      installMpvNativeLibrariesFromGithubUseCase,
      installMpvNativeLibrariesFromLocalDirectoryUseCase
    )
  }

  private val onSearchEnteredSubject = BehaviorProcessor.create<String>()
  private val renderSettingsSubject = PublishProcessor.create<RenderAction>()

  private val scrollPositionsPerScreen = mutableMapOf<IScreenIdentifier, IndexAndTop>()

  private val settingsGraphDelegate = LazySuspend<SettingsGraph> { buildSettingsGraph() }
  private val screenStack = Stack<IScreenIdentifier>()
  private val job = SupervisorJob()

  private val screensBuiltOnce = SuspendableInitializer<Unit>("screensBuiltOnce")

  @OptIn(ExperimentalTime::class)
  fun onCreate() {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    scope.launch {
      onSearchEnteredSubject
        .asFlow()
        .catch { error -> Logger.e(TAG, "Unknown error received from onSearchEnteredSubject", error) }
        .debounce(Duration.milliseconds(125))
        .collect { query ->
          screensBuiltOnce.awaitUntilInitialized()

          if (query.length < MIN_QUERY_LENGTH) {
            rebuildCurrentScreen(BuildOptions.Default)
            return@collect
          }

          rebuildScreenWithSearchQuery(query, BuildOptions.Default)
        }
    }

    scope.launch {
      settingsNotificationManager.listenForNotificationUpdates()
        .asFlow()
        .collect {
          screensBuiltOnce.awaitUntilInitialized()

          rebuildCurrentScreen(BuildOptions.BuildWithNotificationType)
        }
    }

    mainSettingsScreen.onCreate()
    developerSettingsScreen.onCreate()
    databaseSummaryScreen.onCreate()
    threadWatcherSettingsScreen.onCreate()
    appearanceSettingsScreen.onCreate()
    behaviorSettingsScreen.onCreate()
    experimentalSettingsScreen.onCreate()
    captchaSolversSettingsScreen.onCreate()
    importExportSettingsScreen.onCreate()
    mediaSettingsScreen.onCreate()
    securitySettingsScreen.onCreate()
    cachingSettingsScreen.onCreate()
    pluginSettingsScreen.onCreate()
  }

  fun onDestroy() {
    mainSettingsScreen.onDestroy()
    developerSettingsScreen.onDestroy()
    databaseSummaryScreen.onDestroy()
    threadWatcherSettingsScreen.onDestroy()
    appearanceSettingsScreen.onDestroy()
    behaviorSettingsScreen.onDestroy()
    experimentalSettingsScreen.onDestroy()
    captchaSolversSettingsScreen.onDestroy()
    importExportSettingsScreen.onDestroy()
    mediaSettingsScreen.onDestroy()
    securitySettingsScreen.onDestroy()
    cachingSettingsScreen.onDestroy()
    pluginSettingsScreen.onDestroy()

    screenStack.clear()

    if (settingsGraphDelegate.isInitialized()) {
      settingsGraphDelegate.valueOrNull()?.clear()
    }

    scope.cancelChildren()
    job.cancelChildren()
  }

  fun listenForRenderScreenActions(): Flowable<RenderAction> {
    return renderSettingsSubject
      .observeOn(AndroidSchedulers.mainThread())
      .hide()
  }

  override fun rebuildSetting(
    screenIdentifier: IScreenIdentifier,
    groupIdentifier: IGroupIdentifier,
    settingIdentifier: SettingsIdentifier
  ) {
    settingBuilderExecutor.post {
      val settingsScreen = settingsGraphDelegate.value().get(screenIdentifier)
        .apply {
          rebuildSetting(
            groupIdentifier,
            settingIdentifier,
            BuildOptions.Default
          )
        }

      emitRenderAction(RenderAction.RenderScreen(settingsScreen))
    }
  }

  fun getCurrentIndexAndTopOrNull(): IndexAndTop? {
    val currentScreen = if (screenStack.isEmpty()) {
      null
    } else {
      screenStack.peek()
    }

    if (currentScreen == null) {
      return null
    }

    return scrollPositionsPerScreen[currentScreen]
  }

  fun storeRecyclerPositionForCurrentScreen(recyclerView: EpoxyRecyclerView) {
    val currentScreen = if (screenStack.isEmpty()) {
      null
    } else {
      screenStack.peek()
    }

    if (currentScreen == null) {
      return
    }

    scrollPositionsPerScreen[currentScreen] = RecyclerUtils.getIndexAndTop(recyclerView)
  }

  fun rebuildScreen(
    screenIdentifier: IScreenIdentifier,
    buildOptions: BuildOptions,
    isFirstRebuild: Boolean = false
  ) {
    scope.launch(Dispatchers.Main.immediate) {
      val loadingJob = if (isFirstRebuild) {
        val job = scope.launch {
          delay(50)
          emitRenderAction(RenderAction.Loading)
        }

        siteManager.awaitUntilInitialized()
        boardManager.awaitUntilInitialized()

        job
      } else {
        null
      }

      pushScreen(screenIdentifier)

      settingsGraphDelegate.value().rebuildScreen(screenIdentifier, buildOptions)
      val settingsScreen = settingsGraphDelegate.value().get(screenIdentifier)

      loadingJob?.cancel()
      emitRenderAction(RenderAction.RenderScreen(settingsScreen))

      screensBuiltOnce.initWithValue(Unit)
    }
  }

  fun onSearchEntered(query: String) {
    onSearchEnteredSubject.onNext(query)
  }

  fun onBack(): Boolean {
    if (screenStack.size <= 1) {
      screenStack.clear()
      Logger.d(TAG, "onBack() screenStack.size <= 1, exiting")
      return false
    }

    rebuildScreen(popScreen(), BuildOptions.Default)
    return true
  }

  fun rebuildCurrentScreen(buildOptions: BuildOptions) {
    if (screenStack.isEmpty()) {
      return
    }

    val screenIdentifier = screenStack.peek()
    rebuildScreen(screenIdentifier, buildOptions)
  }

  fun post(func: suspend () -> Unit) {
    settingBuilderExecutor.post(func)
  }

  fun rebuildScreenWithSearchQuery(query: String, buildOptions: BuildOptions) {
    settingBuilderExecutor.post {
      settingsGraphDelegate.value().rebuildScreens(buildOptions)
      val graph = settingsGraphDelegate.value()

      val topScreenIdentifier = if (screenStack.isEmpty()) {
        null
      } else {
        screenStack.peek()
      }

      emitRenderAction(RenderAction.RenderSearchScreen(topScreenIdentifier, graph, query))
    }
  }

  private fun emitRenderAction(renderAction: RenderAction) {
    renderSettingsSubject.onNext(renderAction)
  }

  private fun popScreen(): IScreenIdentifier {
    val currentScreen = screenStack.peek()
    scrollPositionsPerScreen.remove(currentScreen)

    screenStack.pop()
    return screenStack.peek()
  }

  private fun pushScreen(screenIdentifier: IScreenIdentifier) {
    val stackAlreadyContainsScreen = screenStack.any { screenIdentifierInStack ->
      screenIdentifierInStack == screenIdentifier
    }

    if (!stackAlreadyContainsScreen) {
      screenStack.push(screenIdentifier)
    }
  }

  private suspend fun buildSettingsGraph(): SettingsGraph {
    return withContext(Dispatchers.Default) {
      val graph = SettingsGraph()

      graph += mainSettingsScreen.build()
      graph += developerSettingsScreen.build()
      graph += databaseSummaryScreen.build()
      graph += threadWatcherSettingsScreen.build()
      graph += appearanceSettingsScreen.build()
      graph += behaviorSettingsScreen.build()
      graph += experimentalSettingsScreen.build()
      graph += captchaSolversSettingsScreen.build()
      graph += importExportSettingsScreen.build()
      graph += mediaSettingsScreen.build()
      graph += securitySettingsScreen.build()
      graph += cachingSettingsScreen.build()
      graph += pluginSettingsScreen.build()

      return@withContext graph
    }
  }

  sealed class RenderAction {
    object Loading : RenderAction()

    class RenderScreen(val settingsScreen: SettingsScreen) : RenderAction()

    class RenderSearchScreen(
      val topScreenIdentifier: IScreenIdentifier?,
      val graph: SettingsGraph,
      val query: String
    ): RenderAction()
  }

  companion object {
    private const val TAG = "SettingsCoordinator"

    private const val MIN_QUERY_LENGTH = 3
  }
}