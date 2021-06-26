package com.github.k1rakishou.chan.core.base

import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Suppress("LeakingThis")
abstract class BaseViewModel : ViewModel() {
  protected val mainScope = KurobaCoroutineScope()

  init {
    Chan.getComponent()
      .viewModelComponentBuilder()
      .build()
      .also { component -> injectDependencies(component) }

    mainScope.launch { onViewModelReady() }
  }

  @CallSuper
  override fun onCleared() {
    mainScope.cancelChildren()
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