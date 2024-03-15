package com.github.k1rakishou.chan.ui.globalstate.drawer

import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface IDrawerGlobalState {
  interface Readable {
    val drawerAppearanceEventFlow: StateFlow<DrawerAppearanceEvent>
  }

  interface Writable {
    fun onDrawerAppearanceChanged(opened: Boolean)
  }
}

class DrawerGlobalState : IDrawerGlobalState.Readable, IDrawerGlobalState.Writable {

  private val _drawerAppearanceEventFlow = MutableStateFlow<DrawerAppearanceEvent>(DrawerAppearanceEvent.Closed)
  override val drawerAppearanceEventFlow: StateFlow<DrawerAppearanceEvent>
    get() = _drawerAppearanceEventFlow.asStateFlow()

  override fun onDrawerAppearanceChanged(opened: Boolean) {
    Logger.verbose(TAG) { "onDrawerAppearanceChanged() opened: ${opened}" }

    val drawerAppearanceEvent = if (opened) {
      DrawerAppearanceEvent.Opened
    } else {
      DrawerAppearanceEvent.Closed
    }

    _drawerAppearanceEventFlow.value = drawerAppearanceEvent
  }

  companion object {
    private const val TAG = "DrawerGlobalState"
  }
}