package com.github.adamantcheese.database.source.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import kotlin.coroutines.CoroutineContext

/**
 * Locks-free auto trimmable poor man's cache implementation
 * */
@Suppress("EXPERIMENTAL_API_USAGE")
open class GenericCacheSource<Key, Value>(
        private val capacity: Int = DEFAULT_CAPACITY,
        private val maxSize: Int = DEFAULT_MAX_SIZE,
        private val cacheEntriesToRemovePerTrim: Int = maxSize / 20
) : CoroutineScope, SuspendableCacheSource<Key, Value> {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    init {
        check(maxSize >= cacheEntriesToRemovePerTrim)  {
            "maxSize (${maxSize}) must be greater than cacheEntriesToRemovePerTrim (${cacheEntriesToRemovePerTrim})"
        }
    }

    private val actor = actor<CacheAction<Key, Value>>(capacity = Channel.UNLIMITED) {
        val cache = LinkedHashMap<Key, Value>(capacity)

        consumeEach { action ->
            when (action) {
                is CacheAction.Get<Key, Value> -> {
                    action.deferred.complete(cache[action.key])
                }
                is CacheAction.Store -> {
                    cache[action.key] = action.value
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

        return deferred.await()
    }

    override suspend fun store(key: Key, value: Value) {
        val deferred = CompletableDeferred<Unit>()
        actor.send(CacheAction.Store(key, value, deferred))

        deferred.await()
    }

    override suspend fun contains(key: Key): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        actor.send(CacheAction.Contains(key, deferred))

        return deferred.await()
    }

    override suspend fun size(): Int {
        val deferred = CompletableDeferred<Int>()
        actor.send(CacheAction.Size(deferred))

        return deferred.await()
    }

    override suspend fun delete(key: Key) {
        val deferred = CompletableDeferred<Unit>()
        actor.send(CacheAction.Delete(key, deferred))

        deferred.await()
    }

    private sealed class CacheAction<out K, out V> {
        class Get<out K, V>(
                val key: K,
                val deferred: CompletableDeferred<V?>
        ) : CacheAction<K, V>()

        class Store<out K, out V>(
                val key: K,
                val value: V,
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
    }

    companion object {
        private const val DEFAULT_CAPACITY = 128
        private const val DEFAULT_MAX_SIZE = 1024
    }
}