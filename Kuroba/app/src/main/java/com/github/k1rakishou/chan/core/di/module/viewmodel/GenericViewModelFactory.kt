package com.github.k1rakishou.chan.core.di.module.viewmodel

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import javax.inject.Inject

class GenericViewModelFactory @Inject constructor(
  private val creators: Map<@JvmSuppressWildcards Class<out ViewModel>, @JvmSuppressWildcards ViewModelAssistedFactory<out ViewModel>>,
  private val savedStateRegistryOwner: SavedStateRegistryOwner
) : AbstractSavedStateViewModelFactory(savedStateRegistryOwner, null) {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(
    key: String,
    modelClass: Class<T>,
    handle: SavedStateHandle
  ): T {
    val creator = creators[modelClass]
      ?: creators.asIterable().firstOrNull { modelClass.isAssignableFrom(it.key) }?.value
      ?: throw IllegalArgumentException("Unknown model class '$modelClass'")

    return try {
      creator.create(handle) as T
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }
}