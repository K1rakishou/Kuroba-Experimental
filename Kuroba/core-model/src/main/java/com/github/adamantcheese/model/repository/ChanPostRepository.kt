package com.github.adamantcheese.model.repository

import android.util.LruCache
import androidx.annotation.GuardedBy
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.source.local.ChanPostLocalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChanPostRepository(
        database: KurobaDatabase,
        loggerTag: String,
        logger: Logger,
        private val localSource: ChanPostLocalSource
) : AbstractRepository(database, logger) {
    private val TAG = "$loggerTag ChanPostRepository"

    private val mutex = Mutex()

    @GuardedBy("mutex")
    private val postCache = mutableMapOf<ChanDescriptor, LruCache<PostDescriptor, ChanPostUnparsed>>()

    suspend fun insert(chanPostUnparsed: ChanPostUnparsed): ModularResult<Unit> {
        return withTransactionSafe {
            localSource.insert(chanPostUnparsed)
            mutex.withLock { postCache.putIntoCache(chanPostUnparsed) }
        }
    }

    fun insertOrUpdateManyBlocking(unparsedPosts: MutableList<ChanPostUnparsed>): ModularResult<Int> {
        return runBlocking {
            return@runBlocking withTransactionSafe {
                var updatedPosts = 0

                // TODO: insert all in one big batch
                unparsedPosts.forEach { chanPostUnparsed ->
                    val differsFromCached = mutex.withLock {
                        val fromCache = postCache.getFromCache(chanPostUnparsed.postDescriptor)
                                ?: return@withLock true

                        return@withLock fromCache != chanPostUnparsed
                    }

                    if (differsFromCached) {
                        localSource.insert(chanPostUnparsed)
                        mutex.withLock { postCache.putIntoCache(chanPostUnparsed) }

                        ++updatedPosts
                    }
                }

                return@withTransactionSafe updatedPosts
            }
        }
    }

    fun getCatalogOriginalPostsBlocking(
            descriptor: ChanDescriptor.CatalogDescriptor,
            threadIds: List<Long>
    ): ModularResult<List<ChanPostUnparsed>> {
        return runBlocking {
            return@runBlocking withTransactionSafe {
                return@withTransactionSafe localSource.getCatalogOriginalPosts(descriptor, threadIds)
            }
        }
    }

    fun getThreadPostsBlocking(
            descriptor: ChanDescriptor.ThreadDescriptor
    ): ModularResult<List<ChanPostUnparsed>> {
        return runBlocking {
            return@runBlocking withTransactionSafe {
                return@withTransactionSafe localSource.getThreadPosts(descriptor)
            }
        }
    }

    fun containsPostBlocking(
            postDescriptor: PostDescriptor
    ): ModularResult<Boolean> {
        return runBlocking {
            return@runBlocking withTransactionSafe {
                val containsInCache = mutex.withLock {
                    return@withLock postCache.getFromCache(postDescriptor) != null
                }

                if (containsInCache) {
                    return@withTransactionSafe true
                }

                return@withTransactionSafe localSource.containsPostBlocking(
                        postDescriptor.descriptor,
                        postDescriptor.postNo
                )
            }
        }
    }

    fun containsPostBlocking(
            descriptor: ChanDescriptor,
            postNo: Long
    ): ModularResult<Boolean> {
        return runBlocking {
            return@runBlocking withTransactionSafe {
                return@withTransactionSafe localSource.containsPostBlocking(descriptor, postNo)
            }
        }
    }

    fun deleteAllSync(): ModularResult<Int> {
        return runBlocking(Dispatchers.Default) { deleteAll() }
    }

    private fun MutableMap<ChanDescriptor, LruCache<PostDescriptor, ChanPostUnparsed>>.putIntoCache(
            chanPostUnparsed: ChanPostUnparsed
    ) {
        val postDescriptor = chanPostUnparsed.postDescriptor

        if (!this.containsKey(postDescriptor.descriptor)) {
            this[postDescriptor.descriptor] = LruCache(MAX_POSTS_IN_CACHE_PER_THREAD)
        }

        this[postDescriptor.descriptor]!!.put(postDescriptor, chanPostUnparsed)
    }

    private fun MutableMap<ChanDescriptor, LruCache<PostDescriptor, ChanPostUnparsed>>.getFromCache(
            postDescriptor: PostDescriptor
    ): ChanPostUnparsed? {
        return this[postDescriptor.descriptor]?.get(postDescriptor)
    }

    suspend fun deleteAll(): ModularResult<Int> {
        return withTransactionSafe {
            return@withTransactionSafe localSource.deleteAll()
        }
    }

    companion object {
        private const val MAX_POSTS_IN_CACHE_PER_THREAD = 2048
    }
}