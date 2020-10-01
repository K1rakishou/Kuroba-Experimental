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
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.loader.LoaderBatchResult
import com.github.k1rakishou.chan.core.loader.LoaderResult.Succeeded
import com.github.k1rakishou.chan.core.manager.*
import com.github.k1rakishou.chan.core.model.ChanThread
import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.model.PostImage
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.loader.ChanLoaderException
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoader
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoader.ChanLoaderCallback
import com.github.k1rakishou.chan.core.site.parser.MockReplyManager
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4PagesRequest.BoardPage
import com.github.k1rakishou.chan.core.usecase.PreloadAllImagesInThreadUseCase
import com.github.k1rakishou.chan.ui.adapter.PostAdapter.PostAdapterCallback
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.ThreadStatusCell
import com.github.k1rakishou.chan.ui.controller.floating_menu.FloatingListMenuController
import com.github.k1rakishou.chan.ui.helper.PostHelper
import com.github.k1rakishou.chan.ui.layout.ThreadListLayout.ThreadListLayoutPresenterCallback
import com.github.k1rakishou.chan.ui.text.span.PostLinkable
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.AndroidUtils.getString
import com.github.k1rakishou.chan.utils.AndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.chan.utils.PostUtils.findPostById
import com.github.k1rakishou.chan.utils.PostUtils.findPostWithReplies
import com.github.k1rakishou.chan.utils.PostUtils.getReadableFileSize
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.model.data.descriptor.ArchiveDescriptor
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.repository.ChanPostRepository
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
  private val chanLoaderManager: ChanLoaderManager,
  private val pageRequestManager: PageRequestManager,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val savedReplyManager: SavedReplyManager,
  private val postHideManager: PostHideManager,
  private val chanPostRepository: ChanPostRepository,
  private val mockReplyManager: MockReplyManager,
  private val onDemandContentLoaderManager: OnDemandContentLoaderManager,
  private val seenPostsManager: SeenPostsManager,
  private val historyNavigationManager: HistoryNavigationManager,
  private val postFilterManager: PostFilterManager,
  private val chanFilterManager: ChanFilterManager,
  private val pastViewedPostNoInfoHolder: LastViewedPostNoInfoHolder,
  private val chanThreadViewableInfoManager: ChanThreadViewableInfoManager,
  private val localSearchManager: LocalSearchManager,
  private val preloadAllImagesInThreadUseCase: PreloadAllImagesInThreadUseCase
) : ChanLoaderCallback,
  PostAdapterCallback,
  PostCellCallback,
  ThreadStatusCell.Callback,
  ThreadListLayoutPresenterCallback,
  CoroutineScope {

  private var threadPresenterCallback: ThreadPresenterCallback? = null
  private var currentChanDescriptor: ChanDescriptor? = null
  private var chanLoader: ChanThreadLoader? = null
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
    get() = currentChanDescriptor != null && chanLoader != null

  val isPinned: Boolean
    get() {
      val threadDescriptor = currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
        ?: return false

      return bookmarksManager.exists(threadDescriptor)
    }

  val currentLocalSearchType: LocalSearchType?
    get() {
      if (currentChanDescriptor == null) {
        return null
      }

      if (currentChanDescriptor is ChanDescriptor.ThreadDescriptor) {
        return LocalSearchType.ThreadSearch
      } else {
        return LocalSearchType.CatalogSearch
      }
    }

  override fun getChanDescriptor(): ChanDescriptor? {
    return currentChanDescriptor
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
    if (chanDescriptor == this.currentChanDescriptor) {
      return
    }

    if (isBound) {
      unbindChanDescriptor(false)
    }

    this.postOptionsClickExecutor = RendezvousCoroutineExecutor(this)
    this.serializedCoroutineExecutor = SerializedCoroutineExecutor(this)
    this.currentChanDescriptor = chanDescriptor

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      bookmarksManager.setCurrentOpenThreadDescriptor(chanDescriptor)
    }

    compositeDisposable += onDemandContentLoaderManager.listenPostContentUpdates()
      .subscribe(
        { batchResult -> onPostUpdatedWithNewContent(batchResult) },
        { error -> Logger.e(TAG, "Post content updates error", error) }
      )

    threadPresenterCallback?.showLoading()

    Logger.d(TAG, "chanLoaderManager.obtain($chanDescriptor)")
    chanLoader = chanLoaderManager.obtain(chanDescriptor, this@ThreadPresenter)
  }

  fun unbindChanDescriptor(isDestroying: Boolean) {
    if (isBound) {
      Logger.d(TAG, "chanLoaderManager.release($chanDescriptor)")

      if (currentChanDescriptor != null) {
        onDemandContentLoaderManager.cancelAllForDescriptor(currentChanDescriptor!!)
      }

      chanLoader!!.clearTimer()
      chanLoaderManager.release(chanLoader!!, this)
      chanLoader = null
      currentChanDescriptor = null
      threadPresenterCallback?.showLoading()
    }

    if (isDestroying) {
      job.cancelChildren()
    }

    compositeDisposable.clear()
  }

  suspend fun requestThreadInitialData(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (!isBound) {
      return
    }

    if (chanLoader?.thread != null) {
      chanLoader!!.quickLoad()
      return
    }

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

    requestData()
  }

  suspend fun requestCatalogInitialData(catalogDescriptor: ChanDescriptor.CatalogDescriptor) {
    BackgroundUtils.ensureMainThread()

    if (!isBound) {
      return
    }

    threadPresenterCallback?.showLoading()

    supervisorScope {
      val jobs = mutableListOf<Deferred<Unit>>()

      jobs += async(Dispatchers.IO) { postHideManager.preloadForCatalog(catalogDescriptor) }

      ModularResult.Try { jobs.awaitAll() }
        .peekError { error -> Logger.e(TAG, "requestCatalogInitialData() error", error) }
        .ignore()
    }

    chanLoader?.requestData()
  }

  fun forceRequestData() {
    BackgroundUtils.ensureMainThread()

    if (!isBound || currentChanDescriptor == null) {
      return
    }

    val threadNo = chanLoader?.thread?.op?.no
      ?: return

    launch {
      val threadDescriptor = currentChanDescriptor!!.toThreadDescriptor(threadNo)
      threadPresenterCallback?.showLoading()

      chanPostRepository.awaitUntilInitialized()
      chanPostRepository.deleteThread(threadDescriptor)
        .peekError { error -> Logger.e(TAG, "Failed to delete thread ${threadDescriptor}", error) }

      val postDescriptors = chanLoader?.thread?.getPosts()
        ?.map { post -> post.postDescriptor }
        ?: emptyList()

      postFilterManager.removeMany(postDescriptors)
      chanLoader?.thread?.clearPosts()

      chanLoader?.requestData()
    }
  }

  fun requestData() {
    BackgroundUtils.ensureMainThread()

    if (isBound) {
      threadPresenterCallback?.showLoading()
      chanLoader?.requestData()
    }
  }

  fun quickReload() {
    BackgroundUtils.ensureMainThread()

    if (isBound) {
      threadPresenterCallback?.showLoading()
      chanLoader?.quickLoad()
    }
  }

  fun fullReload() {
    if (!isBound) {
      return
    }

    val thread = chanLoader?.thread
      ?: return

    when (val descriptor = thread.chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        launch {
          chanPostRepository.deleteThreadsFromCache(listOf(descriptor))
          thread.clearPosts()

          chanLoader!!.reloadFromDatabase()
        }
      }
      is ChanDescriptor.CatalogDescriptor -> {
        launch {
          val catalogThreads = thread.getPosts()
            .map { post ->
              require(post.isOP) { "Not OP post!" }
              return@map ChanDescriptor.ThreadDescriptor.create(descriptor, post.no)
            }

          chanPostRepository.deleteThreadsFromCache(catalogThreads)
          thread.clearPosts()

          // TODO(KurobaEx-themes): reload from the database, we have all the necessary info, what's left
          //  is the SQL query
          chanLoader!!.requestData()
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

    launch { threadPresenterCallback?.showThread(archiveThreadDescriptor) }
  }

  suspend fun onForegroundChanged(foreground: Boolean) {
    if (!isBound) {
      return
    }

    if (foreground && isWatching) {
      chanLoader!!.requestMoreDataAndResetTimer()

      if (chanLoader!!.thread != null) {
        // Show loading indicator in the status cell
        showPosts()
      }

      return
    }

    chanLoader!!.clearTimer()
  }

  @Synchronized
  fun pin(): Boolean {
    if (!bookmarksManager.isReady()) {
      return false
    }

    val threadDescriptor = currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
      ?: return false

    if (bookmarksManager.exists(threadDescriptor)) {
      bookmarksManager.deleteBookmark(threadDescriptor)
      return true
    }

    val op = chanLoader?.thread?.op
    if (op != null) {
      bookmarksManager.createBookmark(
        threadDescriptor,
        PostHelper.getTitle(op, threadDescriptor),
        op.firstImage()?.thumbnailUrl
      )

      return true
    }

    bookmarksManager.createBookmark(threadDescriptor)
    return true
  }

  suspend fun onSearchVisibilityChanged(visible: Boolean) {
    if (currentChanDescriptor == null) {
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

    if (chanLoader != null && chanLoader!!.thread != null) {
      showPosts()
    }
  }

  suspend fun onSearchEntered() {
    if (chanLoader == null || chanLoader!!.thread == null) {
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

      if (chanLoader != null && chanLoader!!.thread != null) {
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
    val posts = threadPresenterCallback?.displayingPosts
    val pos = threadPresenterCallback?.currentPosition

    if (posts == null || pos == null) {
      return
    }

    val displayPosition = pos[0]
    val images: MutableList<PostImage> = ArrayList()
    var index = 0

    for (i in posts.indices) {
      val item = posts[i]
      images.addAll(item.postImages)

      if (i == displayPosition) {
        index = images.size
      }
    }

    threadPresenterCallback?.showAlbum(images, index)
  }

  override fun onPostBind(post: Post) {
    BackgroundUtils.ensureMainThread()

    if (currentChanDescriptor != null) {
      onDemandContentLoaderManager.onPostBind(currentChanDescriptor!!, post)
      seenPostsManager.onPostBind(currentChanDescriptor!!, post)
    }
  }

  override fun onPostUnbind(post: Post, isActuallyRecycling: Boolean) {
    BackgroundUtils.ensureMainThread()

    if (currentChanDescriptor != null) {
      onDemandContentLoaderManager.onPostUnbind(currentChanDescriptor!!, post, isActuallyRecycling)
      seenPostsManager.onPostUnbind(currentChanDescriptor!!, post)
    }
  }

  private suspend fun onFiltersChanged() {
    chanPostRepository.awaitUntilInitialized()
    Logger.d(TAG, "onFiltersChanged() clearing posts cache")

    var shouldShowLoadingIndicator = false

    chanLoaderManager.currentCatalogDescriptor?.let { catalogDescriptor ->
      val chanLoader = chanLoaderManager.getLoader(catalogDescriptor)
        ?: return@let

      val catalogPosts = chanLoader.thread?.getPosts()
        ?: return@let

      val threadDescriptors = catalogPosts.map { catalogPost ->
        check(catalogPost.isOP) { "Not OP post!" }
        return@map ChanDescriptor.ThreadDescriptor.create(catalogDescriptor, catalogPost.no)
      }

      if (catalogDescriptor == currentChanDescriptor) {
        shouldShowLoadingIndicator = true
      }

      chanPostRepository.deleteThreadsFromCache(threadDescriptors)

      chanLoader.thread?.clearPosts()
      chanLoader.requestData()
    }

    chanLoaderManager.currentThreadDescriptor?.let { threadDescriptor ->
      val chanLoader = chanLoaderManager.getLoader(threadDescriptor)
        ?: return@let

      if (threadDescriptor == currentChanDescriptor) {
        shouldShowLoadingIndicator = true
      }

      chanPostRepository.deleteThreadsFromCache(listOf(threadDescriptor))

      chanLoader.thread?.clearPosts()
      chanLoader.requestData()
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
    for (loaderResult in batchResult.results) {
      if (loaderResult is Succeeded && loaderResult.needUpdateView) {
        return true
      }
    }

    return false
  }

  override suspend fun onChanLoaderData(result: ChanThread) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "onChanLoaderData() called")

    if (!isBound) {
      Logger.e(TAG, "onChanLoaderData when not bound!")
      return
    }

    val localChanDescriptor = currentChanDescriptor
      ?: return

    if (isWatching) {
      chanLoader!!.setTimer()
    }

    // allow for search refreshes inside the catalog
    if (result.chanDescriptor.isCatalogDescriptor()) {
      onSearchEntered()
    } else {
      showPosts()
    }

    if (localChanDescriptor is ChanDescriptor.ThreadDescriptor) {
      handleNewPosts(localChanDescriptor, result)
    }

    chanThreadViewableInfoManager.getAndConsumeMarkedPostNo(localChanDescriptor) { markedPostNo ->
      handleMarkedPost(markedPostNo)
    }

    createNewNavHistoryElement(localChanDescriptor, result)
    updateBookmarkInfoIfNecessary(localChanDescriptor, result)
  }

  private fun handleNewPosts(threadDescriptor: ChanDescriptor.ThreadDescriptor, result: ChanThread) {
    var more = 0

    chanThreadViewableInfoManager.update(threadDescriptor) { chanThreadViewableInfo ->
      val lastLoadedPostNo = chanThreadViewableInfo.lastLoadedPostNo

      if (lastLoadedPostNo > 0) {
        for (post in result.getPosts()) {
          if (post.no == lastLoadedPostNo) {
            more = result.postsCount - result.getPosts().indexOf(post) - 1
            break
          }
        }
      }

      chanThreadViewableInfo.lastLoadedPostNo = result.getPosts().lastOrNull()?.no ?: -1L

      if (chanThreadViewableInfo.lastViewedPostNo < 0L) {
        chanThreadViewableInfo.lastViewedPostNo = chanThreadViewableInfo.lastLoadedPostNo
      }
    }

    if (more > 0 && threadDescriptor.threadNo == result.chanDescriptor.threadNoOrNull()) {
      threadPresenterCallback?.showNewPostsNotification(true, more)
    }

    if (threadDescriptor.threadNo == result.chanDescriptor.threadNoOrNull()) {
      if (forcePageUpdate) {
        pageRequestManager.forceUpdateForBoard(threadDescriptor.boardDescriptor)
        forcePageUpdate = false
      }
    }
  }

  private fun handleMarkedPost(markedPostNo: Long) {
    val chanThread = chanLoader?.thread
      ?: return

    val markedPost = findPostById(markedPostNo, chanThread)
      ?: return

    highlightPost(markedPost)

    if (BackgroundUtils.isInForeground()) {
      BackgroundUtils.runOnMainThread({ scrollToPost(markedPost, false) }, 1000)
    }
  }

  private fun updateBookmarkInfoIfNecessary(chanDescriptor: ChanDescriptor, chanThread: ChanThread) {
    val threadDescriptor = chanDescriptor.toThreadDescriptor(chanThread.op.no)
    val opThumbnailUrl = chanLoader?.thread?.op?.firstImage()?.thumbnailUrl
    val title = PostHelper.getTitle(chanThread.op, threadDescriptor)

    bookmarksManager.updateBookmark(
      threadDescriptor,
      BookmarksManager.NotifyListenersOption.NotifyEager
    ) { threadBookmark ->
      if (threadBookmark.title.isNullOrEmpty()) {
        threadBookmark.title = title
      }

      if (threadBookmark.thumbnailUrl == null && opThumbnailUrl != null) {
        threadBookmark.thumbnailUrl = opThumbnailUrl
      }
    }
  }

  private fun createNewNavHistoryElement(chanDescriptor: ChanDescriptor, chanThread: ChanThread) {
    when (chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> {
        val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
          ?: return

        val siteIconUrl = site.icon().url
        val title = String.format(Locale.ENGLISH, "%s/%s", site.name(), chanDescriptor.boardCode())

        historyNavigationManager.createNewNavElement(chanDescriptor, siteIconUrl, title)
      }

      is ChanDescriptor.ThreadDescriptor -> {
        val image = chanLoader?.thread?.op?.firstImage()
        val title = PostHelper.getTitle(chanThread.op, chanDescriptor)

        if (image != null && title.isNotEmpty()) {
          historyNavigationManager.createNewNavElement(chanDescriptor, image.thumbnailUrl, title)
        }
      }
    }
  }

  override fun onChanLoaderError(error: ChanLoaderException) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "onChanLoaderError() called")

    threadPresenterCallback?.showError(error)
  }

  override suspend fun onListScrolledToBottom() {
    if (!isBound) {
      return
    }

    val thread = chanLoader?.thread
      ?: return

    val descriptor = chanDescriptor

    if (descriptor is ChanDescriptor.ThreadDescriptor && thread.postsCount > 0) {
      val posts = thread.getPosts()
      val lastPostNo = posts.last().no

      chanThreadViewableInfoManager.update(descriptor) { chanThreadViewableInfo ->
        chanThreadViewableInfo.lastViewedPostNo = lastPostNo
      }

      pastViewedPostNoInfoHolder.setLastViewedPostNo(descriptor, lastPostNo)
    }

    threadPresenterCallback?.showNewPostsNotification(false, -1)

    // Update the last seen indicator
    showPosts()
  }

  fun onNewPostsViewClicked() {
    if (!isBound || chanDescriptor == null) {
      return
    }

    chanThreadViewableInfoManager.view(chanDescriptor!!) { chanThreadViewableInfoView ->
      val post = findPostById(chanThreadViewableInfoView.lastViewedPostNo, chanLoader!!.thread)
      var position = -1

      if (post != null) {
        val posts = threadPresenterCallback?.displayingPosts
          ?: return@view

        for (i in posts.indices) {
          val needle = posts[i]
          if (post.no == needle.no) {
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

  fun scrollToImage(postImage: PostImage, smooth: Boolean) {
    val localSearchType = currentLocalSearchType
      ?: return

    val searchOpen = localSearchManager.isSearchOpened(localSearchType)
    if (searchOpen) {
      return
    }

    var position = -1
    val posts = threadPresenterCallback?.displayingPosts
      ?: return

    out@ for (i in posts.indices) {
      val post = posts[i]
      for (j in 0 until post.postImagesCount) {
        if (post.postImages[j] === postImage) {
          position = i
          break@out
        }
      }
    }

    if (position >= 0) {
      scrollTo(position, smooth)
    }
  }

  fun scrollToPost(needle: Post, smooth: Boolean) {
    scrollToPostByPostNo(needle.no, smooth)
  }

  @JvmOverloads
  fun scrollToPostByPostNo(postNo: Long, smooth: Boolean = true) {
    var position = -1
    val posts = threadPresenterCallback?.displayingPosts
      ?: return

    for (i in posts.indices) {
      val post = posts[i]
      if (post.no == postNo) {
        position = i
        break
      }
    }

    if (position >= 0) {
      scrollTo(position, smooth)
    }
  }

  fun highlightPost(post: Post) {
    threadPresenterCallback?.highlightPost(post)
  }

  fun selectPost(post: Long) {
    threadPresenterCallback?.selectPost(post)
  }

  fun selectPostImage(postImage: PostImage) {
    val posts = threadPresenterCallback?.displayingPosts
      ?: return

    for (post in posts) {
      for (image in post.postImages) {
        if (image === postImage) {
          scrollToPost(post, false)
          highlightPost(post)
          return
        }
      }
    }
  }

  fun getPostFromPostImage(postImage: PostImage): Post? {
    val posts = threadPresenterCallback?.displayingPosts
      ?: return null

    for (post in posts) {
      for (image in post.postImages) {
        if (image === postImage) {
          return post
        }
      }
    }

    return null
  }

  override fun onPostClicked(post: Post) {
    if (!isBound || currentChanDescriptor is ChanDescriptor.ThreadDescriptor) {
      return
    }

    serializedCoroutineExecutor.post {
      val newThreadDescriptor = currentChanDescriptor!!.toThreadDescriptor(post.no)
      highlightPost(post)

      threadPresenterCallback?.showThread(newThreadDescriptor)
    }
  }

  override fun onPostDoubleClicked(post: Post) {
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

        highlightPost(post)
        scrollToPost(post, false)
      } else {
        threadPresenterCallback?.postClicked(post)
      }
    }
  }

  override fun onThumbnailClicked(postImage: PostImage, thumbnail: ThumbnailView) {
    if (!isBound) {
      return
    }

    val posts = threadPresenterCallback?.displayingPosts
      ?: return

    var index = -1
    val images = ArrayList<PostImage>()

    for (post in posts) {
      for (image in post.postImages) {
        if (image.imageUrl == null && image.thumbnailUrl == null) {
          Logger.d(TAG, "onThumbnailClicked() image.imageUrl == null && image.thumbnailUrl == null")
          continue
        }

        val imageUrl = image.imageUrl
        val setCallback = (!post.deleted.get()
          || ArchiveDescriptor.isActualArchive(image.archiveId)
          || imageUrl != null && cacheHandler.cacheFileExists(imageUrl.toString()))

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

  override fun onThumbnailLongClicked(postImage: PostImage, thumbnail: ThumbnailView) {
    if (!isBound) {
      return
    }

    val items = mutableListOf<FloatingListMenuItem>()
    items += createMenuItem(THUMBNAIL_COPY_URL, R.string.action_copy_image_url)

    val floatingListMenuController = FloatingListMenuController(
      context,
      items,
      { item -> onThumbnailOptionClicked(item.key as Int, postImage, thumbnail) }
    )

    threadPresenterCallback?.presentController(floatingListMenuController, true)
  }

  private fun onThumbnailOptionClicked(id: Int, postImage: PostImage, thumbnail: ThumbnailView) {
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

  override fun onPopulatePostOptions(post: Post, menu: MutableList<FloatingListMenuItem>) {
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
        post.boardDescriptor.boardCode,
        post.no
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

    if (chanDescriptor.isCatalogDescriptor() || chanDescriptor.isThreadDescriptor() && !post.isOP) {
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

    val siteDescriptor = post.boardDescriptor.siteDescriptor
    val containsSite = siteManager.bySiteDescriptor(siteDescriptor) != null

    if (site.siteFeature(Site.SiteFeature.POST_DELETE)) {
      if (containsSite) {
        val savedReply = savedReplyManager.getSavedReply(post.postDescriptor)
        if (savedReply?.password != null) {
          menu.add(createMenuItem(POST_OPTION_DELETE, R.string.post_delete))
        }
      }
    }

    if (post.linkables.size > 0) {
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

    if (AndroidUtils.isDevBuild() && chanDescriptor.threadNoOrNull() ?: -1L > 0) {
      menu.add(createMenuItem(POST_OPTION_MOCK_REPLY, R.string.mock_reply))
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

  override fun onPostOptionClicked(post: Post, id: Any, inPopup: Boolean) {
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
        POST_OPTION_LINKS -> if (post.linkables.size > 0) {
          threadPresenterCallback?.showPostLinkables(post)
        }
        POST_OPTION_COPY_TEXT -> threadPresenterCallback?.clipboardPost(post)
        POST_OPTION_REPORT -> {
          if (inPopup) {
            threadPresenterCallback?.hidePostsPopup()
          }
          threadPresenterCallback?.openReportView(post)
        }
        POST_OPTION_HIGHLIGHT_ID -> threadPresenterCallback?.highlightPostId(post.posterId)
        POST_OPTION_HIGHLIGHT_TRIPCODE -> threadPresenterCallback?.highlightPostTripcode(post.tripcode)
        POST_OPTION_FILTER_TRIPCODE -> threadPresenterCallback?.filterPostTripcode(post.tripcode)
        POST_OPTION_FILTER_IMAGE_HASH -> threadPresenterCallback?.filterPostImageHash(post)
        POST_OPTION_DELETE -> requestDeletePost(post)
        POST_OPTION_SAVE -> saveUnsavePost(post)
        POST_OPTION_BOOKMARK -> {
          val descriptor = currentChanDescriptor
            ?: return@post

          if (!post.isOP) {
            return@post
          }

          bookmarksManager.createBookmark(
            descriptor.toThreadDescriptor(post.no),
            PostHelper.getTitle(post, chanDescriptor),
            post.firstImage()?.thumbnailUrl
          )
        }
        POST_OPTION_OPEN_BROWSER -> if (isBound) {
          val site = currentChanDescriptor?.let { chanDescriptor ->
            siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
          } ?: return@post

          val url = site.resolvable().desktopUrl(currentChanDescriptor!!, post.no)
          AndroidUtils.openLink(url)
        }
        POST_OPTION_SHARE -> if (isBound) {
          val site = currentChanDescriptor?.let { chanDescriptor ->
            siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
          } ?: return@post

          val url = site.resolvable().desktopUrl(currentChanDescriptor!!, post.no)
          AndroidUtils.shareLink(url)
        }
        POST_OPTION_REMOVE,
        POST_OPTION_HIDE -> {
          if (chanLoader == null || chanLoader!!.thread == null) {
            return@post
          }

          val hide = id == POST_OPTION_HIDE
          if (chanLoader!!.thread!!.chanDescriptor.isCatalogDescriptor()) {
            threadPresenterCallback?.hideThread(post, post.no, hide)
          } else {
            val isEmpty = post.repliesFromCount == 0
            if (isEmpty) {
              // no replies to this post so no point in showing the dialog
              hideOrRemovePosts(hide, false, post, chanLoader!!.thread!!.op.no)
            } else {
              // show a dialog to the user with options to hide/remove the whole chain of posts
              threadPresenterCallback?.showHideOrRemoveWholeChainDialog(hide,
                post,
                chanLoader!!.thread!!.op.no
              )
            }
          }
        }
        POST_OPTION_MOCK_REPLY -> if (isBound && currentChanDescriptor is ChanDescriptor.ThreadDescriptor) {
          val threadDescriptor = currentChanDescriptor!! as ChanDescriptor.ThreadDescriptor

          mockReplyManager.addMockReply(
            post.boardDescriptor.siteName(),
            threadDescriptor.boardCode(),
            threadDescriptor.threadNo,
            post.no
          )
          showToast(context, "Refresh to add mock replies")
        }
      }
    }
  }

  override fun onPostLinkableClicked(post: Post, linkable: PostLinkable) {
    serializedCoroutineExecutor.post {
      val thread = chanLoader?.thread
        ?: return@post
      val siteName = currentChanDescriptor?.siteName()
        ?: return@post

      if (ChanSettings.verboseLogs.get()) {
        Logger.d(TAG, "onPostLinkableClicked, linkable=${linkable}")
      }

      if (linkable.type == PostLinkable.Type.QUOTE && isBound) {
        val postId = linkable.linkableValue.extractLongOrNull()
        if (postId == null) {
          Logger.e(TAG, "Bad quote linkable: linkableValue = ${linkable.linkableValue}")
          return@post
        }

        val linked = findPostById(postId, thread)
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

      if (linkable.type == PostLinkable.Type.THREAD && isBound) {
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

      if (linkable.type == PostLinkable.Type.BOARD && isBound) {
        val link = (linkable.linkableValue as? PostLinkable.Value.StringValue)?.value
        if (link == null) {
          Logger.e(TAG, "Bad board linkable: linkableValue = ${linkable.linkableValue}")
          return@post
        }

        val boardDescriptor = BoardDescriptor.create(siteName, link.toString())
        val board = boardManager.byBoardDescriptor(boardDescriptor)

        if (board == null) {
          showToast(context, R.string.site_uses_dynamic_boards)
          return@post
        }

        threadPresenterCallback?.showBoard(boardDescriptor, true)
        return@post
      }

      if (linkable.type == PostLinkable.Type.SEARCH && isBound) {
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

        // TODO(KurobaEx):
//        localSearchManager.onSearchEntered(LocalSearchType.CatalogSearch, searchLink.search)
//        threadPresenterCallback?.setBoard(boardDescriptor, true)
      }
    }
  }

  override fun onPostNoClicked(post: Post) {
    threadPresenterCallback?.quote(post, false)
  }

  override fun onPostSelectionQuoted(post: Post, quoted: CharSequence) {
    threadPresenterCallback?.quote(post, quoted)
  }

  override fun showPostOptions(post: Post, inPopup: Boolean, items: List<FloatingListMenuItem>) {
    val isThreadMode = currentChanDescriptor?.isThreadDescriptor()
      ?: return

    val floatingListMenuController = FloatingListMenuController.create(
      context,
      isThreadMode,
      items
    ) { item -> onPostOptionClicked(post, (item.key as Int), inPopup) }

    threadPresenterCallback?.presentController(floatingListMenuController, true)
  }

  override fun hasAlreadySeenPost(post: Post): Boolean {
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

  override fun onShowPostReplies(post: Post) {
    if (!isBound) {
      return
    }

    val posts: MutableList<Post> = ArrayList()

    for (no in post.repliesFrom) {
      val replyPost = findPostById(no, chanLoader!!.thread)
      if (replyPost != null) {
        posts.add(replyPost)
      }
    }

    if (posts.size > 0) {
      threadPresenterCallback?.showPostsPopup(post, posts)
    }
  }

  override fun getTimeUntilLoadMore(): Long {
    return if (isBound) {
      chanLoader!!.timeUntilLoadMore
    } else {
      0L
    }
  }

  override fun isWatching(): Boolean {
    val thread = chanLoader?.thread
      ?: return false
    val isThreadDescriptor = currentChanDescriptor?.isThreadDescriptor()
      ?: false

    return ChanSettings.autoRefreshThread.get()
      && BackgroundUtils.isInForeground()
      && isBound
      && isThreadDescriptor
      && !thread.isClosed
      && !thread.isArchived
  }

  override fun getChanThread(): ChanThread? {
    return if (isBound) {
      chanLoader!!.thread
    } else {
      null
    }
  }

  fun threadDescriptorOrNull(): ChanDescriptor.ThreadDescriptor? {
    return chanThread?.chanDescriptor as? ChanDescriptor.ThreadDescriptor
  }

  override fun getPage(op: Post): BoardPage? {
    return pageRequestManager.getPage(op)
  }

  override fun onListStatusClicked() {
    if (!isBound) {
      return
    }

    val canRequestMode = chanLoader?.thread
      ?.let { thread -> !thread.isArchived && !thread.isDeleted } == true

    if (canRequestMode) {
      chanLoader?.requestMoreDataAndResetTimer()
    }

    threadPresenterCallback?.showToolbar()
  }

  override suspend fun showThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    threadPresenterCallback?.showThread(threadDescriptor)
  }

  override fun requestNewPostLoad() {
    if (isBound && currentChanDescriptor is ChanDescriptor.ThreadDescriptor) {
      chanLoader?.requestMoreDataAndResetTimer()
      // put in a "request" for a page update whenever the next set of data comes in
      forcePageUpdate = true
    }
  }

  override fun onUnhidePostClick(post: Post) {
    threadPresenterCallback?.unhideOrUnremovePost(post)
  }

  private suspend fun saveUnsavePost(post: Post) {
    if (savedReplyManager.isSaved(post.postDescriptor)) {
      savedReplyManager.unsavePost(post.postDescriptor)
    } else {
      savedReplyManager.savePost(post.postDescriptor)
    }

    chanPostRepository.deleteThreadsFromCache(listOf(post.postDescriptor.threadDescriptor()))
    chanLoader?.thread?.clearPosts()

    // force reload for reply highlighting
    requestData()
  }

  private fun requestDeletePost(post: Post) {
    if (siteManager.bySiteDescriptor(post.boardDescriptor.siteDescriptor) == null) {
      return
    }

    val savedReply = savedReplyManager.getSavedReply(post.postDescriptor)
    if (savedReply?.password != null) {
      threadPresenterCallback?.confirmPostDelete(post)
    }
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  fun deletePostConfirmed(post: Post, onlyImageDelete: Boolean) {
    launch {
      val site = siteManager.bySiteDescriptor(post.boardDescriptor.siteDescriptor)
        ?: return@launch

      threadPresenterCallback?.showDeleting()

      val savedReply = savedReplyManager.getSavedReply(post.postDescriptor)
      if (savedReply?.password == null) {
        threadPresenterCallback?.hideDeleting(
          AndroidUtils.getString(R.string.delete_error_post_is_not_saved)
        )
        return@launch
      }

      val deleteRequest = DeleteRequest(post, savedReply, onlyImageDelete)
      val deleteResult = site.actions().delete(deleteRequest)

      when (deleteResult) {
        is SiteActions.DeleteResult.DeleteComplete -> {
          val deleteResponse = deleteResult.deleteResponse

          val message = when {
            deleteResponse.deleted -> AndroidUtils.getString(R.string.delete_success)
            !TextUtils.isEmpty(deleteResponse.errorMessage) -> deleteResponse.errorMessage
            else -> AndroidUtils.getString(R.string.delete_error)
          }

          if (deleteResponse.deleted) {
            val isSuccess = chanPostRepository.deletePost(post.postDescriptor)
              .peekError { error ->
                Logger.e(TAG, "Error while trying to delete post " +
                  "${post.postDescriptor} from the database", error)
              }
              .valueOrNull() != null

            if (isSuccess) {
              savedReplyManager.unsavePost(post.postDescriptor)
              chanLoader?.thread?.deletePost(post.postDescriptor)

              requestData()
            }
          }

          threadPresenterCallback?.hideDeleting(message)
        }
        is SiteActions.DeleteResult.DeleteError -> {
          val message = AndroidUtils.getString(
            R.string.delete_error,
            deleteResult.error.errorMessageOrClassName()
          )

          threadPresenterCallback?.hideDeleting(message)
        }
      }
    }
  }

  private fun showPostInfo(post: Post) {
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

      if (image.spoiler() && image.isInlined) {
        // all linked files are spoilered, don't say that
        text.append("\nSpoilered")
      }

      text.append("\n")
    }

    text
      .append("Posted: ")
      .append(PostHelper.getLocalDate(post))

    if (!TextUtils.isEmpty(post.posterId) && isBound && chanLoader!!.thread != null) {
      text
        .append("\nId: ")
        .append(post.posterId)

      var count = 0

      chanLoader?.thread?.getPosts()?.forEach { p ->
        if (p.posterId == post.posterId) {
          count++
        }
      }

      text
        .append("\nCount: ")
        .append(count)
    }

    if (!TextUtils.isEmpty(post.tripcode)) {
      text
        .append("\nTripcode: ")
        .append(post.tripcode)
    }

    if (post.httpIcons != null && post.httpIcons.isNotEmpty()) {
      for (icon in post.httpIcons) {
        when {
          icon.url.toString().contains("troll") -> {
            text.append("\nTroll Country: ").append(icon.name)
          }
          icon.url.toString().contains("country") -> {
            text.append("\nCountry: ").append(icon.name)
          }
          icon.url.toString().contains("minileaf") -> {
            text.append("\n4chan Pass Year: ").append(icon.name)
          }
        }
      }
    }

    if (!TextUtils.isEmpty(post.capcode)) {
      text.append("\nCapcode: ").append(post.capcode)
    }

    threadPresenterCallback?.showPostInfo(text.toString())
  }

  private suspend fun showPosts() {
    if (chanLoader == null || chanLoader!!.thread == null) {
      return
    }

    val localSearchType = currentLocalSearchType
      ?: return

    val searchQuery = localSearchManager.getSearchQuery(localSearchType)

    threadPresenterCallback?.showPosts(
      chanLoader!!.thread,
      PostsFilter(order, searchQuery)
    )
  }

  fun showImageReencodingWindow(supportsReencode: Boolean) {
    threadPresenterCallback?.showImageReencodingWindow(currentChanDescriptor!!, supportsReencode)
  }

  fun hideOrRemovePosts(hide: Boolean, wholeChain: Boolean, post: Post, threadNo: Long) {
    val posts: MutableSet<Post> = HashSet()
    if (isBound) {
      if (wholeChain) {
        val thread = chanLoader!!.thread
        if (thread != null) {
          posts.addAll(findPostWithReplies(post.no, thread.getPosts()))
        }
      } else {
        val foundPost = findPostById(post.no, chanLoader!!.thread)
        if (foundPost != null) {
          posts.add(foundPost)
        }
      }
    }

    threadPresenterCallback?.hideOrRemovePosts(hide, wholeChain, posts, threadNo)
  }

  fun showRemovedPostsDialog() {
    if (!isBound || currentChanDescriptor is ChanDescriptor.CatalogDescriptor) {
      return
    }

    val posts = chanLoader?.thread?.getPosts()
      ?: return

    val threadDescriptor = (currentChanDescriptor as? ChanDescriptor.ThreadDescriptor)
      ?: return

    threadPresenterCallback?.viewRemovedPostsForTheThread(posts, threadDescriptor)
  }

  fun onRestoreRemovedPostsClicked(selectedPosts: List<PostDescriptor>) {
    if (!isBound) {
      return
    }

    threadPresenterCallback?.onRestoreRemovedPostsClicked(currentChanDescriptor!!, selectedPosts)
  }

  fun cloudFlareForcePreload() {
    if (!isBound) {
      return
    }

    val descriptor = chanLoader?.chanDescriptor
      ?: return

    if (descriptor !is ChanDescriptor.ThreadDescriptor) {
      return
    }

    if (!descriptor.siteDescriptor().is4chan()) {
      return
    }

    val threadPosts = chanLoader?.thread?.getPosts()
      ?: return

    launch {
      val preloadCallback = object : PreloadAllImagesInThreadUseCase.PreloadCallback {
        override fun onPreloadStarted(toPreload: Int) {
          BackgroundUtils.ensureMainThread()

          if (toPreload > 0) {
            showToast(context, getString(R.string.thread_presenter_started_preloading_images, toPreload))
          } else {
            showToast(context, getString(R.string.thread_presenter_all_images_already_preloaded))
          }
        }

        override fun onPreloadEnded(preloadResult: PreloadAllImagesInThreadUseCase.PreloadResult) {
          BackgroundUtils.ensureMainThread()

          showToast(context, getString(R.string.thread_presenter_done_preloading, preloadResult.preloaded, preloadResult.failed))
        }

        override fun onPreloadingTheSameThread() {
          BackgroundUtils.ensureMainThread()

          showToast(context, getString(R.string.thread_presenter_already_in_progress))
        }
      }

      val params = PreloadAllImagesInThreadUseCase.PreloadAllImagesParams(
        descriptor,
        threadPosts,
        preloadCallback
      )

      // Hack to force Cloudflare to preload all images in a thread on 4chan
      preloadAllImagesInThreadUseCase.execute(params)
    }
  }

  interface ThreadPresenterCallback {
    val displayingPosts: List<Post>
    val currentPosition: IntArray?

    suspend fun showPosts(thread: ChanThread?, filter: PostsFilter)
    fun postClicked(post: Post)
    fun showError(error: ChanLoaderException)
    fun showLoading()
    fun showEmpty()
    fun showPostInfo(info: String)
    fun showPostLinkables(post: Post)
    fun clipboardPost(post: Post)
    suspend fun showThread(threadDescriptor: ChanDescriptor.ThreadDescriptor)
    suspend fun showExternalThread(threadDescriptor: ChanDescriptor.ThreadDescriptor)
    suspend fun showBoard(boardDescriptor: BoardDescriptor, animated: Boolean)
    suspend fun setBoard(boardDescriptor: BoardDescriptor, animated: Boolean)
    fun openLink(link: String)
    fun openReportView(post: Post)
    fun showPostsPopup(forPost: Post, posts: List<Post>)
    fun hidePostsPopup()
    fun showImages(images: List<PostImage>, index: Int, chanDescriptor: ChanDescriptor, thumbnail: ThumbnailView)
    fun showAlbum(images: List<PostImage>, index: Int)
    fun scrollTo(displayPosition: Int, smooth: Boolean)
    fun smoothScrollNewPosts(displayPosition: Int)
    fun highlightPost(post: Post)
    fun highlightPostId(id: String)
    fun highlightPostTripcode(tripcode: CharSequence?)
    fun filterPostTripcode(tripcode: CharSequence?)
    fun filterPostImageHash(post: Post)
    fun selectPost(post: Long)
    fun showSearch(show: Boolean)
    fun setSearchStatus(query: String?, setEmptyText: Boolean, hideKeyboard: Boolean)
    fun quote(post: Post, withText: Boolean)
    fun quote(post: Post, text: CharSequence)
    fun confirmPostDelete(post: Post)
    fun showDeleting()
    fun hideDeleting(message: String)
    fun hideThread(post: Post, threadNo: Long, hide: Boolean)
    fun showNewPostsNotification(show: Boolean, more: Int)
    fun showImageReencodingWindow(chanDescriptor: ChanDescriptor, supportsReencode: Boolean)
    fun showHideOrRemoveWholeChainDialog(hide: Boolean, post: Post, threadNo: Long)
    fun hideOrRemovePosts(hide: Boolean, wholeChain: Boolean, posts: Set<Post>, threadNo: Long)
    fun unhideOrUnremovePost(post: Post)
    fun viewRemovedPostsForTheThread(threadPosts: List<Post>, threadDescriptor: ChanDescriptor.ThreadDescriptor)
    fun onRestoreRemovedPostsClicked(chanDescriptor: ChanDescriptor, selectedPosts: List<PostDescriptor>)
    fun onPostUpdated(post: Post)
    fun presentController(floatingListMenuController: FloatingListMenuController, animate: Boolean)
    fun showToolbar()
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