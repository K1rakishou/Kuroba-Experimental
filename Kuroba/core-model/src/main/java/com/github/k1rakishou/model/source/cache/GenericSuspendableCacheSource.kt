package com.github.k1rakishou.model.source.cache

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.awaitSilently
import com.github.k1rakishou.common.mutableMapWithCap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import kotlin.coroutines.CoroutineContext

/**
 * Lock-free auto trimmable poor man's cache implementation
 * */
@Suppress("EXPERIMENTAL_API_USAGE")
open class GenericSuspendableCacheSource<Key, Value>(
  private val capacity: Int = DEFAULT_CAPACITY,
  private val maxSize: Int = DEFAULT_MAX_SIZE,
  private val cacheEntriesToRemovePerTrim: Int = maxSize / 20
) : CoroutineScope, SuspendableCacheSource<Key, Value> {

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Default

  init {
    check(maxSize >= cacheEntriesToRemovePerTrim) {
      "maxSize (${maxSize}) must be greater than cacheEntriesToRemovePerTrim (${cacheEntriesToRemovePerTrim})"
    }
  }

  private val actor = actor<CacheAction<Key, Value>>(capacity = Channel.UNLIMITED) {
    val cache = LinkedHashMap<Key, Value>(capacity)

    consumeEach { action ->
      when (action) {
        is CacheAction.Get -> {
          action.deferred.complete(cache[action.key])
        }
        is CacheAction.GetMany -> {
          val result = mutableMapWithCap<Key, Value>(action.keys)

          for (key in action.keys) {
            val value = cache[key]
              ?: continue

            result[key] = value
          }

          action.deferred.complete(result)
        }
        is CacheAction.GetAll -> {
          action.deferred.complete(cache.toMap())
        }
        is CacheAction.FilterValues -> {
          val filteredValues = cache.values.filter { value -> action.filterFunc(value) }
          action.deferred.complete(filteredValues)
        }
        is CacheAction.Store -> {
          cache[action.key] = action.value
          action.deferred.complete(Unit)
        }
        is CacheAction.StoreMany -> {
          for (entry in action.entries) {
            cache[entry.key] = entry.value
          }

          action.deferred.complete(Unit)
        }
        is CacheAction.FirstOrNull -> {
          var found = false

          for (value in cache.values) {
            if (action.predicate(value)) {
              found = true
              action.deferred.complete(value)
              break
            }
          }

          if (!found) {
            action.deferred.complete(null)
          }
        }
        is CacheAction.IterateWhile -> {
          var exception: Throwable? = null

          for (value in cache.values) {
            try {
              if (!action.iteratorFunc(value)) {
                break
              }
            } catch (error: Throwable) {
              exception = error
              break
            }
          }

          if (exception == null) {
            action.deferred.complete(ModularResult.value(Unit))
          } else {
            action.deferred.complete(ModularResult.error(exception))
          }
        }
        is CacheAction.UpdateMany -> {
          for (key in action.keys) {
            val value = cache[key] ?: continue

            action.updateFunc(value)
          }

          action.deferred.complete(Unit)
        }
        is CacheAction.Contains -> {
          action.deferred.complete(cache.containsKey(action.key))
        }
        is CacheAction.Size -> {
          action.deferred.complete(cache.size)
        }
        is CacheAction.Delete -> {
          cache.remove(action.key)
          action.deferred.complete(Unit)
        }
        is CacheAction.DeleteMany -> {
          action.keys.forEach { key -> cache.remove(key) }
          action.deferred.complete(Unit)
        }
        is CacheAction.Clear -> {
          cache.clear()
          action.deferred.complete(Unit)
        }
      }

      if (cache.size > maxSize) {
        trimCache(cache)
      }
    }
  }

  private fun trimCache(cache: LinkedHashMap<Key, Value>) {
    val iterator = cache.entries.iterator()
    var entriesToRemoveCount = cacheEntriesToRemovePerTrim

    while (iterator.hasNext() && (entriesToRemoveCount--) > 0) {
      iterator.next()
      iterator.remove()
    }
  }

  override suspend fun get(key: Key): Value? {
    val deferred = CompletableDeferred<Value?>()
    actor.send(CacheAction.Get(key, deferred))

    return deferred.awaitSilently(null)
  }

  override suspend fun getMany(keys: List<Key>): Map<Key, Value> {
    val deferred = CompletableDeferred<Map<Key, Value>>()
    actor.send(CacheAction.GetMany(keys, deferred))

    return deferred.awaitSilently(emptyMap())
  }

  override suspend fun getAll(): Map<Key, Value> {
    val deferred = CompletableDeferred<Map<Key, Value>>()
    actor.send(CacheAction.GetAll(deferred))

    return deferred.awaitSilently(emptyMap())
  }

  override suspend fun filterValues(filterFunc: (Value) -> Boolean): List<Value> {
    val deferred = CompletableDeferred<List<Value>>()
    actor.send(CacheAction.FilterValues(filterFunc, deferred))

    return deferred.awaitSilently(emptyList())
  }

  override suspend fun store(key: Key, value: Value) {
    val deferred = CompletableDeferred<Unit>()
    actor.send(CacheAction.Store(key, value, deferred))

    deferred.awaitSilently()
  }

  override suspend fun storeMany(entries: Map<Key, Value>) {
    val deferred = CompletableDeferred<Unit>()
    actor.send(CacheAction.StoreMany(entries, deferred))

    deferred.awaitSilently()
  }

  override suspend fun firstOrNull(predicate: suspend (Value) -> Boolean): Value? {
    val deferred = CompletableDeferred<Value?>()
    actor.send(CacheAction.FirstOrNull(predicate, deferred))

    return deferred.awaitSilently(null)
  }

  override suspend fun iterateWhile(iteratorFunc: suspend (Value) -> Boolean): ModularResult<Unit> {
    val deferred = CompletableDeferred<ModularResult<Unit>>()
    actor.send(CacheAction.IterateWhile(iteratorFunc, deferred))

    return deferred.awaitSilently(ModularResult.value(Unit))
  }

  override suspend fun updateMany(keys: List<Key>, updateFunc: (Value) -> Unit) {
    val deferred = CompletableDeferred<Unit>()
    actor.send(CacheAction.UpdateMany(keys, updateFunc, deferred))

    deferred.awaitSilently()
  }

  override suspend fun contains(key: Key): Boolean {
    val deferred = CompletableDeferred<Boolean>()
    actor.send(CacheAction.Contains(key, deferred))

    return deferred.awaitSilently(false)
  }

  override suspend fun size(): Int {
    val deferred = CompletableDeferred<Int>()
    actor.send(CacheAction.Size(deferred))

    return deferred.awaitSilently(0)
  }

  override suspend fun delete(key: Key) {
    val deferred = CompletableDeferred<Unit>()
    actor.send(CacheAction.Delete(key, deferred))

    deferred.awaitSilently()
  }

  override suspend fun deleteMany(keys: List<Key>) {
    val deferred = CompletableDeferred<Unit>()
    actor.send(CacheAction.DeleteMany(keys, deferred))

    deferred.awaitSilently()
  }

  override suspend fun clear() {
    val deferred = CompletableDeferred<Unit>()
    actor.send(CacheAction.Clear(deferred))

    deferred.awaitSilently()
  }

  private sealed class CacheAction<out K, out V> {
    class Get<out K, V>(
      val key: K,
      val deferred: CompletableDeferred<V?>
    ) : CacheAction<K, V>()

    class GetMany<K, V>(
      val keys: List<K>,
      val deferred: CompletableDeferred<Map<K, V>>
    ) : CacheAction<K, V>()

    class GetAll<K, V>(
      val deferred: CompletableDeferred<Map<K, V>>
    ) : CacheAction<K, V>()

    class FilterValues<out K, V>(
      val filterFunc: (V) -> Boolean,
      val deferred: CompletableDeferred<List<V>>
    ) : CacheAction<K, V>()

    class Store<out K, out V>(
      val key: K,
      val value: V,
      val deferred: CompletableDeferred<Unit>
    ) : CacheAction<K, V>()

    class StoreMany<K, out V>(
      val entries: Map<K, V>,
      val deferred: CompletableDeferred<Unit>
    ) : CacheAction<K, V>()

    class FirstOrNull<out K, V>(
      val predicate: suspend (V) -> Boolean,
      val deferred: CompletableDeferred<V?>
    ) : CacheAction<K, V>()

    class IterateWhile<out K, V>(
      val iteratorFunc: suspend (V) -> Boolean,
      val deferred: CompletableDeferred<ModularResult<Unit>>
    ) : CacheAction<K, V>()

    class UpdateMany<K, V>(
      val keys: List<K>,
      val updateFunc: (V) -> Unit,
      val deferred: CompletableDeferred<Unit>
    ) : CacheAction<K, V>()

    class Contains<out K, out V>(
      val key: K,
      val deferred: CompletableDeferred<Boolean>
    ) : CacheAction<K, V>()

    class Size<out K, out V>(
      val deferred: CompletableDeferred<Int>
    ) : CacheAction<K, V>()

    class Delete<out K, out V>(
      val key: K,
      val deferred: CompletableDeferred<Unit>
    ) : CacheAction<K, V>()

    class DeleteMany<out K, out V>(
      val keys: List<K>,
      val deferred: CompletableDeferred<Unit>
    ) : CacheAction<K, V>()

    class Clear<out K, out V>(
      val deferred: CompletableDeferred<Unit>
    ) : CacheAction<K, V>()
  }

  companion object {
    private const val DEFAULT_CAPACITY = 128
    private const val DEFAULT_MAX_SIZE = 1024
  }
}