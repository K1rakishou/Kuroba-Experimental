package com.github.k1rakishou.common.options

enum class ChanLoadOptions {
  RetainAll,
  ClearMemoryCache,
  ClearMemoryAndDatabaseCaches;

  fun isNotDefault(): Boolean {
    return this != RetainAll
  }

  fun canClearCache(): Boolean {
    return this == ClearMemoryCache || this == ClearMemoryAndDatabaseCaches
  }

  fun canClearDatabase(): Boolean {
    return this == ClearMemoryAndDatabaseCaches
  }
}