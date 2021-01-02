package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor


interface LoaderContentLoadState{
  fun isContentLoadedFor(chanDescriptor: ChanDescriptor): Boolean
  fun setContentLoadedFor(chanDescriptor: ChanDescriptor)
  fun everythingLoaded(): Boolean
  fun setContentLoaded()
}

class OriginalPostContentLoadState(
  private var loadedForCatalog: Boolean = false,
  private var loadedForThread: Boolean = false
) : LoaderContentLoadState {

  @Synchronized
  override fun isContentLoadedFor(chanDescriptor: ChanDescriptor): Boolean {
    return when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> loadedForThread
      is ChanDescriptor.CatalogDescriptor -> loadedForCatalog
    }
  }

  @Synchronized
  override fun setContentLoadedFor(chanDescriptor: ChanDescriptor) {
    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> loadedForThread = true
      is ChanDescriptor.CatalogDescriptor -> loadedForCatalog = true
    }
  }

  @Synchronized
  override fun everythingLoaded(): Boolean {
    return loadedForCatalog && loadedForThread
  }

  @Synchronized
  override fun setContentLoaded() {
    loadedForCatalog = true
    loadedForThread = true
  }

}

class PostContentLoadState(
  private var loaded: Boolean = false
) : LoaderContentLoadState {

  @Synchronized
  override fun isContentLoadedFor(chanDescriptor: ChanDescriptor): Boolean {
    return loaded
  }

  @Synchronized
  override fun setContentLoadedFor(chanDescriptor: ChanDescriptor) {
    loaded = true
  }

  @Synchronized
  override fun everythingLoaded(): Boolean {
    return loaded
  }

  @Synchronized
  override fun setContentLoaded() {
    loaded = true
  }
}