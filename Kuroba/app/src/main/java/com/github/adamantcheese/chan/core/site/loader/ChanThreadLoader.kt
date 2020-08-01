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
package com.github.adamantcheese.chan.core.site.loader

import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.di.NetModule.ProxiedOkHttpClient
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.core.manager.FilterEngine
import com.github.adamantcheese.chan.core.manager.PostFilterManager
import com.github.adamantcheese.chan.core.model.ChanThread
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.repository.BoardRepository
import com.github.adamantcheese.chan.core.repository.SiteRepository
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoaderCoordinator.Companion.getChanUrl
import com.github.adamantcheese.chan.ui.helper.PostHelper
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.BackgroundUtils.runOnMainThread
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.StringUtils
import com.github.adamantcheese.chan.utils.plusAssign
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.error
import com.github.adamantcheese.common.ModularResult.Companion.value
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.repository.ChanPostRepository
import com.github.adamantcheese.model.repository.ThirdPartyArchiveInfoRepository
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
  lateinit var okHttpClient: ProxiedOkHttpClient
  @Inject
  lateinit var databaseManager: DatabaseManager
  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var filterEngine: FilterEngine
  @Inject
  lateinit var chanPostRepository: ChanPostRepository
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var thirdPartyArchiveInfoRepository: ThirdPartyArchiveInfoRepository
  @Inject
  lateinit var themeHelper: ThemeHelper
  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var siteRepository: SiteRepository
  @Inject
  lateinit var boardRepository: BoardRepository

  @Volatile
  var thread: ChanThread? = null
    private set

  private val job = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main + job + CoroutineName("ChanThreadLoader")

  private val chanThreadLoaderCoordinator by lazy {
    return@lazy ChanThreadLoaderCoordinator(
      gson,
      okHttpClient,
      databaseManager.databaseSavedReplyManager,
      filterEngine,
      chanPostRepository,
      appConstants,
      archivesManager,
      thirdPartyArchiveInfoRepository,
      postFilterManager,
      ChanSettings.verboseLogs.get(),
      themeHelper,
      boardRepository,
      siteRepository
    )
  }

  private val compositeDisposable = CompositeDisposable()

  @Volatile
  private var requestJob: Job? = null
  private var pendingFuture: ScheduledFuture<*>? = null

  private val listeners: MutableList<ChanLoaderCallback> = CopyOnWriteArrayList()
  private var currentTimeout = 0
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
        val waitTime = WATCH_TIMEOUTS.getOrElse(currentTimeout) { WATCH_TIMEOUTS.first() }
        lastLoadTime + (waitTime * 1000L) - System.currentTimeMillis()
      }
    }

  /**
   * **Do not call this constructor yourself, obtain ChanLoaders through [ChanLoaderManager]**
   * Also, do not use feather().instance(WatchManager.class) here because it will create a cyclic
   * dependency instantiation
   */
  init {
    inject(this)
  }

  /**
   * Add a LoaderListener
   *
   * @param listener the listener to add
   */
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

  fun requestDataWithDeletedPosts() {
    requestData(true)
  }

  /**
   * Request data for the first time.
   */
  @JvmOverloads
  fun requestData(retrieveDeletedPostsFromArchives: Boolean = false) {
    BackgroundUtils.ensureMainThread()
    clearTimer()

    requestJob?.cancel()
    requestJob = null

    currentTimeout = -1

    synchronized(this) { thread = null }
    compositeDisposable += requestMoreDataInternal(retrieveDeletedPostsFromArchives)
  }

  /**
   * Request more data. This only works for thread loaders.<br></br>
   * This clears any pending pending timers, created with [.setTimer].
   *
   * @return `true` if a new request was started, `false` otherwise.
   */
  private fun requestMoreData(retrieveDeletedPostsFromArchives: Boolean): Boolean {
    BackgroundUtils.ensureMainThread()
    clearPendingRunnable()

    return if (chanDescriptor.isThreadDescriptor() && requestJob == null) {
      compositeDisposable += requestMoreDataInternal(retrieveDeletedPostsFromArchives)
      true
    } else {
      false
    }
  }

  private fun requestMoreDataInternal(retrieveDeletedPostsFromArchives: Boolean): Disposable {
    val getDataResult = Single.fromCallable {
      val requestJob = getData(retrieveDeletedPostsFromArchives)
        ?: return@fromCallable error<Job>(ThreadAlreadyArchivedException())

      return@fromCallable value(requestJob)
    }

    return getDataResult
      .subscribeOn(backgroundScheduler)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe({ result ->
        when (result) {
          is ModularResult.Value -> requestJob = result.value
          is ModularResult.Error -> handleErrorResult(result.error)
        }
      }, { error ->
        notifyAboutError(ChanLoaderException(error))
      })
  }

  private fun handleErrorResult(error: Throwable) {
    if (error is ThreadAlreadyArchivedException) {
      return
    }

    notifyAboutError(ChanLoaderException(error))
  }

  /**
   * Request more data if [getTimeUntilLoadMore] is negative.
   */
  fun loadMoreIfTime(): Boolean {
    BackgroundUtils.ensureMainThread()
    return timeUntilLoadMore < 0L && requestMoreData(false)
  }

  fun quickLoad() {
    BackgroundUtils.ensureMainThread()

    val localThread = synchronized(this) {
      checkNotNull(thread) { "Cannot quick load without already loaded thread" }
    }

    launch {
      BackgroundUtils.ensureMainThread()

      listeners.forEach { listener -> listener.onChanLoaderData(localThread) }
      requestMoreData(false)
    }
  }

  /**
   * Request more data and reset the watch timer.
   */
  fun requestMoreDataAndResetTimer() {
    BackgroundUtils.ensureMainThread()

    if (requestJob == null) {
      clearTimer()
      requestMoreData(true)
    }
  }

  fun setTimer() {
    BackgroundUtils.ensureMainThread()

    clearPendingRunnable()
    val watchTimeout = WATCH_TIMEOUTS.getOrElse(currentTimeout) { WATCH_TIMEOUTS.first() }

    Logger.d(TAG, "Scheduled reload in " + watchTimeout + "s")

    pendingFuture = executor.schedule({
      runOnMainThread {
        pendingFuture = null
        requestMoreData(false)
      }
    }, watchTimeout.toLong(), TimeUnit.SECONDS)
  }

  fun clearTimer() {
    BackgroundUtils.ensureMainThread()
    currentTimeout = -1
    clearPendingRunnable()
  }

  private fun getData(retrieveDeletedPostsFromArchives: Boolean): Job? {
    BackgroundUtils.ensureBackgroundThread()

    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        Logger.d(TAG, "Requested thread /" + chanDescriptor.boardCode() + "/, " + StringUtils.maskPostNo(chanDescriptor.threadNo))
      }
      is ChanDescriptor.CatalogDescriptor -> {
        Logger.d(TAG, "Requested catalog /" + chanDescriptor.boardCode() + "/")
      }
    }

    val site = siteRepository.bySiteDescriptor(chanDescriptor.siteDescriptor())
    requireNotNull(site) { "site == null, siteDescriptor = ${chanDescriptor.siteDescriptor()}" }
    val chanReader = site.chanReader()

    val requestParams = ChanLoaderRequestParams(
      chanDescriptor,
      chanReader,
      synchronized(this) { thread?.posts ?: ArrayList() },
      retrieveDeletedPostsFromArchives
    )

    val url = getChanUrl(site, chanDescriptor).toString()

    // Notify the listeners that loader is starting fetching data from the server
    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      bookmarksManager.onThreadIsFetchingData(chanDescriptor)
    }

    return chanThreadLoaderCoordinator.loadThread(url, requestParams) { chanLoaderResponseResult ->
      when (chanLoaderResponseResult) {
        is ModularResult.Value -> {
          val chanLoaderResponse = when (val threadLoadResult = chanLoaderResponseResult.value) {
            is ThreadLoadResult.LoadedNormally -> {
              threadLoadResult.chanLoaderResponse
            }
            is ThreadLoadResult.LoadedFromDatabaseCopy -> threadLoadResult.chanLoaderResponse
            is ThreadLoadResult.LoadedFromArchive -> {
              threadLoadResult.chanLoaderResponse.op.archived = true
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
    requestJob = null

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

  @Synchronized
  private suspend fun onResponseInternalNext(fakeOp: Post.Builder) {
    BackgroundUtils.ensureBackgroundThread()

    val localThread = synchronized(this) { checkNotNull(thread) { "thread is null" } }
    processResponse(fakeOp)

    val title = PostHelper.getTitle(localThread.op, chanDescriptor)

    for (post in localThread.posts) {
      post.title = title
    }

    lastLoadTime = System.currentTimeMillis()
    val postCount = localThread.postsCount

    if (postCount > lastPostCount) {
      lastPostCount = postCount
      currentTimeout = 0
    } else {
      currentTimeout = min(currentTimeout + 1, WATCH_TIMEOUTS.size - 1)
    }

    withContext(Dispatchers.Main) {
      listeners.forEach { listener -> listener.onChanLoaderData(localThread) }
    }
  }

  /**
   * Final processing of a response that needs to happen on the main thread.
   */
  @Synchronized
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
      realOp.totalRepliesCount = fakeOp.totalRepliesCount
      realOp.threadImagesCount = fakeOp.threadImagesCount
      realOp.uniqueIps = fakeOp.uniqueIps
      realOp.lastModified = fakeOp.lastModified
      localThread.isClosed = realOp.isClosed
      localThread.isArchived = realOp.isArchived
    }
  }

  private fun onErrorResponse(error: ChanLoaderException) {
    requestJob = null

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

  private class ThreadAlreadyArchivedException : Exception("Thread already archived")

  companion object {
    private const val TAG = "ChanThreadLoader"
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val WATCH_TIMEOUTS = intArrayOf(20, 30, 60, 90, 120, 180, 240, 300, 600, 1800, 3600)
    private val backgroundScheduler = Schedulers.from(executor)
  }

}