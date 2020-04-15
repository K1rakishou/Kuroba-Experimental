package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.source.cache.MultiMapCache
import com.github.adamantcheese.model.source.local.ChanPostLocalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.math.min

class ChanPostRepository(
        database: KurobaDatabase,
        loggerTag: String,
        logger: Logger,
        private val localSource: ChanPostLocalSource,
        private val appConstants: AppConstants
) : AbstractRepository(database, logger) {
    private val TAG = "$loggerTag ChanPostRepository"
    private val postCache =
            MultiMapCache<ChanDescriptor, PostDescriptor, ChanPostUnparsed>(appConstants.maxPostsCountInPostsCache)

    fun getCachedValuesCount(): Int {
        return runBlocking { postCache.getCachedValuesCount() }
    }

    /**
     * Returns a list of posts that differ from the cached ones and which we want to parse again and
     * show the user (otherwise show cached posts)
     * */
    fun insertOrUpdateManyBlocking(
            unparsedPosts: MutableList<ChanPostUnparsed>,
            isCatalog: Boolean
    ): ModularResult<List<Long>> {
        return runBlocking {
            return@runBlocking withTransactionSafe {
                if (isCatalog) {
                    return@withTransactionSafe insertOrUpdateCatalogOriginalPostsBlocking(unparsedPosts)
                } else {
                    return@withTransactionSafe insertOrUpdateThreadPostsBlocking(unparsedPosts)
                }
            }
        }
    }

    private suspend fun insertOrUpdateCatalogOriginalPostsBlocking(
            unparsedPosts: MutableList<ChanPostUnparsed>
    ): List<Long> {
        if (unparsedPosts.isEmpty()) {
            return emptyList()
        }

        require(unparsedPosts.all { post -> post.isOp }) { "Not all posts are original posts" }
        localSource.insertManyOriginalPosts(unparsedPosts)

        if (unparsedPosts.isNotEmpty()) {
            unparsedPosts.forEach { post ->
                postCache.putIntoCache(post.postDescriptor.descriptor, post.postDescriptor, post)
            }
        }

        return unparsedPosts.map { it.postDescriptor.postNo }
    }

    private suspend fun insertOrUpdateThreadPostsBlocking(
            unparsedPosts: MutableList<ChanPostUnparsed>
    ): List<Long> {
        val listCapacity = min(unparsedPosts.size / 2, 10)
        var originalPost: ChanPostUnparsed? = null
        val postsThatDifferWithCache = ArrayList<ChanPostUnparsed>(listCapacity)

        // Figure out what posts differ from the cache that we want to update in the
        // database
        unparsedPosts.forEach { chanPostUnparsed ->
            val differsFromCached = kotlin.run {
                val fromCache = postCache.getFromCache(
                        chanPostUnparsed.postDescriptor.descriptor,
                        chanPostUnparsed.postDescriptor
                )

                if (fromCache == null) {
                    // Posts is not cached yet - update
                    return@run true
                }

                if (fromCache.isOp) {
                    // The post is an original post - always update
                    return@run true
                }

                if (fromCache != chanPostUnparsed) {
                    // Cached post is not the same as the fresh post - update
                    return@run true
                }

                return@run false
            }

            if (differsFromCached) {
                if (chanPostUnparsed.isOp) {
                    if (originalPost != null) {
                        throw IllegalStateException("More than one OP found!")
                    }

                    originalPost = chanPostUnparsed
                } else {
                    postsThatDifferWithCache += chanPostUnparsed
                }
            }
        }

        val chanThreadId = if (originalPost != null) {
            localSource.insertOriginalPost(originalPost!!)
        } else {
            if (postsThatDifferWithCache.isNotEmpty()) {
                localSource.getThreadIdByPostDescriptor(
                        postsThatDifferWithCache.first().postDescriptor
                )
            } else {
                null
            }
        }

        if (chanThreadId == null) {
            return if (originalPost == null) {
                emptyList()
            } else {
                listOf(originalPost!!.postDescriptor.postNo)
            }
        }

        if (postsThatDifferWithCache.isNotEmpty()) {
            localSource.insertPosts(chanThreadId, postsThatDifferWithCache)

            postsThatDifferWithCache.forEach { post ->
                postCache.putIntoCache(post.postDescriptor.descriptor, post.postDescriptor, post)
            }
        }

        return postsThatDifferWithCache.map { it.postDescriptor.postNo }
    }

    fun getCatalogOriginalPostsBlocking(
            descriptor: ChanDescriptor.CatalogDescriptor,
            threadNoList: List<Long>
    ): ModularResult<List<ChanPostUnparsed>> {
        return runBlocking {
            return@runBlocking withTransactionSafe {
                val originalPostsFromCache = postCache.getAll(descriptor)

                val originalPostNoFromCacheSet = originalPostsFromCache.map { post ->
                    post.postDescriptor.postNo
                }.toSet()

                val originalPostNoListToGetFromDatabase = threadNoList.filter { threadNo ->
                    threadNo !in originalPostNoFromCacheSet
                }

                if (originalPostNoListToGetFromDatabase.isEmpty()) {
                    // All posts were found in the cache
                    return@withTransactionSafe originalPostsFromCache
                }

                val originalPostsFromDatabase = localSource.getCatalogOriginalPosts(
                        descriptor,
                        originalPostNoListToGetFromDatabase
                )

                if (originalPostsFromDatabase.isNotEmpty()) {
                    val postDescriptor = originalPostsFromDatabase.first().postDescriptor

                    postCache.putIntoCacheMany(
                            postDescriptor.descriptor,
                            postDescriptor,
                            originalPostsFromDatabase
                    )
                }

                return@withTransactionSafe originalPostsFromCache + originalPostsFromDatabase
            }
        }
    }

    fun getThreadPostsBlocking(
            descriptor: ChanDescriptor.ThreadDescriptor,
            postNoList: List<Long>
    ): ModularResult<List<ChanPostUnparsed>> {
        return runBlocking {
            return@runBlocking withTransactionSafe {
                val postsFromCache = postCache.getAll(descriptor)
                val postNoFromCacheSet = postsFromCache.map { post -> post.postDescriptor.postNo }.toSet()

                val postNoListToGetFromDatabase = postNoList.filter { postNo ->
                    postNo !in postNoFromCacheSet
                }

                if (postNoListToGetFromDatabase.isEmpty()) {
                    // All posts were found in the cache
                    return@withTransactionSafe postsFromCache
                }

                val postsFromDatabase = localSource.getThreadPosts(descriptor, postNoListToGetFromDatabase)
                if (postsFromDatabase.isNotEmpty()) {
                    val postDescriptor = postsFromDatabase.first().postDescriptor

                    postCache.putIntoCacheMany(
                            postDescriptor.descriptor,
                            postDescriptor,
                            postsFromDatabase
                    )
                }

                return@withTransactionSafe postsFromCache + postsFromDatabase
            }
        }
    }

    fun containsPostBlocking(
            postDescriptor: PostDescriptor
    ): ModularResult<Boolean> {
        return runBlocking {
            return@runBlocking withTransactionSafe {
                val containsInCache = postCache.getFromCache(
                        postDescriptor.descriptor,
                        postDescriptor
                ) != null

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

    suspend fun deleteAll(): ModularResult<Int> {
        return withTransactionSafe {
            return@withTransactionSafe localSource.deleteAll()
        }
    }
}