package com.github.adamantcheese.model.source.cache

interface SuspendableCacheSource<Key, Value> {
    suspend fun get(key: Key): Value?
    suspend fun store(key: Key, value: Value)
    suspend fun contains(key: Key): Boolean
    suspend fun size(): Int
    suspend fun delete(key: Key)
}