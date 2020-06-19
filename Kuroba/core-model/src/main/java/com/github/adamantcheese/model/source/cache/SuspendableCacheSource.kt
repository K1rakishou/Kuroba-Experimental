package com.github.adamantcheese.model.source.cache

import com.github.adamantcheese.common.ModularResult

interface SuspendableCacheSource<Key, Value> {
  suspend fun get(key: Key): Value?
  suspend fun getMany(keys: List<Key>): Map<Key, Value>
  suspend fun getAll(): Map<Key, Value>
  suspend fun filterValues(filterFunc: (Value) -> Boolean): List<Value>
  suspend fun store(key: Key, value: Value)
  suspend fun storeMany(entries: Map<Key, Value>)
  suspend fun firstOrNull(predicate: suspend (Value) -> Boolean): Value?
  suspend fun iterateWhile(iteratorFunc: suspend (Value) -> Boolean): ModularResult<Unit>
  suspend fun updateMany(keys: List<Key>, updateFunc: (Value) -> Unit)
  suspend fun contains(key: Key): Boolean
  suspend fun size(): Int
  suspend fun delete(key: Key)
  suspend fun deleteMany(keys: List<Key>)
  suspend fun clear()
}