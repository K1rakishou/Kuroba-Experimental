package com.github.k1rakishou.chan.ui.globalstate.toolbar

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarStateKind
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.core_logger.Logger
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface IToolbarGlobalState {
  interface Readable {
    val toolbarShown: StateFlow<Boolean>
    val toolbarHeight: StateFlow<Dp>
    val currentToolbarStates: StateFlow<ImmutableMap<ControllerKey, ToolbarStateKind>>
    val toolbarBadges: StateFlow<ImmutableMap<ToolbarStateKind, ToolbarBadgeGlobalState>>
  }

  interface Writeable {
    fun updateToolbarVisibilityState(visible: Boolean)
    fun updateToolbarHeightState(height: Dp)
    fun updateToolbarBadge(toolbarStateKind: ToolbarStateKind, toolbarBadgeGlobalState: ToolbarBadgeGlobalState)

    fun onToolbarTopStateChanged(controllerKey: ControllerKey, toolbarStateKind: ToolbarStateKind)
    fun onToolbarStateRemoved(controllerKey: ControllerKey)
  }
}

class ToolbarGlobalState : IToolbarGlobalState.Readable, IToolbarGlobalState.Writeable {
  private val _toolbarShown = MutableStateFlow<Boolean>(true)
  override val toolbarShown: StateFlow<Boolean>
    get() = _toolbarShown.asStateFlow()

  private val _toolbarHeight = MutableStateFlow<Dp>(0.dp)
  override val toolbarHeight: StateFlow<Dp>
    get() = _toolbarHeight.asStateFlow()

  private val _currentToolbarStates = MutableStateFlow<PersistentMap<ControllerKey, ToolbarStateKind>>(persistentMapOf())
  override val currentToolbarStates: StateFlow<ImmutableMap<ControllerKey, ToolbarStateKind>>
    get() = _currentToolbarStates.asStateFlow()

  private val _toolbarBadges = MutableStateFlow<PersistentMap<ToolbarStateKind, ToolbarBadgeGlobalState>>(persistentMapOf())
  override val toolbarBadges: StateFlow<ImmutableMap<ToolbarStateKind, ToolbarBadgeGlobalState>>
    get() = _toolbarBadges.asStateFlow()

  override fun updateToolbarVisibilityState(visible: Boolean) {
    if (toolbarShown.value != visible) {
      Logger.verbose(TAG) { "updateToolbarVisibilityState() visible: ${visible}" }
    }

    _toolbarShown.value = visible
  }

  override fun updateToolbarHeightState(height: Dp) {
    if (toolbarHeight.value != height) {
      Logger.verbose(TAG) { "updateToolbarHeightState() height: ${height}" }
    }

    _toolbarHeight.value = height
  }

  override fun updateToolbarBadge(
    toolbarStateKind: ToolbarStateKind,
    toolbarBadgeGlobalState: ToolbarBadgeGlobalState
  ) {
    Logger.verbose(TAG) {
      "updateToolbarBadge() toolbarStateKind: ${toolbarStateKind}, toolbarBadgeGlobalState: ${toolbarBadgeGlobalState}"
    }

    _toolbarBadges.value = _toolbarBadges.value.put(toolbarStateKind, toolbarBadgeGlobalState)
  }

  override fun onToolbarTopStateChanged(controllerKey: ControllerKey, toolbarStateKind: ToolbarStateKind) {
    if (_currentToolbarStates.value[controllerKey] != toolbarStateKind) {
      _currentToolbarStates.value = _currentToolbarStates.value.put(controllerKey, toolbarStateKind)

      Logger.verbose(TAG) {
        "onToolbarTopStateChanged() controllerKey: ${controllerKey}, " +
          "toolbarStateKind: ${toolbarStateKind}, total: ${_currentToolbarStates.value.size}"
      }
    }
  }

  override fun onToolbarStateRemoved(controllerKey: ControllerKey) {
    if (_currentToolbarStates.value.containsKey(controllerKey)) {
      _currentToolbarStates.value = _currentToolbarStates.value.remove(controllerKey)
      Logger.verbose(TAG) { "onToolbarStateRemoved() controllerKey: ${controllerKey}, total: ${_currentToolbarStates.value.size}" }
    }
  }

  companion object {
    private const val TAG = "ToolbarGlobalState"
  }

}

data class ToolbarBadgeGlobalState(
  val number: Int,
  val highImportance: Boolean
)