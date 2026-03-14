package com.github.k1rakishou.chan.features.settings

import android.content.Context
import android.os.Parcelable
import androidx.annotation.GuardedBy
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.room.concurrent.AtomicBoolean
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.settings.screen.SettingActions
import com.github.k1rakishou.chan.features.settings.screen.SettingActions.OpenScreenRequest
import com.github.k1rakishou.chan.features.settings.screen.SettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.SiteSettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElementGroup
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.settings.SettingNotification
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.KurobaSettings
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.parcelize.Parcelize

@Stable
class AppSettingsGraph(
  private val kurobaSettings: KurobaSettings,
  private val appResources: AppResources,
  private val siteManager: SiteManager,
  private val settingsNotificationManager: SettingsNotificationManager,
  private val builders: Map<SettingsScreenKey, SettingsScreenBuilder>,
  private val siteSettingsScreenBuilder: SiteSettingsScreenBuilder
) {
  private val _mutex = Mutex()
  @GuardedBy("_mutex")
  private val _root = mutableMapOf<SettingsScreenKey, SettingsScreen>()
  private val _builtAppSettings = AtomicBoolean(false)
  private val _buildSiteSettings = mutableSetOf<SiteDescriptor>()
  private val _settingActions = SettingActions()
  private val _coroutineScope = KurobaCoroutineScope()

  private val _settingsSeenByUser = mutableSetOf<String>()

  private val _settingBadges = mutableStateMapOf<String, List<SettingUiElement.Badge>>()
  val settingBadges: SnapshotStateMap<String, List<SettingUiElement.Badge>>
    get() = _settingBadges

  val openScreenRequests: SharedFlow<OpenScreenRequest>
    get() = _settingActions.openScreenRequests
  val pushControllerRequests: SharedFlow<Controller>
    get() = _settingActions.pushControllerRequests
  val presentControllerRequests: SharedFlow<Controller>
    get() = _settingActions.presentControllerRequests
  val showToastRequests: SharedFlow<String>
    get() = _settingActions.showToastRequests

  fun init() {
    _coroutineScope.launch {
      settingsNotificationManager.notificationUpdates
        .collectLatest { updateBadgesFromNotifications() }
    }
  }

  fun destroy() {
    _coroutineScope.cancel()
  }

  suspend fun build(context: Context) {
    if (_builtAppSettings.get()) {
      return
    }

    _mutex.withLock {
      if (_builtAppSettings.compareAndSet(false, true)) {
        buildScreen(
          key = SettingsScreenKey.Main,
          title = appResources.string(R.string.main_settings_screen),
          builder = { screenKey -> requireBuilder(screenKey).build(context, _settingActions, this@buildScreen) }
        )

        buildScreen(
          key = SettingsScreenKey.Watchers,
          title = appResources.string(R.string.settings_watch),
          builder = { screenKey -> requireBuilder(screenKey).build(context, _settingActions, this@buildScreen) }
        )

        buildScreen(
          key = SettingsScreenKey.Appearance,
          title = appResources.string(R.string.settings_appearance),
          builder = { screenKey -> requireBuilder(screenKey).build(context, _settingActions, this@buildScreen) }
        )

        buildScreen(
          key = SettingsScreenKey.Behavior,
          title = appResources.string(R.string.settings_screen_behavior),
          builder = { screenKey -> requireBuilder(screenKey).build(context, _settingActions, this@buildScreen) }
        )

        buildScreen(
          key = SettingsScreenKey.Media,
          title = appResources.string(R.string.settings_screen_media),
          builder = { screenKey -> requireBuilder(screenKey).build(context, _settingActions, this@buildScreen) }
        )

        buildScreen(
          key = SettingsScreenKey.ImportExport,
          title = appResources.string(R.string.settings_import_export),
          builder = { screenKey -> requireBuilder(screenKey).build(context, _settingActions, this@buildScreen) }
        )

        buildScreen(
          key = SettingsScreenKey.Security,
          title = appResources.string(R.string.settings_screen_security),
          builder = { screenKey -> requireBuilder(screenKey).build(context, _settingActions, this@buildScreen) }
        )

        buildScreen(
          key = SettingsScreenKey.Caching,
          title = appResources.string(R.string.settings_caching),
          builder = { screenKey -> requireBuilder(screenKey).build(context, _settingActions, this@buildScreen) }
        )

        buildScreen(
          key = SettingsScreenKey.Plugins,
          title = appResources.string(R.string.settings_plugins),
          builder = { screenKey -> requireBuilder(screenKey).build(context, _settingActions, this@buildScreen) }
        )

        buildScreen(
          key = SettingsScreenKey.CaptchaSolvers,
          title = appResources.string(R.string.settings_captcha_solvers),
          builder = { screenKey -> requireBuilder(screenKey).build(context, _settingActions, this@buildScreen) }
        )

        buildScreen(
          key = SettingsScreenKey.Experimental,
          title = appResources.string(R.string.settings_experimental_settings),
          builder = { screenKey -> requireBuilder(screenKey).build(context, _settingActions, this@buildScreen) }
        )

        buildScreen(
          key = SettingsScreenKey.Developer,
          title = appResources.string(R.string.settings_developer),
          builder = { screenKey -> requireBuilder(screenKey).build(context, _settingActions, this@buildScreen) }
        )

        buildScreen(
          key = SettingsScreenKey.Database,
          title = appResources.string(R.string.settings_database_summary),
          builder = { screenKey -> requireBuilder(screenKey).build(context, _settingActions, this@buildScreen) }
        )

        updateBadges()
        updateBadgesFromNotifications()
      }
    }
  }

  suspend fun buildForSite(context: Context, siteDescriptor: SiteDescriptor) {
    if (_buildSiteSettings.contains(siteDescriptor)) {
      return
    }

    siteManager.awaitUntilInitialized()
    val site = siteManager.bySiteDescriptor(siteDescriptor)
      ?: return

    _mutex.withLock {
      if (_buildSiteSettings.add(siteDescriptor)) {
        buildScreen(
          key = SettingsScreenKey.Site(siteDescriptor),
          title = appResources.string(R.string.settings_screen_site, siteDescriptor.siteName),
          builder = { siteSettingsScreenBuilder.build(context, site, _settingActions, this@buildScreen) }
        )

        updateBadges()
      }
    }
  }

  suspend fun getScreen(settingsScreenKey: SettingsScreenKey): SettingsScreen {
    val screen = _mutex.withLock { _root[settingsScreenKey] }
    if (screen == null) {
      error("Unknown key ${settingsScreenKey}")
    }

    return screen
  }

  suspend fun filter(query: String): List<SettingUiElement> {
    return _mutex.withLock {
      val result = mutableListWithCap<SettingUiElement>(initialCapacity = 32)

      for ((_, settingsScreen) in _root.entries) {
        for (group in settingsScreen.groups) {
          for (settingUiElement in group.settings) {
            if (queryMatchesSetting(query, settingUiElement)) {
              result += settingUiElement
            }
          }
        }
      }

      return@withLock result
    }
  }

  suspend fun findSettingUiElementByKey(rawKey: String): SettingUiElement? {
    return _mutex.withLock {
      for ((_, settingsScreen) in _root.entries) {
        for (group in settingsScreen.groups) {
          for (settingUiElement in group.settings) {
            if (settingUiElement.rawKey() == rawKey) {
              return@withLock settingUiElement
            }
          }
        }
      }

      return@withLock null
    }
  }

  suspend fun findScreenKeyBySettingKey(settingRawKey: String): SettingsScreenKey? {
    return _mutex.withLock {
      for ((_, settingsScreen) in _root.entries) {
        for (group in settingsScreen.groups) {
          for (settingUiElement in group.settings) {
            if (settingUiElement.rawKey() == settingRawKey) {
              return@withLock settingsScreen.key
            }
          }
        }
      }

      return@withLock null
    }
  }

  suspend fun openScreen(screenKey: SettingsScreenKey, rawSettingKey: String) {
    _settingActions.openScreen(screenKey, rawSettingKey)
  }

  suspend fun onSettingUiElementShown(rawSettingKey: String) {
    if (_settingsSeenByUser.contains(rawSettingKey)) {
      return
    }

    _coroutineScope.launch {
      kurobaSettings.onSettingSeenByUser(rawSettingKey)
      _settingsSeenByUser.add(rawSettingKey)
    }
  }

  private suspend fun queryMatchesSetting(
    query: String,
    settingUiElement: SettingUiElement
  ): Boolean {
    if (query.isEmpty()) {
      return true
    }

    if (settingUiElement.title().contains(other = query, ignoreCase = true)) {
      return true
    }

    val description = settingUiElement.description?.invoke()
    if (description?.contains(other = query, ignoreCase = true) == true) {
      return true
    }

    val badges = _settingBadges[settingUiElement.rawKey()]
    if (!badges.isNullOrEmpty()) {
      for (badge in badges) {
        if (
          badge.text?.contains(other = query, ignoreCase = true) == true ||
          badge.description?.contains(other = query, ignoreCase = true) == true
        ) {
          return true
        }
      }
    }

    return false
  }

  private suspend fun updateBadgesFromNotifications() {
    settingsNotificationManager.dismissedNotifications.forEach { settingNotification ->
      when (settingNotification) {
        SettingNotification.ApkUpdate -> {
          val badges = _settingBadges[KurobaSettingKey.Application.AppUpdate.raw]
            ?: emptyList()

          val updatedBadges = badges
            .filter { badge -> badge !is SettingUiElement.Badge.NewAppUpdate }

          _settingBadges[KurobaSettingKey.Application.AppUpdate.raw] = updatedBadges
        }
      }
    }

    settingsNotificationManager.activeNotifications.forEach { settingNotification ->
      when (settingNotification) {
        SettingNotification.ApkUpdate -> {
          val badges = _settingBadges[KurobaSettingKey.Application.AppUpdate.raw]
            ?: emptyList()

          val updatedBadges = badges + SettingUiElement.Badge.NewAppUpdate(
            text = appResources.string(R.string.update_available),
            description = kurobaSettings.internal.apkUpdateInfoJson.read().versionName
          )

          _settingBadges[KurobaSettingKey.Application.AppUpdate.raw] = updatedBadges
        }
      }
    }
  }

  private suspend fun updateBadges() {
    for ((_, settingsScreen) in _root.entries) {
      for (group in settingsScreen.groups) {
        for (settingUiElement in group.settings) {
          val settingKey = settingUiElement.rawKey()

          val badges = createBadges(settingUiElement)
          if (badges.isNotEmpty()) {
            _settingBadges[settingKey] = badges
          }
        }
      }
    }
  }

  private suspend fun createBadges(settingUiElement: SettingUiElement): List<SettingUiElement.Badge> {
    val seenSettings = kurobaSettings.initialSettingsState.seenSettings

    return buildList {
      val settingKey = settingUiElement.settingKey()
      if (settingKey != null) {
        if (!seenSettings.contains(settingKey.raw)) {
          add(
            SettingUiElement.Badge.NewSetting(
              text = appResources.string(R.string.setting_badge_new),
              description = appResources.string(R.string.setting_badge_new_description)
            )
          )
        }
      }

      if (settingUiElement.requiresAppRestart) {
        add(SettingUiElement.Badge.RequiresRestart(text = appResources.string(R.string.setting_badge_requires_restart)))
      }

      if (settingUiElement.deprecated) {
        add(
          SettingUiElement.Badge.Deprecated(
            text = appResources.string(R.string.setting_badge_deprecated),
            description = appResources.string(R.string.setting_badge_deprecated_description)
          )
        )
      }

      addAll(settingUiElement.badges)
    }
  }

  private suspend fun buildScreen(
    key: SettingsScreenKey,
    title: String,
    builder: suspend SettingsScreen.(SettingsScreenKey) -> Unit
  ) {
    require(_mutex.isLocked) { "Must be called within the Mutex!" }
    require(!_root.containsKey(key)) { "Already contains screen with key ${key}" }

    _root[key] = with(SettingsScreen(key, title)) {
      builder(key)
      this
    }
  }

  private fun requireBuilder(screenKey: SettingsScreenKey): SettingsScreenBuilder {
    return requireNotNull(builders[screenKey]) { "Unknown screenKey: ${screenKey}" }
  }

}

class SettingsScreen(
  val key: SettingsScreenKey,
  val title: String
) {
  private val _groups = mutableListOf<SettingUiElementGroup>()
  val groups: List<SettingUiElementGroup>
    get() = _groups.toList()

  suspend fun addGroup(
    key: String,
    title: String,
    builder: suspend SettingUiElementGroup.() -> Unit
  ) {
    require(!_groups.any { group -> group.key == key }) { "Already contains group with key ${key}" }

    _groups += with(SettingUiElementGroup(key, title)) {
      builder()
      this
    }
  }

  fun findScrollIndex(settingKeyRaw: String): Int? {
    var index = 0

    for (group in _groups) {
      ++index

      for (settingUiElement in group.settings) {
        if (settingUiElement.rawKey() == settingKeyRaw) {
          return index
        }

        ++index
      }
    }

    return null
  }
}

@Parcelize
sealed interface SettingsScreenKey : Parcelable {
  val name: String
    get() = this::class.java.simpleName

  data object Main : SettingsScreenKey
  data object Watchers : SettingsScreenKey
  data object Appearance : SettingsScreenKey
  data object Behavior : SettingsScreenKey
  data object Media : SettingsScreenKey
  data object Caching : SettingsScreenKey
  data object CaptchaSolvers : SettingsScreenKey
  data object Database : SettingsScreenKey
  data object Developer : SettingsScreenKey
  data object Experimental : SettingsScreenKey
  data object ImportExport : SettingsScreenKey
  data object Plugins : SettingsScreenKey
  data object Security : SettingsScreenKey
  data class Site(val siteDescriptor: SiteDescriptor) : SettingsScreenKey
}