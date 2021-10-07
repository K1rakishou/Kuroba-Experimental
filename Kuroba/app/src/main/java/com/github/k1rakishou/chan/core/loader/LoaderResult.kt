package com.github.k1rakishou.chan.core.loader

import com.github.k1rakishou.model.data.post.LoaderType

sealed class LoaderResult(val loaderType: LoaderType) {
  /**
   * Loader has successfully loaded new content for current post and we now need to update the
   * post
   *
   * [needUpdateView] is whether we actually need to update the post via notifyItemChanged or not.
   * Some loaders do not add any new visible information to a post (like PrefetchLoader) so it's
   * reasonable to not update the view if it was the only executed loader.
   * */
  class Succeeded(loaderType: LoaderType, val needUpdateView: Boolean) : LoaderResult(loaderType)

  /**
   * Loader failed to load new content for current post (no internet connection or something
   * similar)
   * */
  class Failed(loaderType: LoaderType) : LoaderResult(loaderType)

  /**
   * Loader rejected to load new content for current post (feature is turned off in settings
   * by the user or some other condition is not satisfied (like we are currently not on Wi-Fi
   * network and the loader requires Wi-Fi connection to load huge content, like prefetcher).
   * When Rejected is returned that means that we don't need to update the post (there is no info)
   * */
  class Rejected(loaderType: LoaderType) : LoaderResult(loaderType)
}