package com.github.k1rakishou.chan.core.base.viewmodel

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

interface ControllerNavigationDelegate {
  val navigationEvents: SharedFlow<NavigationEvent>

  fun popController()

  sealed interface NavigationEvent {
    data object Pop : NavigationEvent
  }
}

class HasNavigationDelegateImpl : ControllerNavigationDelegate {
  override val navigationEvents: MutableSharedFlow<ControllerNavigationDelegate.NavigationEvent> =
    MutableSharedFlow<ControllerNavigationDelegate.NavigationEvent>(extraBufferCapacity = Channel.UNLIMITED)

  override fun popController() {
    navigationEvents.tryEmit(ControllerNavigationDelegate.NavigationEvent.Pop)
  }
}