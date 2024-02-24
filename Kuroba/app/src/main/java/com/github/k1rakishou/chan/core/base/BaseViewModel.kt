package com.github.k1rakishou.chan.core.base

import androidx.annotation.CallSuper
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Stable
abstract class BaseViewModel : ViewModel() {
  protected val viewModelInitialized = SuspendableInitializer<Unit>("viewModelInitialized")

  init {
    Chan.getComponent()
      .viewModelComponentBuilder()
      .build()
      .also { component -> injectDependencies(component) }

    viewModelScope.launch(Dispatchers.Main) {
      try {
        onViewModelReady()
        viewModelInitialized.initWithValue(Unit)
      } catch (error: Throwable) {
        if (error is CancellationException) {
          Logger.e("BaseViewModel", "onViewModelReady(${this@BaseViewModel.javaClass.simpleName}) initialization canceled")
          viewModelInitialized.initWithError(error)
          return@launch
        }

        Logger.e("BaseViewModel", "onViewModelReady(${this@BaseViewModel.javaClass.simpleName}) error", error)
        viewModelInitialized.initWithError(error)
        throw RuntimeException(error)
      }
    }
  }

  @CallSuper
  override fun onCleared() {

  }

  protected inline fun <T> MutableStateFlow<T>.updateState(crossinline updater: T.() -> T?) {
    val prevState = this.value
    val newState = updater(this.value)

    if (newState == null) {
      return
    }

    check(prevState !== newState) { "State must be copied!" }
    this.value = newState
  }

  abstract fun injectDependencies(component: ViewModelComponent)
  abstract suspend fun onViewModelReady()
}