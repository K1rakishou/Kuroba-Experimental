package com.github.adamantcheese.chan.core.site.loader

sealed class ThreadLoadResult {
  class LoadedNormally(val chanLoaderResponse: ChanLoaderResponse) : ThreadLoadResult()
  class LoadedFromDatabaseCopy(val chanLoaderResponse: ChanLoaderResponse) : ThreadLoadResult()
  class LoadedFromArchive(val chanLoaderResponse: ChanLoaderResponse) : ThreadLoadResult()
}