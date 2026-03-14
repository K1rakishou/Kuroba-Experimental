package com.github.k1rakishou.chan.core.base.viewmodel

import com.github.k1rakishou.chan.ui.controller.base.Controller
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

interface ControllerNavigationDelegate {
  val navigationEvents: SharedFlow<NavigationEvent>

  fun pushController(controller: Controller)
  fun popController()
  fun presentController(controller: Controller)

  sealed interface NavigationEvent {
    data class Present(val controller: Controller) : NavigationEvent
    data class Push(val controller: Controller) : NavigationEvent
    data object Pop : NavigationEvent
  }
}

class HasNavigationDelegateImpl : ControllerNavigationDelegate {
  override val navigationEvents: MutableSharedFlow<ControllerNavigationDelegate.NavigationEvent> =
    MutableSharedFlow<ControllerNavigationDelegate.NavigationEvent>(extraBufferCapacity = Channel.UNLIMITED)

  override fun pushController(controller: Controller) {
    navigationEvents.tryEmit(ControllerNavigationDelegate.NavigationEvent.Push(controller))
  }

  override fun popController() {
    navigationEvents.tryEmit(ControllerNavigationDelegate.NavigationEvent.Pop)
  }

  override fun presentController(controller: Controller) {
    navigationEvents.tryEmit(ControllerNavigationDelegate.NavigationEvent.Present(controller))
  }
}