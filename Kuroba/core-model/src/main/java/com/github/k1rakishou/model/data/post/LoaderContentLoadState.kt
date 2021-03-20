package com.github.k1rakishou.model.data.post


data class LoaderContentLoadState(
  private var loaded: Boolean = false
) {
  @Synchronized
  fun isContentLoadedFor(): Boolean {
    return loaded
  }

  @Synchronized
  fun setContentLoadedFor(loaded: Boolean) {
    this.loaded = loaded
  }

  @Synchronized
  fun everythingLoaded(): Boolean {
    return loaded
  }

  @Synchronized
  fun setContentLoaded() {
    loaded = true
  }

}