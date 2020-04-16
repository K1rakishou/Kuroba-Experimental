package com.github.adamantcheese.model.source.cache

import androidx.annotation.GuardedBy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

// TODO: tests!
class MultiMapCache<K1, K2, V>(
        private val maxValueCount: Int
) {
    private val mutex = Mutex()
    private val currentValuesCount = AtomicInteger(0)

    @GuardedBy("mutex")
    private val postCache = mutableMapOf<K1, MutableMap<K2, V>>()
    private val accessTimes = mutableMapOf<K1, Long>()

    suspend fun putIntoCache(key1: K1, key2: K2, value: V) {
        mutex.withLock {
            if (postCache[key1] == null) {
                postCache[key1] = mutableMapOf()
            }

            val count = if (!postCache[key1]!!.containsKey(key2)) {
                currentValuesCount.incrementAndGet()
            } else {
                currentValuesCount.get()
            }

            if (count > maxValueCount) {
                // Evict 1/4 of the cache
                var amountToEvict = (count / 100) * 25
                if (amountToEvict >= postCache.size) {
                    amountToEvict = postCache.size - 1
                }

                if (amountToEvict > 0) {
                    evictOld(amountToEvict)
                }
            }

            accessTimes[key1] = System.currentTimeMillis()
            postCache[key1]!![key2] = value
        }
    }

    suspend fun putIntoCacheMany(key1: K1, key2: K2, values: List<V>) {
        mutex.withLock {
            if (postCache[key1] == null) {
                postCache[key1] = mutableMapOf()
            }

            val count = if (!postCache[key1]!!.containsKey(key2)) {
                currentValuesCount.addAndGet(values.size)
            } else {
                currentValuesCount.get()
            }

            if (count > maxValueCount) {
                // Evict 1/4 of the cache
                val amountToEvict = (count / 100) * 25
                if (amountToEvict > 0) {
                    evictOld(amountToEvict)
                }
            }

            accessTimes[key1] = System.currentTimeMillis()
            values.forEach { value -> postCache[key1]!![key2] = value }
        }
    }

    suspend fun getFromCache(key1: K1, key2: K2): V? {
        return mutex.withLock {
            accessTimes[key1] = System.currentTimeMillis()
            return@withLock postCache[key1]?.get(key2)
        }
    }

    suspend fun getAll(key1: K1): List<V> {
        return mutex.withLock {
            return@withLock postCache[key1]?.values?.toList() ?: emptyList()
        }
    }

    private fun evictOld(amountToEvictParam: Int) {
        require(amountToEvictParam > 0) { "amountToEvictParam is too small: $amountToEvictParam" }
        require(mutex.isLocked) { "mutex must be locked!" }

        val keysSorted = accessTimes.entries
                // We will get the latest accessed key in the beginning of the list
                .sortedBy { (_, lastAccessTime) -> lastAccessTime }
                .map { (key, _) -> key }

        val keysToEvict = mutableListOf<K1>()
        var amountToEvict = amountToEvictParam

        for (key in keysSorted) {
            if (amountToEvict <= 0) {
                break
            }

            val count = postCache[key]?.size ?: 0

            keysToEvict += key
            amountToEvict -= count
            currentValuesCount.addAndGet(-count)
        }

        if (currentValuesCount.get() < 0) {
            currentValuesCount.set(0)
        }

        if (keysToEvict.isEmpty()) {
            return
        }

        keysToEvict.forEach { key ->
            postCache.remove(key)?.clear()
            accessTimes.remove(key)
        }
    }

    suspend fun getCachedValuesCount(): Int {
        return mutex.withLock {
            return@withLock currentValuesCount.get()
        }
    }
}