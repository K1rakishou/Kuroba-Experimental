/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.presenter

import android.content.Context
import android.text.TextUtils
import androidx.annotation.StringRes
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.helper.ChanThreadTicker
import com.github.k1rakishou.chan.core.helper.LastViewedPostNoInfoHolder
import com.github.k1rakishou.chan.core.helper.PostHideHelper
import com.github.k1rakishou.chan.core.helper.ThumbnailLongtapOptionsHelper
import com.github.k1rakishou.chan.core.loader.LoaderBatchResult
import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.LoaderResult.Succeeded
import com.github.k1rakishou.chan.core.manager.*
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.loader.ChanLoaderException
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.chan.core.site.parser.MockReplyManager
import com.github.k1rakishou.chan.ui.adapter.PostAdapter.PostAdapterCallback
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.ThreadStatusCell
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper
import com.github.k1rakishou.chan.ui.layout.ThreadListLayout.ThreadListLayoutPresenterCallback
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.HeaderFloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.*
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeParser
import com.github.k1rakishou.model.data.board.pages.BoardPage
import com.github.k1rakishou.model.data.descriptor.*
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.filter.FilterType
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.options.ChanReadOptions
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.util.ChanPostUtils
import com.github.k1rakishou.model.util.ChanPostUtils.getReadableFileSize
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class ThreadPresenter @Inject constructor(
  private val cacheHandler: CacheHandler,
  private val bookmarksManager: BookmarksManager,
  private val pageRequestManager: PageRequestManager,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val savedReplyManager: SavedReplyManager,
  private val postHideManager: PostHideManager,
  private val chanPostRepository: ChanPostRepository,
  private val chanCatalogSnapshotRepository: ChanCatalogSnapshotRepository,
  private val mockReplyManager: MockReplyManager,
  private val archivesManager: ArchivesManager,
  private val onDemandContentLoaderManager: OnDemandContentLoaderManager,
  private val seenPostsManager: SeenPostsManager,
  private val historyNavigationManager: HistoryNavigationManager,
  private val postFilterManager: PostFilterManager,
  private val chanFilterManager: ChanFilterManager,
  private val lastViewedPostNoInfoHolder: LastViewedPostNoInfoHolder,
  private val chanThreadViewableInfoManager: ChanThreadViewableInfoManager,
  private val postHideHelper: PostHideHelper,
  private val chanThreadManager: ChanThreadManager,
  private val globalWindowInsetsManager: GlobalWindowInsetsManager,
  private val thumbnailLongtapOptionsHelper: ThumbnailLongtapOptionsHelper,
  private val themeEngine: ThemeEngine
) : PostAdapterCallback,
  PostCellCallback,
  ThreadStatusCell.Callback,
  ThreadListLayoutPresenterCallback,
  CoroutineScope {

  private val chanThreadTicker by lazy {
    ChanThreadTicker(
      scope = this,
      isDevFlavor = isDevBuild(),
      archivesManager = archivesManager,
      chanThreadManager = chanThreadManager,
      action = this::onChanTickerTick
    )
  }

  private var threadPresenterCallback: ThreadPresenterCallback? = null
  private var forcePageUpdate = false
  private var order = PostsFilter.Order.BUMP
  private var currentFocusedController = CurrentFocusedController.None
  private var currentLoadThreadJob: Job? = null

  var chanThreadLoadingState = ChanThreadLoadingState.Uninitialized
    private set

  private val verboseLogs by lazy { ChanSettings.verboseLogs.get() }
  private val compositeDisposable = CompositeDisposable()
  private val job = SupervisorJob()

  private lateinit var postOptionsClickExecutor: RendezvousCoroutineExecutor
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor
  private lateinit var context: Context

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("ThreadPresenter")

  val isBound: Boolean
    get() {
      val currentDescriptor = chanThreadTicker.currentChanDescriptor
      if (currentDescriptor == null) {
        if (verboseLogs) {
          Logger.e(TAG, "isBound() currentChanDescriptor == null")
        }

        return false
      }

      if (!chanThreadManager.isCached(currentDescriptor)) {
        if (verboseLogs) {
          Logger.e(TAG, "isBound() currentChanDescriptor (${currentDescriptor}) is not cached")
        }

        return false
      }

      return true
    }

  val isPinned: Boolean
    get() {
      val threadDescriptor = chanThreadTicker.currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
        ?: return false

      return bookmarksManager.exists(threadDescriptor)
    }

  override val currentChanDescriptor: ChanDescriptor?
    get() = chanThreadTicker.currentChanDescriptor

  init {
    launch {
      chanFilterManager.listenForFiltersChanges()
        .debounce(1000L)
        .collect { filterEvent -> onFiltersChanged(filterEvent) }
    }
  }

  fun create(context: Context, threadPresenterCallback: ThreadPresenterCallback) {
    this.context = context
    this.threadPresenterCallback = threadPresenterCallback
  }

  fun showNoContent() {
    threadPresenterCallback?.showEmpty()
  }

  fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
    BackgroundUtils.ensureMainThread()

    if (chanDescriptor == chanThreadTicker.currentChanDescriptor) {
      return
    }

    threadPresenterCallback?.showLoading()

    this.currentLoadThreadJob?.cancel()
    this.currentLoadThreadJob = null

    postOptionsClickExecutor = RendezvousCoroutineExecutor(this)
    serializedCoroutineExecutor = SerializedCoroutineExecutor(this)

    if (chanThreadTicker.currentChanDescriptor != null) {
      unbindChanDescriptor(false)
    }

    chanThreadManager.bindChanDescriptor(chanDescriptor)

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      bookmarksManager.setCurrentOpenThreadDescriptor(chanDescriptor)
    }

    compositeDisposable.add(
      onDemandContentLoaderManager.listenPostContentUpdates()
        .subscribe(
          { batchResult -> onPostUpdatedWithNewContent(batchResult) },
          { error -> Logger.e(TAG, "Post content updates error", error) }
        )
    )

    Logger.d(TAG, "chanThreadTicker.startTicker($chanDescriptor)")
    chanThreadTicker.startTicker(chanDescriptor)
  }

  fun unbindChanDescriptor(isDestroying: Boolean) {
    BackgroundUtils.ensureMainThread()

    val currentChanDescriptor = chanThreadTicker.currentChanDescriptor
    if (currentChanDescriptor != null) {
      chanThreadManager.unbindChanDescriptor(currentChanDescriptor)
      onDemandContentLoaderManager.cancelAllForDescriptor(currentChanDescriptor)

      if (currentChanDescriptor is ChanDescriptor.ThreadDescriptor) {
        bookmarksManager.setCurrentOpenThreadDescriptor(null)
      }

      Logger.d(TAG, "chanThreadTicker.stopTicker($currentChanDescriptor)")
      chanThreadTicker.stopTicker(resetCurrentChanDescriptor = true)
    }

    if (isDestroying) {
      job.cancelChildren()

      if (::postOptionsClickExecutor.isInitialized) {
        postOptionsClickExecutor.stop()
      }

      if (::serializedCoroutineExecutor.isInitialized) {
        serializedCoroutineExecutor.stop()
      }
    }

    compositeDisposable.clear()
    chanThreadLoadingState = ChanThreadLoadingState.Uninitialized
  }

  private suspend fun onChanTickerTick(chanDescriptor: ChanDescriptor) {
    Logger.d(TAG, "onChanTickerTick($chanDescriptor)")

    chanPostRepository.awaitUntilInitialized()

    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> preloadThreadInfo(chanDescriptor)
      is ChanDescriptor.CatalogDescriptor -> preloadCatalogInfo(chanDescriptor)
    }

    normalLoad()
  }

  private suspend fun preloadThreadInfo(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    Logger.d(TAG, "preloadThreadInfo($threadDescriptor) begin")

    supervisorScope {
      val jobs = mutableListOf<Deferred<Unit>>()

      jobs += async(Dispatchers.IO) { seenPostsManager.preloadForThread(threadDescriptor) }
      jobs += async(Dispatchers.IO) { chanThreadViewableInfoManager.preloadForThread(threadDescriptor) }
      jobs += async(Dispatchers.IO) { savedReplyManager.preloadForThread(threadDescriptor) }
      jobs += async(Dispatchers.IO) { postHideManager.preloadForThread(threadDescriptor) }

      // Only preload when this thread is not yet in cache
      if (!chanThreadManager.isCached(threadDescriptor)) {
        jobs += async(Dispatchers.IO) {
          chanPostRepository.preloadForThread(threadDescriptor).unwrap()
        }
      }

      ModularResult.Try { jobs.awaitAll() }
        .peekError { error -> Logger.e(TAG, "preloadThreadInfo() error", error) }
        .ignore()
    }

    Logger.d(TAG, "preloadThreadInfo($threadDescriptor) end")
  }

  private suspend fun preloadCatalogInfo(catalogDescriptor: ChanDescriptor.CatalogDescriptor) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "preloadCatalogInfo($catalogDescriptor) begin")

    supervisorScope {
      val jobs = mutableListOf<Deferred<Unit>>()

      jobs += async(Dispatchers.IO) { postHideManager.preloadForCatalog(catalogDescriptor) }
      jobs += async(Dispatchers.IO) {
        chanCatalogSnapshotRepository.preloadChanCatalogSnapshot(catalogDescriptor)
          .peekError { error -> Logger.e(TAG, "preloadChanCatalogSnapshot($catalogDescriptor) error", error) }
          .ignore()

        return@async
      }

      ModularResult.Try { jobs.awaitAll() }
        .peekError { error -> Logger.e(TAG, "preloadCatalogInfo() error", error) }
        .ignore()
    }

    Logger.d(TAG, "preloadCatalogInfo($catalogDescriptor) end")
  }

  override fun quickReload(showLoading: Boolean, chanCacheUpdateOptions: ChanCacheUpdateOptions) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "quickReload($showLoading, $chanCacheUpdateOptions)")

    val currentChanDescriptor = chanThreadTicker.currentChanDescriptor
    if (currentChanDescriptor == null) {
      Logger.d(TAG, "quickReload() chanThreadTicker.currentChanDescriptor==null")
      return
    }

    launch {
      normalLoad(
        showLoading = showLoading,
        chanCacheUpdateOptions = chanCacheUpdateOptions
      )
    }
  }

  fun resetTicker() {
    chanThreadTicker.resetTicker()
  }

  /**
   * A very flexible method to load new posts or reload posts from database.
   * [chanLoadOptions] allows you to delete previous posts from in-memory cache or from database
   * (for example when something goes wrong and some post data gets corrupted).
   * [chanCacheOptions] allows you to select where posts will be stored (in in-memory cache or/and
   * in the database).
   * [chanReadOptions] allows you to configure how many posts to extract out of the posts list that
   * we get from the server. This is very useful when you want to set a thread max posts capacity
   * or if you want to store in the cache only a part of a thread (for example you want to show a
   * thread preview).
   * */
  fun normalLoad(
    showLoading: Boolean = false,
    chanCacheUpdateOptions: ChanCacheUpdateOptions = ChanCacheUpdateOptions.UpdateCache,
    chanLoadOptions: ChanLoadOptions = ChanLoadOptions.retainAll(),
    chanCacheOptions: ChanCacheOptions = ChanCacheOptions.default(),
    chanReadOptions: ChanReadOptions = ChanReadOptions.default()
  ) {
    BackgroundUtils.ensureMainThread()

    val currentChanDescriptor = chanThreadTicker.currentChanDescriptor
    if (currentChanDescriptor == null) {
      Logger.d(TAG, "normalLoad() chanThreadTicker.currentChanDescriptor==null")
      return
    }

    Logger.d(TAG, "normalLoad(currentChanDescriptor=$currentChanDescriptor, showLoading=$showLoading, " +
      "chanCacheUpdateOptions=$chanCacheUpdateOptions, chanLoadOptions=$chanLoadOptions, " +
      "chanCacheOptions=$chanCacheOptions, chanReadOptions=$chanReadOptions)")

    chanThreadLoadingState = ChanThreadLoadingState.Loading

    currentLoadThreadJob = launch {
      if (showLoading) {
        threadPresenterCallback?.showLoading()
      }

      if (chanThreadManager.isRequestAlreadyActive(currentChanDescriptor)) {
        return@launch
      }

      chanThreadManager.loadThreadOrCatalog(
        chanDescriptor = currentChanDescriptor,
        chanCacheUpdateOptions = chanCacheUpdateOptions,
        chanLoadOptions = chanLoadOptions,
        chanCacheOptions = chanCacheOptions,
        chanReadOptions = chanReadOptions
      ) { threadLoadResult ->
        Logger.d(TAG, "normalLoad() threadLoadResult=$threadLoadResult")

        if (threadLoadResult is ThreadLoadResult.Error) {
          onChanLoaderError(threadLoadResult.exception)

          chanThreadLoadingState = ChanThreadLoadingState.Loaded
          currentLoadThreadJob = null

          return@loadThreadOrCatalog
        }

        threadLoadResult as ThreadLoadResult.Loaded
        val successfullyProcessedNewPosts = onChanLoaderData(threadLoadResult.chanDescriptor)

        if (!successfullyProcessedNewPosts) {
          val error = ClientException("Failed to load thread because of unknown error. See logs for more info.")
          onChanLoaderError(error)
        }

        chanThreadLoadingState = ChanThreadLoadingState.Loaded
        currentLoadThreadJob = null
      }
    }
  }

  fun onForegroundChanged(foreground: Boolean) {
    if (!isBound) {
      return
    }

    if (foreground && isWatching()) {
      chanThreadTicker.resetTicker()
    } else {
      chanThreadTicker.stopTicker(resetCurrentChanDescriptor = false)
    }
  }

  suspend fun pin(): Boolean {
    val threadDescriptor = currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
      ?: return false

    if (!bookmarksManager.isReady()) {
      return false
    }

    if (bookmarksManager.exists(threadDescriptor)) {
      bookmarksManager.deleteBookmark(threadDescriptor)
      return true
    }

    if (!isBound) {
      return false
    }

    chanPostRepository.createEmptyThreadIfNotExists(threadDescriptor)
      .safeUnwrap { error ->
        Logger.e(TAG, "createEmptyThreadIfNotExists($threadDescriptor) error", error)
        return false
      }

    val op = chanThreadManager.getChanThread(threadDescriptor)?.getOriginalPost()
    if (op != null) {
      bookmarksManager.createBookmark(
        threadDescriptor,
        ChanPostUtils.getTitle(op, threadDescriptor),
        op.firstImage()?.actualThumbnailUrl
      )

      return true
    }

    bookmarksManager.createBookmark(threadDescriptor)
    return true
  }

  suspend fun setOrder(order: PostsFilter.Order, isManuallyChangedOrder: Boolean) {
    if (this.order != order) {
      this.order = order

      if (isBound) {
        if (isManuallyChangedOrder) {
          scrollTo(0, false)
        }

        showPosts()
      }
    }
  }

  suspend fun refreshUI() {
    showPosts()
  }

  fun showAlbum() {
    val postDescriptors = threadPresenterCallback?.displayingPostDescriptors
    val position = threadPresenterCallback?.currentPosition

    if (postDescriptors == null || position == null) {
      return
    }

    val displayPosition = position[0]
    val images: MutableList<ChanPostImage> = ArrayList()
    var index = 0

    for (i in postDescriptors.indices) {
      val postDescriptor = postDescriptors[i]
      val postImages = chanThreadManager.getPost(postDescriptor)?.postImages
        ?: continue

      images.addAll(postImages)

      if (i == displayPosition) {
        index = images.size
      }
    }

    threadPresenterCallback?.showAlbum(images, index)
  }

  override fun onPostBind(postDescriptor: PostDescriptor) {
    BackgroundUtils.ensureMainThread()

    if (currentChanDescriptor == null) {
      return
    }

    onDemandContentLoaderManager.onPostBind(postDescriptor)
    seenPostsManager.onPostBind(postDescriptor)
  }

  override fun onPostUnbind(postDescriptor: PostDescriptor, isActuallyRecycling: Boolean) {
    BackgroundUtils.ensureMainThread()

    currentChanDescriptor?.let { descriptor ->
      onDemandContentLoaderManager.onPostUnbind(postDescriptor, isActuallyRecycling)
      seenPostsManager.onPostUnbind(postDescriptor)
    }
  }

  private suspend fun onFiltersChanged(filterEvent: ChanFilterManager.FilterEvent) {
    if (filterEvent is ChanFilterManager.FilterEvent.Initialized) {
      return
    }

    chanPostRepository.awaitUntilInitialized()
    Logger.d(TAG, "onFiltersChanged($currentChanDescriptor) clearing posts cache")

    var shouldShowLoadingIndicator = false

    val catalogDescriptor = chanThreadManager.currentCatalogDescriptor
    if (catalogDescriptor != null) {
      if (catalogDescriptor == currentChanDescriptor) {
        shouldShowLoadingIndicator = true
      }

      normalLoad(showLoading = false)
    }

    val threadDescriptor = chanThreadManager.currentThreadDescriptor
    if (threadDescriptor != null) {
      if (threadDescriptor == currentChanDescriptor) {
        shouldShowLoadingIndicator = true
      }

      normalLoad(showLoading = false)
    }

    if (shouldShowLoadingIndicator) {
      threadPresenterCallback?.showLoading()
    }
  }

  private fun onPostUpdatedWithNewContent(batchResult: LoaderBatchResult) {
    BackgroundUtils.ensureMainThread()

    if (threadPresenterCallback != null && needUpdatePost(batchResult)) {
      val updatedPost = chanThreadManager.getPost(batchResult.postDescriptor)
        ?: return

      serializedCoroutineExecutor.post {
        threadPresenterCallback?.onPostUpdated(updatedPost, batchResult.results)
      }
    }
  }

  private fun needUpdatePost(batchResult: LoaderBatchResult): Boolean {
    return batchResult.results.any { it is Succeeded && it.needUpdateView }
  }

  private suspend fun onChanLoaderError(error: ChanLoaderException) {
    BackgroundUtils.ensureMainThread()

    if (error is CancellationException) {
      return
    }

    when {
      error is ClientException -> {
        Logger.e(TAG, "onChanLoaderError() called, error=${error.errorMessageOrClassName()}")
      }
      error.isCloudFlareError() -> {
        Logger.e(TAG, "onChanLoaderError() called CloudFlareDetectedException")
      }
      else -> {
        Logger.e(TAG, "onChanLoaderError() called", error)
      }
    }

    threadPresenterCallback?.showError(error)
  }

  private suspend fun onChanLoaderData(loadedChanDescriptor: ChanDescriptor): Boolean {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "onChanLoaderData() called, loadedChanDescriptor=$loadedChanDescriptor")

    if (!isBound) {
      Logger.e(TAG, "onChanLoaderData() not bound!")
      return false
    }

    val localChanDescriptor = currentChanDescriptor
    if (localChanDescriptor == null) {
      Logger.e(TAG, "onChanLoaderData() currentChanDescriptor==null")
      return false
    }

    if (localChanDescriptor != loadedChanDescriptor) {
      Logger.e(TAG, "onChanLoaderData() localChanDescriptor " +
        "($localChanDescriptor) != loadedChanDescriptor ($loadedChanDescriptor)")
      return false
    }

    val newPostsCount = getNewPostsCount(localChanDescriptor)

    if (isWatching()) {
      val shouldResetTimer = newPostsCount > 0
      chanThreadTicker.kickTicker(resetTimer = shouldResetTimer)
    }

    showPosts()

    if (localChanDescriptor is ChanDescriptor.ThreadDescriptor) {
      if (newPostsCount > 0 && localChanDescriptor.threadNo == loadedChanDescriptor.threadNoOrNull()) {
        threadPresenterCallback?.showNewPostsNotification(true, newPostsCount)
      }

      if (localChanDescriptor.threadNo == loadedChanDescriptor.threadNoOrNull()) {
        if (forcePageUpdate) {
          pageRequestManager.forceUpdateForBoard(localChanDescriptor.boardDescriptor)
          forcePageUpdate = false
        }
      }
    }

    chanThreadViewableInfoManager.getAndConsumeMarkedPostNo(localChanDescriptor) { markedPostNo ->
      handleMarkedPost(markedPostNo)
    }

    createNewNavHistoryElement(localChanDescriptor)

    if (localChanDescriptor is ChanDescriptor.ThreadDescriptor) {
      updateBookmarkInfoIfNecessary(localChanDescriptor)
    }

    return true
  }

  private fun getNewPostsCount(chanDescriptor: ChanDescriptor): Int {
    var newPostsCount = 0

    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return newPostsCount
    }

    chanThreadViewableInfoManager.update(chanDescriptor) { chanThreadViewableInfo ->
      val lastLoadedPostNo = chanThreadViewableInfo.lastLoadedPostNo
      if (lastLoadedPostNo > 0) {
        newPostsCount = chanThreadManager.getNewPostsCount(chanDescriptor, lastLoadedPostNo)
      }

      chanThreadViewableInfo.lastLoadedPostNo = chanThreadManager.getLastPost(chanDescriptor)
        ?.postNo()
        ?: -1L

      if (chanThreadViewableInfo.lastViewedPostNo < 0L) {
        chanThreadViewableInfo.lastViewedPostNo = chanThreadViewableInfo.lastLoadedPostNo
      }
    }

    return newPostsCount
  }

  private fun handleMarkedPost(markedPostNo: Long) {
    val markedPost = chanThreadManager.findPostByPostNo(currentChanDescriptor, markedPostNo)
    if (markedPost == null) {
      Logger.e(TAG, "handleMarkedPost() Failed to find post ($currentChanDescriptor, $markedPostNo)")
      return
    }

    highlightPost(markedPost.postDescriptor)

    if (BackgroundUtils.isInForeground()) {
      BackgroundUtils.runOnMainThread({ scrollToPost(markedPost.postDescriptor, false) }, 250)
    }
  }

  private suspend fun updateBookmarkInfoIfNecessary(localThreadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val originalPost = chanThreadManager.getChanThread(localThreadDescriptor)
      ?.getOriginalPost()

    val opThumbnailUrl = originalPost?.firstImage()
      ?.actualThumbnailUrl

    val title = ChanPostUtils.getTitle(originalPost, localThreadDescriptor)

    val updatedBookmarkDescriptor = bookmarksManager.updateBookmarkNoPersist(localThreadDescriptor) { threadBookmark ->
      if (threadBookmark.title.isNullOrEmpty() && title.isNotEmpty()) {
        threadBookmark.title = title
      }

      if (threadBookmark.thumbnailUrl == null && opThumbnailUrl != null) {
        threadBookmark.thumbnailUrl = opThumbnailUrl
      }
    }

    if (updatedBookmarkDescriptor != null) {
      bookmarksManager.persistBookmarkManually(updatedBookmarkDescriptor)
    }
  }

  private fun createNewNavHistoryElement(localChanDescriptor: ChanDescriptor) {
    val canCreateNavElement = historyNavigationManager.canCreateNavElement(
      bookmarksManager,
      localChanDescriptor
    )

    if (!canCreateNavElement) {
      return
    }

    when (localChanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> {
        val site = siteManager.bySiteDescriptor(localChanDescriptor.siteDescriptor())
          ?: return

        val siteIconUrl = site.icon().url!!
        val title = String.format(Locale.ENGLISH, "%s/%s", site.name(), localChanDescriptor.boardCode())

        historyNavigationManager.createNewNavElement(
          descriptor = localChanDescriptor,
          thumbnailImageUrl = siteIconUrl,
          title = title,
          createdByBookmarkCreation = false
        )
      }

      is ChanDescriptor.ThreadDescriptor -> {
        val chanOriginalPost = chanThreadManager.getChanThread(localChanDescriptor)
          ?.getOriginalPost()

        if (chanOriginalPost == null) {
          return
        }

        var opThumbnailUrl = chanThreadManager.getChanThread(localChanDescriptor)
          ?.getOriginalPost()
          ?.firstImage()
          ?.actualThumbnailUrl

        if (opThumbnailUrl == null) {
          opThumbnailUrl = siteManager.bySiteDescriptor(localChanDescriptor.siteDescriptor())?.icon()?.url
        }

        val title = ChanPostUtils.getTitle(
          chanOriginalPost,
          localChanDescriptor
        )

        if (opThumbnailUrl != null && title.isNotEmpty()) {
          historyNavigationManager.createNewNavElement(
            descriptor = localChanDescriptor,
            thumbnailImageUrl = opThumbnailUrl,
            title = title,
            createdByBookmarkCreation = false
          )
        }
      }
    }
  }

  override suspend fun onListScrolledToBottom() {
    if (!isBound) {
      return
    }

    val descriptor = currentChanDescriptor
    if (descriptor is ChanDescriptor.ThreadDescriptor) {
      if (chanThreadManager.getThreadPostsCount(descriptor) > 0) {
        val lastPostNo = chanThreadManager.getLastPost(descriptor)?.postNo()
        if (lastPostNo != null) {
          chanThreadViewableInfoManager.update(descriptor) { chanThreadViewableInfo ->
            chanThreadViewableInfo.lastViewedPostNo = lastPostNo
          }

          lastViewedPostNoInfoHolder.setLastViewedPostNo(descriptor, lastPostNo)
        }

        // Force mark all posts in this thread as seen (because sometimes the very last post
        // ends up staying unseen for some unknown reason).
        bookmarksManager.readPostsAndNotificationsForThread(descriptor, lastPostNo)
      }
    }

    threadPresenterCallback?.showNewPostsNotification(false, -1)

    // Update the last seen indicator
    showPosts()
  }

  fun onNewPostsViewClicked() {
    if (!isBound) {
      return
    }

    val chanDescriptor = currentChanDescriptor
      ?: return

    chanThreadViewableInfoManager.view(currentChanDescriptor!!) { chanThreadViewableInfoView ->
      val post = chanThreadManager.findPostByPostNo(
        chanDescriptor,
        chanThreadViewableInfoView.lastViewedPostNo
      )

      var position = -1

      if (post != null) {
        val posts = threadPresenterCallback?.displayingPostDescriptors
          ?: return@view

        for (i in posts.indices) {
          val needle = posts[i]
          if (post.postNo() == needle.postNo) {
            position = i
            break
          }
        }
      }

      // -1 is fine here because we add 1 down the chain to make it 0 if there's no last viewed
      threadPresenterCallback?.smoothScrollNewPosts(position)
    }
  }

  fun scrollTo(displayPosition: Int, smooth: Boolean) {
    threadPresenterCallback?.scrollTo(displayPosition, smooth)
  }

  fun scrollToImage(postImage: ChanPostImage, smooth: Boolean) {
    var position = -1
    val postDescriptors = threadPresenterCallback?.displayingPostDescriptors
      ?: return

    out@ for (i in postDescriptors.indices) {
      val postDescriptor = postDescriptors[i]
      val postImages = chanThreadManager.getPost(postDescriptor)?.postImages
        ?: continue

      for (image in postImages) {
        if (image == postImage) {
          position = i
          break@out
        }
      }
    }

    if (position >= 0) {
      scrollTo(position, smooth)
    }
  }

  fun scrollToPost(needle: PostDescriptor, smooth: Boolean) {
    scrollToPostByPostNo(needle.postNo, smooth)
  }

  @JvmOverloads
  fun scrollToPostByPostNo(postNo: Long, smooth: Boolean = true) {
    var position = -1

    val posts = threadPresenterCallback?.displayingPostDescriptors
    if (posts == null || posts.isEmpty()) {
      Logger.e(TAG, "scrollToPostByPostNo($postNo) posts are null or empty")
      return
    }

    for (i in posts.indices) {
      val post = posts[i]
      if (post.postNo == postNo) {
        position = i
        break
      }
    }

    if (position >= 0) {
      scrollTo(position, smooth)
    }
  }

  fun highlightPost(postDescriptor: PostDescriptor) {
    threadPresenterCallback?.highlightPost(postDescriptor)
  }

  fun selectPost(postDescriptor: PostDescriptor?) {
    threadPresenterCallback?.selectPost(postDescriptor)
  }

  fun selectPostImage(postImage: ChanPostImage) {
    val postDescriptors = threadPresenterCallback?.displayingPostDescriptors
      ?: return

    for (postDescriptor in postDescriptors) {
      val post = chanThreadManager.getPost(postDescriptor)
        ?: continue

      for (image in post.postImages) {
        if (image.equalUrl(postImage)) {
          scrollToPost(post.postDescriptor, false)
          highlightPost(post.postDescriptor)
          return
        }
      }
    }
  }

  override fun onPostClicked(postDescriptor: PostDescriptor) {
    if (!isBound || currentChanDescriptor is ChanDescriptor.ThreadDescriptor) {
      return
    }

    val topRepliesData = threadPresenterCallback?.getTopPostRepliesDataOrNull()
    val postViewMode = topRepliesData?.postViewMode

    if (topRepliesData != null && postViewMode?.consumePostClicks() == true) {
      return
    }

    serializedCoroutineExecutor.post {
      val newThreadDescriptor = currentChanDescriptor!!.toThreadDescriptor(postDescriptor.postNo)
      highlightPost(postDescriptor)

      threadPresenterCallback?.showThread(newThreadDescriptor)
    }
  }

  override fun onGoToPostButtonClicked(post: ChanPost, postViewMode: PostCellData.PostViewMode) {
    if (!isBound) {
      return
    }

    serializedCoroutineExecutor.post {
      val isExternalThread = currentChanDescriptor != post.postDescriptor.descriptor
      if (isExternalThread) {
        val showOpenThreadDialog = when (postViewMode) {
          PostCellData.PostViewMode.Normal,
          PostCellData.PostViewMode.RepliesPopup,
          PostCellData.PostViewMode.PostSelection,
          PostCellData.PostViewMode.Search -> false
          PostCellData.PostViewMode.ExternalPostsPopup -> true
        }

        threadPresenterCallback?.openExternalThread(post.postDescriptor, showOpenThreadDialog)
        return@post
      }

      threadPresenterCallback?.postClicked(post.postDescriptor)
    }
  }

  override fun onThumbnailClicked(chanDescriptor: ChanDescriptor, postImage: ChanPostImage, thumbnail: ThumbnailView) {
    if (!isBound) {
      return
    }

    val initialImageUrl = postImage.imageUrl?.toString()
      ?: return
    val transitionThumbnailUrl = postImage.getThumbnailUrl()?.toString()
      ?: return

    threadPresenterCallback?.showImages(chanDescriptor, initialImageUrl, transitionThumbnailUrl)
  }

  override fun onThumbnailLongClicked(chanDescriptor: ChanDescriptor, postImage: ChanPostImage, thumbnail: ThumbnailView) {
    if (!isBound) {
      return
    }

    thumbnailLongtapOptionsHelper.onThumbnailLongTapped(
      context = context,
      isCurrentlyInAlbum = false,
      postImage = postImage,
      presentControllerFunc = { controller ->
        threadPresenterCallback?.presentController(controller, true)
      },
      showFiltersControllerFunc = { chanFilterMutable ->
        threadPresenterCallback?.openFiltersController(chanFilterMutable)
      }
    )

  }

  override fun onPopulatePostOptions(post: ChanPost, menu: MutableList<FloatingListMenuItem>) {
    if (!isBound) {
      return
    }

    val chanDescriptor = currentChanDescriptor
      ?: return

    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
      ?: return

    if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
      val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
        chanDescriptor.siteName(),
        post.postDescriptor.boardDescriptor().boardCode,
        post.postNo()
      )

      if (!bookmarksManager.exists(threadDescriptor)) {
        menu.add(createMenuItem(POST_OPTION_BOOKMARK, R.string.action_pin))
      }
    } else {
      menu.add(createMenuItem(POST_OPTION_QUOTE, R.string.post_quote))
      menu.add(createMenuItem(POST_OPTION_QUOTE_TEXT, R.string.post_quote_text))
    }

    if (site.siteFeature(Site.SiteFeature.POST_REPORT)) {
      menu.add(createMenuItem(POST_OPTION_REPORT, R.string.post_report))
    }

    if (chanDescriptor.isCatalogDescriptor() || chanDescriptor.isThreadDescriptor() && !post.postDescriptor.isOP()) {
      if (!postFilterManager.getFilterStub(post.postDescriptor)) {
        menu.add(createMenuItem(POST_OPTION_HIDE, R.string.post_hide))
      }
      menu.add(createMenuItem(POST_OPTION_REMOVE, R.string.post_remove))
    }

    if (chanDescriptor.isThreadDescriptor()) {
      if (!TextUtils.isEmpty(post.posterId)) {
        menu.add(createMenuItem(POST_OPTION_HIGHLIGHT_ID, R.string.post_highlight_id))
      }

      if (!TextUtils.isEmpty(post.tripcode)) {
        menu.add(createMenuItem(POST_OPTION_HIGHLIGHT_TRIPCODE, R.string.post_highlight_tripcode))
        menu.add(createMenuItem(POST_OPTION_FILTER_TRIPCODE, R.string.post_filter_tripcode))
      }
    }

    val siteDescriptor = post.postDescriptor.boardDescriptor().siteDescriptor
    val containsSite = siteManager.bySiteDescriptor(siteDescriptor) != null

    if (site.siteFeature(Site.SiteFeature.POST_DELETE)) {
      if (containsSite) {
        val savedReply = savedReplyManager.getSavedReply(post.postDescriptor)
        if (savedReply?.password != null) {
          menu.add(createMenuItem(POST_OPTION_DELETE, R.string.post_delete))
        }
      }
    }

    if (post.postComment.linkables.isNotEmpty()) {
      menu.add(createMenuItem(POST_OPTION_LINKS, R.string.post_show_links))
    }

    menu.add(createMenuItem(POST_OPTION_OPEN_BROWSER, R.string.action_open_browser))
    menu.add(createMenuItem(POST_OPTION_SHARE, R.string.post_share))
    menu.add(createMenuItem(POST_OPTION_COPY_TEXT, R.string.post_copy_text))
    menu.add(createMenuItem(POST_OPTION_INFO, R.string.post_info))

    if (containsSite && chanDescriptor.isThreadDescriptor()) {
      val isSaved = savedReplyManager.isSaved(post.postDescriptor)
      val stringId = if (isSaved) {
        R.string.unmark_as_my_post
      } else {
        R.string.mark_as_my_post
      }

      menu.add(createMenuItem(POST_OPTION_SAVE, stringId))
    }

    val themeJsonSpannables = post.postComment.getThemeJsonSpannables()
    if (themeJsonSpannables.isNotEmpty()) {
      val innerItems = themeJsonSpannables.mapIndexed { index, themeJsonSpannable ->
        return@mapIndexed FloatingListMenuItem(
          POST_OPTION_APPLY_THEME_IDX + index,
          getString(R.string.apply_theme, themeJsonSpannable.themeName),
          themeJsonSpannable.themeName
        )
      }

      menu.add(
        FloatingListMenuItem(
          key = POST_OPTION_APPLY_THEME,
          name = getString(R.string.apply_themes),
          value = null,
          more = innerItems.toMutableList()
        )
      )
    }

    if (isDevBuild()) {
      val threadNo = chanDescriptor.threadNoOrNull() ?: -1L
      if (threadNo > 0) {
        menu.add(createMenuItem(POST_OPTION_MOCK_REPLY, R.string.mock_reply))
      }
    }
  }

  private fun createMenuItem(
    menuItemId: Int,
    @StringRes stringId: Int,
    value: Any? = null
  ): FloatingListMenuItem {
    return FloatingListMenuItem(
      menuItemId,
      context.getString(stringId),
      value
    )
  }

  override fun onPostOptionClicked(post: ChanPost, item: FloatingListMenuItem, inPopup: Boolean) {
    postOptionsClickExecutor.post {
      val index = item.key as Int

      if (index in POST_OPTION_APPLY_THEME_IDX until POST_OPTION_APPLY_THEME_IDX_MAX) {
        applyThemeFromPostComment(index - POST_OPTION_APPLY_THEME_IDX, post, item)
        return@post
      }

      when (index) {
        POST_OPTION_QUOTE -> {
          threadPresenterCallback?.hidePostsPopup()
          threadPresenterCallback?.quote(post, false)
        }
        POST_OPTION_QUOTE_TEXT -> {
          threadPresenterCallback?.hidePostsPopup()
          threadPresenterCallback?.quote(post, true)
        }
        POST_OPTION_INFO -> showPostInfo(post)
        POST_OPTION_LINKS -> if (post.postComment.linkables.isNotEmpty()) {
          threadPresenterCallback?.showPostLinkables(post)
        }
        POST_OPTION_COPY_TEXT -> threadPresenterCallback?.clipboardPost(post)
        POST_OPTION_REPORT -> {
          if (inPopup) {
            threadPresenterCallback?.hidePostsPopup()
          }
          threadPresenterCallback?.openReportView(post)
        }
        POST_OPTION_HIGHLIGHT_ID -> {
          val posterId = post.posterId
            ?: return@post
          threadPresenterCallback?.highlightPostId(posterId)
        }
        POST_OPTION_HIGHLIGHT_TRIPCODE -> threadPresenterCallback?.highlightPostTripcode(post.tripcode)
        POST_OPTION_FILTER_TRIPCODE -> threadPresenterCallback?.filterPostTripcode(post.tripcode)
        POST_OPTION_DELETE -> requestDeletePost(post)
        POST_OPTION_SAVE -> saveUnsavePost(post)
        POST_OPTION_BOOKMARK -> {
          val threadDescriptor = currentChanDescriptor?.toThreadDescriptor(post.postNo())
            ?: return@post

          if (!post.postDescriptor.isOP()) {
            return@post
          }

          chanPostRepository.createEmptyThreadIfNotExists(threadDescriptor)
            .safeUnwrap { error ->
              Logger.e(TAG, "createEmptyThreadIfNotExists($threadDescriptor) error", error)
              return@post
            }

          bookmarksManager.createBookmark(
            threadDescriptor,
            ChanPostUtils.getTitle(post as ChanOriginalPost, currentChanDescriptor),
            post.firstImage()?.actualThumbnailUrl
          )
        }
        POST_OPTION_OPEN_BROWSER -> if (isBound) {
          val site = currentChanDescriptor?.let { chanDescriptor ->
            siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
          } ?: return@post

          val url = site.resolvable().desktopUrl(currentChanDescriptor!!, post.postNo())
          openLink(url)
        }
        POST_OPTION_SHARE -> if (isBound) {
          val site = currentChanDescriptor?.let { chanDescriptor ->
            siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
          } ?: return@post

          val url = site.resolvable().desktopUrl(currentChanDescriptor!!, post.postNo())
          shareLink(url)
        }
        POST_OPTION_REMOVE,
        POST_OPTION_HIDE -> {
          val hide = index == POST_OPTION_HIDE
          val chanDescriptor = currentChanDescriptor
            ?: return@post

          if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
            threadPresenterCallback?.hideThread(post, post.postNo(), hide)
            return@post
          }

          chanDescriptor as ChanDescriptor.ThreadDescriptor

          val threadNo = chanThreadManager.getChanThread(chanDescriptor)?.getOriginalPost()?.postNo()
            ?: return@post

          val isEmpty = post.repliesFromCount == 0
          if (isEmpty) {
            // no replies to this post so no point in showing the dialog
            hideOrRemovePosts(
              hide = hide,
              wholeChain = false,
              post = post,
              threadNo = threadNo
            )
          } else {
            // show a dialog to the user with options to hide/remove the whole chain of posts
            threadPresenterCallback?.showHideOrRemoveWholeChainDialog(
              hide = hide,
              post = post,
              threadNo = threadNo
            )
          }
        }
        POST_OPTION_MOCK_REPLY -> if (isBound && currentChanDescriptor is ChanDescriptor.ThreadDescriptor) {
          val threadDescriptor = currentChanDescriptor!! as ChanDescriptor.ThreadDescriptor

          mockReplyManager.addMockReply(
            post.postDescriptor.boardDescriptor().siteName(),
            threadDescriptor.boardCode(),
            threadDescriptor.threadNo,
            post.postNo()
          )
          showToast(context, "Refresh to add mock replies")
        }
      }
    }
  }

  private fun applyThemeFromPostComment(index: Int, post: ChanPost, item: FloatingListMenuItem) {
    val themeName = item.value as? String
      ?: return

    val themeJsonSpannable = post.postComment.getThemeJsonSpannables().getOrNull(index)
      ?: return

    val themeJson = post.postComment.getThemeJsonByThemeName(themeName)
      ?: return

    launch {
      when (themeEngine.tryParseAndApplyTheme(themeJson, themeJsonSpannable.isLightTheme.not())) {
        is ThemeParser.ThemeParseResult.AttemptToImportWrongTheme,
        is ThemeParser.ThemeParseResult.BadName,
        is ThemeParser.ThemeParseResult.Error,
        is ThemeParser.ThemeParseResult.FailedToParseSomeFields -> {
          showToast(context, "Failed to apply theme")
          return@launch
        }
        is ThemeParser.ThemeParseResult.Success -> {
          showToast(context, "Done")
        }
      }
    }
  }

  override fun onPostLinkableClicked(post: ChanPost, linkable: PostLinkable) {
    serializedCoroutineExecutor.post {
      if (!isBound || currentChanDescriptor == null) {
        return@post
      }

      val currentThreadDescriptor = post.postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
        ?: return@post

      val isExternalThread = post.postDescriptor.descriptor != currentChanDescriptor
      val siteName = currentThreadDescriptor.siteName()

      if (ChanSettings.verboseLogs.get()) {
        Logger.d(TAG, "onPostLinkableClicked, linkable=${linkable}")
      }

      if (linkable.type == PostLinkable.Type.QUOTE) {
        val postId = linkable.linkableValue.extractLongOrNull()
        if (postId == null) {
          Logger.e(TAG, "Bad quote linkable: linkableValue = ${linkable.linkableValue}")
          return@post
        }

        val linked = chanThreadManager.findPostByPostNo(currentThreadDescriptor, postId)
        if (linked != null) {
          val postViewMode = if (isExternalThread) {
            PostCellData.PostViewMode.ExternalPostsPopup
          } else {
            PostCellData.PostViewMode.RepliesPopup
          }

          threadPresenterCallback?.showPostsPopup(
            currentThreadDescriptor,
            postViewMode,
            post.postDescriptor,
            listOf(linked)
          )
        }

        return@post
      }

      if (linkable.type == PostLinkable.Type.LINK) {
        val link = (linkable.linkableValue as? PostLinkable.Value.StringValue)?.value
        if (link == null) {
          Logger.e(TAG, "Bad link linkable: linkableValue = ${linkable.linkableValue}")
          return@post
        }

        threadPresenterCallback?.openLink(link.toString())
        return@post
      }

      if (linkable.type == PostLinkable.Type.THREAD) {
        val threadLink = linkable.linkableValue as? PostLinkable.Value.ThreadOrPostLink
        if (threadLink == null || !threadLink.isValid()) {
          Logger.e(TAG, "Bad thread linkable: linkableValue = ${linkable.linkableValue}")
          return@post
        }

        val boardDescriptor = BoardDescriptor.create(siteName, threadLink.board)
        val board = boardManager.byBoardDescriptor(boardDescriptor)

        if (board != null) {
          val postDescriptor = PostDescriptor.create(
            siteName,
            threadLink.board,
            threadLink.threadId,
            threadLink.postId
          )

          threadPresenterCallback?.showPostInExternalThread(postDescriptor)
        }

        return@post
      }

      if (linkable.type == PostLinkable.Type.BOARD) {
        val link = (linkable.linkableValue as? PostLinkable.Value.StringValue)?.value
        if (link == null) {
          Logger.e(TAG, "Bad board linkable: linkableValue = ${linkable.linkableValue}")
          return@post
        }

        val boardDescriptor = BoardDescriptor.create(siteName, link.toString())
        val board = boardManager.byBoardDescriptor(boardDescriptor)

        if (board == null) {
          showToast(context, R.string.archive_is_not_enabled)
          return@post
        }

        threadPresenterCallback?.showBoard(boardDescriptor, true)
        return@post
      }

      if (linkable.type == PostLinkable.Type.SEARCH) {
        val searchLink = linkable.linkableValue as? PostLinkable.Value.SearchLink
        if (searchLink == null) {
          Logger.e(TAG, "Bad search linkable: linkableValue = ${linkable.linkableValue}")
          return@post
        }

        val boardDescriptor = BoardDescriptor.create(siteName, searchLink.board)
        val board = boardManager.byBoardDescriptor(boardDescriptor)

        if (board == null) {
          showToast(context, R.string.site_uses_dynamic_boards)
          return@post
        }

        threadPresenterCallback?.setBoardWithSearchQuery(boardDescriptor, searchLink.query, true)
        return@post
      }

      if (linkable.type == PostLinkable.Type.DEAD) {
        when (val postLinkableValue = linkable.linkableValue) {
          is PostLinkable.Value.LongValue -> {
            val postNo = postLinkableValue.extractLongOrNull()
            if (postNo == null || postNo <= 0L) {
              Logger.e(TAG, "PostLinkable is not valid: linkableValue = ${postLinkableValue}")
              return@post
            }

            val threadDescriptor = currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
            if (threadDescriptor == null) {
              Logger.e(TAG, "Bad currentChanDescriptor: ${currentChanDescriptor} (null or not thread descriptor)")
              return@post
            }

            val archivePostDescriptor = PostDescriptor.create(
              chanDescriptor = threadDescriptor,
              postNo = postNo
            )

            threadPresenterCallback?.showAvailableArchivesList(
              postDescriptor = archivePostDescriptor,
              preview = true
            )
          }
          is PostLinkable.Value.ThreadOrPostLink -> {
            if (!postLinkableValue.isValid()) {
              Logger.e(TAG, "PostLinkable is not valid: linkableValue = ${postLinkableValue}")
              return@post
            }

            val archivePostDescriptor = PostDescriptor.create(
              siteName = siteName,
              boardCode = postLinkableValue.board,
              threadNo = postLinkableValue.threadId,
              postNo = postLinkableValue.postId
            )

            threadPresenterCallback?.showAvailableArchivesList(
              postDescriptor = archivePostDescriptor,
              preview = true
            )
          }
          else -> {
            // no-op
          }
        }

        return@post
      }

      if (linkable.type == PostLinkable.Type.ARCHIVE) {
        val archiveThreadLink = (linkable.linkableValue as? PostLinkable.Value.ArchiveThreadLink)
          ?: return@post

        val archiveDescriptor = archivesManager.getArchiveDescriptorByArchiveType(archiveThreadLink.archiveType)
          ?: return@post

        val isSiteEnabled = siteManager.bySiteDescriptor(SiteDescriptor.create(archiveDescriptor.domain))?.enabled()
          ?: false

        if (!isSiteEnabled) {
          showToast(context, getString(R.string.archive_is_not_enabled, archiveDescriptor.domain))
          return@post
        }

        if (!archiveThreadLink.isValid()) {
          Logger.e(TAG, "PostLinkable is not valid: linkableValue = ${archiveThreadLink}")
          return@post
        }

        val archivePostDescriptor = PostDescriptor.create(
          siteName = archiveDescriptor.siteDescriptor.siteName,
          boardCode = archiveThreadLink.board,
          threadNo = archiveThreadLink.threadId,
          postNo = archiveThreadLink.postIdOrThreadId()
        )

        threadPresenterCallback?.showPostInExternalThread(archivePostDescriptor)
        return@post
      }
    }
  }

  override fun onPostLinkableLongClicked(post: ChanPost, linkable: PostLinkable, inPopup: Boolean) {
    serializedCoroutineExecutor.post {
      if (!isBound || currentChanDescriptor == null) {
        return@post
      }

      val site = siteManager.bySiteDescriptor(post.postDescriptor.siteDescriptor())
        ?: return@post

      val floatingListMenuItems = mutableListOf<FloatingListMenuItem>()
      val postChanDescriptor = post.postDescriptor.descriptor

      floatingListMenuItems += HeaderFloatingListMenuItem(
        POST_LINKABLE_LONG_CLICK_MENU_HEADER,
        linkable.key.toString()
      )

      when (linkable.type) {
        PostLinkable.Type.SPOILER -> {
          // This shouldn't happen but just in case.
          return@post
        }
        PostLinkable.Type.DEAD,
        PostLinkable.Type.QUOTE -> {
          floatingListMenuItems += createMenuItem(
            menuItemId = COPY_LINK_TEXT,
            stringId = R.string.action_copy_link_text,
            value = linkable.key
          )

          when (val postLinkableValue = linkable.linkableValue) {
            is PostLinkable.Value.LongValue -> {
              val postNo = postLinkableValue.extractLongOrNull()
              if (postNo != null) {
                val desktopUrl = site.resolvable().desktopUrl(postChanDescriptor, postNo)
                floatingListMenuItems += createMenuItem(
                  menuItemId = COPY_LINK_VALUE,
                  stringId = R.string.action_copy_link_value,
                  value = desktopUrl
                )
              }
            }
            is PostLinkable.Value.ThreadOrPostLink -> {
              val postDescriptor = PostDescriptor.create(
                siteName = site.name(),
                boardCode = postLinkableValue.board,
                threadNo = postLinkableValue.threadId,
                postNo = postLinkableValue.postId
              )

              val desktopUrl = site.resolvable().desktopUrl(
                postDescriptor.descriptor,
                postDescriptor.postNo
              )

              floatingListMenuItems += createMenuItem(
                menuItemId = COPY_LINK_VALUE,
                stringId = R.string.action_copy_link_value,
                value = desktopUrl
              )
            }
            else -> {
              // no-nop
            }
          }
        }
        PostLinkable.Type.LINK -> {
          val link = (linkable.linkableValue as? PostLinkable.Value.StringValue)?.value
          if (link != null) {
            floatingListMenuItems += createMenuItem(
              menuItemId = COPY_LINK_VALUE,
              stringId = R.string.action_copy_link_value,
              value = link
            )
          }
        }
        PostLinkable.Type.THREAD -> {
          val threadLink = linkable.linkableValue as? PostLinkable.Value.ThreadOrPostLink
          if (threadLink != null) {
            val boardDescriptor = BoardDescriptor.create(site.name(), threadLink.board)
            val board = boardManager.byBoardDescriptor(boardDescriptor)

            if (board != null) {
              val linkPostDescriptor = PostDescriptor.create(
                site.name(),
                threadLink.board,
                threadLink.threadId,
                threadLink.postId
              )

              val desktopUrl = site.resolvable().desktopUrl(
                chanDescriptor = linkPostDescriptor.descriptor,
                postNo = linkPostDescriptor.postNo
              )

              floatingListMenuItems += createMenuItem(
                menuItemId = COPY_LINK_VALUE,
                stringId = R.string.action_copy_link_value,
                value = desktopUrl
              )
            }
          }
        }
        PostLinkable.Type.BOARD -> {
          val link = (linkable.linkableValue as? PostLinkable.Value.StringValue)?.value
          if (link != null) {
            val catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(
              BoardDescriptor.create(site.name(), link.toString())
            )

            val desktopUrl = site.resolvable().desktopUrl(catalogDescriptor, null)

            floatingListMenuItems += createMenuItem(
              menuItemId = COPY_LINK_VALUE,
              stringId = R.string.action_copy_link_value,
              value = desktopUrl
            )
          }

        }
        PostLinkable.Type.SEARCH -> {
          val searchLink = linkable.linkableValue as? PostLinkable.Value.SearchLink
          if (searchLink != null) {
            val catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(
              BoardDescriptor.create(site.name(), searchLink.board)
            )

            val desktopUrl = site.resolvable().desktopUrl(catalogDescriptor, null)

            floatingListMenuItems += createMenuItem(
              menuItemId = COPY_LINK_VALUE,
              stringId = R.string.action_copy_link_value,
              value = desktopUrl
            )
          }
        }
        PostLinkable.Type.ARCHIVE -> {
          val archiveThreadLink = (linkable.linkableValue as? PostLinkable.Value.ArchiveThreadLink)
          if (archiveThreadLink != null) {
            val archiveDescriptor = archivesManager.getArchiveDescriptorByArchiveType(
              archiveThreadLink.archiveType
            )

            if (archiveDescriptor != null) {
              val archivePostDescriptor = PostDescriptor.create(
                siteName = archiveDescriptor.siteDescriptor.siteName,
                boardCode = archiveThreadLink.board,
                threadNo = archiveThreadLink.threadId,
                postNo = archiveThreadLink.postIdOrThreadId()
              )

              val desktopUrl = site.resolvable().desktopUrl(
                archivePostDescriptor.descriptor,
                archivePostDescriptor.postNo
              )

              floatingListMenuItems += createMenuItem(
                menuItemId = COPY_LINK_VALUE,
                stringId = R.string.action_copy_link_value,
                value = desktopUrl
              )
            }
          }
        }
      }

      floatingListMenuItems += createMenuItem(
        menuItemId = SHOW_POST_MENU_OPTIONS,
        stringId = R.string.action_copy_link_show_post_options
      )

      val floatingListMenuController = FloatingListMenuController(
        context = context,
        constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
        items = floatingListMenuItems,
        itemClickListener = { clickedItem ->
          val id = clickedItem.key as Int
          val value = (clickedItem.value as? String) ?: ""

          when (id) {
            COPY_LINK_TEXT -> {
              AndroidUtils.setClipboardContent("Link text", value)
              showToast(context, R.string.link_text_copied_to_clipboard)
            }
            COPY_LINK_VALUE -> {
              AndroidUtils.setClipboardContent("Link value", value)
              showToast(context, R.string.link_value_copied_to_clipboard)
            }
            SHOW_POST_MENU_OPTIONS -> {
              val postMenuOptions = mutableListOf<FloatingListMenuItem>()
              onPopulatePostOptions(post, postMenuOptions)

              if (postMenuOptions.size > 0) {
                showPostOptions(post, inPopup, postMenuOptions)
              }
            }
          }
        }
      )

      threadPresenterCallback?.presentController(floatingListMenuController, true)
    }
  }

  override fun onPostNoClicked(post: ChanPost) {
    threadPresenterCallback?.quote(post, false)
  }

  override fun onPostSelectionQuoted(postDescriptor: PostDescriptor, selection: CharSequence) {
    threadPresenterCallback?.quote(postDescriptor, selection)
  }

  override fun onPostSelectionFilter(postDescriptor: PostDescriptor, selection: CharSequence) {
    if (selection.isEmpty()) {
      showToast(context, R.string.selected_text_is_empty)
      return
    }

    val chanFilterMutable = ChanFilterMutable()
    chanFilterMutable.type = FilterType.COMMENT.flag
    chanFilterMutable.pattern = "/${selection}/"

    threadPresenterCallback?.openFiltersController(chanFilterMutable)
  }

  override fun showPostOptions(
    post: ChanPost,
    inPopup: Boolean,
    items: List<FloatingListMenuItem>
  ) {
    val floatingListMenuController = FloatingListMenuController(
      context,
      globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items,
      { item -> onPostOptionClicked(post, item, inPopup) }
    )

    threadPresenterCallback?.presentController(floatingListMenuController, true)
  }

  override fun hasAlreadySeenPost(postDescriptor: PostDescriptor): Boolean {
    if (currentChanDescriptor == null) {
      // Invalid loadable, hide the label
      return true
    }

    return if (currentChanDescriptor!!.isCatalogDescriptor()) {
      // Not in a thread, hide the label
      true
    } else {
      seenPostsManager.hasAlreadySeenPost(currentChanDescriptor!!, postDescriptor)
    }
  }

  override fun onShowPostReplies(post: ChanPost) {
    if (!isBound || currentChanDescriptor == null) {
      return
    }

    val posts = ArrayList<ChanPost>()
    val threadDescriptor = post.postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    val isExternalThread = post.postDescriptor.descriptor != currentChanDescriptor

    post.iterateRepliesFrom { replyPostNo ->
      val replyPost = chanThreadManager.findPostByPostNo(threadDescriptor, replyPostNo)
      if (replyPost != null) {
        posts.add(replyPost)
      }
    }

    if (posts.size > 0) {
      val postViewMode = if (isExternalThread) {
        PostCellData.PostViewMode.ExternalPostsPopup
      } else {
        PostCellData.PostViewMode.RepliesPopup
      }

      threadPresenterCallback?.showPostsPopup(
        threadDescriptor,
        postViewMode,
        post.postDescriptor,
        posts
      )
    }
  }

  override fun onPreviewThreadPostsClicked(post: ChanPost) {
    serializedCoroutineExecutor.post {
      threadPresenterCallback?.previewCatalogThread(post.postDescriptor)
    }
  }

  override suspend fun timeUntilLoadMoreMs(): Long {
    return chanThreadTicker.timeUntilLoadMoreMs()
  }

  override fun isWatching(): Boolean {
    val threadDescriptor = currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
      ?: return false

    val thread = chanThreadManager.getChanThread(threadDescriptor)
      ?: return false

    return ChanSettings.autoRefreshThread.get()
      && BackgroundUtils.isInForeground()
      && isBound
      && !thread.isClosed()
      && !thread.isArchived()
  }

  override fun getPage(originalPostDescriptor: PostDescriptor): BoardPage? {
    return pageRequestManager.getPage(originalPostDescriptor)
  }

  override fun onListStatusClicked() {
    if (!isBound) {
      return
    }

    val threadDescriptor = currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    val currentThread = chanThreadManager.getChanThread(threadDescriptor)
      ?: return

    val canRequestMore = !currentThread.isArchived() && !currentThread.isDeleted()
    if (canRequestMore) {
      chanThreadTicker.resetEverythingAndKickTicker()

      // put in a "request" for a page update whenever the next set of data comes in
      forcePageUpdate = true
    }

    threadPresenterCallback?.showToolbar()
  }

  fun threadDescriptorOrNull(): ChanDescriptor.ThreadDescriptor? {
    return currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
  }

  override suspend fun showThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    threadPresenterCallback?.showThread(threadDescriptor)
  }

  override fun requestNewPostLoad() {
    if (isBound && currentChanDescriptor is ChanDescriptor.ThreadDescriptor) {
      chanThreadTicker.resetEverythingAndKickTicker()

      // put in a "request" for a page update whenever the next set of data comes in
      forcePageUpdate = true
    }
  }

  override fun onUnhidePostClick(post: ChanPost) {
    threadPresenterCallback?.unhideOrUnremovePost(post)
  }

  override fun currentSpanCount(): Int {
    return threadPresenterCallback?.currentSpanCount() ?: 1
  }

  private suspend fun saveUnsavePost(post: ChanPost) {
    if (savedReplyManager.isSaved(post.postDescriptor)) {
      savedReplyManager.unsavePost(post.postDescriptor)
    } else {
      savedReplyManager.savePost(post.postDescriptor)
    }

    // Trigger onDemandContentLoaderManager for this post again
    onDemandContentLoaderManager.onPostUnbind(post.postDescriptor, isActuallyRecycling = true)

    val chanThread = chanThreadManager.getChanThread(post.postDescriptor.threadDescriptor())
    if (chanThread == null) {
      normalLoad(
        showLoading = true,
        chanLoadOptions = ChanLoadOptions.clearMemoryCache()
      )

      return
    }

    val postsToUpdate = chanThread.getPostWithRepliesToThisPost(post.postDescriptor)

    normalLoad(
      showLoading = false,
      chanLoadOptions = ChanLoadOptions.forceUpdatePosts(postsToUpdate),
      chanCacheUpdateOptions = ChanCacheUpdateOptions.DoNotUpdateCache
    )
  }

  private fun requestDeletePost(post: ChanPost) {
    if (siteManager.bySiteDescriptor(post.postDescriptor.boardDescriptor().siteDescriptor) == null) {
      return
    }

    val savedReply = savedReplyManager.getSavedReply(post.postDescriptor)
    if (savedReply?.password != null) {
      threadPresenterCallback?.confirmPostDelete(post)
    }
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  fun deletePostConfirmed(post: ChanPost, onlyImageDelete: Boolean) {
    launch {
      val site = siteManager.bySiteDescriptor(post.postDescriptor.boardDescriptor().siteDescriptor)
        ?: return@launch

      threadPresenterCallback?.showDeleting()

      val savedReply = savedReplyManager.getSavedReply(post.postDescriptor)
      if (savedReply?.password == null) {
        threadPresenterCallback?.hideDeleting(
          getString(R.string.delete_error_post_is_not_saved)
        )
        return@launch
      }

      val deleteRequest = DeleteRequest(post, savedReply, onlyImageDelete)
      val deleteResult = site.actions().delete(deleteRequest)

      when (deleteResult) {
        is SiteActions.DeleteResult.DeleteComplete -> {
          val deleteResponse = deleteResult.deleteResponse

          val message = when {
            deleteResponse.deleted -> getString(R.string.delete_success)
            !TextUtils.isEmpty(deleteResponse.errorMessage) -> deleteResponse.errorMessage
            else -> getString(R.string.delete_error)
          }

          if (deleteResponse.deleted) {
            chanThreadManager.deletePost(post.postDescriptor)
            normalLoad()
          }

          threadPresenterCallback?.hideDeleting(message)
        }
        is SiteActions.DeleteResult.DeleteError -> {
          val message = getString(
            R.string.delete_error,
            deleteResult.error.errorMessageOrClassName()
          )

          threadPresenterCallback?.hideDeleting(message)
        }
      }
    }
  }

  private fun showPostInfo(post: ChanPost) {
    val text = StringBuilder(128)

    val descriptor = post.postDescriptor.descriptor

    text
      .append("Site name: ")
      .appendLine(descriptor.siteName())
      .append("Board code: ")
      .appendLine(descriptor.boardCode())

    if (descriptor is ChanDescriptor.ThreadDescriptor) {
      text
        .append("Thread id: ")
        .appendLine(descriptor.threadNo)
    }

    siteManager.bySiteDescriptor(post.postDescriptor.siteDescriptor())?.let { site ->
      text
        .append("Full post link: ")
        .append(site.resolvable().desktopUrl(descriptor, post.postDescriptor.postNo))
        .appendLine()
    }

    text.appendLine()

    for (image in post.postImages) {
      text
        .append("Original file name: ")
        .append(image.filename)
        .append(".")
        .appendLine(image.extension)
        .append("Server file name: ")
        .append(image.serverFilename)
        .append(".")
        .appendLine(image.extension)

      if (image.isInlined) {
        text.append("\nLinked file")
      } else {
        text
          .append(" \nDimensions: ")
          .append(image.imageWidth)
          .append("x")
          .append(image.imageHeight)
          .append("\nSize: ")
          .append(getReadableFileSize(image.size))
      }

      if (image.spoiler && image.isInlined) {
        // all linked files are spoilered, don't say that
        text.append("\nSpoilered")
      }

      text.append("\n")
    }

    text.appendLine()

    text
      .append("Posted: ")
      .append(ChanPostUtils.getLocalDate(post))

    if (!TextUtils.isEmpty(post.posterId) && isBound) {
      val threadDescriptor = currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
      if (threadDescriptor != null) {
        val thread = chanThreadManager.getChanThread(threadDescriptor)
        if (thread != null) {
          text
            .append("\nId: ")
            .append(post.posterId)

          var count = 0

          thread.iteratePostsOrdered { chanPost ->
            if (chanPost.posterId == post.posterId) {
              count++
            }
          }

          text
            .append("\nCount: ")
            .append(count)
        }
      }
    }

    if (!TextUtils.isEmpty(post.tripcode)) {
      text
        .append("\nTripcode: ")
        .append(post.tripcode)
    }

    if (post.postIcons.isNotEmpty()) {
      for (icon in post.postIcons) {
        when {
          icon.iconUrl.toString().contains("troll") -> {
            text.append("\nTroll Country: ").append(icon.iconName)
          }
          icon.iconUrl.toString().contains("country") -> {
            text.append("\nCountry: ").append(icon.iconName)
          }
          icon.iconUrl.toString().contains("minileaf") -> {
            text.append("\n4chan Pass Year: ").append(icon.iconName)
          }
        }
      }
    }

    if (!TextUtils.isEmpty(post.moderatorCapcode)) {
      text
        .append("\nCapcode: ")
        .append(post.moderatorCapcode)
    }

    threadPresenterCallback?.showPostInfo(text.toString())
  }

  private suspend fun showPosts() {
    if (!isBound) {
      Logger.d(TAG, "showPosts() isBound==false")
      return
    }

    val descriptor = currentChanDescriptor
    if (descriptor == null) {
      Logger.d(TAG, "showPosts() currentChanDescriptor==null")
      return
    }

    threadPresenterCallback?.showPostsForChanDescriptor(
      descriptor,
      PostsFilter(postHideHelper, order)
    )
  }

  fun showImageReencodingWindow(fileUuid: UUID, supportsReencode: Boolean) {
    val chanDescriptor = currentChanDescriptor
    if (chanDescriptor == null) {
      Logger.e(TAG, "showImageReencodingWindow() chanDescriptor==null")
      return
    }

    threadPresenterCallback?.showImageReencodingWindow(fileUuid, chanDescriptor, supportsReencode)
  }

  fun hideOrRemovePosts(hide: Boolean, wholeChain: Boolean, post: ChanPost, threadNo: Long) {
    if (!isBound) {
      return
    }

    val descriptor = currentChanDescriptor
      ?: return

    val posts: MutableSet<PostDescriptor> = HashSet()

    if (wholeChain) {
      val foundPosts = chanThreadManager.findPostWithReplies(descriptor, post.postNo())
        .map { chanPost -> chanPost.postDescriptor }

      posts.addAll(foundPosts)
    } else {
      val foundPost = chanThreadManager.findPostByPostNo(descriptor, post.postNo())
      if (foundPost != null) {
        posts.add(foundPost.postDescriptor)
      }
    }

    threadPresenterCallback?.hideOrRemovePosts(hide, wholeChain, posts, threadNo)
  }

  fun showRemovedPostsDialog() {
    if (!isBound || currentChanDescriptor is ChanDescriptor.CatalogDescriptor) {
      return
    }

    val threadDescriptor = (currentChanDescriptor as? ChanDescriptor.ThreadDescriptor)
      ?: return

    val postDescriptors = chanThreadManager.getChanThread(threadDescriptor)?.getPostDescriptors()
      ?: return

    threadPresenterCallback?.viewRemovedPostsForTheThread(postDescriptors, threadDescriptor)
  }

  fun onRestoreRemovedPostsClicked(selectedPosts: List<PostDescriptor>) {
    if (!isBound) {
      return
    }

    threadPresenterCallback?.onRestoreRemovedPostsClicked(currentChanDescriptor!!, selectedPosts)
  }

  fun lostFocus(wasFocused: ThreadSlideController.ThreadControllerType) {
    if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
      // If we are not in SLIDE/PHONE layout mode, then we don't need to check the state of SlidingPaneLayout
      currentFocusedController = CurrentFocusedController.None
      return
    }

    currentFocusedController = when (wasFocused) {
      ThreadSlideController.ThreadControllerType.Catalog -> CurrentFocusedController.Thread
      ThreadSlideController.ThreadControllerType.Thread -> CurrentFocusedController.Catalog
    }
  }

  fun gainedFocus(nowFocused: ThreadSlideController.ThreadControllerType) {
    if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
      // If we are not in SLIDE/PHONE layout mode, then we don't need to check the state of SlidingPaneLayout
      currentFocusedController = CurrentFocusedController.None
      return
    }

    currentFocusedController = when (nowFocused) {
      ThreadSlideController.ThreadControllerType.Catalog -> CurrentFocusedController.Catalog
      ThreadSlideController.ThreadControllerType.Thread -> CurrentFocusedController.Thread
    }
  }

  fun currentFocusedController(): CurrentFocusedController {
    return currentFocusedController
  }

  enum class CurrentFocusedController {
    Catalog,
    Thread,
    None
  }

  enum class ChanThreadLoadingState {
    Uninitialized,
    Loading,
    Loaded
  }

  interface ThreadPresenterCallback {
    val displayingPostDescriptors: List<PostDescriptor>
    val currentPosition: IntArray?

    suspend fun showPostsForChanDescriptor(descriptor: ChanDescriptor?, filter: PostsFilter)
    fun postClicked(postDescriptor: PostDescriptor)
    fun showError(error: ChanLoaderException)
    fun showLoading()
    fun showEmpty()
    fun showPostInfo(info: String)
    fun showPostLinkables(post: ChanPost)
    fun clipboardPost(post: ChanPost)
    suspend fun showThread(threadDescriptor: ChanDescriptor.ThreadDescriptor)
    suspend fun showPostInExternalThread(postDescriptor: PostDescriptor)
    suspend fun previewCatalogThread(postDescriptor: PostDescriptor)
    suspend fun openExternalThread(postDescriptor: PostDescriptor, showOpenThreadDialog: Boolean)
    suspend fun showBoard(boardDescriptor: BoardDescriptor, animated: Boolean)
    suspend fun setBoard(boardDescriptor: BoardDescriptor, animated: Boolean)

    suspend fun setBoardWithSearchQuery(
      boardDescriptor: BoardDescriptor,
      searchQuery: String,
      animated: Boolean
    )

    fun openLink(link: String)
    fun openReportView(post: ChanPost)

    fun showPostsPopup(
      threadDescriptor: ChanDescriptor.ThreadDescriptor,
      postViewMode: PostCellData.PostViewMode,
      postDescriptor: PostDescriptor,
      posts: List<ChanPost>
    )

    fun hidePostsPopup()

    fun showImages(chanDescriptor: ChanDescriptor, initialImageUrl: String?, transitionThumbnailUrl: String)
    fun showAlbum(images: List<ChanPostImage>, index: Int)
    fun scrollTo(displayPosition: Int, smooth: Boolean)
    fun smoothScrollNewPosts(displayPosition: Int)
    fun highlightPost(postDescriptor: PostDescriptor)
    fun highlightPostId(id: String)
    fun highlightPostTripcode(tripcode: CharSequence?)
    fun filterPostTripcode(tripcode: CharSequence?)
    fun selectPost(postDescriptor: PostDescriptor?)
    fun quote(post: ChanPost, withText: Boolean)
    fun quote(postDescriptor: PostDescriptor, text: CharSequence)
    fun confirmPostDelete(post: ChanPost)
    fun showDeleting()
    fun hideDeleting(message: String)
    fun hideThread(post: ChanPost, threadNo: Long, hide: Boolean)
    fun showNewPostsNotification(show: Boolean, newPostsCount: Int)
    fun showImageReencodingWindow(
      fileUuid: UUID,
      chanDescriptor: ChanDescriptor,
      supportsReencode: Boolean
    )

    fun showHideOrRemoveWholeChainDialog(hide: Boolean, post: ChanPost, threadNo: Long)
    fun hideOrRemovePosts(
      hide: Boolean,
      wholeChain: Boolean,
      postDescriptors: Set<PostDescriptor>,
      threadNo: Long
    )

    fun unhideOrUnremovePost(post: ChanPost)
    fun viewRemovedPostsForTheThread(
      threadPosts: List<PostDescriptor>,
      threadDescriptor: ChanDescriptor.ThreadDescriptor
    )

    fun onRestoreRemovedPostsClicked(
      chanDescriptor: ChanDescriptor,
      selectedPosts: List<PostDescriptor>
    )

    suspend fun onPostUpdated(updatedPost: ChanPost, results: List<LoaderResult>)
    fun presentController(controller: Controller, animate: Boolean)
    fun showToolbar()
    fun showAvailableArchivesList(postDescriptor: PostDescriptor, preview: Boolean)
    fun currentSpanCount(): Int
    fun getTopPostRepliesDataOrNull(): PostPopupHelper.PostPopupData?
    fun openFiltersController(chanFilterMutable: ChanFilterMutable)
  }

  companion object {
    private const val TAG = "ThreadPresenter"
    private const val POST_OPTION_QUOTE = 0
    private const val POST_OPTION_QUOTE_TEXT = 1
    private const val POST_OPTION_INFO = 2
    private const val POST_OPTION_LINKS = 3
    private const val POST_OPTION_COPY_TEXT = 4
    private const val POST_OPTION_REPORT = 5
    private const val POST_OPTION_HIGHLIGHT_ID = 6
    private const val POST_OPTION_DELETE = 7
    private const val POST_OPTION_SAVE = 8
    private const val POST_OPTION_BOOKMARK = 9
    private const val POST_OPTION_SHARE = 10
    private const val POST_OPTION_HIGHLIGHT_TRIPCODE = 11
    private const val POST_OPTION_HIDE = 12
    private const val POST_OPTION_OPEN_BROWSER = 13
    private const val POST_OPTION_REMOVE = 14
    private const val POST_OPTION_MOCK_REPLY = 15
    private const val POST_OPTION_APPLY_THEME = 16
    private const val POST_OPTION_FILTER_TRIPCODE = 100

    private const val POST_OPTION_APPLY_THEME_IDX = 1000
    // Let's assume a post cannot contain more than 500 themes
    private const val POST_OPTION_APPLY_THEME_IDX_MAX = 1500

    private const val COPY_LINK_TEXT = 2000
    private const val COPY_LINK_VALUE = 2001
    private const val SHOW_POST_MENU_OPTIONS = 2002

    private const val POST_LINKABLE_LONG_CLICK_MENU_HEADER = "post_linkable_long_click_menu_header"
  }

}