package com.github.adamantcheese.chan.core.site.loader.internal

import com.github.adamantcheese.chan.core.site.loader.ChanLoaderRequestParams
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderResponse
import com.github.adamantcheese.common.ModularResult

internal class LocalThreadPostLoader : AbstractPostLoader() {

  suspend fun loadPosts(url: String, requestParams: ChanLoaderRequestParams): ModularResult<ChanLoaderResponse> {
    require(requestParams.loadable.isThreadMode) {
      "Bad loadable mode: ${requestParams.loadable.mode}"
    }

    TODO("Not yet implemented")
  }

  fun markThreadAsDownloaded(requestParams: ChanLoaderRequestParams): ModularResult<Unit> {
    TODO("Not yet implemented")
  }
}