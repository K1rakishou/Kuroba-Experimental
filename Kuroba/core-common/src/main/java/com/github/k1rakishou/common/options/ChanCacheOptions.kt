package com.github.k1rakishou.common.options

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
    private val DEFAULT_OPTIONS = ChanCacheOptions(listOf(
      ChanCacheOption.StoreEverywhere,
      ChanCacheOption.CanAddInFrontOfTheMemoryCache
    ))

    fun default(): ChanCacheOptions {
      return DEFAULT_OPTIONS
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