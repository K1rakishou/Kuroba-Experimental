package com.github.k1rakishou.chan.ui.globalstate.global

import androidx.compose.ui.geometry.Offset
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface IMainUiState {
  interface Readable {
    val touchPositionFlow: StateFlow<Offset>
  }

  interface Writeable {
    fun updateTouchPosition(touchPosition: Offset)
  }
}

internal class MainUiState : IMainUiState.Readable, IMainUiState.Writeable {
  private val _touchPosition = MutableStateFlow<Offset>(Offset.Zero)
  override val touchPositionFlow: StateFlow<Offset>
    get() = _touchPosition.asStateFlow()

  override fun updateTouchPosition(touchPosition: Offset) {
    Logger.verbose(TAG) { "updateTouchPosition() touchPosition: ${touchPosition}" }
    _touchPosition.value = touchPosition
  }

  companion object {
    private const val TAG = "MainUiState"
  }

}