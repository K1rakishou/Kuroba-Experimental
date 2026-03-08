package com.github.k1rakishou.chan.features.settings

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager
import com.github.k1rakishou.chan.features.settings.epoxy.epoxyBooleanSetting
import com.github.k1rakishou.chan.features.settings.epoxy.epoxyLinkSetting
import com.github.k1rakishou.chan.features.settings.epoxy.epoxyNoSettingsFoundView
import com.github.k1rakishou.chan.features.settings.epoxy.epoxySettingsGroupTitle
import com.github.k1rakishou.chan.features.settings.setting.BooleanSetting
import com.github.k1rakishou.chan.features.settings.setting.CookieSetting
import com.github.k1rakishou.chan.features.settings.setting.InputSetting
import com.github.k1rakishou.chan.features.settings.setting.LinkSetting
import com.github.k1rakishou.chan.features.settings.setting.ListSetting
import com.github.k1rakishou.chan.features.settings.setting.RangeSetting
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.ui.epoxy.epoxyDividerView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.helper.AppSettingsUpdateAppRefreshHelper
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.chan.ui.view.insets.InsetAwareEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.addOneshotModelBuildListener
import com.github.k1rakishou.common.exhaustive
import dagger.Lazy
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class MainSettingsController(
  context: Context
) : BaseSettingsController(context) {

  @Inject
  lateinit var settingsNotificationManager: SettingsNotificationManager
  @Inject
  lateinit var appSettingsUpdateAppRefreshHelper: Lazy<AppSettingsUpdateAppRefreshHelper>
  @Inject
  lateinit var appRestarter: AppRestarter

  lateinit var epoxyRecyclerView: InsetAwareEpoxyRecyclerView

  private val settingsCoordinator by lazy { SettingsCoordinator(context, requireNavController()) }
  private val defaultScreen = MainScreen

  private var hasPendingRestart = false
  private var hasPendingUiRefresh = false

  private val scrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      super.onScrollStateChanged(recyclerView, newState)

      if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        storeRecyclerPositionForCurrentScreen()
      }
    }
  }

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    view = inflate(context, R.layout.controller_settings_main)
    epoxyRecyclerView = view.findViewById(R.id.settings_recycler_view)
    epoxyRecyclerView.itemAnimator = null

    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags(
        hasBack = false,
        hasDrawer = true
      )
    )

    settingsCoordinator.onCreate()

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = {
          if (settingsCoordinator.onBack()) {
            return@BackArrowMenuItem
          }

          requireNavController().popController()
        }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.from(com.github.k1rakishou.chan.R.string.loading)
      ),
      menuBuilder = {
        withMenuItem(
          id = ACTION_SEARCH,
          drawableId = R.drawable.ic_search_white_24dp,
          onClick = { toolbarState.enterSearchMode() }
        )
      }
    )

    compositeDisposable.add(
      settingsCoordinator.listenForRenderScreenActions()
        .subscribe { renderAction -> renderScreen(renderAction) }
    )

    settingsCoordinator.rebuildScreen(
      screenIdentifier = defaultScreen,
      buildOptions = BuildOptions.Default,
      isFirstRebuild = true
    )

    epoxyRecyclerView.addOnScrollListener(scrollListener)

    controllerScope.launch {
      toolbarState.search.listenForSearchVisibilityUpdates()
        .onEach { searchVisible ->
          if (!searchVisible) {
            settingsCoordinator.rebuildCurrentScreen(BuildOptions.Default)
          }
        }
        .collect()
    }

    controllerScope.launch {
      toolbarState.search.listenForSearchQueryUpdates()
        .onEach { searchQuery -> settingsCoordinator.onSearchEntered(searchQuery) }
        .collect()
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    requireBottomPanelContract().hideBottomPanel(controllerKey)

    epoxyRecyclerView.removeOnScrollListener(scrollListener)
    epoxyRecyclerView.clear()

    settingsCoordinator.onDestroy()
    restartAppOrRefreshUiIfNecessary()
  }

  override fun onBack(): Boolean {
    return settingsCoordinator.onBack()
  }

  private fun renderScreen(renderAction: SettingsCoordinator.RenderAction) {
    epoxyRecyclerView.withModels {
      addOneshotModelBuildListener {
        if (renderAction !is SettingsCoordinator.RenderAction.RenderScreen) {
          return@addOneshotModelBuildListener
        }

        val indexAndTop = settingsCoordinator.getCurrentIndexAndTopOrNull()
        val llm = (epoxyRecyclerView.layoutManager as? LinearLayoutManager)

        if (indexAndTop != null) {
          llm?.scrollToPositionWithOffset(indexAndTop.index, indexAndTop.top)
        } else {
          llm?.scrollToPositionWithOffset(0, 0)
        }
      }

      when (renderAction) {
        is SettingsCoordinator.RenderAction.Loading -> {
          epoxyLoadingView {
            id("epoxy_settings_loading_view")
          }
        }
        is SettingsCoordinator.RenderAction.RenderScreen -> {
          toolbarState.default.updateTitle(
            newTitle = ToolbarText.String(renderAction.settingsScreen.title)
          )

          renderScreen(renderAction.settingsScreen)
        }
        is SettingsCoordinator.RenderAction.RenderSearchScreen -> {
          renderSearchScreen(
            renderAction.topScreenIdentifier,
            renderAction.graph,
            renderAction.query
          )
        }
      }
    }
  }

  private fun updateRestartRefreshButton(setting: SettingUiElement) {
    if (setting.requiresRestart) {
      showToast(context.getString(R.string.the_app_will_be_restarted))
      hasPendingRestart = true
    } else if (setting.requiresUiRefresh) {
      hasPendingUiRefresh = true
    }
  }

  private fun restartAppOrRefreshUiIfNecessary() {
    if (hasPendingRestart) {
      appRestarter.restart()
    } else if (hasPendingUiRefresh) {
      hasPendingUiRefresh = false
      appSettingsUpdateAppRefreshHelper.get().settingsUpdated()
    }
  }

  private fun EpoxyController.renderSearchScreen(
    topScreenIdentifier: IScreenIdentifier?,
    graph: SettingsGraph,
    query: String
  ) {
    var foundSomething = false
    var globalSettingIndex = 0

    val isDefaultScreen = (topScreenIdentifier != null
      && topScreenIdentifier.screenIdentifier() == defaultScreen.screenIdentifier())

    graph.iterateScreens { settingsScreen ->
      val isCurrentScreen =
        settingsScreen.screenIdentifier.screenIdentifier() != topScreenIdentifier?.screenIdentifier()

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

      settingsGroup.iterateSettings { setting ->
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
    setting: SettingUiElement,
    settingsScreen: SettingsScreen,
    settingsGroup: SettingsGroup,
    groupSettingIndex: Int,
    globalSettingIndex: Int,
    query: String?
  ) {
    val notificationType = if (settingsNotificationManager.contains(setting.notificationType)) {
      setting.notificationType!!
    } else {
      SettingNotificationType.Default
    }

    when (setting) {
      is LinkSetting -> {
        epoxyLinkSetting {
          id("epoxy_link_setting_${setting.settingsIdentifier.getIdentifier()}")
          topDescription(setting.topDescription)
          bottomDescription(setting.bottomDescription)
          bindNotificationIcon(notificationType)

          if (setting.isEnabled()) {
            settingEnabled(true)

            clickListener {
              postThrowable {
                when (val clickAction = setting.callback.invoke()) {
                  SettingClickAction.NoAction -> {
                    // no-op
                  }
                  SettingClickAction.RefreshClickedSetting -> {
                    if (!query.isNullOrEmpty()) {
                      settingsCoordinator.rebuildScreenWithSearchQuery(query, BuildOptions.Default)
                    } else {
                      settingsCoordinator.rebuildSetting(
                        settingsScreen.screenIdentifier,
                        settingsGroup.groupIdentifier,
                        setting.settingsIdentifier
                      )
                    }
                  }
                  is SettingClickAction.OpenScreen -> {
                    storeRecyclerPositionForCurrentScreen()
                    settingsCoordinator.rebuildScreen(clickAction.screenIdentifier, BuildOptions.Default)
                  }
                  is SettingClickAction.ShowToast -> {
                    showToast(clickAction.messageId)
                  }
                }.exhaustive
              }
            }
          } else {
            settingEnabled(false)
            clickListener(null)
          }
        }
      }
      is BooleanSetting -> {
        epoxyBooleanSetting {
          id("epoxy_boolean_setting_${setting.settingsIdentifier.getIdentifier()}")
          topDescription(setting.topDescription)
          bottomDescription(setting.bottomDescription)
          checked(setting.isChecked)
          bindNotificationIcon(notificationType)

          if (setting.isEnabled()) {
            settingEnabled(true)

            clickListener {
              val prev = setting.isChecked
              val curr = setting.callback?.invoke()

              if (prev != curr) {
                updateRestartRefreshButton(setting)
              }

              if (!query.isNullOrEmpty()) {
                settingsCoordinator.rebuildScreenWithSearchQuery(query, BuildOptions.Default)
              } else {
                settingsCoordinator.rebuildCurrentScreen(BuildOptions.Default)
              }
            }
          } else {
            settingEnabled(false)
            clickListener(null)
          }
        }
      }
      is ListSetting<*> -> {
        epoxyLinkSetting {
          id("epoxy_list_setting_${setting.settingsIdentifier.getIdentifier()}")
          topDescription(setting.topDescription)
          bottomDescription(setting.bottomDescription)
          bindNotificationIcon(notificationType)

          if (setting.isEnabled()) {
            settingEnabled(true)

            clickListener {
              val prev = setting.getValue()

              showListDialog(setting) { curr ->
                if (prev != curr) {
                  updateRestartRefreshButton(setting)
                }

                if (!query.isNullOrEmpty()) {
                  settingsCoordinator.rebuildScreenWithSearchQuery(query, BuildOptions.Default)
                } else {
                  settingsCoordinator.rebuildCurrentScreen(BuildOptions.Default)
                }
              }
            }
          } else {
            settingEnabled(false)
            clickListener(null)
          }
        }
      }
      is InputSetting<*> -> {
        epoxyLinkSetting {
          id("epoxy_string_setting_${setting.settingsIdentifier.getIdentifier()}")
          topDescription(setting.topDescription)
          bottomDescription(setting.bottomDescription)
          bindNotificationIcon(notificationType)

          if (setting.isEnabled()) {
            settingEnabled(true)

            clickListener { _ ->
              val prev = setting.getCurrent()

              showInputDialog(setting) { curr ->
                if (prev != curr) {
                  updateRestartRefreshButton(setting)
                }

                if (!query.isNullOrEmpty()) {
                  settingsCoordinator.rebuildScreenWithSearchQuery(query, BuildOptions.Default)
                } else {
                  settingsCoordinator.rebuildCurrentScreen(BuildOptions.Default)
                }
              }
            }
          } else {
            settingEnabled(false)
            clickListener(null)
          }
        }
      }
      is RangeSetting -> {
        epoxyLinkSetting {
          id("epoxy_range_setting_${setting.settingsIdentifier.getIdentifier()}")
          topDescription(setting.topDescription)
          bottomDescription(setting.bottomDescription)
          currentValue(setting.currentValueText)
          bindNotificationIcon(notificationType)

          if (setting.isEnabled()) {
            settingEnabled(true)

            clickListener {
              val prev = setting.current

              showUpdateRangeSettingDialog(setting) { curr ->
                if (prev != curr) {
                  updateRestartRefreshButton(setting)
                }

                if (!query.isNullOrEmpty()) {
                  settingsCoordinator.rebuildScreenWithSearchQuery(query, BuildOptions.Default)
                } else {
                  settingsCoordinator.rebuildCurrentScreen(BuildOptions.Default)
                }
              }
            }
          } else {
            settingEnabled(false)
            clickListener(null)
          }
        }
      }
      is CookieSetting -> {
        epoxyLinkSetting {
          id("epoxy_cookie_setting_${setting.settingsIdentifier.getIdentifier()}")
          topDescription(setting.topDescription)
          bottomDescription(setting.bottomDescription)
          currentValue(setting.getCurrent()?.value)
          bindNotificationIcon(notificationType)

          if (setting.isEnabled()) {
            settingEnabled(true)

            clickListener {
              val prev = setting.getCurrent()?.value

              showInputDialog(setting) { curr ->
                if (prev != curr) {
                  updateRestartRefreshButton(setting)
                }

                if (!query.isNullOrEmpty()) {
                  settingsCoordinator.rebuildScreenWithSearchQuery(query, BuildOptions.Default)
                } else {
                  settingsCoordinator.rebuildCurrentScreen(BuildOptions.Default)
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
        updateMargins(null)
      }
    }
  }

  private fun postThrowable(func: suspend () -> Unit) {
    settingsCoordinator.post {
      try {
        func()
      } catch (error: Throwable) {
        throw RuntimeException(error)
      }
    }
  }

  private fun storeRecyclerPositionForCurrentScreen() {
    settingsCoordinator.storeRecyclerPositionForCurrentScreen(epoxyRecyclerView)
  }

  companion object {
    private const val TAG = "MainSettingsControllerV2"

    private const val ACTION_SEARCH = 0
  }
}