package com.github.k1rakishou.chan.core.di.module.viewmodel

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.savedstate.SavedStateRegistryOwner
import dagger.Binds
import dagger.Module

@Module
abstract class ViewModelFactoryModule {

  @Binds
  abstract fun bindSavedStateRegistryOwner(impl: AppCompatActivity): SavedStateRegistryOwner

  @Binds
  abstract fun bindViewModelFactory(impl: GenericViewModelFactory): ViewModelProvider.Factory

  @Binds
  abstract fun bindViewModelStore(impl: KurobaViewModelStore): ViewModelStore

}