package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.core.site.loader.ChanLoaderException
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.chan.core.usecase.CatalogDataPreloader
import com.github.k1rakishou.chan.core.usecase.ThreadDataPreloader
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.flatMapNotNull
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
  private val _siteManager: Lazy<SiteManager>,
  private val _bookmarksManager: Lazy<BookmarksManager>,
  private val _postFilterManager: Lazy<PostFilterManager>,
  private val _savedReplyManager: Lazy<SavedReplyManager>,
  private val _chanThreadsCache: Lazy<ChanThreadsCache>,
  private val _chanPostRepository: Lazy<ChanPostRepository>,
  private val _chanThreadLoaderCoordinator: Lazy<ChanThreadLoaderCoordinator>,
  private val _threadDataPreloader: Lazy<ThreadDataPreloader>,
  private val _catalogDataPreloader: Lazy<CatalogDataPreloader>
) {

  private val siteManager: SiteManager
    get() = _siteManager.get()
  private val bookmarksManager: BookmarksManager
    get() = _bookmarksManager.get()
  private val postFilterManager: PostFilterManager
    get() = _postFilterManager.get()
  private val savedReplyManager: SavedReplyManager
    get() = _savedReplyManager.get()
  private val chanThreadsCache: ChanThreadsCache
    get() = _chanThreadsCache.get()
  private val chanPostRepository: ChanPostRepository
    get() = _chanPostRepository.get()
  private val chanThreadLoaderCoordinator: ChanThreadLoaderCoordinator
    get() = _chanThreadLoaderCoordinator.get()
  private val threadDataPreloader: ThreadDataPreloader
    get() = _threadDataPreloader.get()
  private val catalogDataPreloader: CatalogDataPreloader
    get() = _catalogDataPreloader.get()

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
      is ChanDescriptor.ICatalogDescriptor -> {
        // no-op
      }
    }
  }

  fun addRequestedChanDescriptor(chanDescriptor: ChanDescriptor): Boolean {
    BackgroundUtils.ensureMainThread()

    return requestedChanDescriptors.add(chanDescriptor)
  }

  fun removeRequestedChanDescriptor(chanDescriptor: ChanDescriptor) {
    BackgroundUtils.ensureMainThread()

    requestedChanDescriptors.remove(chanDescriptor)
  }

  @OptIn(ExperimentalTime::class)
  suspend fun loadThreadOrCatalog(
    page: Int?,
    compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor?,
    chanDescriptor: ChanDescriptor,
    chanCacheUpdateOptions: ChanCacheUpdateOptions,
    chanLoadOptions: ChanLoadOptions,
    chanCacheOptions: ChanCacheOptions,
    chanReadOptions: ChanReadOptions
  ): ThreadLoadResult {
    require(chanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor) {
      "CompositeCatalogDescriptor cannot be used here"
    }

    Logger.d(TAG, "loadThreadOrCatalog($page, $compositeCatalogDescriptor, $chanDescriptor, " +
      "$chanCacheUpdateOptions, $chanLoadOptions, $chanCacheOptions, $chanReadOptions)")

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

    val threadLoadResult = loadInternal(
      page = page,
      compositeCatalogDescriptor = compositeCatalogDescriptor,
      chanDescriptor = chanDescriptor,
      chanCacheUpdateOptions = chanCacheUpdateOptions,
      chanLoadOptions = chanLoadOptions,
      chanCacheOptions = chanCacheOptions,
      chanReadOptions = chanReadOptions
    )

    when (threadLoadResult) {
      is ThreadLoadResult.Loaded -> {
        when (val descriptor = threadLoadResult.chanDescriptor) {
          is ChanDescriptor.ThreadDescriptor -> {
            val preloadTime = measureTime { threadDataPreloader.postloadThreadInfo(descriptor) }
            Logger.d(TAG, "loadThreadOrCatalog(), descriptor=${descriptor} postloadThreadInfo took $preloadTime")
          }
          is ChanDescriptor.CatalogDescriptor -> {
            val preloadTime = measureTime { catalogDataPreloader.postloadCatalogInfo(descriptor) }
            Logger.d(TAG, "loadThreadOrCatalog(), descriptor=${descriptor} postloadCatalogInfo took $preloadTime")
          }
          is ChanDescriptor.CompositeCatalogDescriptor -> error("Cannot use CompositeCatalogDescriptor here")
        }
      }
      is ThreadLoadResult.Error -> {
        // no-op
      }
    }

    return threadLoadResult
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
      is ChanDescriptor.ICatalogDescriptor -> {
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

  fun getChanCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor?): ChanCatalog? {
    if (catalogDescriptor == null) {
      return null
    }

    return chanThreadsCache.getCatalog(catalogDescriptor)
  }

  suspend fun deletePost(postDescriptor: PostDescriptor): Boolean {
    val result = chanPostRepository.deletePost(postDescriptor)
    if (result is ModularResult.Error) {
      Logger.e(TAG, "Failed to delete post ($postDescriptor) from chanPostRepository")
      return false
    }

    postFilterManager.remove(postDescriptor)
    savedReplyManager.unsavePost(postDescriptor)

    return true
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

    val postDescriptor = PostDescriptor.create(chanDescriptor, postNo)

    return chanThreadsCache.getPostFromCache(chanDescriptor, postDescriptor)
  }

  fun findPostByPostDescriptor(postDescriptor: PostDescriptor): ChanPost? {
    return chanThreadsCache.getPostFromCache(postDescriptor.descriptor, postDescriptor)
  }

  fun getThreadPostsCount(descriptor: ChanDescriptor.ThreadDescriptor): Int {
    return chanThreadsCache.getThreadPostsCount(descriptor)
  }

  fun getLastPost(descriptor: ChanDescriptor.ThreadDescriptor): ChanPost? {
    return chanThreadsCache.getLastPost(descriptor)
  }

  fun findPostWithReplies(postDescriptor: PostDescriptor): Set<ChanPost> {
    val postsSet = hashSetOf<ChanPost>()

    when (val descriptor = postDescriptor.descriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        chanThreadsCache.getThread(descriptor)
          ?.findPostWithRepliesRecursive(postDescriptor, postsSet)
      }
      is ChanDescriptor.ICatalogDescriptor -> {
        getChanCatalog(descriptor)
          ?.findPostWithRepliesRecursive(postDescriptor, postsSet)
      }
    }

    return postsSet
  }

  fun getPostImages(imagesToGet: Collection<Pair<PostDescriptor, HttpUrl>>): List<ChanPostImage> {
    if (imagesToGet.isEmpty()) {
      return emptyList()
    }

    val postImages = mutableListOf<ChanPostImage>()

    imagesToGet.forEach { (postDescriptor, imageUrl) ->
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

  fun getPosts(postDescriptors: Collection<PostDescriptor>): List<ChanPost> {
    val postGroups = postDescriptors
      .groupBy { postDescriptor -> postDescriptor.threadDescriptor() }

    return postGroups.entries.flatMapNotNull { (threadDescriptor, postDescriptors) ->
      chanThreadsCache.getThread(threadDescriptor)?.getPosts(postDescriptors)
    }
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
      is ChanDescriptor.ICatalogDescriptor -> {
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
      is ChanDescriptor.ThreadDescriptor -> {
        return chanThreadsCache.getThread(descriptor)
          ?.getPost(postDescriptor)
          ?.isContentLoadedForLoader(loaderType)
          ?: false
      }
      is ChanDescriptor.ICatalogDescriptor -> {
        return chanThreadsCache.getCatalog(descriptor)
          ?.getPost(postDescriptor)
          ?.isContentLoadedForLoader(loaderType)
          ?: false
      }
    }
  }

  fun setContentLoadedForLoader(postDescriptor: PostDescriptor, loaderType: LoaderType) {
    when (val descriptor = postDescriptor.descriptor) {
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        error("Cannot use CompositeCatalogDescriptor here")
      }
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
    compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor?,
    chanDescriptor: ChanDescriptor,
    chanCacheUpdateOptions: ChanCacheUpdateOptions,
    chanLoadOptions: ChanLoadOptions,
    chanCacheOptions: ChanCacheOptions,
    chanReadOptions: ChanReadOptions
  ): ThreadLoadResult {
    awaitUntilDependenciesInitialized()

    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        Logger.d(TAG, "loadInternal() Requested thread /$chanDescriptor/")
      }
      is ChanDescriptor.CatalogDescriptor -> {
        Logger.d(TAG, "loadInternal() Requested catalog /$chanDescriptor/")
      }
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        error("Cannot use CompositeCatalogDescriptor here")
      }
    }

    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        val preloadTime = measureTime {
          threadDataPreloader.preloadThreadInfo(chanDescriptor, isCached(chanDescriptor))
        }
        Logger.d(TAG, "loadInternal(), chanDescriptor=${chanDescriptor} preloadThreadInfo took $preloadTime")
      }
      is ChanDescriptor.CatalogDescriptor -> {
        val preloadTime = measureTime { catalogDataPreloader.preloadCatalogInfo(chanDescriptor) }
        Logger.d(TAG, "loadInternal(), chanDescriptor=${chanDescriptor} preloadCatalogInfo took $preloadTime")
      }
      is ChanDescriptor.CompositeCatalogDescriptor -> error("Cannot use CompositeCatalogDescriptor here")
    }

    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (site == null) {
      val error = CommonClientException("Couldn't find site ${chanDescriptor.siteDescriptor()}")
      return ThreadLoadResult.Error(chanDescriptor, ChanLoaderException(error))
    }

    if (
      compositeCatalogDescriptor == null
      && !chanThreadsCache.cacheNeedsUpdate(chanDescriptor, chanCacheUpdateOptions)
    ) {
      Logger.d(TAG, "loadInternal() chanThreadsCache.cacheNeedsUpdate($chanDescriptor, " +
        "$chanCacheUpdateOptions) -> false")

      val result = tryRefreshCacheFromTheDatabase(
        page = page,
        chanDescriptor = chanDescriptor,
        chanLoadOptions = chanLoadOptions,
        // To avoid using UpdateIfCacheIsOlderThan which is ambiguous
        chanCacheUpdateOptions = ChanCacheUpdateOptions.DoNotUpdateCache
      )

      if (result != null) {
        return ThreadLoadResult.fromModularResult(chanDescriptor, result)
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

    val result = chanThreadLoaderCoordinator.loadThreadOrCatalog(
      page = page,
      site = site,
      compositeCatalogDescriptor = compositeCatalogDescriptor,
      chanDescriptor = chanDescriptor,
      chanCacheOptions = chanCacheOptions,
      // To avoid using UpdateIfCacheIsOlderThan which is ambiguous
      chanCacheUpdateOptions = ChanCacheUpdateOptions.UpdateCache,
      chanReadOptions = chanReadOptions,
      chanLoadOptions = chanLoadOptions
    )

    when (result) {
      is ModularResult.Error -> {
        val error = if (result.error is ChanLoaderException) {
          result.error as ChanLoaderException
        } else {
          ChanLoaderException(result.error)
        }

        return ThreadLoadResult.Error(chanDescriptor, error)
      }
      is ModularResult.Value -> {
        return result.value
      }
    }
  }

  private suspend fun tryRefreshCacheFromTheDatabase(
    page: Int?,
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

        val result = chanThreadLoaderCoordinator.reloadAndReparseThreadPosts(
          page = null,
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

        return chanThreadLoaderCoordinator.reloadAndReparseCatalogPosts(
          page = page,
          postParser = postParser,
          catalogDescriptor = chanDescriptor
        )
      }
      is ChanDescriptor.CompositeCatalogDescriptor -> error("Cannot use CompositeCatalogDescriptor here")
    }

    return null
  }

  companion object {
    private const val TAG = "ChanThreadManager"
    private const val CATALOG_PREVIEW_POSTS_COUNT = 6 // Original post + 5 last posts
  }
}