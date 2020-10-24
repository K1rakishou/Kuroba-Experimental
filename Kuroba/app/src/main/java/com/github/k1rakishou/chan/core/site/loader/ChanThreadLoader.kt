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
package com.github.k1rakishou.chan.core.site.loader

import com.github.k1rakishou.chan.Chan.Companion.inject
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.*
import com.github.k1rakishou.chan.core.model.ChanThread
import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator.Companion.getChanUrl
import com.github.k1rakishou.chan.ui.helper.PostHelper
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils.runOnMainThread
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.google.gson.Gson
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.min

class ChanThreadLoader(val chanDescriptor: ChanDescriptor) : CoroutineScope {
  @Inject
  lateinit var gson: Gson
  @Inject
  lateinit var proxiedOkHttpClient: ProxiedOkHttpClient
  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var filterEngine: FilterEngine
  @Inject
  lateinit var chanPostRepository: ChanPostRepository
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var savedReplyManager: SavedReplyManager

  var thread: ChanThread? = null
    @Synchronized
    get
    @Synchronized
    private set

  private val job = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main + job + CoroutineName("ChanThreadLoader")

  private val chanThreadLoaderCoordinator by lazy {
    return@lazy ChanThreadLoaderCoordinator(
      gson,
      proxiedOkHttpClient,
      savedReplyManager,
      filterEngine,
      chanPostRepository,
      appConstants,
      postFilterManager,
      ChanSettings.verboseLogs.get(),
      themeEngine,
      boardManager
    )
  }

  private val compositeDisposable = CompositeDisposable()

  @Volatile
  private var requestJob: Job? = null
  private var pendingFuture: ScheduledFuture<*>? = null

  private val listeners: MutableList<ChanLoaderCallback> = CopyOnWriteArrayList()
  private var currentTimeoutIndex = 0
  private var lastPostCount = 0
  private var lastLoadTime: Long = 0

  /**
   * Get the time in milliseconds until another loadMore is recommended
   */
  val timeUntilLoadMore: Long
    get() {
      BackgroundUtils.ensureMainThread()
      return if (requestJob != null) {
        0L
      } else {
        val waitTime = nextWaitTime()
        lastLoadTime + (waitTime * 1000L) - System.currentTimeMillis()
      }
    }

  init {
    inject(this)
  }

  /**
   * Add a LoaderListener
   *
   * @param listener the listener to add
   */
  @Synchronized
  fun addListener(listener: ChanLoaderCallback) {
    BackgroundUtils.ensureMainThread()
    listeners.add(listener)
  }

  /**
   * Remove a LoaderListener
   *
   * @param listener the listener to remove
   * @return true if there are no more listeners, false otherwise
   */
  @Synchronized
  fun removeListener(listener: ChanLoaderCallback?): Boolean {
    BackgroundUtils.ensureMainThread()

    listeners.remove(listener)
    compositeDisposable.clear()

    return if (listeners.isEmpty()) {
      clearTimer()

      requestJob?.cancel()
      requestJob = null

      job.cancelChildren()

      true
    } else {
      false
    }
  }

  /**
   * Request data for the first time.
   */
  fun requestData() {
    BackgroundUtils.ensureMainThread()
    clearTimer()

    requestJob?.cancel()
    requestJob = null

    currentTimeoutIndex = -1

    synchronized(this) { thread = null }
    compositeDisposable += requestMoreDataInternal()
  }

  /**
   * Request more data. This only works for thread loaders.<br></br>
   * This clears any pending pending timers, created with [.setTimer].
   *
   * @return `true` if a new request was started, `false` otherwise.
   */
  private fun requestMoreData(): Boolean {
    BackgroundUtils.ensureMainThread()
    clearPendingRunnable()

    return if (chanDescriptor.isThreadDescriptor() && requestJob == null) {
      compositeDisposable += requestMoreDataInternal()
      true
    } else {
      false
    }
  }

  private fun requestMoreDataInternal(): Disposable {
    return Single.fromCallable {
      return@fromCallable Try { reloadFromNetwork() }
        .peekError { requestJob = null }
    }
      .subscribeOn(backgroundScheduler)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe({ result ->
        when (result) {
          is ModularResult.Value -> {
            // no-op
          }
          is ModularResult.Error -> {
            notifyAboutError(ChanLoaderException(result.error))
          }
        }
      }, { error ->
        notifyAboutError(ChanLoaderException(error))
      })
  }

  fun reloadThreadFromDatabase() {
    BackgroundUtils.ensureMainThread()
    require(chanDescriptor is ChanDescriptor.ThreadDescriptor) { "$chanDescriptor is not a thread descriptor!" }

    launch(Dispatchers.IO) {
      Logger.d(TAG, "reloadThreadFromDatabase() Requested thread /${chanDescriptor}/")
      chanPostRepository.awaitUntilInitialized()

      val chanLoaderResponse = chanThreadLoaderCoordinator.reloadThreadFromDatabase(chanDescriptor)
      if (chanLoaderResponse == null) {
        Logger.e(TAG, "reloadThreadFromDatabase() reloadThreadFromDatabase returned null for ${chanDescriptor}")
        return@launch
      }

      synchronized(this) {
        thread?.clearPosts()
        thread = null
      }

      chanPostRepository.deleteThreadsFromCache(listOf(chanDescriptor))
      onResponse(chanLoaderResponse)
    }
  }

  fun reloadCatalogFromDatabase(threadDescriptors: List<ChanDescriptor.ThreadDescriptor>) {
    BackgroundUtils.ensureMainThread()
    require(chanDescriptor is ChanDescriptor.CatalogDescriptor) { "$chanDescriptor is not a catalog descriptor!" }

    launch(Dispatchers.IO) {
      Logger.d(TAG, "reloadCatalogFromDatabase() Requested catalog /${chanDescriptor}/")
      chanPostRepository.awaitUntilInitialized()

      val chanLoaderResponse = chanThreadLoaderCoordinator.reloadCatalogFromDatabase(threadDescriptors)
      if (chanLoaderResponse == null) {
        Logger.e(TAG, "reloadCatalogFromDatabase() reloadCatalogFromDatabase returned null for ${chanDescriptor}")
        return@launch
      }

      synchronized(this) {
        thread?.clearPosts()
        thread = null
      }

      chanPostRepository.deleteThreadsFromCache(threadDescriptors)
      onResponse(chanLoaderResponse)
    }
  }

  fun quickLoad(requestNewPosts: Boolean = true) {
    BackgroundUtils.ensureMainThread()

    val hasThread = synchronized(this) { thread != null }
    if (!hasThread) {
      return
    }

    launch {
      BackgroundUtils.ensureMainThread()

      val currentThread = synchronized(this) { thread }
        ?: return@launch

      listeners.forEach { listener -> listener.onChanLoaderData(currentThread) }

      if (requestNewPosts) {
        requestMoreData()
      }
    }
  }

  /**
   * Request more data and reset the watch timer.
   */
  fun requestMoreDataAndResetTimer() {
    BackgroundUtils.ensureMainThread()

    if (requestJob == null) {
      clearTimer()
      requestMoreData()
    }
  }

  fun setTimer() {
    BackgroundUtils.ensureMainThread()

    clearPendingRunnable()
    val watchTimeout = nextWaitTime()

    Logger.d(TAG, "Scheduled reload in " + watchTimeout + "s")

    pendingFuture = executor.schedule({
      runOnMainThread {
        pendingFuture = null
        requestMoreData()
      }
    }, watchTimeout.toLong(), TimeUnit.SECONDS)
  }

  @Synchronized
  private fun increaseCurrentTimeoutIndex(): Int {
    val isArchive = archivesManager.isSiteArchive(chanDescriptor.siteDescriptor())
    val maxIndex = if (isArchive) {
      ARCHIVE_WATCH_TIMEOUTS.lastIndex
    } else {
      NORMAL_WATCH_TIMEOUTS.lastIndex
    }

    return min(currentTimeoutIndex + 1, maxIndex)
  }

  @Synchronized
  private fun nextWaitTime(): Int {
    val isArchive = archivesManager.isSiteArchive(chanDescriptor.siteDescriptor())
    if (isArchive) {
      return ARCHIVE_WATCH_TIMEOUTS.getOrElse(currentTimeoutIndex) {
        currentTimeoutIndex = 0
        return@getOrElse ARCHIVE_WATCH_TIMEOUTS[currentTimeoutIndex]
      }
    } else {
      return NORMAL_WATCH_TIMEOUTS.getOrElse(currentTimeoutIndex) {
        currentTimeoutIndex = 0
        return@getOrElse NORMAL_WATCH_TIMEOUTS[currentTimeoutIndex]
      }
    }
  }

  fun clearTimer() {
    BackgroundUtils.ensureMainThread()
    currentTimeoutIndex = -1
    clearPendingRunnable()
  }

  @Synchronized
  private fun reloadFromNetwork() {
    BackgroundUtils.ensureBackgroundThread()

    if (requestJob != null) {
      Logger.d(TAG, "reloadFromNetwork() requestJob is not null!")
      return
    }

    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        Logger.d(TAG, "reloadFromNetwork() Requested thread /${chanDescriptor}/")
      }
      is ChanDescriptor.CatalogDescriptor -> {
        Logger.d(TAG, "reloadFromNetwork() Requested catalog /${chanDescriptor}/")
      }
    }

    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (site == null) {
      val error = CommonClientException("Couldn't find site ${chanDescriptor.siteDescriptor()}")
      onErrorResponse(ChanLoaderException(error))

      requestJob = null
      return
    }

    val chanReader = site.chanReader()

    val requestParams = ChanLoaderRequestParams(
      chanDescriptor,
      chanReader,
      thread?.getPosts() ?: ArrayList()
    )

    val url = getChanUrl(site, chanDescriptor).toString()

    // Notify the listeners that loader is starting fetching data from the server
    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      bookmarksManager.onThreadIsFetchingData(chanDescriptor)
    }

    requestJob = chanThreadLoaderCoordinator.loadThread(url, requestParams) { chanLoaderResponseResult ->
      requestJob = null

      when (chanLoaderResponseResult) {
        is ModularResult.Value -> {
          val chanLoaderResponse = when (val threadLoadResult = chanLoaderResponseResult.value) {
            is ThreadLoadResult.LoadedNormally -> {
              threadLoadResult.chanLoaderResponse
            }
            is ThreadLoadResult.LoadedFromDatabaseCopy -> {
              if (threadLoadResult.deleted) {
                threadLoadResult.chanLoaderResponse.op.deleted(true)
              }

              threadLoadResult.chanLoaderResponse
            }
          }

          onResponse(chanLoaderResponse)
        }
        is ModularResult.Error -> {
          onErrorResponse(chanLoaderResponseResult.error as ChanLoaderException)
        }
      }
    }
  }

  private suspend fun onResponse(response: ChanLoaderResponse) {
    try {
      onResponseInternal(response)
    } catch (error: Throwable) {
      runOnMainThread { notifyAboutError(ChanLoaderException(error)) }
    }
  }

  private suspend fun onResponseInternal(response: ChanLoaderResponse): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    // Normal thread, not archived/deleted/closed
    if (response.posts.isEmpty()) {
      onErrorResponse(ChanLoaderException(IOException("Post size is 0")))
      return false
    }

    synchronized(this) {
      if (thread == null) {
        thread = ChanThread(chanDescriptor, ArrayList())
      }

      thread!!.setNewPosts(response.posts)
      thread!!.postPreloadedInfoHolder = response.postPreloadedInfoHolder
    }

    onResponseInternalNext(response.op)
    return true
  }

  private suspend fun onResponseInternalNext(fakeOp: Post.Builder) {
    BackgroundUtils.ensureBackgroundThread()

    val localThread = synchronized(this) {
      val localThread = checkNotNull(thread) { "thread is null" }
      processResponse(fakeOp)

      val title = PostHelper.getTitle(localThread.op, chanDescriptor)

      for (post in localThread.getPosts()) {
        post.title = title
      }

      lastLoadTime = System.currentTimeMillis()
      val postCount = localThread.postsCount

      if (postCount > lastPostCount) {
        lastPostCount = postCount
        currentTimeoutIndex = 0
      } else {
        currentTimeoutIndex = increaseCurrentTimeoutIndex()
      }

      return@synchronized localThread
    }

    withContext(Dispatchers.Main) {
      listeners.forEach { listener -> listener.onChanLoaderData(localThread) }
    }
  }

  /**
   * Final processing of a response that needs to happen on the main thread.
   */
  private fun processResponse(fakeOp: Post.Builder?) {
    BackgroundUtils.ensureBackgroundThread()

    val localThread = synchronized(this) {
      checkNotNull(thread) { "thread is null during processResponse" }
    }

    if (chanDescriptor.isThreadDescriptor() && localThread.postsCount > 0) {
      // Replace some op parameters to the real op (index 0).
      // This is done on the main thread to avoid race conditions.
      val realOp = localThread.op

      if (fakeOp == null) {
        Logger.e(TAG, "Thread has no op!")
        return
      }

      realOp.isClosed = fakeOp.closed
      realOp.isArchived = fakeOp.archived
      realOp.isSticky = fakeOp.sticky
      realOp.deleted.set(fakeOp.deleted)
      realOp.totalRepliesCount = fakeOp.totalRepliesCount
      realOp.threadImagesCount = fakeOp.threadImagesCount
      realOp.uniqueIps = fakeOp.uniqueIps
      realOp.lastModified = fakeOp.lastModified
      localThread.isClosed = realOp.isClosed
      localThread.isArchived = realOp.isArchived
      localThread.isDeleted = realOp.deleted.get()
    }
  }

  private fun onErrorResponse(error: ChanLoaderException) {
    if (error.isCoroutineCancellationError()) {
      Logger.d(TAG, "Request canceled")
      return
    }

    runOnMainThread { notifyAboutError(error) }
  }

  private fun notifyAboutError(error: ChanLoaderException) {
    BackgroundUtils.ensureMainThread()
    clearTimer()

    if (error.isCoroutineCancellationError()) {
      Logger.d(TAG, "Request canceled")
      return
    }

    Logger.e(TAG, "onChanLoaderError()", error)
    listeners.forEach { listener -> listener.onChanLoaderError(error) }
  }

  private fun clearPendingRunnable() {
    BackgroundUtils.ensureMainThread()

    if (pendingFuture != null) {
      Logger.d(TAG, "Cleared timer")
      pendingFuture!!.cancel(false)
      pendingFuture = null
    }
  }

  interface ChanLoaderCallback {
    suspend fun onChanLoaderData(result: ChanThread)
    fun onChanLoaderError(error: ChanLoaderException)
  }

  companion object {
    private const val TAG = "ChanThreadLoader"
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val NORMAL_WATCH_TIMEOUTS = intArrayOf(15, 20, 30, 45, 60, 90, 120, 180, 240, 300, 450, 600, 750, 1000)
    private val ARCHIVE_WATCH_TIMEOUTS = intArrayOf(300, 600, 1200, 1800, 2400, 3600)
    private val backgroundScheduler = Schedulers.from(executor)
  }

}