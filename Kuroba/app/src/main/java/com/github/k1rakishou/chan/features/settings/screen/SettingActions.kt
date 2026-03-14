package com.github.k1rakishou.chan.features.settings.screen

import com.github.k1rakishou.chan.features.settings.SettingsScreenKey
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SettingActions {
  private val _openScreenRequests = MutableSharedFlow<OpenScreenRequest>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val openScreenRequests: SharedFlow<OpenScreenRequest>
    get() = _openScreenRequests.asSharedFlow()

  private val _pushControllerRequests = MutableSharedFlow<Controller>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val pushControllerRequests: SharedFlow<Controller>
    get() = _pushControllerRequests.asSharedFlow()

  private val _presentControllerRequests = MutableSharedFlow<Controller>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val presentControllerRequests: SharedFlow<Controller>
    get() = _presentControllerRequests.asSharedFlow()

  private val _showToastRequests = MutableSharedFlow<String>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val showToastRequests: SharedFlow<String>
    get() = _showToastRequests.asSharedFlow()

  suspend fun openScreen(screenKey: SettingsScreenKey, rawSettingKey: String) {
    _openScreenRequests.emit(OpenScreenRequest(screenKey, rawSettingKey))
  }

  suspend fun pushController(controller: Controller) {
    _pushControllerRequests.emit(controller)
  }

  suspend fun presentController(controller: Controller) {
    _presentControllerRequests.emit(controller)
  }

  fun showToast(message: String) {
    _showToastRequests.tryEmit(message)
  }

  fun openUrl(url: String) {
    AppModuleAndroidUtils.openLink(url)
  }

  data class OpenScreenRequest(
    val screenKey: SettingsScreenKey,
    val rawSettingKey: String
  )
}