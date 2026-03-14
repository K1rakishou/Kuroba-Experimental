package com.github.k1rakishou.chan.ui.controller.navigation

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarState
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.transition.ControllerTransition
import com.github.k1rakishou.chan.ui.controller.base.transition.TransitionMode

abstract class ToolbarNavigationController(context: Context) : NavigationController(context) {
  private val _containerToolbarStateUpdatedListeners = mutableListOf<ContainerToolbarStateUpdatedListener>()

  private val _containerToolbarState by lazy(LazyThreadSafetyMode.NONE) {
    mutableStateOf<KurobaToolbarState>(
      kurobaToolbarStateManager.getOrCreate(this, controllerKey)
    )
  }

  final override val toolbarState: KurobaToolbarState
    get() = _containerToolbarState.value

  override var containerToolbarState: KurobaToolbarState
    get() = _containerToolbarState.value
    set(value) {
      _containerToolbarStateUpdatedListeners.forEach { listener -> listener.onStateUpdated(value) }
      _containerToolbarState.value = value
    }

  fun addOrReplaceContainerToolbarStateUpdated(listener: ContainerToolbarStateUpdatedListener) {
    _containerToolbarStateUpdatedListeners -= listener
    _containerToolbarStateUpdatedListeners += listener
  }

  fun removeContainerToolbarStateUpdated(listener: ContainerToolbarStateUpdatedListener) {
    _containerToolbarStateUpdatedListeners -= listener
  }

  override fun transition(
    from: Controller?,
    to: Controller?,
    pushing: Boolean,
    controllerTransition: ControllerTransition?
  ) {
    if (from == null && to == null) {
      return
    }

    super.transition(from, to, pushing, controllerTransition)

    if (to != null) {
      containerToolbarState.showToolbar()
      globalUiStateHolder.updateScrollState { resetScrollState() }
    }
  }

  override fun beginSwipeTransition(from: Controller?, to: Controller?): Boolean {
    if (from == null && to == null) {
      return false
    }

    if (!super.beginSwipeTransition(from, to)) {
      return false
    }

    containerToolbarState.showToolbar()
    globalUiStateHolder.updateScrollState { resetScrollState() }

    if (to != null) {
      containerToolbarState.onTransitionProgressStart(
        other = to.toolbarState,
        transitionMode = TransitionMode.Out
      )
    }

    return true
  }

  override fun swipeTransitionProgress(progress: Float) {
    super.swipeTransitionProgress(progress)

    containerToolbarState.onTransitionProgress(progress)
  }

  override fun endSwipeTransition(from: Controller?, to: Controller?, finish: Boolean) {
    if (from == null && to == null) {
      return
    }

    super.endSwipeTransition(from, to, finish)

    containerToolbarState.showToolbar()
    globalUiStateHolder.updateScrollState { resetScrollState() }

    val prevToolbarState = containerToolbarState

    if (finish && to != null) {
      containerToolbarState = to.toolbarState
      prevToolbarState.onTransitionProgressFinished()
    } else if (!finish && from != null) {
      containerToolbarState = from.toolbarState
      prevToolbarState.onTransitionProgressFinished()
    }
  }

  override fun onBack(): Boolean {
    if (toolbarState.onBack()) {
      return true
    }

    return super.onBack()
  }
}

interface ContainerToolbarStateUpdatedListener {
  fun onStateUpdated(kurobaToolbarState: KurobaToolbarState)
}