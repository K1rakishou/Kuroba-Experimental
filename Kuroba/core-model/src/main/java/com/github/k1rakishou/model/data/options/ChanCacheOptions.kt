package com.github.k1rakishou.model.data.options

data class ChanCacheOptions(val options: List<ChanCacheOption>) {

  fun canStoreInMemory(): Boolean {
    return options.any { chanCacheOption -> chanCacheOption.canStoreInMemory() }
  }

  fun canAddInFrontOfTheMemoryCache(): Boolean {
    return options.any { chanCacheOption -> chanCacheOption.canAddInFrontOfTheMemoryCache() }
  }

  companion object {
    fun onlyCacheInMemory(): ChanCacheOptions {
      return ChanCacheOptions(listOf(
        ChanCacheOption.StoreInMemory,
        ChanCacheOption.CanAddInFrontOfTheMemoryCache
      ))
    }

    fun singleOption(option: ChanCacheOption): ChanCacheOptions {
      return ChanCacheOptions(listOf(option))
    }

  }
}

enum class ChanCacheOption {
  DoNotStoreInMemory,
  StoreInMemory,
  CanAddInFrontOfTheMemoryCache;

  fun canStoreInMemory(): Boolean {
    return this == StoreInMemory
  }

  fun canAddInFrontOfTheMemoryCache(): Boolean {
    return this == CanAddInFrontOfTheMemoryCache
  }

}