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
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager
import com.github.adamantcheese.chan.features.settings.epoxy.epoxyBooleanSetting
import com.github.adamantcheese.chan.features.settings.epoxy.epoxyLinkSetting
import com.github.adamantcheese.chan.features.settings.epoxy.epoxyNoSettingsFoundView
import com.github.adamantcheese.chan.features.settings.epoxy.epoxySettingsGroupTitle
import com.github.adamantcheese.chan.features.settings.setting.*
import com.github.adamantcheese.chan.ui.controller.FloatingListMenuController
import com.github.adamantcheese.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.adamantcheese.chan.ui.controller.navigation.ToolbarNavigationController.ToolbarSearchCallback
import com.github.adamantcheese.chan.ui.epoxy.epoxyDividerView
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage
import com.github.adamantcheese.chan.ui.settings.SettingNotificationType
import com.github.adamantcheese.chan.ui.view.floating_menu.FloatingListMenu
import com.github.adamantcheese.chan.utils.AndroidUtils.*
import com.github.adamantcheese.common.exhaustive
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import javax.inject.Inject

class MainSettingsControllerV2(context: Context)
  : Controller(context),
  ToolbarSearchCallback {

  @Inject
  lateinit var settingsNotificationManager: SettingsNotificationManager

  lateinit var recyclerView: EpoxyRecyclerView
  lateinit var settingsCoordinator: SettingsCoordinator

  private val defaultScreen = MainScreen

  private var hasPendingRestart = false
  private var hasPendingUiRefresh = false

  // TODO(KurobaEx): Implement Save/Restore recycler position.
  //  See https://github.com/K1rakishou/MvRxTest/blob/master/app/src/main/java/com/kirakishou/mvrxtest/ui/MainFragment.kt#L58

  @OptIn(FlowPreview::class)
  override fun onCreate() {
    super.onCreate()
    Chan.inject(this)

    view = inflate(context, R.layout.controller_settings_main)
    recyclerView = view.findViewById(R.id.settings_recycler_view)

    navigation.buildMenu()
      .withItem(R.drawable.ic_search_white_24dp) {
        (navigationController as ToolbarNavigationController).showSearch()
      }
      .build()

    navigation.swipeable = false

    settingsCoordinator = SettingsCoordinator(context, navigationController!!)
    settingsCoordinator.onCreate()
    settingsCoordinator.rebuildScreen(defaultScreen, BuildOptions.Default)

    mainScope.launch {
      settingsCoordinator.listenForRenderScreenActions()
        .asFlow()
        .collect { renderAction ->
          renderScreen(renderAction)
        }
    }
  }

  private fun renderScreen(renderAction: SettingsCoordinator.RenderAction) {
    recyclerView.withModels {
      when (renderAction) {
        is SettingsCoordinator.RenderAction.RenderScreen -> {
          navigation.title = renderAction.settingsScreen.title
          (navigationController as ToolbarNavigationController).toolbar!!.updateTitle(navigation)

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

  override fun onDestroy() {
    super.onDestroy()

    settingsCoordinator.onDestroy()
    restartAppOrRefreshUiIfNecessary()
  }

  override fun onSearchVisibilityChanged(visible: Boolean) {
    if (!visible) {
      settingsCoordinator.rebuildCurrentScreen(BuildOptions.Default)
    }
  }

  override fun onSearchEntered(entered: String?) {
    settingsCoordinator.onSearchEntered(entered ?: "")
  }

  override fun onBack(): Boolean {
    return settingsCoordinator.onBack()
  }

  private fun updateRestartRefreshButton(settingV2: SettingV2) {
    if (settingV2.requiresRestart) {
      hasPendingRestart = true
    } else if (settingV2.requiresUiRefresh) {
      hasPendingUiRefresh = true
    }
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

  private fun EpoxyController.renderSearchScreen(
    topScreenIdentifier: IScreenIdentifier?,
    graph: SettingsGraph,
    query: String
  ) {
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
                      settingV2.settingsIdentifier
                    )
                  }
                }
                is SettingClickAction.OpenScreen -> {
                  settingsCoordinator.rebuildScreen(clickAction.screenIdentifier, BuildOptions.Default)
                }
                is SettingClickAction.ShowToast -> {
                  showToast(context, clickAction.messageId)
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
      }
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

  private fun showInputDialog(
    view: View,
    inputSettingV2: InputSettingV2<*>,
    rebuildScreenFunc: (Any?) -> Unit
  ) {
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

  companion object {
    private const val TAG = "MainSettingsControllerV2"
  }
}