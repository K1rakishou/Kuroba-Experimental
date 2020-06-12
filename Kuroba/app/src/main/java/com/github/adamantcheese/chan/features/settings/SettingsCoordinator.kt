package com.github.adamantcheese.chan.features.settings

import android.content.Context
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.StartActivity
import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.manager.*
import com.github.adamantcheese.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder
import com.github.adamantcheese.chan.features.settings.screens.*
import com.github.adamantcheese.chan.ui.controller.navigation.NavigationController
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.repository.InlinedFileInfoRepository
import com.github.adamantcheese.model.repository.MediaServiceLinkExtraContentRepository
import com.github.adamantcheese.model.repository.SeenPostRepository
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
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
import kotlin.coroutines.CoroutineContext

class SettingsCoordinator(
  private val context: Context,
  private val navigationController: NavigationController
) : CoroutineScope, SettingsCoordinatorCallbacks {

  @Inject
  lateinit var databaseManager: DatabaseManager
  @Inject
  lateinit var fileCacheV2: FileCacheV2
  @Inject
  lateinit var cacheHandler: CacheHandler
  @Inject
  lateinit var seenPostRepository: SeenPostRepository
  @Inject
  lateinit var mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository
  @Inject
  lateinit var inlinedFileInfoRepository: InlinedFileInfoRepository
  @Inject
  lateinit var filterWatchManager: FilterWatchManager
  @Inject
  lateinit var wakeManager: WakeManager
  @Inject
  lateinit var reportManager: ReportManager
  @Inject
  lateinit var settingsNotificationManager: SettingsNotificationManager
  @Inject
  lateinit var themeHelper: ThemeHelper
  @Inject
  lateinit var exclusionZonesHolder: Android10GesturesExclusionZonesHolder
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var fileManager: FileManager
  @Inject
  lateinit var watchManager: WatchManager

  private val mainSettingsScreen by lazy {
    MainSettingsScreen(
      context,
      databaseManager,
      (context as StartActivity).updateManager,
      reportManager,
      navigationController
    )
  }

  private val threadWatcherSettingsScreen by lazy {
    ThreadWatcherSettingsScreen(
      context
    )
  }

  private val appearanceSettingsScreen by lazy {
    AppearanceSettingsScreen(
      context,
      navigationController,
      themeHelper
    )
  }

  private val behaviorSettingsScreen by lazy {
    BehaviourSettingsScreen(
      context,
      navigationController,
      databaseManager
    )
  }

  private val experimentalSettingsScreen by lazy {
    ExperimentalSettingsScreen(
      context,
      navigationController,
      exclusionZonesHolder
    )
  }

  private val developerSettingsScreen by lazy {
    DeveloperSettingsScreen(
      context,
      navigationController,
      cacheHandler,
      fileCacheV2,
      filterWatchManager,
      wakeManager
    )
  }

  private val databaseSummaryScreen by lazy {
    DatabaseSettingsSummaryScreen(
      context,
      inlinedFileInfoRepository,
      mediaServiceLinkExtraContentRepository,
      seenPostRepository
    )
  }

  private val importExportSettingsScreen by lazy {
    ImportExportSettingsScreen(
      context,
      navigationController,
      fileChooser,
      fileManager,
      databaseManager
    )
  }

  private val mediaSettingsScreen by lazy {
    val runtimePermissionsHelper = (context as StartActivity).runtimePermissionsHelper

    MediaSettingsScreen(
      context,
      this,
      navigationController,
      fileManager,
      fileChooser,
      runtimePermissionsHelper
    )
  }

  private val onSearchEnteredSubject = BehaviorProcessor.create<String>()
  private val renderSettingsSubject = PublishProcessor.create<RenderAction>()

  private val settingsGraphDelegate = lazy { buildSettingsGraph() }
  private val settingsGraph by settingsGraphDelegate
  private val screenStack = Stack<IScreenIdentifier>()
  private val job = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("SettingsCoordinator")

  fun onCreate() {
    Chan.inject(this)

    launch {
      onSearchEnteredSubject
        .asFlow()
        .catch { error ->
          Logger.e(TAG, "Unknown error received from onSearchEnteredSubject", error)
        }
        .debounce(DEBOUNCE_TIME_MS)
        .collect { query ->
          if (query.length < MIN_QUERY_LENGTH) {
            rebuildCurrentScreen()
            return@collect
          }

          rebuildScreenWithSearchQuery(query)
        }
    }

    launch {
      settingsNotificationManager.listenForNotificationUpdates()
        .asFlow()
        .catch { error ->
          Logger.e(TAG, "Unknown error received from SettingsNotificationManager", error)
        }
        .collect {
          rebuildCurrentScreen()
        }
    }

    mainSettingsScreen.onCreate()
    developerSettingsScreen.onCreate()
    databaseSummaryScreen.onCreate()
    threadWatcherSettingsScreen.onCreate()
    appearanceSettingsScreen.onCreate()
    behaviorSettingsScreen.onCreate()
    experimentalSettingsScreen.onCreate()
    importExportSettingsScreen.onCreate()
    mediaSettingsScreen.onCreate()
  }

  fun onDestroy() {
    mainSettingsScreen.onDestroy()
    developerSettingsScreen.onDestroy()
    databaseSummaryScreen.onDestroy()
    threadWatcherSettingsScreen.onDestroy()
    appearanceSettingsScreen.onDestroy()
    behaviorSettingsScreen.onDestroy()
    experimentalSettingsScreen.onDestroy()
    importExportSettingsScreen.onDestroy()
    mediaSettingsScreen.onDestroy()

    screenStack.clear()

    if (settingsGraphDelegate.isInitialized()) {
      settingsGraph.clear()
    }

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
    val settingsScreen = settingsGraph[screenIdentifier]
      .apply { rebuildSetting(groupIdentifier, settingIdentifier) }

    renderSettingsSubject.onNext(RenderAction.RenderScreen(settingsScreen))
  }

  fun rebuildScreen(screenIdentifier: IScreenIdentifier) {
    pushScreen(screenIdentifier)
    rebuildScreenInternal(screenIdentifier)
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

    rebuildScreen(popScreen())
    return true
  }

  fun rebuildCurrentScreen() {
    require(screenStack.isNotEmpty()) { "Stack is empty" }

    val screenIdentifier = screenStack.peek()
    rebuildScreen(screenIdentifier)
  }

  fun rebuildScreenWithSearchQuery(query: String) {
    settingsGraph.rebuildScreens()
    val graph = settingsGraph

    val topScreenIdentifier = if (screenStack.isEmpty()) {
      null
    } else {
      screenStack.peek()
    }

    renderSettingsSubject.onNext(RenderAction.RenderSearchScreen(topScreenIdentifier, graph, query))
  }

  private fun rebuildScreenInternal(screen: IScreenIdentifier) {
    settingsGraph.rebuildScreen(screen)
    val settingsScreen = settingsGraph[screen]

    renderSettingsSubject.onNext(RenderAction.RenderScreen(settingsScreen))
  }

  private fun popScreen(): IScreenIdentifier {
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

  private fun buildSettingsGraph(): SettingsGraph {
    val graph = SettingsGraph()

    graph += mainSettingsScreen.build()
    graph += developerSettingsScreen.build()
    graph += databaseSummaryScreen.build()
    graph += threadWatcherSettingsScreen.build()
    graph += appearanceSettingsScreen.build()
    graph += behaviorSettingsScreen.build()
    graph += experimentalSettingsScreen.build()
    graph += importExportSettingsScreen.build()
    graph += mediaSettingsScreen.build()

    return graph
  }

  sealed class RenderAction {
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
    private const val DEBOUNCE_TIME_MS = 350L
  }
}