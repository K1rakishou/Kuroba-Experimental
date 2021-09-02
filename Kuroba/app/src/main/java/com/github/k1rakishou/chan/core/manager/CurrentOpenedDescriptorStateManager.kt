package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.core.presenter.ThreadPresenter
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CurrentOpenedDescriptorStateManager {
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

  private var currentFocusedController = ThreadPresenter.CurrentFocusedController.None

  val currentFocusedDescriptor: ChanDescriptor?
    get() {
      return when (currentFocusedController) {
        ThreadPresenter.CurrentFocusedController.Catalog -> currentCatalogDescriptor as ChanDescriptor?
        ThreadPresenter.CurrentFocusedController.Thread -> currentThreadDescriptor
        ThreadPresenter.CurrentFocusedController.None -> null
      }
    }

  fun updateCatalogDescriptor(catalogDescriptor: ChanDescriptor.ICatalogDescriptor?) {
    _currentCatalogDescriptorFlow.value = catalogDescriptor
  }

  fun updateThreadDescriptor(threadDescriptor: ChanDescriptor.ThreadDescriptor?) {
    _currentThreadDescriptorFlow.value = threadDescriptor
  }

  fun updateCurrentFocusedController(focusedController: ThreadPresenter.CurrentFocusedController) {
    this.currentFocusedController = focusedController
  }

}