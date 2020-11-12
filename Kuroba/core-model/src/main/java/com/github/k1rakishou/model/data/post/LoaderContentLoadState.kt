package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor


class LoaderContentLoadState(
  var loadedForCatalog: Boolean = false,
  var loadedForThread: Boolean = false
) {

  @Synchronized
  fun isContentLoadedFor(chanDescriptor: ChanDescriptor): Boolean {
    return when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> loadedForThread
      is ChanDescriptor.CatalogDescriptor -> loadedForCatalog
    }
  }

  @Synchronized
  fun setContentLoadedFor(chanDescriptor: ChanDescriptor) {
    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> loadedForThread = true
      is ChanDescriptor.CatalogDescriptor -> loadedForCatalog = true
    }
  }

  @Synchronized
  fun everythingLoaded(): Boolean {
    return loadedForCatalog && loadedForThread
  }

  @Synchronized
  fun setContentLoaded() {
    loadedForCatalog = true
    loadedForThread = true
  }

}