package com.github.k1rakishou.chan.features.setup

import android.content.Context
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.features.settings.BaseSettingsController
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.epoxy.epoxyBooleanSetting
import com.github.k1rakishou.chan.features.settings.epoxy.epoxyLinkSetting
import com.github.k1rakishou.chan.features.settings.epoxy.epoxySettingsGroupTitle
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.InputSettingV2
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.features.settings.setting.ListSettingV2
import com.github.k1rakishou.chan.features.settings.setting.MapSettingV2
import com.github.k1rakishou.chan.features.settings.setting.SettingV2
import com.github.k1rakishou.chan.ui.epoxy.epoxyDividerView
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SiteSettingsController(
  context: Context,
  private val siteDescriptor: SiteDescriptor
) : BaseSettingsController(context), SiteSettingsView, WindowInsetsListener {

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

  private lateinit var epoxyRecyclerView: EpoxyRecyclerView

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    val siteName = siteManager.bySiteDescriptor(siteDescriptor)
      ?.name()
      ?: siteDescriptor.siteName

    navigation.title = context.getString(R.string.controller_site_settings_title, siteName)

    view = inflate(context, R.layout.controller_site_settings)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)

    onInsetsChanged()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    presenter.onCreate(this)
  }

  override fun onShow() {
    super.onShow()
    rebuildSettings()
  }

  override fun onDestroy() {
    super.onDestroy()

    epoxyRecyclerView.clear()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    presenter.onDestroy()
  }

  override fun onInsetsChanged() {
    val bottomPaddingDp = calculateBottomPaddingForRecyclerInDp(
      globalWindowInsetsManager = globalWindowInsetsManager,
      mainControllerCallbacks = null
    )

    epoxyRecyclerView.updatePaddings(bottom = dp(bottomPaddingDp.toFloat()))
  }

  private fun rebuildSettings() {
    mainScope.launch {
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
    settingV2: SettingV2,
    settingsGroup: SettingsGroup,
    groupSettingIndex: Int,
    globalSettingIndex: Int
  ) {
    when (settingV2) {
      is LinkSettingV2 -> {
        epoxyLinkSetting {
          id("epoxy_link_setting_${settingV2.settingsIdentifier.getIdentifier()}")
          topDescription(settingV2.topDescription)
          bottomDescription(settingV2.bottomDescription)
          settingEnabled(true)
          bindNotificationIcon(SettingNotificationType.Default)

          clickListener {
            mainScope.launch { settingV2.callback.invoke() }
          }
        }
      }
      is ListSettingV2<*> -> {
        epoxyLinkSetting {
          id("epoxy_list_setting_${settingV2.settingsIdentifier.getIdentifier()}")
          topDescription(settingV2.topDescription)
          bottomDescription(settingV2.bottomDescription)
          bindNotificationIcon(SettingNotificationType.Default)
          settingEnabled(true)

          clickListener {
            val prev = settingV2.getValue()

            showListDialog(settingV2) { curr ->
              if (prev == curr) {
                return@showListDialog
              }

              rebuildSettings()
            }
          }
        }
      }
      is MapSettingV2 -> {
        epoxyLinkSetting {
          id("epoxy_map_entry_setting_${settingV2.settingsIdentifier.getIdentifier()}")
          topDescription(settingV2.topDescription)
          bottomDescription(settingV2.bottomDescription)
          bindNotificationIcon(SettingNotificationType.Default)
          settingEnabled(true)

          clickListener { view ->
            val prev = settingV2.getCurrent()

            showInputDialog(
              mapSettingV2 = settingV2,
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
      is InputSettingV2<*> -> {
        epoxyLinkSetting {
          id("epoxy_string_setting_${settingV2.settingsIdentifier.getIdentifier()}")
          topDescription(settingV2.topDescription)
          bottomDescription(settingV2.bottomDescription)
          bindNotificationIcon(SettingNotificationType.Default)
          settingEnabled(true)

          clickListener { view ->
            val prev = settingV2.getCurrent()

            showInputDialog(settingV2) { curr ->
              if (prev == curr) {
                return@showInputDialog
              }

              rebuildSettings()
            }
          }
        }
      }
      is BooleanSettingV2 -> {
        epoxyBooleanSetting {
          id("epoxy_boolean_setting_${settingV2.settingsIdentifier.getIdentifier()}")
          topDescription(settingV2.topDescription)
          bottomDescription(settingV2.bottomDescription)
          checked(settingV2.isChecked)
          bindNotificationIcon(SettingNotificationType.Default)
          settingEnabled(true)

          clickListener {
            val prev = settingV2.isChecked
            val curr = settingV2.callback?.invoke()

            if (prev != curr) {
              rebuildSettings()
            }
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

  override fun openControllerWrappedIntoBottomNavAwareController(controller: Controller) {
    requireStartActivity().openControllerWrappedIntoBottomNavAwareController(controller)
    requireStartActivity().setSettingsMenuItemSelected()
  }

}