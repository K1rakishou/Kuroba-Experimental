package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.core.site.loader.ChanLoaderException
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.thread.ChanCatalog
import com.github.k1rakishou.model.data.thread.ChanThread
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.source.cache.ChanCacheOptions
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache

/**
 * The only manager class that can hold other manager classes. Do not use this class in other manager
 * classes to avoid circular dependencies.
 * */
class ChanThreadManager(
  private val siteManager: SiteManager,
  private val bookmarksManager: BookmarksManager,
  private val postFilterManager: PostFilterManager,
  private val savedReplyManager: SavedReplyManager,
  private val chanThreadsCache: ChanThreadsCache,
  private val chanPostRepository: ChanPostRepository,
  private val chanThreadLoaderCoordinator: ChanThreadLoaderCoordinator
) {
  @get:Synchronized
  @set:Synchronized
  var currentCatalogDescriptor: ChanDescriptor.CatalogDescriptor? = null
    private set

  @get:Synchronized
  @set:Synchronized
  var currentThreadDescriptor: ChanDescriptor.ThreadDescriptor? = null
    private set

  fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> currentThreadDescriptor = chanDescriptor
      is ChanDescriptor.CatalogDescriptor -> currentCatalogDescriptor = chanDescriptor
    }
  }

  fun unbindChanDescriptor(chanDescriptor: ChanDescriptor) {
    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> currentThreadDescriptor = null
      is ChanDescriptor.CatalogDescriptor -> currentCatalogDescriptor = null
    }
  }

  suspend fun reloadFromNetwork(
    chanDescriptor: ChanDescriptor,
    reloadOptions: ChanReloadOptions,
    cacheOptions: ChanCacheOptions,
    onReloaded: suspend (ThreadLoadResult) -> Unit
  ) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "reloadFromNetwork($chanDescriptor, $reloadOptions, $cacheOptions)")

    if (reloadOptions.isNotDefault()) {
      postFilterManager.removeAllForDescriptor(chanDescriptor)
    }

    if (reloadOptions.canClearCache()) {
      when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> chanThreadsCache.deleteThread(chanDescriptor)
        is ChanDescriptor.CatalogDescriptor -> chanThreadsCache.deleteCatalog(chanDescriptor)
      }
    }

    if (reloadOptions.canClearDatabase()) {
      when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> chanPostRepository.deleteThread(chanDescriptor)
        is ChanDescriptor.CatalogDescriptor -> chanPostRepository.deleteCatalog(chanDescriptor)
      }
    }

    when (val threadLoaderResult = reloadFromNetworkInternal(chanDescriptor, cacheOptions)) {
      is ModularResult.Value -> {
        onReloaded.invoke(threadLoaderResult.value)
      }
      is ModularResult.Error -> {
        val error = threadLoaderResult.error
        if (error is ChanLoaderException) {
          onReloaded.invoke(ThreadLoadResult.Error(error))
        } else {
          onReloaded.invoke(ThreadLoadResult.Error(ChanLoaderException(error)))
        }
      }
    }
  }

  fun iteratePostsWhile(
    chanDescriptor: ChanDescriptor,
    postDescriptors: Collection<PostDescriptor>,
    iterator: (ChanPost) -> Boolean
  ) {
    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        val chanThread = chanThreadsCache.getThread(chanDescriptor)
          ?: return

        for (postDescriptor in postDescriptors) {
          val post = chanThread.getPost(postDescriptor)
            ?: continue

          if (!iterator(post)) {
            return
          }
        }
      }
      is ChanDescriptor.CatalogDescriptor -> {
        val chanCatalog = chanThreadsCache.getCatalog(chanDescriptor)
          ?: return

        for (postDescriptor in postDescriptors) {
          val post = chanCatalog.getPost(postDescriptor)
            ?: continue

          if (!iterator(post)) {
            return
          }
        }
      }
    }
  }

  fun getChanThread(threadDescriptor: ChanDescriptor.ThreadDescriptor?): ChanThread? {
    if (threadDescriptor == null) {
      return null
    }

    return chanThreadsCache.getThread(threadDescriptor)
  }

  fun getChanCatalog(catalogDescriptor: ChanDescriptor.CatalogDescriptor?): ChanCatalog? {
    if (catalogDescriptor == null) {
      return null
    }

    return chanThreadsCache.getCatalog(catalogDescriptor)
  }

  suspend fun deletePost(postDescriptor: PostDescriptor) {
    val result = chanPostRepository.deletePost(postDescriptor)
    if (result is ModularResult.Error) {
      Logger.e(TAG, "Failed to delete post ($postDescriptor) from chanPostRepository")
      return
    }

    postFilterManager.remove(postDescriptor)
    savedReplyManager.unsavePost(postDescriptor)
  }

  fun isCached(chanDescriptor: ChanDescriptor?): Boolean {
    if (chanDescriptor == null) {
      return false
    }

    return chanThreadsCache.contains(chanDescriptor)
  }

  fun findPostByPostNo(chanDescriptor: ChanDescriptor?, postNo: Long): ChanPost? {
    if (chanDescriptor == null) {
      return null
    }

    return chanThreadsCache.getPostFromCache(chanDescriptor, postNo)
  }

  fun getThreadPostsCount(descriptor: ChanDescriptor.ThreadDescriptor): Int {
    return chanThreadsCache.getThreadPostsCount(descriptor)
  }

  fun getLastPost(descriptor: ChanDescriptor.ThreadDescriptor): ChanPost? {
    return chanThreadsCache.getLastPost(descriptor)
  }

  fun findPostWithReplies(descriptor: ChanDescriptor, postNo: Long): Set<ChanPost> {
    val postsSet = hashSetOf<ChanPost>()

    when (descriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        chanThreadsCache.getThread(descriptor)?.findPostWithRepliesRecursive(postNo, postsSet)
      }
      is ChanDescriptor.CatalogDescriptor -> {
        getChanCatalog(descriptor)?.findPostWithRepliesRecursive(postNo, postsSet)
      }
    }

    return postsSet
  }

  fun getPost(postDescriptor: PostDescriptor): ChanPost? {
    return chanThreadsCache.getThread(postDescriptor.threadDescriptor())?.getPost(postDescriptor)
  }

  /**
   * Just an optimization to not convert an immutable list of posts into a mutable list of posts
   * when applying filters to posts. You should prefer immutable version of this method in all
   * different places.
   * */
  fun getMutableListOfPosts(chanDescriptor: ChanDescriptor): MutableList<ChanPost> {
    val listOfPosts = mutableListWithCap<ChanPost>(128)

    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        val chanThread = chanThreadsCache.getThread(chanDescriptor)
          ?: return mutableListOf()

        chanThread.iteratePostsOrdered { chanPost -> listOfPosts += chanPost }
      }
      is ChanDescriptor.CatalogDescriptor -> {
        val chanCatalog = getChanCatalog(chanDescriptor)
          ?: return mutableListOf()

        chanCatalog.iteratePostsOrdered { chanOriginalPost -> listOfPosts += chanOriginalPost }
      }
    }

    return listOfPosts
  }

  fun getNewPostsCount(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    lastPostNo: Long
  ): Int {
    return chanThreadsCache.getThread(threadDescriptor)?.getNewPostsCount(lastPostNo) ?: 0
  }

  private suspend fun reloadFromNetworkInternal(
    chanDescriptor: ChanDescriptor,
    cacheOptions: ChanCacheOptions
  ): ModularResult<ThreadLoadResult> {
    siteManager.awaitUntilInitialized()
    bookmarksManager.awaitUntilInitialized()

    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        Logger.d(TAG, "reloadFromNetworkInternal() Requested thread /$chanDescriptor/")
      }
      is ChanDescriptor.CatalogDescriptor -> {
        Logger.d(TAG, "reloadFromNetworkInternal() Requested catalog /$chanDescriptor/")
      }
    }

    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (site == null) {
      val error = CommonClientException("Couldn't find site ${chanDescriptor.siteDescriptor()}")
      return ModularResult.value(ThreadLoadResult.Error(ChanLoaderException(error)))
    }

    val url = ChanThreadLoaderCoordinator.getChanUrl(site, chanDescriptor).toString()

    // Notify the listeners that loader is starting to fetch data from the server
    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      bookmarksManager.onThreadIsFetchingData(chanDescriptor)
    }

    return chanThreadLoaderCoordinator.loadThread(
      url,
      chanDescriptor,
      cacheOptions,
      site.chanReader()
    )
  }

  companion object {
    private const val TAG = "ChanThreadManager"
  }
}

enum class ChanReloadOptions {
  RetainAll,
  ClearMemoryCache,
  ClearDatabaseCache;

  fun isNotDefault(): Boolean {
    return this != RetainAll
  }

  fun canClearCache(): Boolean {
    return this == ClearMemoryCache || this == ClearDatabaseCache
  }

  fun canClearDatabase(): Boolean {
    return this == ClearDatabaseCache
  }
}