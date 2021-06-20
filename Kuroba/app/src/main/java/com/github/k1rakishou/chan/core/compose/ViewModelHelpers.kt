package com.github.k1rakishou.chan.core.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

fun <VM : ViewModel> viewModelProviderFactoryOf(
  create: () -> VM
): ViewModelProvider.Factory = SimpleFactory(create)

private class SimpleFactory<VM : ViewModel>(
  private val create: () -> VM
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    val vm = create()
    if (modelClass.isInstance(vm)) {
      @Suppress("UNCHECKED_CAST")
      return vm as T
    }
    throw IllegalArgumentException("Can not create ViewModel for class: $modelClass")
  }
}