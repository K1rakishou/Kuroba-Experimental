package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.source.cache.PostsCache
import com.github.adamantcheese.model.source.local.ChanPostLocalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ChanPostRepository(
        database: KurobaDatabase,
        loggerTag: String,
        logger: Logger,
        private val localSource: ChanPostLocalSource,
        private val appConstants: AppConstants
) : AbstractRepository(database, logger) {
    private val TAG = "$loggerTag ChanPostRepository"
    private val postCache = PostsCache(appConstants.maxPostsCountInPostsCache)

    fun getCachedValuesCount(): Int {
        return runBlocking { postCache.getCachedValuesCount() }
    }

    /**
     * Returns a list of posts that differ from the cached ones and which we want to parse again and
     * show the user (otherwise show cached posts)
     * */
    fun insertOrUpdateManyBlocking(
            posts: MutableList<ChanPost>,
            isCatalog: Boolean
    ): ModularResult<List<Long>> {
        return runBlocking {
            return@runBlocking withTransactionSafe {
                if (isCatalog) {
                    return@withTransactionSafe insertOrUpdateCatalogOriginalPostsBlocking(posts)
                } else {
                    return@withTransactionSafe insertOrUpdateThreadPostsBlocking(posts)
                }
            }
        }
    }

    suspend fun insertOrUpdateCatalogOriginalPostsBlocking(
            posts: MutableList<ChanPost>
    ): List<Long> {
        if (posts.isEmpty()) {
            return emptyList()
        }

        require(posts.all { post -> post.isOp }) { "Not all posts are original posts" }
        localSource.insertManyOriginalPosts(posts)

        if (posts.isNotEmpty()) {
            posts.forEach { post ->
                postCache.putIntoCache(post.postDescriptor, post)
            }
        }

        return posts.map { it.postDescriptor.postNo }
    }

    suspend fun insertOrUpdateThreadPostsBlocking(
            posts: MutableList<ChanPost>
    ): List<Long> {
        var originalPost: ChanPost? = null
        val postsThatDifferWithCache = ArrayList<ChanPost>()

        // Figure out what posts differ from the cache that we want to update in the
        // database
        posts.forEach { chanPost ->
            val differsFromCached = postDiffersFromCached(chanPost)
            if (differsFromCached) {
                if (chanPost.isOp) {
                    if (originalPost != null) {
                        throw IllegalStateException("More than one OP found!")
                    }

                    originalPost = chanPost
                } else {
                    postsThatDifferWithCache += chanPost
                }
            }
        }

        val chanThreadId = if (originalPost != null) {
            val chanThreadId = localSource.insertOriginalPost(originalPost!!)
            postCache.putIntoCache(
                    originalPost!!.postDescriptor,
                    originalPost!!
            )

            chanThreadId
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
                postCache.putIntoCache(post.postDescriptor, post)
            }
        }

        return postsThatDifferWithCache.map { it.postDescriptor.postNo }
    }

    fun getCachedPostBlocking(postDescriptor: PostDescriptor, isOP: Boolean): ChanPost? {
        return runBlocking { postCache.getPostFromCache(postDescriptor, isOP) }
    }

    suspend fun postDiffersFromCached(chanPost: ChanPost): Boolean {
        val fromCache = postCache.getPostFromCache(
                chanPost.postDescriptor,
                chanPost.isOp
        )

        if (fromCache == null) {
            // Posts is not cached yet - update
            return true
        }

        if (fromCache.isOp) {
            // The post is an original post - always update
            return true
        }

        if (fromCache != chanPost) {
            // Cached post is not the same as the fresh post - update
            return true
        }

        return false
    }

    fun getCatalogOriginalPostsBlocking(
            descriptor: ChanDescriptor.CatalogDescriptor,
            threadNoList: List<Long>
    ): ModularResult<List<ChanPost>> {
        return runBlocking {
            return@runBlocking withTransactionSafe {
                val originalPostsFromCache = threadNoList.mapNotNull { threadNo ->
                    postCache.getOriginalPostFromCache(descriptor.toThreadDescriptor(threadNo))
                }

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
                    originalPostsFromDatabase.forEach { post ->
                        postCache.putIntoCache(post.postDescriptor, post)
                    }
                }

                return@withTransactionSafe originalPostsFromCache + originalPostsFromDatabase
            }
        }
    }

    fun getThreadPostsBlocking(
            descriptor: ChanDescriptor.ThreadDescriptor,
            postNoList: List<Long>
    ): ModularResult<List<ChanPost>> {
        return runBlocking {
            return@runBlocking withTransactionSafe {
                val postsFromCache = postCache.getAll(descriptor)
                val postNoFromCacheSet = postsFromCache.map { post ->
                    post.postDescriptor.postNo
                }.toSet()

                val postNoListToGetFromDatabase = postNoList.filter { postNo ->
                    postNo !in postNoFromCacheSet
                }

                if (postNoListToGetFromDatabase.isEmpty()) {
                    // All posts were found in the cache
                    return@withTransactionSafe postsFromCache
                }

                val postsFromDatabase = localSource.getThreadPosts(
                        descriptor,
                        postNoListToGetFromDatabase
                )

                if (postsFromDatabase.isNotEmpty()) {
                    postsFromDatabase.forEach { post ->
                        postCache.putIntoCache(post.postDescriptor, post)
                    }
                }

                return@withTransactionSafe postsFromCache + postsFromDatabase
            }
        }
    }

    fun containsPostBlocking(postDescriptor: PostDescriptor, isOP: Boolean): ModularResult<Boolean> {
        return runBlocking {
            return@runBlocking withTransactionSafe {
                val containsInCache = postCache.getPostFromCache(
                        postDescriptor,
                        isOP
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

    fun filterAlreadyCachedPostNo(
            threadDescriptor: ChanDescriptor.ThreadDescriptor,
            archivePostsNoList: List<Long>
    ): ModularResult<List<Long>> {
        return runBlocking {
            return@runBlocking withTransactionSafe {
                val cachedInMemorySet = postCache.getAll(threadDescriptor)
                        .map { post -> post.postDescriptor.postNo }.toSet()

                val notInMemoryCached = archivePostsNoList.filter { archivePostNo ->
                    archivePostNo !in cachedInMemorySet
                }

                if (notInMemoryCached.isEmpty()) {
                    return@withTransactionSafe emptyList<Long>()
                }

                val cachedInDatabase = localSource.getThreadPostNoList(threadDescriptor)
                        .toSet()

                return@withTransactionSafe notInMemoryCached.filter { archivePostNo ->
                    archivePostNo !in cachedInDatabase
                }
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