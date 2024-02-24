package com.github.k1rakishou.chan.core.di.module.viewmodel

import androidx.lifecycle.ViewModelProvider

interface IHasViewModelProviderFactory {
  val viewModelFactory: ViewModelProvider.Factory
}