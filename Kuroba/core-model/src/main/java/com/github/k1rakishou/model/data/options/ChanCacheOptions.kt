package com.github.k1rakishou.model.data.options

data class ChanCacheOptions(val options: List<ChanCacheOption>) {

  fun canStoreInMemory(): Boolean {
    return options.any { chanCacheOption -> chanCacheOption.canStoreInMemory() }
  }

  fun canStoreInDatabase(): Boolean {
    return options.any { chanCacheOption -> chanCacheOption.canStoreInDatabase() }
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

    fun cacheEverywhere(): ChanCacheOptions {
      return ChanCacheOptions(listOf(
        ChanCacheOption.StoreEverywhere,
        ChanCacheOption.CanAddInFrontOfTheMemoryCache
      ))
    }

    fun singleOption(option: ChanCacheOption): ChanCacheOptions {
      return ChanCacheOptions(listOf(option))
    }

  }
}

enum class ChanCacheOption {
  StoreInMemory,
  StoreInDatabase,
  StoreEverywhere,
  CanAddInFrontOfTheMemoryCache;

  fun canStoreInMemory(): Boolean {
    return this == StoreInMemory || this == StoreEverywhere
  }

  fun canStoreInDatabase(): Boolean {
    return this == StoreInDatabase || this == StoreEverywhere
  }

  fun canAddInFrontOfTheMemoryCache(): Boolean {
    return this == CanAddInFrontOfTheMemoryCache
  }

}