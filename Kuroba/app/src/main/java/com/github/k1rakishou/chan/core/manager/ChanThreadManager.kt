package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.core.site.loader.ChanLoaderException
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.chan.core.usecase.CatalogDataPreloader
import com.github.k1rakishou.chan.core.usecase.ThreadDataPreloader
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.catalog.ChanCatalog
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.options.ChanLoadOption
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.options.ChanReadOptions
import com.github.k1rakishou.model.data.options.PostsToReloadOptions
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.LoaderType
import com.github.k1rakishou.model.data.thread.ChanThread
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import com.github.k1rakishou.model.util.ChanPostUtils
import dagger.Lazy
import okhttp3.HttpUrl
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ChanThreadManager(
  private val verboseLogs: Boolean,
  private val siteManager: SiteManager,
  private val bookmarksManager: BookmarksManager,
  private val postFilterManager: PostFilterManager,
  private val savedReplyManager: SavedReplyManager,
  private val chanThreadsCache: ChanThreadsCache,
  private val chanPostRepository: ChanPostRepository,
  private val chanThreadLoaderCoordinator: Lazy<ChanThreadLoaderCoordinator>,
  private val threadDataPreloader: Lazy<ThreadDataPreloader>,
  private val catalogDataPreloader: Lazy<CatalogDataPreloader>
) {

  // Only accessed on the main thread
  private val requestedChanDescriptors = hashSetOf<ChanDescriptor>()

  suspend fun awaitUntilDependenciesInitialized() {
    siteManager.awaitUntilInitialized()
    bookmarksManager.awaitUntilInitialized()
    chanPostRepository.awaitUntilInitialized()
  }

  fun isThreadLockCurrentlyLocked(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean {
    return chanThreadsCache.isThreadLockCurrentlyLocked(threadDescriptor)
  }

  fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        chanPostRepository.updateThreadLastAccessTime(chanDescriptor)
      }
      is ChanDescriptor.CatalogDescriptor -> {
        // no-op
      }
    }
  }

  suspend fun isRequestAlreadyActive(chanDescriptor: ChanDescriptor): Boolean {
    if (!requestedChanDescriptors.add(chanDescriptor)) {
      // This chan descriptor has already been requested
      if (verboseLogs) {
        Logger.d(TAG, "loadThreadOrCatalog() skipping $chanDescriptor because it was already requested")
      }

      return true
    }

    return false
  }

  @OptIn(ExperimentalTime::class)
  suspend fun loadThreadOrCatalog(
    page: Int?,
    chanDescriptor: ChanDescriptor,
    chanCacheUpdateOptions: ChanCacheUpdateOptions,
    chanLoadOptions: ChanLoadOptions,
    chanCacheOptions: ChanCacheOptions,
    chanReadOptions: ChanReadOptions
  ): ThreadLoadResult {
    try {
      Logger.d(TAG, "loadThreadOrCatalog($page, $chanDescriptor, $chanCacheUpdateOptions, " +
        "$chanLoadOptions, $chanCacheOptions, $chanReadOptions)")

      if (chanLoadOptions.canClearCache()) {
        Logger.d(TAG, "loadThreadOrCatalog() postFilterManager.removeAllForDescriptor()")
        postFilterManager.removeAllForDescriptor(chanDescriptor)
      } else if (chanLoadOptions.isForceUpdating(postDescriptor = null)) {
        val postDescriptors = (chanLoadOptions.chanLoadOption as ChanLoadOption.ForceUpdatePosts).postDescriptors
        if (postDescriptors == null) {
          Logger.d(TAG, "loadThreadOrCatalog() postFilterManager.removeAllForDescriptor()")
          postFilterManager.removeAllForDescriptor(chanDescriptor)
        } else {
          Logger.d(TAG, "loadThreadOrCatalog() postFilterManager.removeMany()")
          postFilterManager.removeMany(postDescriptors)
        }
      }

      if (chanLoadOptions.canClearCache()) {
        Logger.d(TAG, "loadThreadOrCatalog() deleting posts from the cache")

        if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
          when (chanLoadOptions.chanLoadOption) {
            ChanLoadOption.ClearMemoryCache -> {
              chanThreadsCache.deleteThread(chanDescriptor)
            }
            is ChanLoadOption.ForceUpdatePosts -> {
              // no-op
            }
            ChanLoadOption.RetainAll -> error("Can't retain all here")
          }
        }
      }

      val threadLoaderResult = loadInternal(
        page = page,
        chanDescriptor = chanDescriptor,
        chanCacheUpdateOptions = chanCacheUpdateOptions,
        chanLoadOptions = chanLoadOptions,
        chanCacheOptions = chanCacheOptions,
        chanReadOptions = chanReadOptions
      )

      when (threadLoaderResult) {
        is ModularResult.Value -> {
          when (chanDescriptor) {
            is ChanDescriptor.ThreadDescriptor -> {
              val preloadTime = measureTime { threadDataPreloader.get().postloadThreadInfo(chanDescriptor) }
              Logger.d(TAG, "loadThreadOrCatalog(), chanDescriptor=${chanDescriptor} postloadThreadInfo took $preloadTime")
            }
            is ChanDescriptor.CatalogDescriptor -> {
              val preloadTime = measureTime { catalogDataPreloader.get().postloadCatalogInfo(chanDescriptor) }
              Logger.d(TAG, "loadThreadOrCatalog(), chanDescriptor=${chanDescriptor} postloadCatalogInfo took $preloadTime")
            }
          }

          return threadLoaderResult.value
        }
        is ModularResult.Error -> {
          val error = threadLoaderResult.error
          if (error is ChanLoaderException) {
            return ThreadLoadResult.Error(chanDescriptor, error)
          } else {
            return ThreadLoadResult.Error(chanDescriptor, ChanLoaderException(error))
          }
        }
      }
    } finally {
      requestedChanDescriptors.remove(chanDescriptor)
    }
  }

  fun iteratePostsWhile(
    chanDescriptor: ChanDescriptor,
    iterator: (ChanPost) -> Boolean
  ) {
    iteratePostsWhile(chanDescriptor, null, iterator)
  }

  fun iteratePostsWhile(
    chanDescriptor: ChanDescriptor,
    postDescriptors: Collection<PostDescriptor>?,
    iterator: (ChanPost) -> Boolean
  ) {
    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        val chanThread = chanThreadsCache.getThread(chanDescriptor)
          ?: return

        if (postDescriptors != null) {
          for (postDescriptor in postDescriptors) {
            val post = chanThread.getPost(postDescriptor)
              ?: continue

            if (!iterator(post)) {
              return
            }
          }
        } else {
          chanThread.iteratePostsOrderedWhile(iterator)
        }
      }
      is ChanDescriptor.CatalogDescriptor -> {
        val chanCatalog = chanThreadsCache.getCatalog(chanDescriptor)
          ?: return

        if (postDescriptors != null) {
          for (postDescriptor in postDescriptors) {
            val post = chanCatalog.getPost(postDescriptor)
              ?: continue

            if (!iterator(post)) {
              return
            }
          }
        } else {
          chanCatalog.iteratePostsOrderedWhile(iterator)
        }
      }
    }
  }

  fun getSafeToUseThreadSubject(threadDescriptor: ChanDescriptor.ThreadDescriptor): String? {
    val originalPost = chanThreadsCache.getThread(threadDescriptor)
      ?.getOriginalPost()
      ?: return null

    return ChanPostUtils.getSafeToUseTitle(originalPost)
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

  fun getPostImages(imageToGet: Collection<Pair<PostDescriptor, HttpUrl>>): List<ChanPostImage> {
    if (imageToGet.isEmpty()) {
      return emptyList()
    }

    val postImages = mutableListOf<ChanPostImage>()

    imageToGet.forEach { (postDescriptor, imageUrl) ->
      val chanPostImage = chanThreadsCache.getThread(postDescriptor.threadDescriptor())
        ?.getPostImage(postDescriptor, imageUrl)

      if (chanPostImage != null) {
        postImages += chanPostImage
      }
    }

    return postImages
  }

  fun getPost(postDescriptor: PostDescriptor): ChanPost? {
    return chanThreadsCache.getThread(postDescriptor.threadDescriptor())?.getPost(postDescriptor)
  }

  fun getCatalogPreviewPosts(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ChanPost> {
    val chanThread = chanThreadsCache.getThread(threadDescriptor)
      ?: return emptyList()

    val postsCount = chanThread.postsCount

    if (postsCount < CATALOG_PREVIEW_POSTS_COUNT) {
      return chanThread.slicePosts(0..CATALOG_PREVIEW_POSTS_COUNT)
    }

    return chanThread.slicePosts(
      0 until 1,
      (postsCount - CATALOG_PREVIEW_POSTS_COUNT)..postsCount
    )
  }

  fun <T> iteratePostIndexes(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    input: Collection<T>,
    postDescriptorSelector: (T) -> PostDescriptor,
    iterator: (ChanPost, Int) -> Unit,
  ) {
    chanThreadsCache.getThread(threadDescriptor)
      ?.iteratePostIndexes(input, threadDescriptor, postDescriptorSelector, iterator)
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

  fun isContentLoadedForLoader(postDescriptor: PostDescriptor, loaderType: LoaderType): Boolean {
    when (val descriptor = postDescriptor.descriptor) {
      is ChanDescriptor.CatalogDescriptor -> {
        return chanThreadsCache.getCatalog(descriptor)
          ?.getPost(postDescriptor)
          ?.isContentLoadedForLoader(loaderType)
          ?: false
      }
      is ChanDescriptor.ThreadDescriptor -> {
        return chanThreadsCache.getThread(descriptor)
          ?.getPost(postDescriptor)
          ?.isContentLoadedForLoader(loaderType)
          ?: false
      }
    }
  }

  fun setContentLoadedForLoader(postDescriptor: PostDescriptor, loaderType: LoaderType) {
    when (val descriptor = postDescriptor.descriptor) {
      is ChanDescriptor.CatalogDescriptor -> {
        chanThreadsCache.getCatalog(descriptor)
          ?.getPost(postDescriptor)
          ?.setContentLoadedForLoader(loaderType)
      }
      is ChanDescriptor.ThreadDescriptor -> {
        chanThreadsCache.getThread(descriptor)
          ?.getPost(postDescriptor)
          ?.setContentLoadedForLoader(loaderType)
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun loadInternal(
    page: Int?,
    chanDescriptor: ChanDescriptor,
    chanCacheUpdateOptions: ChanCacheUpdateOptions,
    chanLoadOptions: ChanLoadOptions,
    chanCacheOptions: ChanCacheOptions,
    chanReadOptions: ChanReadOptions
  ): ModularResult<ThreadLoadResult> {
    awaitUntilDependenciesInitialized()

    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        Logger.d(TAG, "loadInternal() Requested thread /$chanDescriptor/")
      }
      is ChanDescriptor.CatalogDescriptor -> {
        Logger.d(TAG, "loadInternal() Requested catalog /$chanDescriptor/")
      }
    }

    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        val preloadTime = measureTime {
          threadDataPreloader.get().preloadThreadInfo(chanDescriptor, isCached(chanDescriptor))
        }
        Logger.d(TAG, "loadInternal(), chanDescriptor=${chanDescriptor} preloadThreadInfo took $preloadTime")
      }
      is ChanDescriptor.CatalogDescriptor -> {
        val preloadTime = measureTime { catalogDataPreloader.get().preloadCatalogInfo(chanDescriptor) }
        Logger.d(TAG, "loadInternal(), chanDescriptor=${chanDescriptor} preloadCatalogInfo took $preloadTime")
      }
    }

    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (site == null) {
      val error = CommonClientException("Couldn't find site ${chanDescriptor.siteDescriptor()}")
      return ModularResult.value(ThreadLoadResult.Error(chanDescriptor, ChanLoaderException(error)))
    }

    if (!chanThreadsCache.cacheNeedsUpdate(chanDescriptor, chanCacheUpdateOptions)) {
      Logger.d(TAG, "loadInternal() chanThreadsCache.cacheNeedsUpdate($chanDescriptor, " +
        "$chanCacheUpdateOptions) -> false")

      val result = tryRefreshCacheFromTheDatabase(
        chanDescriptor = chanDescriptor,
        chanLoadOptions = chanLoadOptions,
        // To avoid using UpdateIfCacheIsOlderThan which is ambiguous
        chanCacheUpdateOptions = ChanCacheUpdateOptions.DoNotUpdateCache
      )

      if (result != null) {
        return result
      }

      // fallthrough
    }

    Logger.d(TAG, "loadInternal() chanThreadsCache.cacheNeedsUpdate($chanDescriptor, " +
      "$chanCacheUpdateOptions) -> true")

    // Notify the bookmarksManager that loader is starting to fetch data from the server so that
    //  bookmarksManager can start loading bookmark info for this thread
    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      bookmarksManager.onThreadIsFetchingData(chanDescriptor)
    }

    return chanThreadLoaderCoordinator.get().loadThreadOrCatalog(
      page = page,
      site = site,
      chanDescriptor = chanDescriptor,
      chanCacheOptions = chanCacheOptions,
      // To avoid using UpdateIfCacheIsOlderThan which is ambiguous
      chanCacheUpdateOptions = ChanCacheUpdateOptions.UpdateCache,
      chanReadOptions = chanReadOptions,
      chanLoadOptions = chanLoadOptions
    )
  }

  private suspend fun tryRefreshCacheFromTheDatabase(
    chanDescriptor: ChanDescriptor,
    chanLoadOptions: ChanLoadOptions,
    chanCacheUpdateOptions: ChanCacheUpdateOptions
  ): ModularResult<ThreadLoadResult>? {
    // Do not load new posts from the network, just refresh memory caches with data from the
    //  database
    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        val siteDescriptor = chanDescriptor.siteDescriptor()

        val postParser = siteManager.bySiteDescriptor(siteDescriptor)
          ?.chanReader()
          ?.getParser()

        if (postParser == null) {
          val threadLoadResult = ThreadLoadResult.Error(
            chanDescriptor,
            ChanLoaderException(SiteManager.SiteNotFoundException(siteDescriptor))
          )

          return ModularResult.value(threadLoadResult)
        }

        val postsToReloadOptions = when (val chanLoadOption = chanLoadOptions.chanLoadOption) {
          is ChanLoadOption.ForceUpdatePosts -> {
            val postDescriptors = chanLoadOption.postDescriptors
            if (postDescriptors == null) {
              PostsToReloadOptions.ReloadAll
            } else {
              PostsToReloadOptions.Reload(postDescriptors.toSet())
            }
          }
          ChanLoadOption.ClearMemoryCache -> PostsToReloadOptions.ReloadAll
          ChanLoadOption.RetainAll -> return null
        }

        val result = chanThreadLoaderCoordinator.get().reloadAndReparseThreadPosts(
          postParser = postParser,
          threadDescriptor = chanDescriptor,
          cacheUpdateOptions = chanCacheUpdateOptions,
          postsToReloadOptions = postsToReloadOptions
        )

        if (result is ModularResult.Error) {
          return result
        }

        val threadLoadResult = (result as ModularResult.Value).value
        if (threadLoadResult !is ThreadLoadResult.Error || !threadLoadResult.exception.isCacheEmptyException()) {
          return result
        }

        // fallthrough
      }
      is ChanDescriptor.CatalogDescriptor -> {
        val siteDescriptor = chanDescriptor.siteDescriptor()

        val postParser = siteManager.bySiteDescriptor(siteDescriptor)
          ?.chanReader()
          ?.getParser()

        if (postParser == null) {
          val threadLoadResult = ThreadLoadResult.Error(
            chanDescriptor,
            ChanLoaderException(SiteManager.SiteNotFoundException(siteDescriptor))
          )

          return ModularResult.value(threadLoadResult)
        }

        return chanThreadLoaderCoordinator.get().reloadAndReparseCatalogPosts(
          postParser = postParser,
          catalogDescriptor = chanDescriptor
        )
      }
    }

    return null
  }

  companion object {
    private const val TAG = "ChanThreadManager"
    private const val CATALOG_PREVIEW_POSTS_COUNT = 6 // Original post + 5 last posts
  }
}