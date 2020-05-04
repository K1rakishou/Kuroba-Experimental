package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.common.SuspendableInitializer
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.source.cache.PostsCache
import com.github.adamantcheese.model.source.local.ChanPostLocalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

class ChanPostRepository(
        database: KurobaDatabase,
        loggerTag: String,
        logger: Logger,
        private val applicationScope: CoroutineScope,
        private val localSource: ChanPostLocalSource,
        private val appConstants: AppConstants
) : AbstractRepository(database, logger) {
    private val TAG = "$loggerTag ChanPostRepository"
    private val suspendableInitializer = SuspendableInitializer<Unit>("${TAG}_initializer")
    private val postCache = PostsCache(appConstants.maxPostsCountInPostsCache)

    init {
        applicationScope.launch {
            val result = tryWithTransaction { deleteOldPostsIfNeeded() }
            suspendableInitializer.initWithModularResult(result)
        }
    }

    suspend fun getCachedValuesCount(): Int {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized postCache.getCachedValuesCount()
        }
    }

    /**
     * Returns a list of posts that differ from the cached ones and which we want to parse again and
     * show the user (otherwise show cached posts)
     * */
    suspend fun insertOrUpdateMany(
            posts: MutableList<ChanPost>,
            isCatalog: Boolean
    ): ModularResult<List<Long>> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized tryWithTransaction {
                if (isCatalog) {
                    return@tryWithTransaction insertOrUpdateCatalogOriginalPosts(posts)
                } else {
                    return@tryWithTransaction insertOrUpdateThreadPosts(posts)
                }
            }
        }
    }

    suspend fun getCachedPost(postDescriptor: PostDescriptor, isOP: Boolean): ChanPost? {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized postCache.getPostFromCache(postDescriptor, isOP)
        }
    }

    suspend fun getCatalogOriginalPosts(
            descriptor: ChanDescriptor.CatalogDescriptor,
            threadNoList: List<Long>
    ): ModularResult<List<ChanPost>> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized tryWithTransaction {
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
                    return@tryWithTransaction originalPostsFromCache
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

                return@tryWithTransaction originalPostsFromCache + originalPostsFromDatabase
            }
        }
    }

    suspend fun getThreadPosts(
            threadDescriptor: ChanDescriptor.ThreadDescriptor,
            postNoSet: Set<Long>
    ): ModularResult<List<ChanPost>> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized tryWithTransaction {
                val fromCache = postCache.getPostsFromCache(threadDescriptor, postNoSet)
                if (fromCache.size == postNoSet.size) {
                    return@tryWithTransaction fromCache
                }

                val getFromDatabasePostList = postNoSet.subtract(
                        fromCache.map { post -> post.postDescriptor.postNo }
                )

                val postsFromDatabase = localSource.getThreadPosts(
                        threadDescriptor,
                        getFromDatabasePostList
                )

                if (postsFromDatabase.isNotEmpty()) {
                    postsFromDatabase.forEach { post ->
                        postCache.putIntoCache(post.postDescriptor, post)
                    }
                }

                return@tryWithTransaction fromCache + postsFromDatabase
            }
        }
        }

    suspend fun getThreadPosts(
            descriptor: ChanDescriptor.ThreadDescriptor,
            maxCount: Int
    ): ModularResult<List<ChanPost>> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized tryWithTransaction {
                val postsFromDatabase = localSource.getThreadPosts(
                        descriptor,
                        maxCount
                )

                if (postsFromDatabase.isNotEmpty()) {
                    postsFromDatabase.forEach { post ->
                        postCache.putIntoCache(post.postDescriptor, post)
                    }
                }

                return@tryWithTransaction postsFromDatabase
            }
        }
    }

    suspend fun containsPostBlocking(postDescriptor: PostDescriptor, isOP: Boolean): ModularResult<Boolean> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized tryWithTransaction {
                val containsInCache = postCache.getPostFromCache(
                        postDescriptor,
                        isOP
                ) != null

                if (containsInCache) {
                    return@tryWithTransaction true
                }

                return@tryWithTransaction localSource.containsPostBlocking(
                        postDescriptor.descriptor,
                        postDescriptor.postNo
                )
            }
        }
    }

    suspend fun containsPost(
            descriptor: ChanDescriptor,
            postNo: Long
    ): ModularResult<Boolean> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized tryWithTransaction {
                return@tryWithTransaction localSource.containsPostBlocking(descriptor, postNo)
            }
        }
    }

    suspend fun filterAlreadyCachedPostNo(
            threadDescriptor: ChanDescriptor.ThreadDescriptor,
            archivePostsNoList: List<Long>
    ): ModularResult<List<Long>> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized tryWithTransaction {
                val cachedInMemorySet = postCache.getAll(threadDescriptor)
                        .map { post -> post.postDescriptor.postNo }.toSet()

                val notInMemoryCached = archivePostsNoList.filter { archivePostNo ->
                    archivePostNo !in cachedInMemorySet
                }

                if (notInMemoryCached.isEmpty()) {
                    return@tryWithTransaction emptyList<Long>()
                }

                val cachedInDatabase = localSource.getThreadPostNoList(threadDescriptor)
                        .toSet()

                return@tryWithTransaction notInMemoryCached.filter { archivePostNo ->
                    archivePostNo !in cachedInDatabase
                }
            }
        }
    }

    suspend fun deleteAll(): ModularResult<Int> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized tryWithTransaction {
                return@tryWithTransaction localSource.deleteAll()
            }
        }
    }

    private suspend fun insertOrUpdateCatalogOriginalPosts(
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

    private suspend fun insertOrUpdateThreadPosts(
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

    private suspend fun deleteOldPostsIfNeeded() {
        require(isInTransaction()) { "Not in transaction" }

        val totalAmountOfPostsInDatabase = localSource.countTotalAmountOfPosts()
        val maxPostsAmount = appConstants.maxAmountOfPostsInDatabase

        if (totalAmountOfPostsInDatabase < maxPostsAmount) {
            logger.log(TAG, "Not enough posts to start deleting, " +
                    "posts in database amount: $totalAmountOfPostsInDatabase, " +
                    "max allowed posts amount: $maxPostsAmount")
            return
        }

        // Delete half of the posts in the database
        val toDeleteCount = (max(totalAmountOfPostsInDatabase, maxPostsAmount) / 2)

        logger.log(TAG, "Starting deleting $toDeleteCount posts " +
                "(totalAmountOfPostsInDatabase = $totalAmountOfPostsInDatabase, " +
                "maxPostsAmount = $maxPostsAmount)")

        val deleteResult = Try { localSource.deleteOldPosts(toDeleteCount) }
        val deletedPostsCount = if (deleteResult is ModularResult.Error) {
            logger.logError(TAG, "Error while trying to delete old posts", deleteResult.error)
            throw deleteResult.error
        } else {
            (deleteResult as ModularResult.Value).value
        }

        val newAmount = localSource.countTotalAmountOfPosts()
        logger.log(TAG, "Deleted $deletedPostsCount posts, $newAmount posts left")
    }

    private suspend fun postDiffersFromCached(chanPost: ChanPost): Boolean {
        val fromCache = postCache.getPostFromCache(
                chanPost.postDescriptor,
                chanPost.isOp
        )

        if (fromCache == null) {
            // Post is not cached yet - update
            return true
        }

        if (fromCache.isOp) {
            // Cached post is an original post - always update
            return true
        }

        if (fromCache != chanPost) {
            // Cached post is not the same as the fresh post - update
            return true
        }

        return false
    }
}