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
package com.github.adamantcheese.chan.core.presenter

import android.content.Context
import android.text.TextUtils
import android.widget.Toast
import androidx.annotation.StringRes
import com.github.adamantcheese.chan.BuildConfig
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.StartActivity
import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.loader.LoaderBatchResult
import com.github.adamantcheese.chan.core.loader.LoaderResult.Succeeded
import com.github.adamantcheese.chan.core.manager.*
import com.github.adamantcheese.chan.core.manager.WatchManager.PinMessages.PinAddedMessage
import com.github.adamantcheese.chan.core.manager.WatchManager.PinMessages.PinChangedMessage
import com.github.adamantcheese.chan.core.model.ChanThread
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.model.orm.*
import com.github.adamantcheese.chan.core.model.orm.Loadable.LoadableDownloadingState
import com.github.adamantcheese.chan.core.repository.SiteRepository
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.site.Site
import com.github.adamantcheese.chan.core.site.SiteActions
import com.github.adamantcheese.chan.core.site.http.DeleteRequest
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderException
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader.ChanLoaderCallback
import com.github.adamantcheese.chan.core.site.parser.MockReplyManager
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest.BoardPage
import com.github.adamantcheese.chan.ui.adapter.PostAdapter.PostAdapterCallback
import com.github.adamantcheese.chan.ui.adapter.PostsFilter
import com.github.adamantcheese.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell
import com.github.adamantcheese.chan.ui.controller.FloatingListMenuController
import com.github.adamantcheese.chan.ui.helper.PostHelper
import com.github.adamantcheese.chan.ui.layout.ThreadListLayout.ThreadListLayoutPresenterCallback
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory
import com.github.adamantcheese.chan.ui.text.span.PostLinkable
import com.github.adamantcheese.chan.ui.view.ThumbnailView
import com.github.adamantcheese.chan.ui.view.floating_menu.FloatingListMenu.FloatingListMenuItem
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.PostUtils.findPostById
import com.github.adamantcheese.chan.utils.PostUtils.findPostWithReplies
import com.github.adamantcheese.chan.utils.PostUtils.getReadableFileSize
import com.github.k1rakishou.fsaf.FileManager
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class ThreadPresenter @Inject constructor(
  private val cacheHandler: CacheHandler,
  private val filterWatchManager: FilterWatchManager,
  private val watchManager: WatchManager,
  private val databaseManager: DatabaseManager,
  private val chanLoaderManager: ChanLoaderManager,
  private val pageRequestManager: PageRequestManager,
  private val threadSaveManager: ThreadSaveManager,
  private val fileManager: FileManager,
  private val siteRepository: SiteRepository,
  private val mockReplyManager: MockReplyManager,
  private val onDemandContentLoaderManager: OnDemandContentLoaderManager,
  private val seenPostsManager: SeenPostsManager
) : ChanLoaderCallback,
  PostAdapterCallback,
  PostCellCallback,
  ThreadStatusCell.Callback,
  ThreadListLayoutPresenterCallback,
  CoroutineScope {

  private var threadPresenterCallback: ThreadPresenterCallback? = null
  private var loadable: Loadable? = null
  private var chanLoader: ChanThreadLoader? = null
  private var searchOpen = false
  private var searchQuery: String? = null
  private var forcePageUpdate = false
  private var order = PostsFilter.Order.BUMP
  private var historyAdded = false
  private var addToLocalBackHistory = false
  private var context: Context? = null
  private val compositeDisposable = CompositeDisposable()

  private lateinit var job: Job

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("ThreadPresenter")

  val isBound: Boolean
    get() = loadable != null && chanLoader != null

  val isPinned: Boolean
    get() {
      if (!isBound) {
        return false
      }

      val pin = watchManager.findPinByLoadableId(loadable!!.id)
        ?: return false

      return PinType.hasWatchNewPostsFlag(pin.pinType)
    }

  fun create(threadPresenterCallback: ThreadPresenterCallback?) {
    this.threadPresenterCallback = threadPresenterCallback
  }

  fun showNoContent() {
    threadPresenterCallback?.showEmpty()
  }

  @Synchronized
  fun bindLoadable(_loadable: Loadable, addToLocalBackHistory: Boolean) {
    var loadable = _loadable

    if (::job.isInitialized) {
      job.cancel()
    }

    job = SupervisorJob()

    if (loadable != this.loadable) {
      if (isBound) {
        stopSavingThreadIfItIsBeingSaved(this.loadable)
        unbindLoadable()
      }

      val pin = watchManager.findPinByLoadableId(loadable.id)
      // TODO this isn't true anymore, because all loadables come from one location.
      if (pin != null) {
        // Use the loadable from the pin.
        // This way we can store the list position in the pin loadable,
        // and not in a separate loadable instance.
        loadable = pin.loadable
      }

      this.loadable = loadable
      this.addToLocalBackHistory = addToLocalBackHistory

      startSavingThreadIfItIsNotBeingSaved(this.loadable)

      chanLoader = chanLoaderManager.obtain(loadable, this)
      threadPresenterCallback?.showLoading()
      seenPostsManager.preloadForThread(loadable)

      val disposable = onDemandContentLoaderManager.listenPostContentUpdates()
        .subscribe(
          { batchResult -> onPostUpdatedWithNewContent(batchResult) },
          { error -> Logger.e(TAG, "Post content updates error", error) }
        )

      compositeDisposable.add(disposable)
    }
  }

  @Synchronized
  fun bindLoadable(loadable: Loadable) {
    bindLoadable(loadable, true)
  }

  @Synchronized
  fun unbindLoadable() {
    if (isBound) {

      if (loadable != null) {
        onDemandContentLoaderManager.cancelAllForLoadable(loadable!!)
      }

      chanLoader!!.clearTimer()
      chanLoaderManager.release(chanLoader!!, this)
      chanLoader = null
      loadable = null
      historyAdded = false
      addToLocalBackHistory = true
      threadPresenterCallback?.showLoading()
    }

    job.cancel()
    compositeDisposable.clear()
  }

  private fun stopSavingThreadIfItIsBeingSaved(loadable: Loadable?) {
    if (ChanSettings.watchEnabled.get()
      && ChanSettings.watchBackground.get()
      || loadable == null
      || loadable.mode != Loadable.Mode.THREAD) {
      return
    }

    val pin = watchManager.findPinByLoadableId(loadable.id)
    if (pin == null || !PinType.hasDownloadFlag(pin.pinType)) {
      return
    }

    val savedThread = watchManager.findSavedThreadByLoadableId(loadable.id)
    if (savedThread == null
      || loadable.loadableDownloadingState == LoadableDownloadingState.AlreadyDownloaded
      || savedThread.isFullyDownloaded || savedThread.isStopped) {
      return
    }

    watchManager.stopSavingThread(loadable)
    AndroidUtils.postToEventBus(PinChangedMessage(pin))
  }

  private fun startSavingThreadIfItIsNotBeingSaved(loadable: Loadable?) {
    if (ChanSettings.watchEnabled.get()
      && ChanSettings.watchBackground.get()
      || loadable == null
      || loadable.mode != Loadable.Mode.THREAD) {
      // Do not start thread saving if background watcher is enabled
      // Or if we're in the catalog
      return
    }

    val pin = watchManager.findPinByLoadableId(loadable.id)
    if (pin == null || !PinType.hasDownloadFlag(pin.pinType)) {
      // No pin for this loadable we are probably not downloading this thread
      // Pin has no downloading flag
      return
    }

    val savedThread = watchManager.findSavedThreadByLoadableId(loadable.id)
    if (loadable.loadableDownloadingState == LoadableDownloadingState.AlreadyDownloaded
      || savedThread == null
      || savedThread.isFullyDownloaded
      || !savedThread.isStopped) {
      // We are viewing already saved copy of the thread
      // We are not downloading this thread
      // Thread is already fully downloaded
      // Thread saving is already in progress
      return
    }

    if (!fileManager.baseDirectoryExists(LocalThreadsBaseDirectory::class.java)) {
      // Base directory for local threads does not exist or was deleted
      return
    }

    watchManager.startSavingThread(loadable)
    AndroidUtils.postToEventBus(PinChangedMessage(pin))
  }

  fun requestInitialData() {
    if (isBound) {
      if (chanLoader!!.thread == null) {
        requestData()
      } else {
        chanLoader!!.quickLoad()
      }
    }
  }

  @JvmOverloads
  fun requestData(forced: Boolean = false) {
    BackgroundUtils.ensureMainThread()
    if (isBound) {
      threadPresenterCallback?.showLoading()
      chanLoader!!.requestData(forced)
    }
  }

  fun onForegroundChanged(foreground: Boolean) {
    if (isBound) {
      if (foreground && isWatching) {
        chanLoader!!.requestMoreDataAndResetTimer()
        if (chanLoader!!.thread != null) {
          // Show loading indicator in the status cell
          showPosts()
        }
      } else {
        chanLoader!!.clearTimer()
      }
    }
  }

  @Synchronized
  fun pin(): Boolean {
    if (!isBound) {
      return false
    }

    val pin = watchManager.findPinByLoadableId(loadable!!.id)
    if (pin == null) {
      if (chanLoader!!.thread != null) {
        val op = chanLoader!!.thread!!.op
        watchManager.createPin(loadable, op, PinType.WATCH_NEW_POSTS)
      } else {
        watchManager.createPin(loadable)
      }
      return true
    }

    if (PinType.hasWatchNewPostsFlag(pin.pinType)) {
      pin.pinType = PinType.removeWatchNewPostsFlag(pin.pinType)
      if (PinType.hasNoFlags(pin.pinType)) {
        watchManager.deletePin(pin)
      } else {
        watchManager.updatePin(pin)
      }
    } else {
      pin.pinType = PinType.addWatchNewPostsFlag(pin.pinType)
      watchManager.updatePin(pin)
    }

    return true
  }

  @Synchronized
  fun save(): Boolean {
    if (!isBound) {
      return false
    }

    val pin = watchManager.findPinByLoadableId(loadable!!.id)
    if (pin == null || !PinType.hasDownloadFlag(pin.pinType)) {
      val startedSaving = saveInternal()
      if (!startedSaving) {
        watchManager.stopSavingThread(loadable)
      }
      return startedSaving
    }

    if (!PinType.hasWatchNewPostsFlag(pin.pinType)) {
      pin.pinType = PinType.removeDownloadNewPostsFlag(pin.pinType)
      watchManager.deletePin(pin)
    } else {
      watchManager.stopSavingThread(pin.loadable)

      // Remove the flag after stopping thread saving, otherwise we just won't find the thread
      // because the pin won't have the download flag which we check somewhere deep inside the
      // stopSavingThread() method
      pin.pinType = PinType.removeDownloadNewPostsFlag(pin.pinType)
      watchManager.updatePin(pin)
    }

    loadable!!.setLoadableState(LoadableDownloadingState.NotDownloading)
    return true
  }

  private fun saveInternal(): Boolean {
    if (chanLoader!!.thread == null) {
      Logger.e(TAG, "chanLoader.getThread() == null")
      return false
    }

    val op = chanLoader!!.thread!!.op
    val postsToSave = chanLoader!!.thread!!.posts
    val oldPin = watchManager.findPinByLoadableId(loadable!!.id)

    if (oldPin != null) {
      // Save button is clicked and bookmark button is already pressed
      // Update old pin and start saving the thread
      check(!PinType.hasDownloadFlag(oldPin.pinType)) {
        // We forgot to delete pin when cancelling thread download?
        "oldPin already contains DownloadFlag"
      }

      oldPin.pinType = PinType.addDownloadNewPostsFlag(oldPin.pinType)
      watchManager.updatePin(oldPin)

      if (!startSavingThreadInternal(loadable, postsToSave, oldPin)) {
        return false
      }

      AndroidUtils.postToEventBus(PinChangedMessage(oldPin))
    } else {
      // Save button is clicked and bookmark button is not yet pressed
      // Create new pin and start saving the thread

      // We don't want to send PinAddedMessage broadcast right away. We will send it after
      // the thread has been saved
      check(watchManager.createPin(loadable, op, PinType.DOWNLOAD_NEW_POSTS, false)) {
        "Could not create pin for loadable $loadable"
      }

      val newPin = watchManager.getPinByLoadable(loadable)
        ?: throw IllegalStateException("Could not find freshly created pin by loadable $loadable")

      if (!startSavingThreadInternal(loadable, postsToSave, newPin)) {
        return false
      }

      AndroidUtils.postToEventBus(PinAddedMessage(newPin))
    }
    if (!ChanSettings.watchEnabled.get() || !ChanSettings.watchBackground.get()) {
      AndroidUtils.showToast(context, R.string.thread_layout_background_watcher_is_disabled_message, Toast.LENGTH_LONG)
    }
    return true
  }

  private fun startSavingThreadInternal(loadable: Loadable?, postsToSave: List<Post>, newPin: Pin): Boolean {
    check(PinType.hasDownloadFlag(newPin.pinType)) {
      "newPin does not have DownloadFlag: " + newPin.pinType
    }

    return watchManager.startSavingThread(loadable, postsToSave)
  }

  fun onSearchVisibilityChanged(visible: Boolean) {
    searchOpen = visible
    threadPresenterCallback?.showSearch(visible)
    if (!visible) {
      searchQuery = null
    }
    if (chanLoader != null && chanLoader!!.thread != null) {
      showPosts()
    }
  }

  fun onSearchEntered(entered: String?) {
    searchQuery = entered
    if (chanLoader != null && chanLoader!!.thread != null) {
      showPosts()
      if (TextUtils.isEmpty(entered)) {
        threadPresenterCallback?.setSearchStatus(null, true, false)
      } else {
        threadPresenterCallback?.setSearchStatus(entered, false, false)
      }
    }
  }

  fun setOrder(order: PostsFilter.Order) {
    if (this.order != order) {
      this.order = order
      if (chanLoader != null && chanLoader!!.thread != null) {
        scrollTo(0, false)
        showPosts()
      }
    }
  }

  fun refreshUI() {
    showPosts(true)
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

  override fun getLoadable(): Loadable? {
    return loadable
  }

  override fun onPostBind(post: Post) {
    BackgroundUtils.ensureMainThread()

    if (loadable != null) {
      onDemandContentLoaderManager.onPostBind(loadable!!, post)
      seenPostsManager.onPostBind(loadable!!, post)
    }
  }

  override fun onPostUnbind(post: Post, isActuallyRecycling: Boolean) {
    BackgroundUtils.ensureMainThread()
    if (loadable != null) {
      onDemandContentLoaderManager.onPostUnbind(loadable!!, post, isActuallyRecycling)
      seenPostsManager.onPostUnbind(loadable!!, post)
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
      if (loaderResult is Succeeded) {
        if (loaderResult.needUpdateView) {
          return true
        }
      }
    }
    return false
  }

  /*
     * ChanThreadLoader callbacks
     */
  override fun onChanLoaderData(result: ChanThread) {
    BackgroundUtils.ensureMainThread()

    if (isBound) {
      if (isWatching) {
        chanLoader!!.setTimer()
      }
    } else {
      Logger.e(TAG, "onChanLoaderData when not bound!")
      return
    }

    loadable!!.setLoadableState(result.loadable.loadableDownloadingState)
    Logger.d(TAG, "onChanLoaderData() loadableDownloadingState = " + loadable!!.loadableDownloadingState.name)

    //allow for search refreshes inside the catalog
    if (result.loadable.isCatalogMode && !TextUtils.isEmpty(searchQuery)) {
      onSearchEntered(searchQuery)
    } else {
      showPosts()
    }

    if (loadable!!.isThreadMode) {
      val lastLoaded = loadable!!.lastLoaded
      var more = 0

      if (lastLoaded > 0) {
        for (p in result.posts) {
          if (p.no == lastLoaded.toLong()) {
            more = result.postsCount - result.posts.indexOf(p) - 1
            break
          }
        }
      }

      loadable!!.setLastLoaded(result.posts[result.postsCount - 1].no)

      if (loadable!!.lastViewed == -1) {
        loadable!!.setLastViewed(loadable!!.lastLoaded.toLong())
      }

      if (more > 0 && loadable!!.no == result.loadable.no) {
        threadPresenterCallback?.showNewPostsNotification(true, more)
        // deal with any "requests" for a page update
        if (forcePageUpdate) {
          pageRequestManager.forceUpdateForBoard(loadable!!.board.boardDescriptor())
          forcePageUpdate = false
        }
      }
    }

    if (loadable!!.markedNo >= 0) {
      val markedPost = findPostById(loadable!!.markedNo.toLong(), chanLoader!!.thread)
      if (markedPost != null) {
        highlightPost(markedPost)

        if (BackgroundUtils.isInForeground()) {
          scrollToPost(markedPost, false)
        }

        if (StartActivity.loadedFromURL) {
          BackgroundUtils.runOnMainThread({ scrollToPost(markedPost, false) }, 1000)
          StartActivity.loadedFromURL = false
        }
      }

      loadable!!.markedNo = -1
    }

    storeNewPostsIfThreadIsBeingDownloaded(result.posts)
    addHistory()

    // Update loadable in the database
    databaseManager.runTaskAsync(databaseManager.databaseLoadableManager.updateLoadable(loadable))

    if (!ChanSettings.watchEnabled.get() && !ChanSettings.watchBackground.get()
      && loadable!!.loadableDownloadingState == LoadableDownloadingState.AlreadyDownloaded) {
      Logger.d(TAG, "Background watcher is disabled, so we need to update "
        + "ViewThreadController's downloading icon as well as the pin in the DrawerAdapter")

      val pin = watchManager.findPinByLoadableId(loadable!!.id)
      if (pin == null) {
        Logger.d(TAG, "Could not find pin with loadableId = " + loadable!!.id + ", it was already deleted?")
        return
      }

      pin.isError = true
      pin.watching = false

      watchManager.updatePin(pin, true)
    }

    if (result.loadable.isCatalogMode) {
      filterWatchManager.onCatalogLoad(result)
    }
  }

  private fun storeNewPostsIfThreadIsBeingDownloaded(posts: List<Post>) {
    if (posts.isEmpty() || loadable!!.isCatalogMode
      || loadable!!.loadableDownloadingState == LoadableDownloadingState.AlreadyDownloaded) {
      return
    }
    val pin = watchManager.findPinByLoadableId(loadable!!.id)
    if (pin == null || !PinType.hasDownloadFlag(pin.pinType)) {
      // No pin for this loadable we are probably not downloading this thread
      // or no downloading flag
      return
    }
    val savedThread = watchManager.findSavedThreadByLoadableId(loadable!!.id)
    if (savedThread == null || savedThread.isStopped || savedThread.isFullyDownloaded) {
      // Either the thread is not being downloaded or it is stopped or already fully downloaded
      return
    }
    if (!fileManager.baseDirectoryExists(LocalThreadsBaseDirectory::class.java)) {
      Logger.d(TAG, "storeNewPostsIfThreadIsBeingDownloaded() LocalThreadsBaseDirectory does not exist")
      watchManager.stopSavingAllThreads()
      return
    }
    if (!threadSaveManager.enqueueThreadToSave(loadable, posts)) {
      // Probably base directory was removed by the user, can't do anything other than
      // just stop this download
      watchManager.stopSavingThread(loadable)
    }
  }

  override fun onChanLoaderError(error: ChanLoaderException) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "onChanLoaderError()")
    threadPresenterCallback?.showError(error)
  }

  /*
     * PostAdapter callbacks
     */
  override fun onListScrolledToBottom() {
    if (!isBound) {
      return
    }

    val thread = chanLoader?.thread
      ?: return

    if (
      loadable?.isThreadMode == true &&
      thread.postsCount > 0
    ) {
      val posts = thread.posts
      loadable!!.setLastViewed(posts[posts.size - 1].no)
    }

    val pin = watchManager.findPinByLoadableId(loadable!!.id)
    if (pin != null) {
      watchManager.onBottomPostViewed(pin)
    }

    threadPresenterCallback?.showNewPostsNotification(false, -1)

    // Update the last seen indicator
    showPosts()

    // Update loadable in the database
    databaseManager.runTaskAsync(databaseManager.databaseLoadableManager.updateLoadable(loadable))
  }

  fun onNewPostsViewClicked() {
    if (!isBound) {
      return
    }

    val post = findPostById(loadable!!.lastViewed.toLong(), chanLoader!!.thread)
    var position = -1

    if (post != null) {
      val posts = threadPresenterCallback?.displayingPosts
        ?: return

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

  fun scrollTo(displayPosition: Int, smooth: Boolean) {
    threadPresenterCallback?.scrollTo(displayPosition, smooth)
  }

  fun scrollToImage(postImage: PostImage, smooth: Boolean) {
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

  fun selectPost(post: Int) {
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

  /*
     * PostView callbacks
     */
  override fun onPostClicked(post: Post) {
    if (!isBound || loadable?.isCatalogMode != true) {
      return
    }

    val newLoadable = Loadable.forThread(
      loadable!!.site,
      post.board,
      post.no,
      PostHelper.getTitle(post, loadable)
    )

    highlightPost(post)
    val threadLoadable = databaseManager.databaseLoadableManager.get(newLoadable)

    threadPresenterCallback?.showThread(threadLoadable)
  }

  override fun onPostDoubleClicked(post: Post) {
    if (!isBound || !loadable!!.isThreadMode) {
      return
    }

    if (searchOpen) {
      searchQuery = null

      showPosts()
      threadPresenterCallback?.setSearchStatus(null, setEmptyText = false, hideKeyboard = true)
      threadPresenterCallback?.showSearch(false)

      highlightPost(post)
      scrollToPost(post, false)
    } else {
      threadPresenterCallback?.postClicked(post)
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
          || image.isFromArchive
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
      threadPresenterCallback?.showImages(images, index, loadable!!, thumbnail)
    }
  }

  override fun onPopulatePostOptions(post: Post, menu: MutableList<FloatingListMenuItem>) {
    if (!isBound) {
      return
    }

    if (loadable!!.isCatalogMode) {
      menu.add(createMenuItem(POST_OPTION_PIN, R.string.action_pin))
    } else if (!loadable!!.isLocal) {
      menu.add(createMenuItem(POST_OPTION_QUOTE, R.string.post_quote))
      menu.add(createMenuItem(POST_OPTION_QUOTE_TEXT, R.string.post_quote_text))
    }

    if (loadable!!.getSite().siteFeature(Site.SiteFeature.POST_REPORT) && !loadable!!.isLocal) {
      menu.add(createMenuItem(POST_OPTION_REPORT, R.string.post_report))
    }

    if ((loadable!!.isCatalogMode || loadable!!.isThreadMode && !post.isOP) && !loadable!!.isLocal) {
      if (!post.postFilter.filterStub) {
        menu.add(createMenuItem(POST_OPTION_HIDE, R.string.post_hide))
      }
      menu.add(createMenuItem(POST_OPTION_REMOVE, R.string.post_remove))
    }

    if (loadable!!.isThreadMode) {
      if (!TextUtils.isEmpty(post.posterId)) {
        menu.add(createMenuItem(POST_OPTION_HIGHLIGHT_ID, R.string.post_highlight_id))
      }
      if (!TextUtils.isEmpty(post.tripcode)) {
        menu.add(createMenuItem(POST_OPTION_HIGHLIGHT_TRIPCODE, R.string.post_highlight_tripcode))
        menu.add(createMenuItem(POST_OPTION_FILTER_TRIPCODE, R.string.post_filter_tripcode))
      }
      if (loadable!!.site.siteFeature(Site.SiteFeature.IMAGE_FILE_HASH) && post.postImages.isNotEmpty()) {
        menu.add(createMenuItem(POST_OPTION_FILTER_IMAGE_HASH, R.string.post_filter_image_hash))
      }
    }

    if (loadable!!.site.siteFeature(Site.SiteFeature.POST_DELETE)) {
      val isSaved = databaseManager.databaseSavedReplyManager.isSaved(
        post.board,
        post.no
      )
      if (isSaved && !loadable!!.isLocal) {
        menu.add(createMenuItem(POST_OPTION_DELETE, R.string.post_delete))
      }
    }

    if (post.linkables.size > 0) {
      menu.add(createMenuItem(POST_OPTION_LINKS, R.string.post_show_links))
    }

    menu.add(createMenuItem(POST_OPTION_OPEN_BROWSER, R.string.action_open_browser))
    menu.add(createMenuItem(POST_OPTION_SHARE, R.string.post_share))
    menu.add(createMenuItem(POST_OPTION_COPY_TEXT, R.string.post_copy_text))
    menu.add(createMenuItem(POST_OPTION_INFO, R.string.post_info))

    if (!loadable!!.isLocal) {
      val isSaved = databaseManager.databaseSavedReplyManager.isSaved(
        post.board,
        post.no
      )
      val stringId = if (isSaved) R.string.unmark_as_my_post else R.string.mark_as_my_post
      menu.add(createMenuItem(POST_OPTION_SAVE, stringId))
      if (BuildConfig.DEV_BUILD && loadable!!.no > 0) {
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
      context!!.getString(stringId)
    )
  }

  override fun onPostOptionClicked(post: Post, id: Any, inPopup: Boolean) {
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
      POST_OPTION_SAVE -> {
        val savedReply = SavedReply.fromBoardNoPassword(post.board, post.no, "")
        if (databaseManager.databaseSavedReplyManager.isSaved(post.board, post.no)) {
          databaseManager.runTask(databaseManager.databaseSavedReplyManager.unsaveReply(savedReply))
          val watchedPin = watchManager.getPinByLoadable(loadable)
          if (watchedPin != null) {
            watchedPin.quoteLastCount -= post.repliesFromCount
          }
        } else {
          databaseManager.runTask(databaseManager.databaseSavedReplyManager.saveReply(savedReply))
          val watchedPin = watchManager.getPinByLoadable(loadable)
          if (watchedPin != null) {
            watchedPin.quoteLastCount += post.repliesFromCount
          }
        }
        //force reload for reply highlighting
        requestData()
      }
      POST_OPTION_PIN -> {
        val title = PostHelper.getTitle(post, loadable)
        val loadable = Loadable.forThread(loadable!!.site, post.board, post.no, title)
        val pinLoadable = databaseManager.databaseLoadableManager.get(loadable)
        watchManager.createPin(pinLoadable, post, PinType.WATCH_NEW_POSTS)
      }
      POST_OPTION_OPEN_BROWSER -> if (isBound) {
        AndroidUtils.openLink(loadable!!.site.resolvable().desktopUrl(loadable!!, post.no))
      }
      POST_OPTION_SHARE -> if (isBound) {
        AndroidUtils.shareLink(loadable!!.site.resolvable().desktopUrl(loadable!!, post.no))
      }
      POST_OPTION_REMOVE, POST_OPTION_HIDE -> {
        if (chanLoader == null || chanLoader!!.thread == null) {
          return
        }

        val hide = id == POST_OPTION_HIDE
        if (chanLoader!!.thread!!.loadable.mode == Loadable.Mode.CATALOG) {
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
      POST_OPTION_MOCK_REPLY -> if (isBound && loadable!!.isThreadMode) {
        mockReplyManager.addMockReply(
          post.board.site.name(),
          loadable!!.boardCode,
          loadable!!.no.toLong(),
          post.no
        )
        AndroidUtils.showToast(context, "Refresh to add mock replies")
      }
    }
  }

  override fun onPostLinkableClicked(post: Post, linkable: PostLinkable) {
    val thread = chanLoader?.thread
      ?: return

    if (linkable.type == PostLinkable.Type.QUOTE && isBound) {
      val postId = linkable.linkableValue.extractLongOrNull()
      if (postId == null) {
        Logger.e(TAG, "Bad quote linkable: linkableValue = ${linkable.linkableValue}")
        return
      }

      val linked = findPostById(postId, thread)
      if (linked != null) {
        threadPresenterCallback?.showPostsPopup(post, listOf(linked))
      }

      return
    }

    if (linkable.type == PostLinkable.Type.LINK) {
      val link = (linkable.linkableValue as? PostLinkable.Value.StringValue)?.value
      if (link == null) {
        Logger.e(TAG, "Bad link linkable: linkableValue = ${linkable.linkableValue}")
        return
      }

      threadPresenterCallback?.openLink(link.toString())
      return
    }

    if (linkable.type == PostLinkable.Type.THREAD && isBound) {
      val threadLink = linkable.linkableValue as? PostLinkable.Value.ThreadLink
      if (threadLink == null) {
        Logger.e(TAG, "Bad thread linkable: linkableValue = ${linkable.linkableValue}")
        return
      }

      val board = loadable?.site?.board(threadLink.board)
      if (board != null) {
        val loadable = Loadable.forThread(
          board.site,
          board,
          threadLink.threadId.toLong(),
          ""
        )

        val threadLoadable = databaseManager.databaseLoadableManager.get(loadable)
        threadLoadable.markedNo = threadLink.postId

        threadPresenterCallback?.showThread(threadLoadable)
      }

      return
    }

    if (linkable.type == PostLinkable.Type.BOARD && isBound) {
      val link = (linkable.linkableValue as? PostLinkable.Value.StringValue)?.value
      if (link == null) {
        Logger.e(TAG, "Bad board linkable: linkableValue = ${linkable.linkableValue}")
        return
      }

      val board = databaseManager.runTask(
        databaseManager.databaseBoardManager.getBoard(loadable!!.site, link.toString())
      )

      if (board == null) {
        AndroidUtils.showToast(context, R.string.site_uses_dynamic_boards)
        return
      }

      val catalog = databaseManager.databaseLoadableManager.get(Loadable.forCatalog(board))
      threadPresenterCallback?.showBoard(catalog)

      return
    }

    if (linkable.type == PostLinkable.Type.SEARCH && isBound) {
      val searchLink = linkable.linkableValue as? PostLinkable.Value.SearchLink
      if (searchLink == null) {
        Logger.e(TAG, "Bad search linkable: linkableValue = ${linkable.linkableValue}")
        return
      }

      val board = databaseManager.runTask(
        databaseManager.databaseBoardManager.getBoard(loadable!!.site, searchLink.board)
      )

      if (board == null) {
        AndroidUtils.showToast(context, R.string.site_uses_dynamic_boards)
        return
      }

      val catalog = databaseManager.databaseLoadableManager.get(Loadable.forCatalog(board))
      threadPresenterCallback?.showBoardAndSearch(catalog, searchLink.search)
    }
  }

  override fun onPostNoClicked(post: Post) {
    threadPresenterCallback?.quote(post, false)
  }

  override fun onPostSelectionQuoted(post: Post, quoted: CharSequence) {
    threadPresenterCallback?.quote(post, quoted)
  }

  override fun presentController(floatingListMenuController: FloatingListMenuController, animate: Boolean) {
    threadPresenterCallback?.presentController(floatingListMenuController, animate)
  }

  override fun hasAlreadySeenPost(post: Post): Boolean {
    if (loadable == null) {
      // Invalid loadable, hide the label
      return true
    }

    return if (loadable?.mode != Loadable.Mode.THREAD) {
      // Not in a thread, hide the label
      true
    } else {
      seenPostsManager.hasAlreadySeenPost(loadable!!, post)
    }
  }

  override fun onShowPostReplies(post: Post) {
    if (!isBound) return
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
    return (ChanSettings.autoRefreshThread.get()
      && BackgroundUtils.isInForeground()
      && isBound
      && loadable!!.isThreadMode
      && chanLoader!!.thread != null && !chanLoader!!.thread!!.isClosed
      && !chanLoader!!.thread!!.isArchived)
  }

  override fun getChanThread(): ChanThread? {
    return if (isBound) chanLoader!!.thread else null
  }

  override fun getPage(op: Post): BoardPage? {
    return pageRequestManager.getPage(op)
  }

  override fun onListStatusClicked() {
    if (!isBound) return
    if (!chanLoader!!.thread!!.isArchived) {
      chanLoader!!.requestMoreDataAndResetTimer()
    }
  }

  override fun showThread(loadable: Loadable) {
    threadPresenterCallback?.showThread(loadable)
  }

  override fun requestNewPostLoad() {
    if (isBound && loadable!!.isThreadMode) {
      chanLoader!!.requestMoreDataAndResetTimer()
      //put in a "request" for a page update whenever the next set of data comes in
      forcePageUpdate = true
    }
  }

  override fun onUnhidePostClick(post: Post) {
    threadPresenterCallback?.unhideOrUnremovePost(post)
  }

  private fun requestDeletePost(post: Post) {
    val reply = databaseManager.databaseSavedReplyManager.getSavedReply(post.board, post.no)
    if (reply != null) {
      threadPresenterCallback?.confirmPostDelete(post)
    }
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  fun deletePostConfirmed(post: Post, onlyImageDelete: Boolean) {
    launch {
      threadPresenterCallback?.showDeleting()

      val reply = databaseManager.databaseSavedReplyManager.getSavedReply(post.board, post.no)
      if (reply != null) {
        val deleteRequest = DeleteRequest(post, reply, onlyImageDelete)
        val siteProtocol = siteRepository.bySiteDescriptor(post.board.boardDescriptor().siteDescriptor)
        if (siteProtocol == null) {
          return@launch
        }

        val deleteResult = siteProtocol.actions().delete(deleteRequest)

        when (deleteResult) {
          is SiteActions.DeleteResult.DeleteComplete -> {
            val deleteResponse = deleteResult.deleteResponse

            val message = when {
              deleteResponse.deleted -> AndroidUtils.getString(R.string.delete_success)
              !TextUtils.isEmpty(deleteResponse.errorMessage) -> deleteResponse.errorMessage
              else -> AndroidUtils.getString(R.string.delete_error)
            }

            threadPresenterCallback?.hideDeleting(message)
          }
          is SiteActions.DeleteResult.DeleteError -> {
            threadPresenterCallback?.hideDeleting(AndroidUtils.getString(R.string.delete_error))
          }
        }
      }
    }
  }

  private fun showPostInfo(post: Post) {
    val text = StringBuilder()
    for (image in post.postImages) {
      text.append("Filename: ").append(image.filename).append(".").append(image.extension)
      if (image.isInlined) {
        text.append("\nLinked file")
      } else {
        text.append(" \nDimensions: ")
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
    text.append("Posted: ").append(PostHelper.getLocalDate(post))
    if (!TextUtils.isEmpty(post.posterId) && isBound && chanLoader!!.thread != null) {
      text.append("\nId: ").append(post.posterId)
      var count = 0
      try {
        for (p in chanLoader!!.thread!!.posts) {
          if (p.posterId == post.posterId) count++
        }
      } catch (ignored: Exception) {
      }
      text.append("\nCount: ").append(count)
    }
    if (!TextUtils.isEmpty(post.tripcode)) {
      text.append("\nTripcode: ").append(post.tripcode)
    }
    if (post.httpIcons != null && !post.httpIcons.isEmpty()) {
      for (icon in post.httpIcons) {
        if (icon.url.toString().contains("troll")) {
          text.append("\nTroll Country: ").append(icon.name)
        } else if (icon.url.toString().contains("country")) {
          text.append("\nCountry: ").append(icon.name)
        } else if (icon.url.toString().contains("minileaf")) {
          text.append("\n4chan Pass Year: ").append(icon.name)
        }
      }
    }
    if (!TextUtils.isEmpty(post.capcode)) {
      text.append("\nCapcode: ").append(post.capcode)
    }
    threadPresenterCallback?.showPostInfo(text.toString())
  }

  private fun showPosts(refreshAfterHideOrRemovePosts: Boolean = false) {
    if (chanLoader != null && chanLoader!!.thread != null) {
      threadPresenterCallback?.showPosts(
        chanLoader!!.thread,
        PostsFilter(order, searchQuery),
        refreshAfterHideOrRemovePosts
      )
    }
  }

  private fun addHistory() {
    if (!isBound || chanLoader!!.thread == null) {
      return
    }
    if (!historyAdded && addToLocalBackHistory
      && ChanSettings.historyEnabled.get()
      && loadable!!.isThreadMode
      && !loadable!!.isLocal
    ) {
      historyAdded = true
      val history = History()
      history.loadable = loadable
      val image = chanLoader!!.thread!!.op.firstImage()
      history.thumbnailUrl = image?.getThumbnailUrl()?.toString() ?: ""
      databaseManager.runTaskAsync(databaseManager.databaseHistoryManager.addHistory(history))
    }
  }

  fun showImageReencodingWindow(supportsReencode: Boolean) {
    threadPresenterCallback?.showImageReencodingWindow(loadable!!, supportsReencode)
  }

  fun hideOrRemovePosts(hide: Boolean, wholeChain: Boolean, post: Post, threadNo: Long) {
    val posts: MutableSet<Post> = HashSet()
    if (isBound) {
      if (wholeChain) {
        val thread = chanLoader!!.thread
        if (thread != null) {
          posts.addAll(findPostWithReplies(post.no, thread.posts))
        }
      } else {
        val post = findPostById(post.no, chanLoader!!.thread)
        if (post != null) {
          posts.add(post)
        }
      }
    }

    threadPresenterCallback?.hideOrRemovePosts(hide, wholeChain, posts, threadNo)
  }

  fun showRemovedPostsDialog() {
    if (!isBound || chanLoader!!.thread == null || loadable!!.isCatalogMode) {
      return
    }
    threadPresenterCallback?.viewRemovedPostsForTheThread(chanLoader!!.thread!!.posts, loadable!!.no.toLong())
  }

  fun onRestoreRemovedPostsClicked(selectedPosts: List<Long>) {
    if (!isBound) {
      return
    }

    threadPresenterCallback?.onRestoreRemovedPostsClicked(loadable!!, selectedPosts)
  }

  fun setContext(context: Context?) {
    this.context = context
  }

  fun updateLoadable(loadableDownloadingState: LoadableDownloadingState?) {
    if (isBound) {
      loadable!!.setLoadableState(loadableDownloadingState)
    }
  }

  fun markAllPostsAsSeen() {
    if (!isBound) return
    val pin = watchManager.findPinByLoadableId(loadable!!.id)
    if (pin != null) {
      var savedThread: SavedThread? = null
      if (PinType.hasDownloadFlag(pin.pinType)) {
        savedThread = watchManager.findSavedThreadByLoadableId(loadable!!.id)
      }
      if (savedThread == null) {
        watchManager.onBottomPostViewed(pin)
      }
    }
  }

  interface ThreadPresenterCallback {
    val displayingPosts: List<Post>
    val currentPosition: IntArray

    fun showPosts(thread: ChanThread?, filter: PostsFilter, refreshAfterHideOrRemovePosts: Boolean)
    fun postClicked(post: Post)
    fun showError(error: ChanLoaderException)
    fun showLoading()
    fun showEmpty()
    fun showPostInfo(info: String)
    fun showPostLinkables(post: Post)
    fun clipboardPost(post: Post)
    fun showThread(threadLoadable: Loadable)
    fun showBoard(catalogLoadable: Loadable)
    fun showBoardAndSearch(catalogLoadable: Loadable, searchQuery: String?)
    fun openLink(link: String)
    fun openReportView(post: Post)
    fun showPostsPopup(forPost: Post, posts: List<Post>)
    fun hidePostsPopup()
    fun showImages(images: List<PostImage>, index: Int, loadable: Loadable, thumbnail: ThumbnailView)
    fun showAlbum(images: List<PostImage>, index: Int)
    fun scrollTo(displayPosition: Int, smooth: Boolean)
    fun smoothScrollNewPosts(displayPosition: Int)
    fun highlightPost(post: Post)
    fun highlightPostId(id: String)
    fun highlightPostTripcode(tripcode: CharSequence?)
    fun filterPostTripcode(tripcode: CharSequence?)
    fun filterPostImageHash(post: Post)
    fun selectPost(post: Int)
    fun showSearch(show: Boolean)
    fun setSearchStatus(query: String?, setEmptyText: Boolean, hideKeyboard: Boolean)
    fun quote(post: Post, withText: Boolean)
    fun quote(post: Post, text: CharSequence)
    fun confirmPostDelete(post: Post)
    fun showDeleting()
    fun hideDeleting(message: String)
    fun hideThread(post: Post, threadNo: Long, hide: Boolean)
    fun showNewPostsNotification(show: Boolean, more: Int)
    fun showImageReencodingWindow(loadable: Loadable, supportsReencode: Boolean)
    fun showHideOrRemoveWholeChainDialog(hide: Boolean, post: Post, threadNo: Long)
    fun hideOrRemovePosts(hide: Boolean, wholeChain: Boolean, posts: Set<Post>, threadNo: Long)
    fun unhideOrUnremovePost(post: Post)
    fun viewRemovedPostsForTheThread(threadPosts: List<Post>, threadNo: Long)
    fun onRestoreRemovedPostsClicked(threadLoadable: Loadable, selectedPosts: List<Long>)
    fun onPostUpdated(post: Post)
    fun presentController(floatingListMenuController: FloatingListMenuController, animate: Boolean)
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
    private const val POST_OPTION_PIN = 9
    private const val POST_OPTION_SHARE = 10
    private const val POST_OPTION_HIGHLIGHT_TRIPCODE = 11
    private const val POST_OPTION_HIDE = 12
    private const val POST_OPTION_OPEN_BROWSER = 13
    private const val POST_OPTION_FILTER_TRIPCODE = 14
    private const val POST_OPTION_FILTER_IMAGE_HASH = 15
    private const val POST_OPTION_REMOVE = 16
    private const val POST_OPTION_MOCK_REPLY = 17
  }

}