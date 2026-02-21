package com.github.k1rakishou.chan.ui.globalstate.bottompanel

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface IBottomPanelGlobalState {
  interface Readable {
    val controllersHoldingBottomPanel: StateFlow<Set<ControllerKey>>
    val bottomPanelWidth: StateFlow<Dp>
    val bottomPanelHeight: StateFlow<Dp>
    val bottomPanelHeightDp: Dp

    fun isShownOnScreen(controllerKey: ControllerKey): Boolean
    fun listenForBottomPanelVisibilityOnScreen(controllerKey: ControllerKey): Flow<Boolean>
  }

  interface Writeable {
    fun onBottomPanelWidthKnown(width: Dp)
    fun onBottomPanelHeightKnown(height: Dp)
    fun onBottomPanelShown(controllerKey: ControllerKey)
    fun onBottomPanelHidden(controllerKey: ControllerKey)
  }
}

class BottomPanelGlobalState : IBottomPanelGlobalState.Readable, IBottomPanelGlobalState.Writeable {
  private val _controllersHoldingBottomPanel = MutableStateFlow<Set<ControllerKey>>(emptySet())
  override val controllersHoldingBottomPanel: StateFlow<Set<ControllerKey>>
    get() = _controllersHoldingBottomPanel.asStateFlow()

  private val _bottomPanelWidth = MutableStateFlow<Dp>(0.dp)
  override val bottomPanelWidth: StateFlow<Dp>
    get() = _bottomPanelWidth.asStateFlow()

  private val _bottomPanelHeight = MutableStateFlow<Dp>(0.dp)
  override val bottomPanelHeight: StateFlow<Dp>
    get() = _bottomPanelHeight.asStateFlow()

  override val bottomPanelHeightDp: Dp
    get() = _bottomPanelHeight.value

  override fun isShownOnScreen(controllerKey: ControllerKey): Boolean {
    return _controllersHoldingBottomPanel.value.contains(controllerKey)
  }

  override fun listenForBottomPanelVisibilityOnScreen(controllerKey: ControllerKey): Flow<Boolean> {
    return _controllersHoldingBottomPanel
      .map { controllerKeys -> controllerKey in controllerKeys }
      .distinctUntilChanged()
  }

  override fun onBottomPanelWidthKnown(width: Dp) {
    _bottomPanelWidth.value = width
  }

  override fun onBottomPanelHeightKnown(height: Dp) {
    _bottomPanelHeight.value = height
  }

  override fun onBottomPanelShown(controllerKey: ControllerKey) {
    _controllersHoldingBottomPanel.value += controllerKey
  }

  override fun onBottomPanelHidden(controllerKey: ControllerKey) {
    _controllersHoldingBottomPanel.value -= controllerKey

    if (_controllersHoldingBottomPanel.value.isEmpty()) {
      _bottomPanelHeight.value = 0.dp
    }
  }

}