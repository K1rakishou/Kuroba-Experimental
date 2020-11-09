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
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.helper.ChanThreadTicker
import com.github.k1rakishou.chan.core.helper.LastViewedPostNoInfoHolder
import com.github.k1rakishou.chan.core.helper.PostHideHelper
import com.github.k1rakishou.chan.core.loader.LoaderBatchResult
import com.github.k1rakishou.chan.core.loader.LoaderResult.Succeeded
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.ChanReloadOptions
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.LocalSearchManager
import com.github.k1rakishou.chan.core.manager.LocalSearchType
import com.github.k1rakishou.chan.core.manager.OnDemandContentLoaderManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.manager.SeenPostsManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.loader.ChanLoaderException
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.chan.core.site.parser.MockReplyManager
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4PagesRequest.BoardPage
import com.github.k1rakishou.chan.ui.adapter.PostAdapter.PostAdapterCallback
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.ThreadStatusCell
import com.github.k1rakishou.chan.ui.controller.floating_menu.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.floating_menu.FloatingListMenuGravity
import com.github.k1rakishou.chan.ui.layout.ThreadListLayout.ThreadListLayoutPresenterCallback
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openLink
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.shareLink
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.PostUtils.getReadableFileSize
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AndroidUtils.getString
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.model.data.descriptor.ArchiveDescriptor
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.source.cache.ChanCacheOptions
import com.github.k1rakishou.model.util.ChanPostUtils
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
  private val mockReplyManager: MockReplyManager,
  private val archivesManager: ArchivesManager,
  private val onDemandContentLoaderManager: OnDemandContentLoaderManager,
  private val seenPostsManager: SeenPostsManager,
  private val historyNavigationManager: HistoryNavigationManager,
  private val postFilterManager: PostFilterManager,
  private val chanFilterManager: ChanFilterManager,
  private val lastViewedPostNoInfoHolder: LastViewedPostNoInfoHolder,
  private val chanThreadViewableInfoManager: ChanThreadViewableInfoManager,
  private val localSearchManager: LocalSearchManager,
  private val postHideHelper: PostHideHelper,
  private val chanThreadManager: ChanThreadManager
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
      action = this::onChanTickerTick
    )
  }

  private var threadPresenterCallback: ThreadPresenterCallback? = null
  private var forcePageUpdate = false
  private var order = PostsFilter.Order.BUMP
  private val compositeDisposable = CompositeDisposable()
  private val job = SupervisorJob()

  private lateinit var postOptionsClickExecutor: RendezvousCoroutineExecutor
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor
  private lateinit var context: Context

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("ThreadPresenter")

  val isBound: Boolean
    get() {
      return chanThreadTicker.currentChanDescriptor != null
        && chanThreadManager.isCached(chanThreadTicker.currentChanDescriptor)
    }

  val isPinned: Boolean
    get() {
      val threadDescriptor = chanThreadTicker.currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
        ?: return false

      return bookmarksManager.exists(threadDescriptor)
    }

  val currentLocalSearchType: LocalSearchType?
    get() {
      val currentChanDescriptor = chanThreadTicker.currentChanDescriptor
      if (currentChanDescriptor == null) {
        return null
      }

      return if (currentChanDescriptor is ChanDescriptor.ThreadDescriptor) {
        LocalSearchType.ThreadSearch
      } else {
        LocalSearchType.CatalogSearch
      }
    }

  override fun getCurrentChanDescriptor(): ChanDescriptor? {
    return chanThreadTicker.currentChanDescriptor
  }

  fun create(context: Context, threadPresenterCallback: ThreadPresenterCallback) {
    this.context = context
    this.threadPresenterCallback = threadPresenterCallback

    launch {
      chanFilterManager.listenForFiltersChanges()
        .debounce(500L)
        .collect { onFiltersChanged() }
    }
  }

  fun showNoContent() {
    threadPresenterCallback?.showEmpty()
  }

  fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
    BackgroundUtils.ensureMainThread()

    if (chanDescriptor == chanThreadTicker.currentChanDescriptor) {
      return
    }

    if (isBound) {
      unbindChanDescriptor(false)
    }

    postOptionsClickExecutor = RendezvousCoroutineExecutor(this)
    serializedCoroutineExecutor = SerializedCoroutineExecutor(this)

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      bookmarksManager.setCurrentOpenThreadDescriptor(chanDescriptor)
    }

    chanThreadManager.bindChanDescriptor(chanDescriptor)

    compositeDisposable += onDemandContentLoaderManager.listenPostContentUpdates()
      .subscribe(
        { batchResult -> onPostUpdatedWithNewContent(batchResult) },
        { error -> Logger.e(TAG, "Post content updates error", error) }
      )

    threadPresenterCallback?.showLoading()

    Logger.d(TAG, "chanThreadTicker.startTicker($chanDescriptor)")
    chanThreadTicker.startTicker(chanDescriptor)
  }

  fun unbindChanDescriptor(isDestroying: Boolean) {
    BackgroundUtils.ensureMainThread()

    if (isBound) {
      val currentChanDescriptor = chanThreadTicker.currentChanDescriptor
      if (currentChanDescriptor != null) {
        chanThreadManager.unbindChanDescriptor(currentChanDescriptor)
        onDemandContentLoaderManager.cancelAllForDescriptor(currentChanDescriptor)
      }

      threadPresenterCallback?.showLoading()

      Logger.d(TAG, "chanThreadTicker.stopTicker($currentChanDescriptor)")
      chanThreadTicker.stopTicker()
    }

    if (isDestroying) {
      job.cancelChildren()
    }

    postOptionsClickExecutor.stop()
    serializedCoroutineExecutor.stop()
    compositeDisposable.clear()
  }

  private suspend fun onChanTickerTick(chanDescriptor: ChanDescriptor) {
    Logger.d(TAG, "onChanTickerTicked($chanDescriptor)")

    if (!chanThreadManager.isCached(chanDescriptor)) {
      when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> preloadThreadInfo(chanDescriptor)
        is ChanDescriptor.CatalogDescriptor -> preloadCatalogInfo(chanDescriptor)
      }
    }

    normalLoad()
  }

  private suspend fun preloadThreadInfo(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (!isBound) {
      return
    }

    Logger.d(TAG, "preloadThreadInfo($threadDescriptor)")

    supervisorScope {
      val jobs = mutableListOf<Deferred<Unit>>()

      jobs += async(Dispatchers.IO) { seenPostsManager.preloadForThread(threadDescriptor) }
      jobs += async(Dispatchers.IO) { chanThreadViewableInfoManager.preloadForThread(threadDescriptor) }
      jobs += async(Dispatchers.IO) { savedReplyManager.preloadForThread(threadDescriptor) }
      jobs += async(Dispatchers.IO) { postHideManager.preloadForThread(threadDescriptor) }
      jobs += async(Dispatchers.IO) { chanPostRepository.preloadForThread(threadDescriptor).unwrap() }

      ModularResult.Try { jobs.awaitAll() }
        .peekError { error -> Logger.e(TAG, "requestThreadInitialData() error", error) }
        .ignore()
    }
  }

  private suspend fun preloadCatalogInfo(catalogDescriptor: ChanDescriptor.CatalogDescriptor) {
    BackgroundUtils.ensureMainThread()

    if (!isBound) {
      return
    }

    Logger.d(TAG, "preloadCatalogInfo($catalogDescriptor)")

    supervisorScope {
      val jobs = mutableListOf<Deferred<Unit>>()

      jobs += async(Dispatchers.IO) { postHideManager.preloadForCatalog(catalogDescriptor) }

      ModularResult.Try { jobs.awaitAll() }
        .peekError { error -> Logger.e(TAG, "requestCatalogInitialData() error", error) }
        .ignore()
    }
  }

  fun quickReload(showLoading: Boolean = true, requestNewPosts: Boolean = true) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "quickReload($showLoading, $requestNewPosts)")

    val currentChanDescriptor = chanThreadTicker.currentChanDescriptor
    if (currentChanDescriptor == null) {
      Logger.d(TAG, "quickReload() chanThreadTicker.currentChanDescriptor==null")
      return
    }

    launch {
      if (requestNewPosts) {
        normalLoad(showLoading = showLoading)
      }

      onChanLoaderData(currentChanDescriptor)
    }
  }

  fun clearMemoryCacheAndReload() {
    Logger.d(TAG, "clearMemoryCacheAndReload()")
    normalLoad(reloadOptions = ChanReloadOptions.ClearMemoryCache)
  }

  fun normalLoad(
    showLoading: Boolean = true,
    reloadOptions: ChanReloadOptions = ChanReloadOptions.RetainAll,
    cacheOptions: ChanCacheOptions = ChanCacheOptions.StoreEverywhere
  ) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "normalLoad($showLoading, $reloadOptions, $cacheOptions)")

    val currentChanDescriptor = chanThreadTicker.currentChanDescriptor
    if (currentChanDescriptor == null) {
      Logger.d(TAG, "normalLoad() chanThreadTicker.currentChanDescriptor==null")
      return
    }

    launch {
      if (showLoading) {
        threadPresenterCallback?.showLoading()
      }

      chanThreadManager.reloadFromNetwork(currentChanDescriptor, reloadOptions, cacheOptions) { threadLoadResult ->
        Logger.d(TAG, "normalLoad() threadLoadResult=$threadLoadResult")

        if (threadLoadResult is ThreadLoadResult.Error) {
          onChanLoaderError(threadLoadResult.exception)
          return@reloadFromNetwork
        }

        if (threadLoadResult is ThreadLoadResult.Loaded) {
          onChanLoaderData(threadLoadResult.chanDescriptor)
        }
      }
    }
  }

  fun openThreadInArchive(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    archiveDescriptor: ArchiveDescriptor
  ) {
    if (!isBound) {
      return
    }

    Logger.d(TAG, "openThreadInArchive($threadDescriptor, $archiveDescriptor)")

    val archiveThreadDescriptor = ChanDescriptor.ThreadDescriptor(
      boardDescriptor = BoardDescriptor(
        siteDescriptor = archiveDescriptor.siteDescriptor,
        boardCode = threadDescriptor.boardDescriptor.boardCode
      ),
      threadNo = threadDescriptor.threadNo,
    )

    launch {
      // Take the current thread's scroll position and set it for the thread we are about to open
      val currentThreadIndexAndTop = chanThreadViewableInfoManager.getIndexAndTop(threadDescriptor)
      if (currentThreadIndexAndTop != null) {
        chanThreadViewableInfoManager.update(archiveThreadDescriptor, true) { chanThreadViewableInfo ->
          chanThreadViewableInfo.listViewIndex = currentThreadIndexAndTop.index
          chanThreadViewableInfo.listViewTop = currentThreadIndexAndTop.top
        }
      }

      threadPresenterCallback?.showThread(archiveThreadDescriptor)
    }
  }

  fun onForegroundChanged(foreground: Boolean) {
    if (!isBound) {
      return
    }

    if (foreground && isWatching()) {
      chanThreadTicker.resetTicker()
    } else {
      chanThreadTicker.stopTicker()
    }
  }

  @Synchronized
  fun pin(): Boolean {
    if (!isBound) {
      return false
    }

    if (!bookmarksManager.isReady()) {
      return false
    }

    val threadDescriptor = currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
      ?: return false

    if (bookmarksManager.exists(threadDescriptor)) {
      bookmarksManager.deleteBookmark(threadDescriptor)
      return true
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

  suspend fun onSearchVisibilityChanged(visible: Boolean) {
    if (!isBound) {
      return
    }

    val localSearchType = currentLocalSearchType
      ?: return

    threadPresenterCallback?.showSearch(visible)

    if (visible) {
      localSearchManager.onSearchEntered(localSearchType, "")
    } else {
      localSearchManager.clearSearch(localSearchType)
    }

    showPosts()
  }

  suspend fun onSearchEntered() {
    if (!isBound) {
      return
    }

    val localSearchType = currentLocalSearchType
      ?: return

    val query = localSearchManager.getSearchQuery(localSearchType)
    showPosts()

    if (TextUtils.isEmpty(query)) {
      threadPresenterCallback?.setSearchStatus(
        query = null,
        setEmptyText = true,
        hideKeyboard = false
      )
    } else {
      threadPresenterCallback?.setSearchStatus(
        query = query,
        setEmptyText = false,
        hideKeyboard = false
      )
    }
  }

  suspend fun setOrder(order: PostsFilter.Order) {
    if (this.order != order) {
      this.order = order

      if (isBound) {
        scrollTo(0, false)
        showPosts()
      }
    }
  }

  suspend fun refreshUI() {
    showPosts()
  }

  @Synchronized
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

  override fun onPostBind(post: ChanPost) {
    BackgroundUtils.ensureMainThread()

    currentChanDescriptor?.let { descriptor ->
      onDemandContentLoaderManager.onPostBind(descriptor, post)
      seenPostsManager.onPostBind(descriptor, post)
    }
  }

  override fun onPostUnbind(post: ChanPost, isActuallyRecycling: Boolean) {
    BackgroundUtils.ensureMainThread()

    currentChanDescriptor?.let { descriptor ->
      onDemandContentLoaderManager.onPostUnbind(descriptor, post, isActuallyRecycling)
      seenPostsManager.onPostUnbind(descriptor, post)
    }
  }

  private suspend fun onFiltersChanged() {
    chanPostRepository.awaitUntilInitialized()
    Logger.d(TAG, "onFiltersChanged() clearing posts cache")

    var shouldShowLoadingIndicator = false

    chanThreadManager.currentCatalogDescriptor?.let { catalogDescriptor ->
      if (catalogDescriptor == currentChanDescriptor) {
        shouldShowLoadingIndicator = true
      }

      clearMemoryCacheAndReload()
    }

    chanThreadManager.currentThreadDescriptor?.let { threadDescriptor ->
      if (threadDescriptor == currentChanDescriptor) {
        shouldShowLoadingIndicator = true
      }

      clearMemoryCacheAndReload()
    }

    if (shouldShowLoadingIndicator) {
      threadPresenterCallback?.showLoading()
    }
  }

  private fun onPostUpdatedWithNewContent(batchResult: LoaderBatchResult) {
    BackgroundUtils.ensureMainThread()

    if (threadPresenterCallback != null && needUpdatePost(batchResult)) {
      threadPresenterCallback?.onPostUpdated(batchResult.post)
    }
  }

  private fun needUpdatePost(batchResult: LoaderBatchResult): Boolean {
    return batchResult.results.any { it is Succeeded && it.needUpdateView }
  }

  private suspend fun onChanLoaderError(error: ChanLoaderException) {
    BackgroundUtils.ensureMainThread()
    Logger.e(TAG, "onChanLoaderError() called", error)

    threadPresenterCallback?.showError(error)
  }

  private suspend fun onChanLoaderData(loadedChanDescriptor: ChanDescriptor) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "onChanLoaderData() called")

    if (!isBound) {
      Logger.e(TAG, "onChanLoaderData() not bound!")
      return
    }

    val localChanDescriptor = currentChanDescriptor
    if (localChanDescriptor == null) {
      Logger.e(TAG, "onChanLoaderData() currentChanDescriptor==null")
      return
    }

    if (localChanDescriptor != loadedChanDescriptor) {
      Logger.e(TAG, "onChanLoaderData() localChanDescriptor " +
        "($localChanDescriptor) != loadedChanDescriptor ($loadedChanDescriptor)")
      return
    }

    if (isWatching()) {
      chanThreadTicker.kickTicker()
    }

    // allow for search refreshes inside the catalog
    if (loadedChanDescriptor is ChanDescriptor.CatalogDescriptor) {
      onSearchEntered()
    } else {
      showPosts()
    }

    if (localChanDescriptor is ChanDescriptor.ThreadDescriptor) {
      handleNewPosts(localChanDescriptor, loadedChanDescriptor)
    }

    chanThreadViewableInfoManager.getAndConsumeMarkedPostNo(localChanDescriptor) { markedPostNo ->
      handleMarkedPost(markedPostNo)
    }

    createNewNavHistoryElement(localChanDescriptor)

    if (localChanDescriptor is ChanDescriptor.ThreadDescriptor) {
      updateBookmarkInfoIfNecessary(localChanDescriptor)
    }
  }

  private fun handleNewPosts(
    localThreadDescriptor: ChanDescriptor.ThreadDescriptor,
    loadedChanDescriptor: ChanDescriptor
  ) {
    var newPostsCount = 0

    chanThreadViewableInfoManager.update(localThreadDescriptor) { chanThreadViewableInfo ->
      val lastLoadedPostNo = chanThreadViewableInfo.lastLoadedPostNo
      if (lastLoadedPostNo > 0) {
        newPostsCount = chanThreadManager.getNewPostsCount(localThreadDescriptor, lastLoadedPostNo)
      }

      chanThreadViewableInfo.lastLoadedPostNo = chanThreadManager.getLastPost(localThreadDescriptor)
        ?.postNo()
        ?: -1L

      if (chanThreadViewableInfo.lastViewedPostNo < 0L) {
        chanThreadViewableInfo.lastViewedPostNo = chanThreadViewableInfo.lastLoadedPostNo
      }
    }

    if (newPostsCount > 0 && localThreadDescriptor.threadNo == loadedChanDescriptor.threadNoOrNull()) {
      threadPresenterCallback?.showNewPostsNotification(true, newPostsCount)
    }

    if (localThreadDescriptor.threadNo == loadedChanDescriptor.threadNoOrNull()) {
      if (forcePageUpdate) {
        pageRequestManager.forceUpdateForBoard(localThreadDescriptor.boardDescriptor)
        forcePageUpdate = false
      }
    }
  }

  private fun handleMarkedPost(markedPostNo: Long) {
    val markedPost = chanThreadManager.findPostByPostNo(currentChanDescriptor, markedPostNo)
      ?: return

    highlightPost(markedPost.postDescriptor)

    if (BackgroundUtils.isInForeground()) {
      BackgroundUtils.runOnMainThread({ scrollToPost(markedPost.postDescriptor, false) }, 1000)
    }
  }

  private fun updateBookmarkInfoIfNecessary(localThreadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val originalPost = chanThreadManager.getChanThread(localThreadDescriptor)
      ?.getOriginalPost()

    val opThumbnailUrl = originalPost?.firstImage()
      ?.actualThumbnailUrl

    val title = ChanPostUtils.getTitle(originalPost, localThreadDescriptor)

    val updatedBookmarkDescriptor = bookmarksManager.updateBookmark(localThreadDescriptor) { threadBookmark ->
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
    when (localChanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> {
        val site = siteManager.bySiteDescriptor(localChanDescriptor.siteDescriptor())
          ?: return

        val siteIconUrl = site.icon().url
        val title = String.format(Locale.ENGLISH, "%s/%s", site.name(), localChanDescriptor.boardCode())

        historyNavigationManager.createNewNavElement(localChanDescriptor, siteIconUrl, title)
      }

      is ChanDescriptor.ThreadDescriptor -> {
        val opThumbnailUrl = chanThreadManager.getChanThread(localChanDescriptor)
          ?.getOriginalPost()
          ?.firstImage()
          ?.actualThumbnailUrl

        val chanOriginalPost = chanThreadManager.getChanThread(localChanDescriptor)
          ?.getOriginalPost()

        if (chanOriginalPost != null) {
          val title = ChanPostUtils.getTitle(chanOriginalPost, localChanDescriptor)
          if (opThumbnailUrl != null && title.isNotEmpty()) {
            historyNavigationManager.createNewNavElement(localChanDescriptor, opThumbnailUrl, title)
          }
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
    val localSearchType = currentLocalSearchType
      ?: return

    val searchOpen = localSearchManager.isSearchOpened(localSearchType)
    if (searchOpen) {
      return
    }

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
      ?: return

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

  fun selectPost(post: Long) {
    threadPresenterCallback?.selectPost(post)
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

  override fun onPostClicked(post: ChanPost) {
    if (!isBound || currentChanDescriptor is ChanDescriptor.ThreadDescriptor) {
      return
    }

    serializedCoroutineExecutor.post {
      val newThreadDescriptor = currentChanDescriptor!!.toThreadDescriptor(post.postNo())
      highlightPost(post.postDescriptor)

      threadPresenterCallback?.showThread(newThreadDescriptor)
    }
  }

  override fun onPostDoubleClicked(post: ChanPost) {
    if (!isBound || currentChanDescriptor is ChanDescriptor.CatalogDescriptor) {
      return
    }

    serializedCoroutineExecutor.post {
      val localSearchType = currentLocalSearchType
        ?: return@post

      val searchOpen = localSearchManager.isSearchOpened(localSearchType)
      if (searchOpen) {
        localSearchManager.clearSearch(localSearchType)

        showPosts()
        threadPresenterCallback?.setSearchStatus(null, setEmptyText = false, hideKeyboard = true)
        threadPresenterCallback?.showSearch(false)

        highlightPost(post.postDescriptor)
        scrollToPost(post.postDescriptor, false)
      } else {
        threadPresenterCallback?.postClicked(post.postDescriptor)
      }
    }
  }

  override fun onThumbnailClicked(postImage: ChanPostImage, thumbnail: ThumbnailView) {
    if (!isBound) {
      return
    }

    val postDescriptors = threadPresenterCallback?.displayingPostDescriptors
      ?: return

    var index = -1
    val images = ArrayList<ChanPostImage>()

    for (postDescriptor in postDescriptors) {
      val post = chanThreadManager.getPost(postDescriptor)
        ?: continue

      for (image in post.postImages) {
        if (image.imageUrl == null && image.actualThumbnailUrl == null) {
          Logger.d(TAG, "onThumbnailClicked() image.imageUrl == null && image.thumbnailUrl == null")
          continue
        }

        val imageUrl = image.imageUrl
        val setCallback = (!post.deleted ||
          imageUrl != null && cacheHandler.cacheFileExists(imageUrl.toString()))

        if (setCallback) {
          // Deleted posts always have 404'd images, but let it through if the file exists
          // in cache or the image is from a third-party archive
          images.add(image)

          if (image.equalUrl(postImage)) {
            index = images.size - 1
          }
        }
      }
    }

    if (images.isNotEmpty()) {
      threadPresenterCallback?.showImages(images, index, currentChanDescriptor!!, thumbnail)
    }
  }

  override fun onThumbnailLongClicked(postImage: ChanPostImage, thumbnail: ThumbnailView) {
    if (!isBound) {
      return
    }

    val gravity = if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
      when (currentChanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> FloatingListMenuGravity.Left
        is ChanDescriptor.ThreadDescriptor -> FloatingListMenuGravity.Right
        else -> return
      }
    } else {
      FloatingListMenuGravity.Bottom
    }

    val items = mutableListOf<FloatingListMenuItem>()
    items += createMenuItem(THUMBNAIL_COPY_URL, R.string.action_copy_image_url)

    val floatingListMenuController = FloatingListMenuController(
      context,
      gravity,
      items,
      { item -> onThumbnailOptionClicked(item.key as Int, postImage, thumbnail) }
    )

    threadPresenterCallback?.presentController(floatingListMenuController, true)
  }

  private fun onThumbnailOptionClicked(id: Int, postImage: ChanPostImage, thumbnail: ThumbnailView) {
    when (id) {
      THUMBNAIL_COPY_URL -> {
        if (postImage.imageUrl == null) {
          return
        }

        AndroidUtils.setClipboardContent("Image URL", postImage.imageUrl.toString())
        showToast(context, R.string.image_url_copied_to_clipboard)
      }
    }
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

      if (site.siteFeature(Site.SiteFeature.IMAGE_FILE_HASH) && post.postImages.isNotEmpty()) {
        menu.add(createMenuItem(POST_OPTION_FILTER_IMAGE_HASH, R.string.post_filter_image_hash))
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

    if (containsSite) {
      val isSaved = savedReplyManager.isSaved(post.postDescriptor)
      val stringId = if (isSaved) {
        R.string.unmark_as_my_post
      } else {
        R.string.mark_as_my_post
      }

      menu.add(createMenuItem(POST_OPTION_SAVE, stringId))
    }

    if (isDevBuild()) {
      val threadNo = chanDescriptor.threadNoOrNull() ?: -1L
      if (threadNo > 0) {
        menu.add(createMenuItem(POST_OPTION_MOCK_REPLY, R.string.mock_reply))
      }
    }
  }

  private fun createMenuItem(
    postOptionPin: Int,
    @StringRes stringId: Int
  ): FloatingListMenuItem {
    return FloatingListMenuItem(
      postOptionPin,
      context.getString(stringId)
    )
  }

  override fun onPostOptionClicked(post: ChanPost, id: Any, inPopup: Boolean) {
    postOptionsClickExecutor.post {
      when (id as Int) {
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
        POST_OPTION_FILTER_IMAGE_HASH -> threadPresenterCallback?.filterPostImageHash(post)
        POST_OPTION_DELETE -> requestDeletePost(post)
        POST_OPTION_SAVE -> saveUnsavePost(post)
        POST_OPTION_BOOKMARK -> {
          val descriptor = currentChanDescriptor
            ?: return@post

          if (!post.postDescriptor.isOP()) {
            return@post
          }

          bookmarksManager.createBookmark(
            descriptor.toThreadDescriptor(post.postNo()),
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
          val hide = id == POST_OPTION_HIDE
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

  override fun onPostLinkableClicked(post: ChanPost, linkable: PostLinkable) {
    serializedCoroutineExecutor.post {
      if (!isBound) {
        return@post
      }

      val currentThreadDescriptor = currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
        ?: return@post
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
          threadPresenterCallback?.showPostsPopup(post, listOf(linked))
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
        val threadLink = linkable.linkableValue as? PostLinkable.Value.ThreadLink
        if (threadLink == null) {
          Logger.e(TAG, "Bad thread linkable: linkableValue = ${linkable.linkableValue}")
          return@post
        }

        val boardDescriptor = BoardDescriptor.create(siteName, threadLink.board)
        val board = boardManager.byBoardDescriptor(boardDescriptor)

        if (board != null) {
          val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
            siteName,
            threadLink.board,
            threadLink.threadId
          )

          chanThreadViewableInfoManager.update(threadDescriptor) { chanThreadViewableInfo ->
            chanThreadViewableInfo.markedPostNo = threadLink.postId
          }

          threadPresenterCallback?.showExternalThread(threadDescriptor)
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

        showToast(context, R.string.board_search_links_are_disabled)

        // TODO(KurobaEx): search links are broken
//        localSearchManager.onSearchEntered(LocalSearchType.CatalogSearch, searchLink.search)
//        threadPresenterCallback?.setBoard(boardDescriptor, true)
        return@post
      }

      if (linkable.type == PostLinkable.Type.DEAD) {
        val descriptor = currentChanDescriptor
          ?: return@post
        val postNo = linkable.linkableValue.extractLongOrNull()
          ?: return@post

        val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
          descriptor,
          postNo
        )

        threadPresenterCallback?.showAvailableArchivesList(threadDescriptor)
        return@post
      }

      if (linkable.type == PostLinkable.Type.ARCHIVE) {
        val archiveThreadLink = (linkable.linkableValue as? PostLinkable.Value.ArchiveThreadLink)
          ?: return@post

        val archiveDescriptor = archivesManager.getArchiveDescriptorByArchiveType(archiveThreadLink.archiveType)
          ?: return@post

        val isSiteEnabled = siteManager.bySiteDescriptor(SiteDescriptor(archiveDescriptor.domain))?.enabled()
          ?: false

        if (!isSiteEnabled) {
          showToast(context, getString(R.string.archive_is_not_enabled, archiveDescriptor.domain))
          return@post
        }

        val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
          archiveDescriptor.domain,
          archiveThreadLink.board,
          archiveThreadLink.threadId
        )

        val postNo = archiveThreadLink.postId
        if (postNo != null) {
          chanThreadViewableInfoManager.update(threadDescriptor) { chanThreadViewableInfo ->
            chanThreadViewableInfo.markedPostNo = postNo
          }
        }

        threadPresenterCallback?.openThreadInArchive(threadDescriptor)
        return@post
      }
    }
  }

  override fun onPostNoClicked(post: ChanPost) {
    threadPresenterCallback?.quote(post, false)
  }

  override fun onPostSelectionQuoted(post: ChanPost, quoted: CharSequence) {
    threadPresenterCallback?.quote(post, quoted)
  }

  override fun showPostOptions(post: ChanPost, inPopup: Boolean, items: List<FloatingListMenuItem>) {
    val gravity = if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
      when (currentChanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> FloatingListMenuGravity.BottomLeft
        is ChanDescriptor.ThreadDescriptor -> FloatingListMenuGravity.BottomRight
        else -> return
      }
    } else {
      FloatingListMenuGravity.Bottom
    }

    val floatingListMenuController = FloatingListMenuController(
      context,
      gravity,
      items,
      { item -> onPostOptionClicked(post, (item.key as Int), inPopup) }
    )

    threadPresenterCallback?.presentController(floatingListMenuController, true)
  }

  override fun hasAlreadySeenPost(post: ChanPost): Boolean {
    if (currentChanDescriptor == null) {
      // Invalid loadable, hide the label
      return true
    }

    return if (currentChanDescriptor!!.isCatalogDescriptor()) {
      // Not in a thread, hide the label
      true
    } else {
      seenPostsManager.hasAlreadySeenPost(currentChanDescriptor!!, post)
    }
  }

  override fun onShowPostReplies(post: ChanPost) {
    if (!isBound) {
      return
    }

    val posts: MutableList<ChanPost> = ArrayList()
    val threadDescriptor = currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    post.iterateRepliesFrom { replyPostNo ->
      val replyPost = chanThreadManager.findPostByPostNo(threadDescriptor, replyPostNo)
      if (replyPost != null) {
        posts.add(replyPost)
      }
    }

    if (posts.size > 0) {
      threadPresenterCallback?.showPostsPopup(post, posts)
    }
  }

  override suspend fun timeUntilLoadMoreMs(): Long {
    return chanThreadTicker.timeUntilLoadMoreMs() ?: 0L
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

  private suspend fun saveUnsavePost(post: ChanPost) {
    if (savedReplyManager.isSaved(post.postDescriptor)) {
      savedReplyManager.unsavePost(post.postDescriptor)
    } else {
      savedReplyManager.savePost(post.postDescriptor)
    }

    // TODO(KurobaEx): refactoring (maybe I should reload everything from the database instead?)

    // force reload for reply highlighting
    clearMemoryCacheAndReload()
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
            val isSuccess = chanPostRepository.deletePost(post.postDescriptor)
              .peekError { error ->
                Logger.e(TAG, "Error while trying to delete post " +
                  "${post.postDescriptor} from the database", error)
              }
              .valueOrNull() != null

            if (isSuccess) {
              chanThreadManager.deletePost(post.postDescriptor)

              normalLoad()
            }
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
    val text = StringBuilder()

    for (image in post.postImages) {
      text
        .append("Filename: ")
        .append(image.filename)
        .append(".")
        .append(image.extension)

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
      return
    }

    val descriptor = currentChanDescriptor
      ?: return

    val localSearchType = currentLocalSearchType
      ?: return

    val searchQuery = localSearchManager.getSearchQuery(localSearchType)

    threadPresenterCallback?.showPostsForChanDescriptor(
      descriptor,
      PostsFilter(postHideHelper, order, searchQuery)
    )
  }

  fun showImageReencodingWindow(supportsReencode: Boolean) {
    threadPresenterCallback?.showImageReencodingWindow(currentChanDescriptor!!, supportsReencode)
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
    suspend fun showExternalThread(threadDescriptor: ChanDescriptor.ThreadDescriptor)
    suspend fun openThreadInArchive(threadDescriptor: ChanDescriptor.ThreadDescriptor)
    suspend fun showBoard(boardDescriptor: BoardDescriptor, animated: Boolean)
    suspend fun setBoard(boardDescriptor: BoardDescriptor, animated: Boolean)
    fun openLink(link: String)
    fun openReportView(post: ChanPost)
    fun showPostsPopup(forPost: ChanPost, posts: List<ChanPost>)
    fun hidePostsPopup()
    fun showImages(images: List<ChanPostImage>, index: Int, chanDescriptor: ChanDescriptor, thumbnail: ThumbnailView)
    fun showAlbum(images: List<ChanPostImage>, index: Int)
    fun scrollTo(displayPosition: Int, smooth: Boolean)
    fun smoothScrollNewPosts(displayPosition: Int)
    fun highlightPost(postDescriptor: PostDescriptor)
    fun highlightPostId(id: String)
    fun highlightPostTripcode(tripcode: CharSequence?)
    fun filterPostTripcode(tripcode: CharSequence?)
    fun filterPostImageHash(post: ChanPost)
    fun selectPost(post: Long)
    fun showSearch(show: Boolean)
    fun setSearchStatus(query: String?, setEmptyText: Boolean, hideKeyboard: Boolean)
    fun quote(post: ChanPost, withText: Boolean)
    fun quote(post: ChanPost, text: CharSequence)
    fun confirmPostDelete(post: ChanPost)
    fun showDeleting()
    fun hideDeleting(message: String)
    fun hideThread(post: ChanPost, threadNo: Long, hide: Boolean)
    fun showNewPostsNotification(show: Boolean, newPostsCount: Int)
    fun showImageReencodingWindow(chanDescriptor: ChanDescriptor, supportsReencode: Boolean)
    fun showHideOrRemoveWholeChainDialog(hide: Boolean, post: ChanPost, threadNo: Long)
    fun hideOrRemovePosts(hide: Boolean, wholeChain: Boolean, postDescriptors: Set<PostDescriptor>, threadNo: Long)
    fun unhideOrUnremovePost(post: ChanPost)
    fun viewRemovedPostsForTheThread(threadPosts: List<PostDescriptor>, threadDescriptor: ChanDescriptor.ThreadDescriptor)
    fun onRestoreRemovedPostsClicked(chanDescriptor: ChanDescriptor, selectedPosts: List<PostDescriptor>)
    fun onPostUpdated(post: ChanPost)
    fun presentController(floatingListMenuController: FloatingListMenuController, animate: Boolean)
    fun showToolbar()
    fun showAvailableArchivesList(threadDescriptor: ChanDescriptor.ThreadDescriptor)
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
    private const val POST_OPTION_FILTER_TRIPCODE = 100
    private const val POST_OPTION_FILTER_IMAGE_HASH = 101

    private const val THUMBNAIL_COPY_URL = 1000
  }

}