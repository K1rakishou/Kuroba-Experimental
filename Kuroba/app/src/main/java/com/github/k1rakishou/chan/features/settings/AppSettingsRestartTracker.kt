package com.github.k1rakishou.chan.features.settings

import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AppSettingsRestartTracker {
  private val _settingToggleEventFlow = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val settingToggleEventFlow: SharedFlow<Unit>
    get() = _settingToggleEventFlow.asSharedFlow()

  private val _requireRestart = mutableSetOf<String>()
  private val _requireRefresh = mutableSetOf<String>()

  fun toggleSetting(settingUiElement: SettingUiElement) {
    val rawSettingKey = settingUiElement.rawKey()

    if (settingUiElement.requiresAppRestart) {
      if (!_requireRestart.contains(rawSettingKey)) {
        _requireRestart.add(rawSettingKey)
      } else {
        _requireRestart.remove(rawSettingKey)
      }

      _settingToggleEventFlow.tryEmit(Unit)
    }

    if (settingUiElement.requiresPostListRefresh) {
      if (!_requireRefresh.contains(rawSettingKey)) {
        _requireRefresh.add(rawSettingKey)
      } else {
        _requireRefresh.remove(rawSettingKey)
      }

      _settingToggleEventFlow.tryEmit(Unit)
    }
  }

  fun setSetting(settingUiElement: SettingUiElement) {
    val rawSettingKey = settingUiElement.rawKey()

    if (settingUiElement.requiresAppRestart) {
      if (_requireRestart.add(rawSettingKey)) {
        _settingToggleEventFlow.tryEmit(Unit)
      }
    }

    if (settingUiElement.requiresPostListRefresh) {
      if (_requireRefresh.add(rawSettingKey)) {
        _settingToggleEventFlow.tryEmit(Unit)
      }
    }
  }

  fun needToRestartApp(): Boolean {
    return _requireRestart.isNotEmpty()
  }

  fun needToRefreshPostList(): Boolean {
    return _requireRefresh.isNotEmpty()
  }

  fun resetAll() {
    _requireRestart.clear()
    _requireRefresh.clear()
  }
}