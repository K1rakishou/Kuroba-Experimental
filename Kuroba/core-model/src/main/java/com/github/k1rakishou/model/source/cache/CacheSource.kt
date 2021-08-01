package com.github.k1rakishou.model.source.cache

import com.github.k1rakishou.common.ModularResult

interface CacheSource<Key, Value> {
  fun get(key: Key): Value?
  fun getOrPut(key: Key, valueFunc: () -> Value): Value
  fun getMany(keys: List<Key>): Map<Key, Value>
  fun getAll(): Map<Key, Value>
  fun filterValues(filterFunc: (Value) -> Boolean): List<Value>
  fun store(key: Key, value: Value)
  fun storeMany(entries: Map<Key, Value>)
  fun firstOrNull(predicate: (Value) -> Boolean): Value?
  fun iterateWhile(iteratorFunc: (Value) -> Boolean): ModularResult<Unit>
  fun updateMany(keys: List<Key>, updateFunc: (Value) -> Unit)
  fun contains(key: Key): Boolean
  fun size(): Int
  fun delete(key: Key)
  fun deleteMany(keys: List<Key>)
  fun clear()
}