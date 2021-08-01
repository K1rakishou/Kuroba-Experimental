package com.github.k1rakishou.model.source.cache

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.mutableMapWithCap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

open class GenericCacheSource<Key, Value>(
  private val capacity: Int = DEFAULT_CAPACITY,
  private val maxSize: Int = DEFAULT_MAX_SIZE,
  private val cacheEntriesToRemovePerTrim: Int = maxSize / 20
) : CacheSource<Key, Value> {
  private val lock = ReentrantReadWriteLock()
  private val actualCache = LinkedHashMap<Key, Value>(capacity)

  init {
    check(maxSize >= cacheEntriesToRemovePerTrim) {
      "maxSize (${maxSize}) must be greater than cacheEntriesToRemovePerTrim (${cacheEntriesToRemovePerTrim})"
    }
  }

  override fun get(key: Key): Value? {
    return lock.read { actualCache[key] }
  }

  override fun getOrPut(key: Key, valueFunc: () -> Value): Value {
    return lock.write {
      val prevValue = actualCache[key]
      if (prevValue != null) {
        return@write prevValue
      }

      val newValue = valueFunc()
      actualCache[key] = newValue

      return@write newValue
    }
  }

  override fun getMany(keys: List<Key>): Map<Key, Value> {
    return lock.read {
      val result = mutableMapWithCap<Key, Value>(keys)

      for (key in keys) {
        val value = actualCache[key]
          ?: continue

        result[key] = value
      }

      return@read result
    }
  }

  override fun getAll(): Map<Key, Value> {
    return lock.read { actualCache.toMap() }
  }

  override fun filterValues(filterFunc: (Value) -> Boolean): List<Value> {
    return lock.read {
      return@read actualCache.values.filter { value -> filterFunc(value) }
    }
  }

  override fun store(key: Key, value: Value) {
    lock.write {
      actualCache[key] = value
      trimCache()
    }
  }

  override fun storeMany(entries: Map<Key, Value>) {
    lock.write {
      entries.forEach { (key, value) -> actualCache[key] = value }
      trimCache()
    }
  }

  override fun firstOrNull(predicate: (Value) -> Boolean): Value? {
    return lock.read {
      return@read actualCache.values.firstOrNull {
        predicate(it)
      }
    }
  }

  override fun iterateWhile(iteratorFunc: (Value) -> Boolean): ModularResult<Unit> {
    return lock.read {
      var exception: Throwable? = null

      for (value in actualCache.values) {
        try {
          if (!iteratorFunc(value)) {
            break
          }
        } catch (error: Throwable) {
          exception = error
          break
        }
      }

      if (exception != null) {
        return@read ModularResult.error(exception)
      }

      return@read ModularResult.value(Unit)
    }
  }

  override fun updateMany(keys: List<Key>, updateFunc: (Value) -> Unit) {
    lock.write {
      for (key in keys) {
        val value = actualCache[key] ?: continue

        updateFunc(value)
      }
    }
  }

  override fun contains(key: Key): Boolean {
    return lock.read { actualCache.containsKey(key) }
  }

  override fun size(): Int {
    return lock.read { actualCache.size }
  }

  override fun delete(key: Key) {
    lock.write { actualCache.remove(key) }
  }

  override fun deleteMany(keys: List<Key>) {
    lock.write {
      keys.forEach { key -> actualCache.remove(key) }
    }
  }

  override fun clear() {
    lock.write { actualCache.clear() }
  }

  private fun trimCache() {
    if (actualCache.size <= maxSize) {
      return
    }

    require(lock.isWriteLocked) { "lock is not write locked" }

    val iterator = actualCache.entries.iterator()
    var entriesToRemoveCount = cacheEntriesToRemovePerTrim

    while (iterator.hasNext() && (entriesToRemoveCount--) > 0) {
      iterator.next()
      iterator.remove()
    }
  }

  companion object {
    private const val DEFAULT_CAPACITY = 128
    private const val DEFAULT_MAX_SIZE = 1024
  }
}