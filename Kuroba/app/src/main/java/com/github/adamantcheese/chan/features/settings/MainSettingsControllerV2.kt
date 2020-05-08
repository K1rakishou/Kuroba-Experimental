package com.github.adamantcheese.chan.features.settings

import android.content.Context
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.StartActivity
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.manager.FilterWatchManager
import com.github.adamantcheese.chan.core.manager.ReportManager
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager
import com.github.adamantcheese.chan.core.manager.WakeManager
import com.github.adamantcheese.chan.features.settings.epoxy.epoxyBooleanSetting
import com.github.adamantcheese.chan.features.settings.epoxy.epoxyLinkSetting
import com.github.adamantcheese.chan.features.settings.epoxy.epoxyNoSettingsFoundView
import com.github.adamantcheese.chan.features.settings.epoxy.epoxySettingsGroupTitle
import com.github.adamantcheese.chan.features.settings.screens.DatabaseSettingsSummaryScreen
import com.github.adamantcheese.chan.features.settings.screens.DeveloperSettingsScreen
import com.github.adamantcheese.chan.features.settings.screens.MainSettingsScreen
import com.github.adamantcheese.chan.features.settings.screens.ThreadWatcherSettingsScreen
import com.github.adamantcheese.chan.features.settings.setting.BooleanSettingV2
import com.github.adamantcheese.chan.features.settings.setting.LinkSettingV2
import com.github.adamantcheese.chan.features.settings.setting.ListSettingV2
import com.github.adamantcheese.chan.features.settings.setting.SettingV2
import com.github.adamantcheese.chan.ui.controller.FloatingListMenuController
import com.github.adamantcheese.chan.ui.controller.ToolbarNavigationController
import com.github.adamantcheese.chan.ui.controller.ToolbarNavigationController.ToolbarSearchCallback
import com.github.adamantcheese.chan.ui.epoxy.epoxyDividerView
import com.github.adamantcheese.chan.ui.settings.SettingNotificationType
import com.github.adamantcheese.chan.ui.view.floating_menu.FloatingListMenu
import com.github.adamantcheese.chan.utils.AndroidUtils.inflate
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.exhaustive
import com.github.adamantcheese.model.repository.InlinedFileInfoRepository
import com.github.adamantcheese.model.repository.MediaServiceLinkExtraContentRepository
import com.github.adamantcheese.model.repository.SeenPostRepository
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.util.*
import javax.inject.Inject

class MainSettingsControllerV2(context: Context) : Controller(context), ToolbarSearchCallback {

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

  lateinit var recyclerView: EpoxyRecyclerView

  private val mainSettingsScreen by lazy {
    MainSettingsScreen(
      context,
      databaseManager,
      (context as StartActivity).updateManager,
      reportManager,
      navigationController!!
    )
  }

  private val threadWatcherSettingsScreen by lazy {
    ThreadWatcherSettingsScreen(
      context
    )
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
    DatabaseSettingsSummaryScreen(
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
  private val defaultScreen = MainScreen

  @OptIn(FlowPreview::class)
  override fun onCreate() {
    super.onCreate()
    Chan.inject(this)

    view = inflate(context, R.layout.controller_developer_settings)
    recyclerView = view.findViewById(R.id.settings_recycler_view)

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
            rebuildCurrentScreen()
            return@collect
          }

          rebuildScreenWithSearchQuery(query)
        }
    }

    mainScope.launch {
      settingsNotificationManager.listenForNotificationUpdates()
        .asFlow()
        .catch { error ->
          Logger.e(TAG, "Unknown error received from SettingsNotificationManager", error)
        }
        .collect {
          rebuildCurrentScreen()
        }
    }

    rebuildDefaultScreen()
  }

  override fun onDestroy() {
    super.onDestroy()

    normalSettingsGraph.clear()
    searchSettingsGraph.clear()
    screenStack.clear()
  }

  override fun onSearchVisibilityChanged(visible: Boolean) {
    if (!visible) {
      rebuildCurrentScreen()
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
    pushScreen(defaultScreen)
    rebuildScreen(defaultScreen)
  }

  private fun rebuildCurrentScreen() {
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

  private fun rebuildScreen(screen: IScreenIdentifier) {
    normalSettingsGraph.rebuildScreen(screen)
    val settingsScreen = normalSettingsGraph[screen]

    recyclerView.withModels {
      updateTitle(settingsScreen)

      renderScreen(settingsScreen)
    }
  }

  private fun rebuildSetting(
    screen: IScreenIdentifier,
    group: IGroupIdentifier,
    setting: SettingsIdentifier
  ) {
    val settingsScreen = normalSettingsGraph[screen].apply { rebuildSetting(group, setting) }

    recyclerView.withModels {
      updateTitle(settingsScreen)

      renderScreen(settingsScreen)
    }
  }

  private fun updateTitle(settingsScreen: SettingsScreen) {
    navigation.title = settingsScreen.title
    (navigationController as ToolbarNavigationController).toolbar!!.updateTitle(navigation)
  }

  private fun EpoxyController.renderSearchScreen(graph: SettingsGraph, query: String) {
    val topScreenIdentifier = if (screenStack.isEmpty()) {
      null
    } else {
      screenStack.peek()
    }

    var foundSomething = false
    var settingIndex = 0

    val isDefaultScreen = (topScreenIdentifier != null
      && topScreenIdentifier.getScreenIdentifier() == defaultScreen.getScreenIdentifier())

    graph.iterateScreens { settingsScreen ->
      val isCurrentScreen =
        settingsScreen.screenIdentifier.getScreenIdentifier() != topScreenIdentifier?.getScreenIdentifier()

      if (!isDefaultScreen && isCurrentScreen) {
        return@iterateScreens
      }

      settingsScreen.iterateGroups { settingsGroup ->
        settingsGroup.iterateSettingsFilteredByQuery(query) { setting ->
          foundSomething = true
          renderSettingInternal(setting, settingsScreen, settingsGroup, settingIndex++, query)
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
    var settingIndex = 0

    settingsScreen.iterateGroups { settingsGroup ->
      epoxySettingsGroupTitle {
        id("epoxy_settings_group_title_${settingsGroup.groupIdentifier.getGroupIdentifier()}")
        groupTitle(settingsGroup.groupTitle)
      }

      settingsGroup.iterateGroups { setting ->
        renderSettingInternal(setting, settingsScreen, settingsGroup, settingIndex++, null)
      }
    }
  }

  private fun EpoxyController.renderSettingInternal(
    settingV2: SettingV2,
    settingsScreen: SettingsScreen,
    settingsGroup: SettingsGroup,
    settingIndex: Int,
    query: String?
  ) {
    val notificationType = if (settingsNotificationManager.contains(settingV2.notificationType)) {
      settingV2.notificationType!!
    } else {
      SettingNotificationType.Default
    }

    when (settingV2) {
      is LinkSettingV2 -> {
        epoxyLinkSetting {
          id("epoxy_link_setting_${settingV2.settingsIdentifier.getIdentifier()}")
          topDescription(settingV2.topDescription)
          bottomDescription(settingV2.bottomDescription)
          bindNotificationIcon(notificationType)

          if (settingV2.isEnabled()) {
            settingEnabled(true)

            clickListener {
              when (val clickAction = settingV2.callback.invoke()) {
                SettingClickAction.RefreshClickedSetting -> {
                  if (!query.isNullOrEmpty()) {
                    rebuildScreenWithSearchQuery(query)
                  } else {
                    rebuildSetting(
                      settingsScreen.screenIdentifier,
                      settingsGroup.groupIdentifier,
                      settingV2.settingsIdentifier
                    )
                  }
                }
                is SettingClickAction.OpenScreen -> {
                  pushScreen(clickAction.screenIdentifier)
                  rebuildScreen(clickAction.screenIdentifier)
                }
              }.exhaustive
            }
          } else {
            settingEnabled(false)
            clickListener(null)
          }
        }
      }
      is BooleanSettingV2 -> {
        epoxyBooleanSetting {
          id("epoxy_boolean_setting_${settingV2.settingsIdentifier.getIdentifier()}")
          topDescription(settingV2.topDescription)
          bottomDescription(settingV2.bottomDescription)
          checked(settingV2.isChecked)
          bindNotificationIcon(notificationType)

          if (settingV2.isEnabled()) {
            settingEnabled(true)

            clickListener {
              settingV2.callback?.invoke()

              rebuildSetting(
                settingsScreen.screenIdentifier,
                settingsGroup.groupIdentifier,
                settingV2.settingsIdentifier
              )
            }
          } else {
            settingEnabled(false)
            clickListener(null)
          }
        }
      }
      is ListSettingV2<*> -> {
        epoxyLinkSetting {
          id("epoxy_list_setting_${settingV2.settingsIdentifier.getIdentifier()}")
          topDescription(settingV2.topDescription)
          bottomDescription(settingV2.bottomDescription)
          bindNotificationIcon(notificationType)

          if (settingV2.isEnabled()) {
            settingEnabled(true)

            clickListener {
              showListDialog(settingV2) {
                rebuildCurrentScreen()
              }
            }
          } else {
            settingEnabled(false)
            clickListener(null)
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

  private fun showListDialog(settingV2: ListSettingV2<*>, onItemClicked: () -> Unit) {
    val items = settingV2.items.mapIndexed { index, item ->
      return@mapIndexed FloatingListMenu.FloatingListMenuItem(
        index,
        settingV2.itemNameMapper(item),
        item
      )
    }

    val controller = FloatingListMenuController(
      context,
      items
    ) { clickedItem ->
      settingV2.updateSetting(clickedItem.value)
      onItemClicked()
    }

    navigationController!!.presentController(
      controller,
      true
    )
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
    graph += threadWatcherSettingsScreen.build()

    return graph
  }

  companion object {
    private const val TAG = "DeveloperSettingsControllerV2"
    private const val MIN_QUERY_LENGTH = 3
    private const val DEBOUNCE_TIME_MS = 350L
  }
}