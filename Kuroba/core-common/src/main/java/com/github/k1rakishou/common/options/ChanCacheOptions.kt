package com.github.k1rakishou.common.options

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