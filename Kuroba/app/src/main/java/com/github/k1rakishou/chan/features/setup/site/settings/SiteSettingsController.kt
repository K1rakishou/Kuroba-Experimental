package com.github.k1rakishou.chan.features.setup.site.settings

import android.content.Context
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.settings.BaseSettingsController
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.epoxy.epoxyBooleanSetting
import com.github.k1rakishou.chan.features.settings.epoxy.epoxyLinkSetting
import com.github.k1rakishou.chan.features.settings.epoxy.epoxySettingsGroupTitle
import com.github.k1rakishou.chan.features.settings.setting.BooleanSetting
import com.github.k1rakishou.chan.features.settings.setting.CookieSetting
import com.github.k1rakishou.chan.features.settings.setting.InputSetting
import com.github.k1rakishou.chan.features.settings.setting.LinkSetting
import com.github.k1rakishou.chan.features.settings.setting.ListSetting
import com.github.k1rakishou.chan.features.settings.setting.MapSetting
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.ui.epoxy.epoxyDividerView
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.chan.ui.view.insets.InsetAwareEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SiteSettingsController(
  context: Context,
  private val siteDescriptor: SiteDescriptor
) : BaseSettingsController(context), SiteSettingsView {

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var compositeCatalogManager: CompositeCatalogManager

  private val presenter by lazy {
    SiteSettingsPresenter(
      siteManager = siteManager,
      boardManager = boardManager,
      compositeCatalogManager = compositeCatalogManager
    )
  }

  private lateinit var epoxyRecyclerView: InsetAwareEpoxyRecyclerView

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags()
    )

    val siteName = siteManager.bySiteDescriptorAndActive(siteDescriptor)
      ?.name
      ?: siteDescriptor.siteName

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.String(context.getString(R.string.controller_site_settings_title, siteName))
      )
    )

    view = inflate(context, R.layout.controller_site_settings)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)

    presenter.onCreate(this)
  }

  override fun onShow() {
    super.onShow()
    rebuildSettings()
  }

  override fun onDestroy() {
    super.onDestroy()

    epoxyRecyclerView.clear()

    presenter.onDestroy()
  }

  private fun rebuildSettings() {
    controllerScope.launch {
      val groups = presenter.showSiteSettings(context, siteDescriptor)
      renderSettingGroups(groups)
    }
  }

  private fun renderSettingGroups(groups: List<SettingsGroup>) {
    epoxyRecyclerView.withModels {
      var globalSettingIndex = 0

      groups.forEach { settingsGroup ->
        epoxySettingsGroupTitle {
          id("epoxy_settings_group_title_${settingsGroup.groupIdentifier.getGroupIdentifier()}")
          groupTitle(settingsGroup.groupTitle)
        }

        var groupSettingIndex = 0

        settingsGroup.iterateSettings { setting ->
          renderSettingInternal(
            setting,
            settingsGroup,
            groupSettingIndex++,
            globalSettingIndex++
          )
        }
      }
    }
  }

  private fun EpoxyController.renderSettingInternal(
    setting: SettingUiElement,
    settingsGroup: SettingsGroup,
    groupSettingIndex: Int,
    globalSettingIndex: Int
  ) {
    when (setting) {
      is LinkSetting -> {
        epoxyLinkSetting {
          id("epoxy_link_setting_${setting.settingsIdentifier.getIdentifier()}")
          topDescription(setting.topDescription)
          bottomDescription(setting.bottomDescription)
          settingEnabled(true)
          bindNotificationIcon(SettingNotificationType.Default)

          clickListener {
            controllerScope.launch { setting.callback.invoke() }
          }
        }
      }
      is ListSetting<*> -> {
        epoxyLinkSetting {
          id("epoxy_list_setting_${setting.settingsIdentifier.getIdentifier()}")
          topDescription(setting.topDescription)
          bottomDescription(setting.bottomDescription)
          bindNotificationIcon(SettingNotificationType.Default)
          settingEnabled(true)

          clickListener {
            val prev = setting.getValue()

            showListDialog(setting) { curr ->
              if (prev == curr) {
                return@showListDialog
              }

              rebuildSettings()
            }
          }
        }
      }
      is MapSetting -> {
        epoxyLinkSetting {
          id("epoxy_map_entry_setting_${setting.settingsIdentifier.getIdentifier()}")
          topDescription(setting.topDescription)
          bottomDescription(setting.bottomDescription)
          bindNotificationIcon(SettingNotificationType.Default)
          settingEnabled(true)

          clickListener { view ->
            val prev = setting.getCurrent()

            showInputDialog(
              mapSetting = setting,
              rebuildScreenFunc = { curr ->
                if (prev == curr) {
                  return@showInputDialog
                }

                rebuildSettings()
              },
              forceRebuildScreen = { rebuildSettings() }
            )
          }
        }
      }
      is InputSetting<*> -> {
        epoxyLinkSetting {
          id("epoxy_string_setting_${setting.settingsIdentifier.getIdentifier()}")
          topDescription(setting.topDescription)
          bottomDescription(setting.bottomDescription)
          bindNotificationIcon(SettingNotificationType.Default)
          settingEnabled(true)

          clickListener { view ->
            val prev = setting.getCurrent()

            showInputDialog(setting) { curr ->
              if (prev == curr) {
                return@showInputDialog
              }

              rebuildSettings()
            }
          }
        }
      }
      is BooleanSetting -> {
        epoxyBooleanSetting {
          id("epoxy_boolean_setting_${setting.settingsIdentifier.getIdentifier()}")
          topDescription(setting.topDescription)
          bottomDescription(setting.bottomDescription)
          checked(setting.isChecked)
          bindNotificationIcon(SettingNotificationType.Default)
          settingEnabled(true)

          clickListener {
            val prev = setting.isChecked
            val curr = setting.callback?.invoke()

            if (prev != curr) {
              rebuildSettings()
            }
          }
        }
      }
      is CookieSetting -> {
        epoxyLinkSetting {
          id("epoxy_cookie_setting_${setting.settingsIdentifier.getIdentifier()}")
          topDescription(setting.topDescription)
          bottomDescription(setting.bottomDescription)
          bindNotificationIcon(SettingNotificationType.Default)

          if (setting.isEnabled()) {
            settingEnabled(true)

            clickListener {
              val prev = setting.getCurrent()?.value

              showInputDialog(setting) { curr ->
                if (prev == curr) {
                  return@showInputDialog
                }

                rebuildSettings()
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

  override suspend fun showErrorToast(message: String) {
    withContext(Dispatchers.Main) { showToast(message) }
  }

  override fun pushController(controller: Controller) {
    navigationController!!.pushController(controller)
  }

}