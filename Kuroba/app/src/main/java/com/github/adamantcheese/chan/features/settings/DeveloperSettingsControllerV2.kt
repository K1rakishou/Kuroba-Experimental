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
import com.github.adamantcheese.chan.features.settings.screens.DatabaseSummaryScreen
import com.github.adamantcheese.chan.features.settings.screens.DeveloperSettingsScreen
import com.github.adamantcheese.chan.ui.controller.ToolbarNavigationController
import com.github.adamantcheese.chan.ui.controller.ToolbarNavigationController.ToolbarSearchCallback
import com.github.adamantcheese.chan.ui.epoxy.epoxyDividerView
import com.github.adamantcheese.chan.utils.AndroidUtils.inflate
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

  private val screenStack = Stack<SettingsIdentifier.Screen>()
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
            rebuildDefaultScreen()
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
      rebuildDefaultScreen()
    }
  }

  override fun onSearchEntered(entered: String?) {
    onSearchEnteredSubject.onNext(entered ?: "")
  }

  override fun onBack(): Boolean {
    if (screenStack.isEmpty()) {
      return false
    }

    rebuildScreen(screenStack.pop())
    return true
  }

  private fun rebuildDefaultScreen() {
    rebuildScreen(SettingsIdentifier.Screen.DeveloperSettingsScreen)
  }

  private fun rebuildScreenWithSearchQuery(query: String) {
    val graph = searchSettingsGraph

    recyclerView.withModels {
      renderSearchScreen(graph, query)
    }
  }

  private fun rebuildScreen(screen: SettingsIdentifier.Screen) {
    val settingsScreen = normalSettingsGraph[screen]

    recyclerView.withModels {
      navigation.title = settingsScreen.title

      renderScreen(settingsScreen)
    }
  }

  private fun rebuildSetting(
    screen: SettingsIdentifier.Screen,
    group: SettingsIdentifier.Group,
    setting: SettingsIdentifier
  ) {
    val settingsScreen = normalSettingsGraph[screen].apply { rebuildSetting(group, setting) }

    recyclerView.withModels {
      navigation.title = settingsScreen.title

      renderScreen(settingsScreen)
    }
  }

  private fun EpoxyController.renderSearchScreen(graph: SettingsGraph, query: String) {
    graph.iterateScreens { settingsScreen ->
      settingsScreen.iterateGroupsIndexed { _, settingsGroup ->
        settingsGroup.iterateSettingsIndexedFilteredByQuery(query) { settingIndex, setting ->
          renderSettingInternal(setting, settingsScreen, settingsGroup, settingIndex, true)
        }
      }
    }
  }

  private fun EpoxyController.renderScreen(settingsScreen: SettingsScreen) {
    settingsScreen.iterateGroupsIndexed { _, settingsGroup ->
      epoxySettingsGroupTitle {
        id("epoxy_settings_group_title_${settingsGroup.groupIdentifier.identifier}")
        groupTitle(settingsGroup.groupTitle)
      }

      settingsGroup.iterateGroupsIndexed { settingIndex, setting ->
        renderSettingInternal(setting, settingsScreen, settingsGroup, settingIndex, false)
      }
    }
  }

  private fun EpoxyController.renderSettingInternal(
    setting: SettingV2,
    settingsScreen: SettingsScreen,
    settingsGroup: SettingsGroup,
    settingIndex: Int,
    searchMode: Boolean
  ) {
    epoxySettingLink {
      id("epoxy_setting_link_${setting.settingsIdentifier.identifier}")
      topDescription(setting.topDescription)
      bottomDescription(setting.bottomDescription)

      clickListener {
        when (val clickAction = setting.callback.invoke()) {
          SettingClickAction.RefreshClickedSetting -> {
            // TODO(archives): when searchMode == true we need to use another version of
            //  rebuildSetting method which will rebuild searchSettingsGraph instead of
            //  normalSettingsGraph.
            rebuildSetting(
              settingsScreen.screenIdentifier,
              settingsGroup.groupIdentifier,
              setting.settingsIdentifier
            )
          }
          // TODO(archives): when searchMode == true we need to use another version of
          //  rebuildSetting method which will rebuild searchSettingsGraph instead of
          //  normalSettingsGraph.
          is SettingClickAction.OpenScreen -> {
            screenStack.push(settingsScreen.screenIdentifier)
            rebuildScreen(clickAction.screenIdentifier)
          }
        }.exhaustive
      }
    }

    if (settingIndex != settingsGroup.lastIndex()) {
      epoxyDividerView {
        id("epoxy_divider_${settingIndex}")
      }
    }
  }

  private fun buildSettingsGraph(): SettingsGraph {
    val graph = SettingsGraph()

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