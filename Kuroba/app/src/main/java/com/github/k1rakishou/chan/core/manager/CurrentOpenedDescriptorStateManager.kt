package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.v2.KurobaSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CurrentOpenedDescriptorStateManager(
  private val kurobaSettings: KurobaSettings
) {
  private val _currentCatalogDescriptorFlow = MutableStateFlow<ChanDescriptor.ICatalogDescriptor?>(null)
  val currentCatalogDescriptorFlow: StateFlow<ChanDescriptor.ICatalogDescriptor?>
    get() = _currentCatalogDescriptorFlow.asStateFlow()
  val currentCatalogDescriptor: ChanDescriptor.ICatalogDescriptor?
    get() = currentCatalogDescriptorFlow.value

  private val _currentThreadDescriptorFlow = MutableStateFlow<ChanDescriptor.ThreadDescriptor?>(null)
  val currentThreadDescriptorFlow: StateFlow<ChanDescriptor.ThreadDescriptor?>
    get() = _currentThreadDescriptorFlow.asStateFlow()
  val currentThreadDescriptor: ChanDescriptor.ThreadDescriptor?
    get() = currentThreadDescriptorFlow.value

  private val _currentFocusedControllers = MutableStateFlow<CurrentFocusedControllers>(CurrentFocusedControllers())
  val currentFocusedControllers: StateFlow<CurrentFocusedControllers>
    get() = _currentFocusedControllers.asStateFlow()

  val currentControllersFocusState: CurrentFocusedControllers.FocusState
    get() = _currentFocusedControllers.value.focusState()

  val currentFocusedDescriptors: CurrentFocusedDescriptors
    get() {
      if (kurobaSettings.application.isSplitLayoutModeBlocking()) {
        return CurrentFocusedDescriptors(
          kurobaSettings = kurobaSettings,
          catalogDescriptor = currentCatalogDescriptor,
          threadDescriptor = currentThreadDescriptor
        )
      }

      return when (_currentFocusedControllers.value.focusState()) {
        CurrentFocusedControllers.FocusState.Both -> {
          CurrentFocusedDescriptors(
            kurobaSettings = kurobaSettings,
            catalogDescriptor = currentCatalogDescriptor,
            threadDescriptor = currentThreadDescriptor
          )
        }
        CurrentFocusedControllers.FocusState.Catalog -> {
          CurrentFocusedDescriptors(
            kurobaSettings = kurobaSettings,
            catalogDescriptor = currentCatalogDescriptor
          )
        }
        CurrentFocusedControllers.FocusState.Thread -> {
          CurrentFocusedDescriptors(
            kurobaSettings = kurobaSettings,
            threadDescriptor = currentThreadDescriptor
          )
        }
        CurrentFocusedControllers.FocusState.None -> {
          CurrentFocusedDescriptors(
            kurobaSettings = kurobaSettings,
            catalogDescriptor = null,
            threadDescriptor = null
          )
        }
      }
    }

  fun isDescriptorFocused(chanDescriptor: ChanDescriptor): Boolean {
    if (kurobaSettings.application.isSplitLayoutModeBlocking()) {
      return true
    }

    return currentFocusedDescriptors.isDescriptorFocused(chanDescriptor)
  }

  fun isDescriptorNotFocused(chanDescriptor: ChanDescriptor): Boolean {
    return !isDescriptorFocused(chanDescriptor)
  }

  fun updateCatalogDescriptor(catalogDescriptor: ChanDescriptor.ICatalogDescriptor?) {
    _currentCatalogDescriptorFlow.value = catalogDescriptor
  }

  fun updateThreadDescriptor(threadDescriptor: ChanDescriptor.ThreadDescriptor?) {
    _currentThreadDescriptorFlow.value = threadDescriptor
  }

  fun updateCurrentFocusedController(focusedController: CurrentFocusedController) {
    if (kurobaSettings.application.isSplitLayoutModeBlocking()) {
      _currentFocusedControllers.value = CurrentFocusedControllers(
        catalogFocused = currentCatalogDescriptor != null,
        threadFocused = currentThreadDescriptor != null
      )
    } else {
      _currentFocusedControllers.value = when (focusedController) {
        CurrentFocusedController.Catalog -> CurrentFocusedControllers(catalogFocused = true)
        CurrentFocusedController.Thread -> CurrentFocusedControllers(threadFocused = true)
      }
    }
  }

}

data class CurrentFocusedDescriptors(
  private val kurobaSettings: KurobaSettings,
  private val catalogDescriptor: ChanDescriptor.ICatalogDescriptor? = null,
  private val threadDescriptor: ChanDescriptor.ThreadDescriptor? = null
) {

  fun anyFocused(): Boolean {
    return catalogDescriptor != null || threadDescriptor != null
  }

  fun getAllNonNull(): List<ChanDescriptor> {
    return buildList {
      if (catalogDescriptor != null) {
        add(catalogDescriptor as ChanDescriptor)
      }

      if (threadDescriptor != null) {
        add(threadDescriptor)
      }
    }
  }

  fun getFocused(): ChanDescriptor? {
    if (kurobaSettings.application.isSplitLayoutModeBlocking()) {
      error("Cannot be used in SPLIT layout mode because both descriptors are always focused")
    }

    if (catalogDescriptor != null) {
      return catalogDescriptor as ChanDescriptor
    }

    if (threadDescriptor != null) {
      return threadDescriptor
    }

    return null
  }

  fun isDescriptorFocused(chanDescriptor: ChanDescriptor): Boolean {
    if (catalogDescriptor == chanDescriptor) {
      return true
    }

    if (threadDescriptor == chanDescriptor) {
      return true
    }

    return false
  }

}

data class CurrentFocusedControllers(
  private val catalogFocused: Boolean = false,
  private val threadFocused: Boolean = false
) {

  fun focusState(): FocusState {
    if (catalogFocused && threadFocused) {
      return FocusState.Both
    }

    if (catalogFocused) {
      return FocusState.Catalog
    }

    if (threadFocused) {
      return FocusState.Thread
    }

    return FocusState.None
  }

  enum class FocusState {
    None,
    Catalog,
    Thread,
    Both
  }

}

enum class CurrentFocusedController {
  Catalog,
  Thread
}