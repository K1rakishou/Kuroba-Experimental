package com.github.adamantcheese.chan.features.settings

import android.content.Context
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.manager.FilterWatchManager
import com.github.adamantcheese.chan.core.manager.WakeManager
import com.github.adamantcheese.chan.features.settings.epoxy.epoxyBooleanSetting
import com.github.adamantcheese.chan.features.settings.epoxy.epoxyLinkSetting
import com.github.adamantcheese.chan.features.settings.epoxy.epoxyNoSettingsFoundView
import com.github.adamantcheese.chan.features.settings.epoxy.epoxySettingsGroupTitle
import com.github.adamantcheese.chan.features.settings.screens.DatabaseSummaryScreen
import com.github.adamantcheese.chan.features.settings.screens.DeveloperSettingsScreen
import com.github.adamantcheese.chan.features.settings.screens.MainSettingsScreen
import com.github.adamantcheese.chan.features.settings.setting.BooleanSettingV2
import com.github.adamantcheese.chan.features.settings.setting.LinkSettingV2
import com.github.adamantcheese.chan.features.settings.setting.SettingV2
import com.github.adamantcheese.chan.ui.controller.ToolbarNavigationController
import com.github.adamantcheese.chan.ui.controller.ToolbarNavigationController.ToolbarSearchCallback
import com.github.adamantcheese.chan.ui.epoxy.epoxyDividerView
import com.github.adamantcheese.chan.utils.AndroidUtils.inflate
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.exhaustive
import com.github.adamantcheese.model.repository.ChanPostRepository
import com.github.adamantcheese.model.repository.InlinedFileInfoRepository
import com.github.adamantcheese.model.repository.MediaServiceLinkExtraContentRepository
import com.github.adamantcheese.model.repository.SeenPostRepository
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.util.*
import javax.inject.Inject

class DeveloperSettingsControllerV2(context: Context) : Controller(context), ToolbarSearchCallback {

  @Inject
  lateinit var databaseManager: DatabaseManager
  @Inject
  lateinit var fileCacheV2: FileCacheV2
  @Inject
  lateinit var cacheHandler: CacheHandler
  @Inject
  lateinit var seenPostRepository: SeenPostRepository
  @Inject
  lateinit var chanPostRepository: ChanPostRepository
  @Inject
  lateinit var mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository
  @Inject
  lateinit var inlinedFileInfoRepository: InlinedFileInfoRepository
  @Inject
  lateinit var filterWatchManager: FilterWatchManager
  @Inject
  lateinit var wakeManager: WakeManager

  lateinit var recyclerView: EpoxyRecyclerView

  private val mainSettingsScreen by lazy {
    MainSettingsScreen(context)
  }

  private val developerSettingsScreen by lazy {
    DeveloperSettingsScreen(
      context,
      navigationController!!,
      cacheHandler,
      fileCacheV2,
      filterWatchManager,
      wakeManager
    )
  }

  private val databaseSummaryScreen by lazy {
    DatabaseSummaryScreen(
      context,
      inlinedFileInfoRepository,
      mediaServiceLinkExtraContentRepository,
      seenPostRepository
    )
  }

  private val normalSettingsGraph by lazy { buildSettingsGraph() }
  private val searchSettingsGraph by lazy { buildSettingsGraph().apply { rebuildScreens() } }

  private val screenStack = Stack<IScreenIdentifier>()
  private val onSearchEnteredSubject = BehaviorProcessor.create<String>()

  @OptIn(FlowPreview::class)
  override fun onCreate() {
    super.onCreate()
    Chan.inject(this)

    view = inflate(context, R.layout.controller_developer_settings)
    recyclerView = view.findViewById(R.id.archives_recycler_view)

    navigation.buildMenu()
      .withItem(R.drawable.ic_search_white_24dp) {
        (navigationController as ToolbarNavigationController).showSearch()
      }
      .build()

    mainScope.launch {
      onSearchEnteredSubject
        .asFlow()
        .debounce(DEBOUNCE_TIME_MS)
        .collect { query ->
          if (query.length < MIN_QUERY_LENGTH) {
            rebuildTopStackScreen()
            return@collect
          }

          rebuildScreenWithSearchQuery(query)
        }
    }

    rebuildDefaultScreen()
  }

  override fun onDestroy() {
    super.onDestroy()

    normalSettingsGraph.clear()
    searchSettingsGraph.clear()
  }

  override fun onSearchVisibilityChanged(visible: Boolean) {
    if (!visible) {
      rebuildTopStackScreen()
    }
  }

  override fun onSearchEntered(entered: String?) {
    onSearchEnteredSubject.onNext(entered ?: "")
  }

  override fun onBack(): Boolean {
    if (screenStack.size <= 1) {
      Logger.d(TAG, "onBack() screenStack.size <= 1, exiting")
      return false
    }

    screenStack.pop()
    val screenIdentifier = screenStack.peek()
    Logger.d(TAG, "onBack() switching to ${screenIdentifier} screen")

    rebuildScreen(screenIdentifier)
    return true
  }

  private fun rebuildDefaultScreen() {
    pushScreen(SettingsIdentifier.MainScreen)
    rebuildScreen(SettingsIdentifier.MainScreen)
  }

  private fun rebuildTopStackScreen() {
    require(screenStack.isNotEmpty()) { "Stack is empty" }

    val screenIdentifier = screenStack.peek()
    rebuildScreen(screenIdentifier)
  }

  private fun rebuildScreenWithSearchQuery(query: String) {
    val graph = searchSettingsGraph

    recyclerView.withModels {
      renderSearchScreen(graph, query)
    }
  }

  private fun rebuildScreen(
    screen: IScreenIdentifier,
    searchMode: Boolean = false
  ) {
    val settingsScreen = if (searchMode) {
      searchSettingsGraph.rebuildScreen(screen)
      searchSettingsGraph[screen]
    } else {
      normalSettingsGraph.rebuildScreen(screen)
      normalSettingsGraph[screen]
    }

    recyclerView.withModels {
      navigation.title = settingsScreen.title

      renderScreen(settingsScreen)
    }
  }

  private fun rebuildSetting(
    screen: IScreenIdentifier,
    group: IGroupIdentifier,
    setting: SettingsIdentifier,
    searchMode: Boolean = false
  ) {
    val settingsScreen = if (searchMode) {
      searchSettingsGraph[screen].apply { rebuildSetting(group, setting) }
    } else {
      normalSettingsGraph[screen].apply { rebuildSetting(group, setting) }
    }

    recyclerView.withModels {
      navigation.title = settingsScreen.title

      renderScreen(settingsScreen)
    }
  }

  private fun EpoxyController.renderSearchScreen(graph: SettingsGraph, query: String) {
    val topScreenIdentifier = if (screenStack.isEmpty()) {
      null
    } else {
      screenStack.peek()
    }

    var foundSomething = false

    graph.iterateScreens { settingsScreen ->
      // TODO(archives):
      //  https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect/-k-class/sealed-subclasses.html
      //  Search not only on the current screen, but on all child screens as well
      if (topScreenIdentifier != null && settingsScreen.screenIdentifier != topScreenIdentifier) {
        return@iterateScreens
      }

      settingsScreen.iterateGroupsIndexed { _, settingsGroup ->
        settingsGroup.iterateSettingsIndexedFilteredByQuery(query) { settingIndex, setting ->
          foundSomething = true
          renderSettingInternal(setting, settingsScreen, settingsGroup, settingIndex, true)
        }
      }
    }

    if (!foundSomething) {
      epoxyNoSettingsFoundView {
        id("no_settings_found")
        query(query)
      }
    }
  }

  private fun EpoxyController.renderScreen(settingsScreen: SettingsScreen) {
    settingsScreen.iterateGroupsIndexed { _, settingsGroup ->
      epoxySettingsGroupTitle {
        id("epoxy_settings_group_title_${settingsGroup.groupIdentifier.getGroupIdentifier()}")
        groupTitle(settingsGroup.groupTitle)
      }

      settingsGroup.iterateGroupsIndexed { settingIndex, setting ->
        renderSettingInternal(setting, settingsScreen, settingsGroup, settingIndex, false)
      }
    }
  }

  private fun EpoxyController.renderSettingInternal(
    settingV2: SettingV2,
    settingsScreen: SettingsScreen,
    settingsGroup: SettingsGroup,
    settingIndex: Int,
    searchMode: Boolean
  ) {
    when (settingV2) {
      is LinkSettingV2 -> {
        epoxyLinkSetting {
          id("epoxy_link_setting_${settingV2.settingsIdentifier.getIdentifier()}")
          topDescription(settingV2.topDescription)
          bottomDescription(settingV2.bottomDescription)

          clickListener {
            when (val clickAction = settingV2.callback.invoke()) {
              SettingClickAction.RefreshClickedSetting -> {
                rebuildSetting(
                  settingsScreen.screenIdentifier,
                  settingsGroup.groupIdentifier,
                  settingV2.settingsIdentifier,
                  searchMode
                )
              }
              is SettingClickAction.OpenScreen -> {
                pushScreen(clickAction.screenIdentifier)
                rebuildScreen(clickAction.screenIdentifier, searchMode)
              }
            }.exhaustive
          }
        }
      }
      is BooleanSettingV2 -> {
        epoxyBooleanSetting {
          id("epoxy_boolean_setting_${settingV2.settingsIdentifier.getIdentifier()}")
          topDescription(settingV2.topDescription)
          bottomDescription(settingV2.bottomDescription)

          clickListener {
            settingV2.callback.invoke()

            // TODO: rebuild?
          }

          checkListener { isChecked ->
            settingV2.onCheckedChanged(isChecked)

            // TODO: rebuild?
          }
        }
      }
    }

    if (settingIndex != settingsGroup.lastIndex()) {
      epoxyDividerView {
        id("epoxy_divider_${settingIndex}")
      }
    }
  }

  private fun pushScreen(screenIdentifier: IScreenIdentifier) {
    val stackAlreadyContainsScreen = screenStack.any { screenIdentifierInStack ->
      screenIdentifierInStack == screenIdentifier
    }

    if (!stackAlreadyContainsScreen) {
      Logger.d(TAG, "Pushing $screenIdentifier screen onto the stack")
      screenStack.push(screenIdentifier)
    }
  }

  private fun buildSettingsGraph(): SettingsGraph {
    val graph = SettingsGraph()

    graph += mainSettingsScreen.build()
    graph += developerSettingsScreen.build()
    graph += databaseSummaryScreen.build()

    return graph
  }

  companion object {
    private const val TAG = "DeveloperSettingsControllerV2"
    private const val MIN_QUERY_LENGTH = 4
    private const val DEBOUNCE_TIME_MS = 250L
  }
}