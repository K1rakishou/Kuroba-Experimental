package com.github.adamantcheese.chan.features.settings

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.StartActivity
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.manager.*
import com.github.adamantcheese.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder
import com.github.adamantcheese.chan.features.settings.epoxy.epoxyBooleanSetting
import com.github.adamantcheese.chan.features.settings.epoxy.epoxyLinkSetting
import com.github.adamantcheese.chan.features.settings.epoxy.epoxyNoSettingsFoundView
import com.github.adamantcheese.chan.features.settings.epoxy.epoxySettingsGroupTitle
import com.github.adamantcheese.chan.features.settings.screens.*
import com.github.adamantcheese.chan.features.settings.setting.*
import com.github.adamantcheese.chan.ui.controller.FloatingListMenuController
import com.github.adamantcheese.chan.ui.controller.navigation.RequiresNoBottomNavBar
import com.github.adamantcheese.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.adamantcheese.chan.ui.controller.navigation.ToolbarNavigationController.ToolbarSearchCallback
import com.github.adamantcheese.chan.ui.epoxy.epoxyDividerView
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage
import com.github.adamantcheese.chan.ui.settings.SettingNotificationType
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.ui.view.floating_menu.FloatingListMenu
import com.github.adamantcheese.chan.ui.widget.CancellableToast
import com.github.adamantcheese.chan.utils.AndroidUtils.*
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.addOneshotModelBuildListener
import com.github.adamantcheese.chan.utils.exhaustive
import com.github.adamantcheese.model.repository.InlinedFileInfoRepository
import com.github.adamantcheese.model.repository.MediaServiceLinkExtraContentRepository
import com.github.adamantcheese.model.repository.SeenPostRepository
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.util.*
import javax.inject.Inject

class MainSettingsControllerV2(context: Context)
  : Controller(context),
  ToolbarSearchCallback,
  MainSettingsControllerV2RebuildCallbacks,
  RequiresNoBottomNavBar {

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
  lateinit var threadSaveManager: ThreadSaveManager
  @Inject
  lateinit var watchManager: WatchManager

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

  private val appearanceSettingsScreen by lazy {
    AppearanceSettingsScreen(
      context,
      navigationController!!,
      themeHelper
    )
  }

  private val behaviorSettingsScreen by lazy {
    BehaviourSettingsScreen(
      context,
      navigationController!!,
      databaseManager
    )
  }

  private val experimentalSettingsScreen by lazy {
    ExperimentalSettingsScreen(
      context,
      navigationController!!,
      exclusionZonesHolder
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

  private val importExportSettingsScreen by lazy {
    ImportExportSettingsScreen(
      context,
      navigationController!!,
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
      navigationController!!,
      fileManager,
      fileChooser,
      databaseManager,
      threadSaveManager,
      watchManager,
      runtimePermissionsHelper
    )
  }

  private val settingsGraphDelegate = lazy { buildSettingsGraph() }
  private val settingsGraph by settingsGraphDelegate

  private val screenStack = Stack<IScreenIdentifier>()
  private val onSearchEnteredSubject = BehaviorProcessor.create<String>()
  private val defaultScreen = MainScreen

  private val cancellableToast = CancellableToast()
  private var hasPendingRestart = false
  private var hasPendingUiRefresh = false

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
    navigation.swipeable = false

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
        .catch { error -> Logger.e(TAG, "Unknown error received from SettingsNotificationManager", error) }
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

    rebuildDefaultScreen()
  }

  private fun restartAppOrRefreshUiIfNecessary() {
    if (hasPendingRestart) {
      (context as StartActivity).restartApp()
    } else if (hasPendingUiRefresh) {
      postToEventBus(RefreshUIMessage("SettingsController refresh"))
      hasPendingUiRefresh = false
      cancellableToast.showToast(context, "UI refreshed")
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    mainSettingsScreen.onDestroy()
    developerSettingsScreen.onDestroy()
    databaseSummaryScreen.onDestroy()
    threadWatcherSettingsScreen.onDestroy()
    appearanceSettingsScreen.onDestroy()
    behaviorSettingsScreen.onDestroy()
    experimentalSettingsScreen.onDestroy()
    importExportSettingsScreen.onDestroy()
    mediaSettingsScreen.onDestroy()

    if (settingsGraphDelegate.isInitialized()) {
      settingsGraph.clear()
    }

    screenStack.clear()
    restartAppOrRefreshUiIfNecessary()
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
      screenStack.clear()
      Logger.d(TAG, "onBack() screenStack.size <= 1, exiting")
      return false
    }

    rebuildScreen(popScreen())
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
    settingsGraph.rebuildScreens()
    val graph = settingsGraph

    recyclerView.withModels {
      addOneshotModelBuildListener {
        recyclerView.scrollToPosition(0)
      }

      renderSearchScreen(graph, query)
    }
  }

  private fun rebuildScreen(screen: IScreenIdentifier) {
    settingsGraph.rebuildScreen(screen)
    val settingsScreen = settingsGraph[screen]

    recyclerView.withModels {
      addOneshotModelBuildListener {
        recyclerView.scrollToPosition(0)
      }

      updateTitle(settingsScreen)
      renderScreen(settingsScreen)
    }
  }

  override fun rebuildSetting(
    screenIdentifier: IScreenIdentifier,
    groupIdentifier: IGroupIdentifier,
    settingIdentifier: SettingsIdentifier
  ) {
    val settingsScreen = settingsGraph[screenIdentifier]
      .apply { rebuildSetting(groupIdentifier, settingIdentifier) }

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
    var globalSettingIndex = 0

    val isDefaultScreen = (topScreenIdentifier != null
      && topScreenIdentifier.getScreenIdentifier() == defaultScreen.getScreenIdentifier())

    graph.iterateScreens { settingsScreen ->
      val isCurrentScreen =
        settingsScreen.screenIdentifier.getScreenIdentifier() != topScreenIdentifier?.getScreenIdentifier()

      if (!isDefaultScreen && isCurrentScreen) {
        return@iterateScreens
      }

      var groupSettingIndex = 0

      settingsScreen.iterateGroups { settingsGroup ->
        settingsGroup.iterateSettingsFilteredByQuery(query) { setting ->
          foundSomething = true
          renderSettingInternal(
            setting,
            settingsScreen,
            settingsGroup,
            groupSettingIndex++,
            globalSettingIndex++,
            query
          )
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
    var globalSettingIndex = 0

    settingsScreen.iterateGroups { settingsGroup ->
      epoxySettingsGroupTitle {
        id("epoxy_settings_group_title_${settingsGroup.groupIdentifier.getGroupIdentifier()}")
        groupTitle(settingsGroup.groupTitle)
      }

      var groupSettingIndex = 0

      settingsGroup.iterateGroups { setting ->
        renderSettingInternal(
          setting,
          settingsScreen,
          settingsGroup,
          groupSettingIndex++,
          globalSettingIndex++,
          null
        )
      }
    }
  }

  private fun EpoxyController.renderSettingInternal(
    settingV2: SettingV2,
    settingsScreen: SettingsScreen,
    settingsGroup: SettingsGroup,
    groupSettingIndex: Int,
    globalSettingIndex: Int,
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
              val prev = settingV2.isChecked
              val curr = settingV2.callback?.invoke()

              if (prev != curr) {
                updateRestartRefreshButton(settingV2)
              }

              if (!query.isNullOrEmpty()) {
                rebuildScreenWithSearchQuery(query)
              } else {
                rebuildCurrentScreen()
              }
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
              val prev = settingV2.getValue()

              showListDialog(settingV2) { curr ->
                if (prev != curr) {
                  updateRestartRefreshButton(settingV2)
                }

                if (!query.isNullOrEmpty()) {
                  rebuildScreenWithSearchQuery(query)
                } else {
                  rebuildCurrentScreen()
                }
              }
            }
          } else {
            settingEnabled(false)
            clickListener(null)
          }
        }
      }
      is InputSettingV2<*> -> {
        epoxyLinkSetting {
          id("epoxy_string_setting_${settingV2.settingsIdentifier.getIdentifier()}")
          topDescription(settingV2.topDescription)
          bottomDescription(settingV2.bottomDescription)
          bindNotificationIcon(notificationType)

          if (settingV2.isEnabled()) {
            settingEnabled(true)

            clickListener { view ->
              val prev = settingV2.getCurrent()

              showInputDialog(view, settingV2) { curr ->
                if (prev != curr) {
                  updateRestartRefreshButton(settingV2)
                }

                if (!query.isNullOrEmpty()) {
                  rebuildScreenWithSearchQuery(query)
                } else {
                  rebuildCurrentScreen()
                }
              }
            }
          } else {
            settingEnabled(false)
            clickListener(null)
          }
        }
      }
    }

    if (groupSettingIndex != settingsGroup.lastIndex()) {
      epoxyDividerView {
        id("epoxy_divider_${globalSettingIndex}")
      }
    }
  }

  private fun updateRestartRefreshButton(settingV2: SettingV2) {
    if (settingV2.requiresRestart) {
      hasPendingRestart = true
    } else if (settingV2.requiresUiRefresh) {
      hasPendingUiRefresh = true
    }
  }

  private fun showListDialog(settingV2: ListSettingV2<*>, onItemClicked: (Any?) -> Unit) {
    val items = settingV2.items.mapIndexed { index, item ->
      return@mapIndexed FloatingListMenu.FloatingListMenuItem(
        key = index,
        name = settingV2.itemNameMapper(item),
        value = item,
        isCurrentlySelected = settingV2.isCurrent(item)
      )
    }

    val controller = FloatingListMenuController(
      context = context,
      items = items,
      itemClickListener = { clickedItem ->
        settingV2.updateSetting(clickedItem.value)
        onItemClicked(clickedItem.value)
      })

    navigationController!!.presentController(
      controller,
      true
    )
  }

  private fun showInputDialog(view: View, inputSettingV2: InputSettingV2<*>, rebuildScreenFunc: (Any?) -> Unit) {
    val container = LinearLayout(view.context)
    container.setPadding(dp(24f), dp(8f), dp(24f), 0)

    val editText = EditText(view.context).apply {
      imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
      isSingleLine = true
      setText(inputSettingV2.getCurrent().toString())

      inputType = when (inputSettingV2.inputType) {
        InputSettingV2.InputType.String -> InputType.TYPE_CLASS_TEXT
        InputSettingV2.InputType.Integer -> InputType.TYPE_CLASS_NUMBER
        null -> throw IllegalStateException("InputType is null")
      }.exhaustive

      setSelection(text.length)
      requestFocus()
    }

    container.addView(
      editText,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    )

    val dialog: AlertDialog = AlertDialog.Builder(view.context)
      .setTitle(inputSettingV2.topDescription)
      .setView(container)
      .setPositiveButton(R.string.ok) { _, _ ->
        onInputValueEntered(inputSettingV2, editText, rebuildScreenFunc)
      }
      .setNegativeButton(R.string.cancel, null)
      .create()

    dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dialog.show()
  }

  @Suppress("FoldInitializerAndIfToElvis")
  private fun onInputValueEntered(
    inputSettingV2: InputSettingV2<*>,
    editText: EditText,
    rebuildScreenFunc: (Any?) -> Unit
  ) {
    when (inputSettingV2.inputType) {
      InputSettingV2.InputType.String -> {
        val input = editText.text.toString()

        val text = if (input.isNotEmpty()) {
          input
        } else {
          inputSettingV2.getDefault()?.toString()
        }

        if (text == null) {
          return
        }

        inputSettingV2.updateSetting(text)
      }
      InputSettingV2.InputType.Integer -> {
        val input = editText.text.toString()

        val integer = if (input.isNotEmpty()) {
          input.toIntOrNull()
        } else {
          inputSettingV2.getDefault() as? Int
        }

        if (integer == null) {
          return
        }

        inputSettingV2.updateSetting(integer)
      }
      null -> throw IllegalStateException("InputType is null")
    }.exhaustive

    rebuildScreenFunc(inputSettingV2.getCurrent())
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

  companion object {
    private const val TAG = "MainSettingsControllerV2"
    private const val MIN_QUERY_LENGTH = 3
    private const val DEBOUNCE_TIME_MS = 350L
  }
}