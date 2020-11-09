package com.github.k1rakishou.model.source.cache

enum class ChanCacheOptions {
  StoreInMemory,
  StoreInDatabase,
  StoreEverywhere;

  fun canStoreInMemory(): Boolean {
    return this == StoreInMemory || this == StoreEverywhere
  }

  fun canStoreInDatabase(): Boolean {
    return this == StoreInDatabase || this == StoreEverywhere
  }

}