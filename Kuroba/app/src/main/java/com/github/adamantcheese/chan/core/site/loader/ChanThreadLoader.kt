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

import android.text.TextUtils
import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.di.NetModule.ProxiedOkHttpClient
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.manager.FilterEngine
import com.github.adamantcheese.chan.core.manager.SavedThreadLoaderManager
import com.github.adamantcheese.chan.core.manager.WatchManager
import com.github.adamantcheese.chan.core.model.ChanThread
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.model.orm.Loadable.LoadableDownloadingState
import com.github.adamantcheese.chan.core.model.orm.PinType
import com.github.adamantcheese.chan.core.model.orm.SavedThread
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader.ChanLoaderCallback
import com.github.adamantcheese.chan.core.site.parser.ChanReaderRequestExecutor
import com.github.adamantcheese.chan.core.site.parser.ChanReaderRequestExecutor.Companion.getChanUrl
import com.github.adamantcheese.chan.ui.helper.PostHelper
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.StringUtils
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.error
import com.github.adamantcheese.common.ModularResult.Companion.value
import com.github.adamantcheese.model.repository.ChanPostRepository
import com.github.adamantcheese.model.repository.ThirdPartyArchiveInfoRepository
import com.google.gson.Gson
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Job
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * A ChanThreadLoader is the loader for Loadables.
 *
 * Obtain ChanLoaders with [ChanLoaderManager].
 *
 * ChanLoaders can load boards and threads, and return [ChanThread] objects on success, through
 * [ChanLoaderCallback].
 *
 * For threads timers can be started with [setTimer] to do a request later.
 */
class ChanThreadLoader(
        val loadable: Loadable,
        private val watchManager: WatchManager
) {
    @Inject
    lateinit var gson: Gson
    @Inject
    lateinit var okHttpClient: ProxiedOkHttpClient
    @Inject
    lateinit var databaseManager: DatabaseManager
    @Inject
    lateinit var savedThreadLoaderManager: SavedThreadLoaderManager
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

    @Volatile
    var thread: ChanThread? = null
        private set

    private val compositeDisposable = CompositeDisposable()
    private var requestJob: Job? = null
    private var pendingFuture: ScheduledFuture<*>? = null

    private val listeners: MutableList<ChanLoaderCallback> = CopyOnWriteArrayList()
    private var currentTimeout = 0
    private var lastPostCount = 0
    private var lastLoadTime: Long = 0

    /**
     * Indicates that this ChanThreadLoader belongs to a Pin. We use this info for archives posts
     * fetching (we don't load posts from archives for pins)
     */
    private var isPinWatcherLoader = false

    /**
     * Get the time in milliseconds until another loadMore is recommended
     */
    val timeUntilLoadMore: Long
        get() {
            BackgroundUtils.ensureMainThread()
            return if (requestJob != null) {
                0L
            } else {
                val waitTime = WATCH_TIMEOUTS[max(0, currentTimeout)] * 1000L
                lastLoadTime + waitTime - System.currentTimeMillis()
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

    fun setPinWatcherLoader(pinWatcherLoader: Boolean) {
        isPinWatcherLoader = pinWatcherLoader
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

            // Since chan thread loaders are cached in ChanThreadLoaderManager, instead of being
            // destroyed, and thus can be reused, we need to reset them before they are put into
            // cache.
            resetLoader()
            true
        } else {
            false
        }
    }

    private fun resetLoader() {
        isPinWatcherLoader = false
    }

    /**
     * Request data for the first time.
     */
    @JvmOverloads
    fun requestData(forced: Boolean = false) {
        BackgroundUtils.ensureMainThread()
        clearTimer()

        val disposable = Single.fromCallable { loadSavedCopyIfExists() }
                .subscribeOn(backgroundScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { loaded: Boolean -> requestDataInternal(loaded, forced) },
                        { error: Throwable ->
                            Logger.e(TAG, "Error while loading saved thread", error)
                            notifyAboutError(ChanLoaderException(error))
                        }
                )

        compositeDisposable.add(disposable)
    }

    private fun requestDataInternal(loaded: Boolean, forced: Boolean) {
        BackgroundUtils.ensureMainThread()

        if (loaded) {
            return
        }

        requestJob?.cancel()
        requestJob = null

        if (loadable.isCatalogMode) {
            loadable.no = 0
            loadable.listViewIndex = 0
            loadable.listViewTop = 0
        }

        currentTimeout = -1

        synchronized(this) { thread = null }
        requestMoreDataInternal(forced)
    }

    private fun loadSavedCopyIfExists(): Boolean {
        BackgroundUtils.ensureBackgroundThread()

        if (loadable.isLocal) {
            // Do not attempt to load data from the network when viewing a saved thread use local
            // saved thread instead
            val chanThread = loadSavedThreadIfItExists()
            if (chanThread != null && chanThread.postsCount > 0) {
                // HACK: When opening a pin with local thread that is not yet fully downloaded
                // we don't want to set the thread as archived/closed because it will make
                // it permanently archived (fully downloaded)
                if (loadable.loadableDownloadingState == LoadableDownloadingState.DownloadingAndViewable) {
                    chanThread.isArchived = false
                    chanThread.isClosed = false
                }

                thread = chanThread

                onPreparedResponseInternal(
                        chanThread,
                        loadable.loadableDownloadingState,
                        chanThread.isClosed,
                        chanThread.isArchived
                )

                return true
            }
        }

        return false
    }

    /**
     * Request more data. This only works for thread loaders.<br></br>
     * This clears any pending pending timers, created with [.setTimer].
     *
     * @return `true` if a new request was started, `false` otherwise.
     */
    fun requestMoreData(forced: Boolean): Boolean {
        BackgroundUtils.ensureMainThread()
        clearPendingRunnable()

        return if (loadable.isThreadMode && requestJob == null) {
            compositeDisposable.add(requestMoreDataInternal(forced))
            true
        } else {
            false
        }
    }

    private fun requestMoreDataInternal(forced: Boolean): Disposable {
        val getDataResult = Single.fromCallable {
            val requestJob = getData(forced)
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
     * Request more data if [.getTimeUntilLoadMore] is negative.
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

        listeners.forEach { listener -> listener.onChanLoaderData(localThread) }
        requestMoreData(false)
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
        val watchTimeout = WATCH_TIMEOUTS[currentTimeout]

        Logger.d(TAG, "Scheduled reload in " + watchTimeout + "s")

        pendingFuture = executor.schedule({
            BackgroundUtils.runOnMainThread {
                pendingFuture = null
                requestMoreData(false)
            }
        }, watchTimeout.toLong(), TimeUnit.SECONDS)
    }

    fun clearTimer() {
        BackgroundUtils.ensureMainThread()
        currentTimeout = 0
        clearPendingRunnable()
    }

    private fun getData(forced: Boolean): Job? {
        BackgroundUtils.ensureBackgroundThread()

        if (loadable.loadableDownloadingState == LoadableDownloadingState.AlreadyDownloaded
                && loadable.mode == Loadable.Mode.THREAD) {
            // If loadableDownloadingState is AlreadyDownloaded try to load the local thread from
            // the disk. If we couldn't do that then try to send the request to the server
            if (onThreadArchived(closed = true, archived = true)) {
                Logger.d(TAG, "Thread is already fully downloaded for loadable $loadable")
                return null
            }
        }

        Logger.d(TAG, "Requested /" + loadable.boardCode + "/, " + StringUtils.maskPostNo(loadable.no))

        val requestParams = ChanLoaderRequestParams(
                isPinWatcherLoader,
                loadable,
                loadable.getSite().chanReader(),
                synchronized(this) { thread?.posts ?: ArrayList() },
                forced
        )

        val readerRequest = ChanReaderRequestExecutor(
                gson,
                okHttpClient,
                databaseManager.databaseSavedReplyManager,
                filterEngine,
                chanPostRepository,
                appConstants,
                archivesManager,
                thirdPartyArchiveInfoRepository,
                requestParams
        )

        val url = getChanUrl(loadable).toString()

        return readerRequest.execute(url) { chanLoaderResponseResult ->
            when (chanLoaderResponseResult) {
                is ModularResult.Value -> {
                    onResponse(chanLoaderResponseResult.value)
                }
                is ModularResult.Error -> {
                    onErrorResponse(chanLoaderResponseResult.error as ChanLoaderException)
                }
            }
        }
    }

    private fun onResponse(response: ChanLoaderResponse?) {
        requestJob = null

        val disposable = Single.fromCallable { onResponseInternal(response) }
                .subscribeOn(backgroundScheduler)
                .subscribe({
                    // no-op
                }, { error ->
                    Logger.e(TAG, "onResponse error", error)
                    notifyAboutError(ChanLoaderException(error!!))
                })

        compositeDisposable.add(disposable)
    }

    private fun onResponseInternal(response: ChanLoaderResponse?): Boolean {
        BackgroundUtils.ensureBackgroundThread()

        // The server returned us a closed or an archived thread
        if (response?.op != null && (response.op.closed || response.op.archived)) {
            if (onThreadArchived(response.op.closed, response.op.archived)) {
                return true
            }
        }

        // Normal thread, not archived/deleted/closed
        if (response == null || response.posts.isEmpty()) {
            onErrorResponse(ChanLoaderException(IOException("Post size is 0")))
            return false
        }

        synchronized(this) {
            if (thread == null) {
                thread = ChanThread(loadable, ArrayList())
            }

            thread!!.setNewPosts(response.posts)
        }

        onResponseInternalNext(response.op)
        return true
    }

    private fun onThreadArchived(closed: Boolean, archived: Boolean): Boolean {
        BackgroundUtils.ensureBackgroundThread()

        val chanThread = loadSavedThreadIfItExists()
        if (chanThread == null) {
            Logger.d(TAG, "Thread " + StringUtils.maskPostNo(loadable.no) +
                    " is archived but we don't have a local copy of the thread")

            // We don't have this thread locally saved, so return false and DO NOT SET thread to
            // chanThread because this will close this thread (user will see 404 not found error)
            // which we don't want.
            return false
        }

        val threadNo = StringUtils.maskPostNo(chanThread.loadable.no)
        Logger.d(TAG, "Thread $threadNo is archived ($archived) or closed ($closed)")

        synchronized(this) { thread = chanThread }

        // If saved thread was not found or it has no posts (deserialization error) switch to
        // the error route
        if (chanThread.postsCount > 0) {
            // Update SavedThread info in the database and in the watchManager.
            // Set isFullyDownloaded and isStopped to true so we can stop downloading it and stop
            // showing the download thread animated icon.
            BackgroundUtils.runOnMainThread {
                val savedThread = watchManager.findSavedThreadByLoadableId(chanThread.loadableId)
                if (savedThread != null && !savedThread.isFullyDownloaded) {
                    updateThreadAsDownloaded(archived, chanThread, savedThread)
                }
            }

            // Otherwise pass it to the response parse method
            onPreparedResponseInternal(
                    chanThread,
                    LoadableDownloadingState.AlreadyDownloaded,
                    closed,
                    archived
            )
            return true
        }

        Logger.d(TAG, "Thread " + StringUtils.maskPostNo(chanThread.loadable.no) + " has no posts")
        return false
    }

    private fun updateThreadAsDownloaded(
            archived: Boolean,
            chanThread: ChanThread,
            savedThread: SavedThread
    ) {
        BackgroundUtils.ensureMainThread()

        savedThread.isFullyDownloaded = true
        savedThread.isStopped = true

        chanThread.updateLoadableState(LoadableDownloadingState.AlreadyDownloaded)
        watchManager.createOrUpdateSavedThread(savedThread)

        var pin = watchManager.findPinByLoadableId(savedThread.loadableId)
        if (pin == null) {
            pin = databaseManager.runTask(
                    databaseManager.databasePinManager.getPinByLoadableId(savedThread.loadableId)
            )
        }

        if (pin == null) {
            throw RuntimeException("Wtf? We have saved thread but we don't have a pin " +
                    "associated with it?")
        }

        pin.archived = archived
        pin.watching = false

        // Trigger the drawer to be updated so the downloading icon is updated as well
        watchManager.updatePin(pin)

        databaseManager.runTask {
            databaseManager.databaseSavedThreadManager
                    .updateThreadStoppedFlagByLoadableId(savedThread.loadableId, true)
                    .call()
            databaseManager.databaseSavedThreadManager
                    .updateThreadFullyDownloadedByLoadableId(savedThread.loadableId)
                    .call()
        }

        val threadNo = StringUtils.maskPostNo(chanThread.loadable.no)
        Logger.d(TAG, "Successfully updated thread $threadNo as fully downloaded")
    }

    private fun onPreparedResponseInternal(
            chanThread: ChanThread,
            state: LoadableDownloadingState,
            closed: Boolean,
            archived: Boolean
    ) {
        BackgroundUtils.ensureBackgroundThread()

        synchronized(this) {
            val localThread = checkNotNull(thread) { "thread is null" }
            localThread.isClosed = closed
            localThread.isArchived = archived
        }

        val fakeOp = Post.Builder()
        val savedOp = chanThread.op

        fakeOp.closed(closed)
        fakeOp.archived(archived)
        fakeOp.sticky(savedOp.isSticky)
        fakeOp.replies(savedOp.totalRepliesCount)
        fakeOp.threadImagesCount(savedOp.threadImagesCount)
        fakeOp.uniqueIps(savedOp.uniqueIps)
        fakeOp.lastModified(savedOp.lastModified)

        chanThread.updateLoadableState(state)
        onResponseInternalNext(fakeOp)
    }

    @Synchronized
    private fun onResponseInternalNext(fakeOp: Post.Builder) {
        BackgroundUtils.ensureBackgroundThread()

        val localThread: ChanThread = synchronized(this) { checkNotNull(thread) { "thread is null" } }
        processResponse(fakeOp)

        if (TextUtils.isEmpty(loadable.title)) {
            loadable.setTitle(PostHelper.getTitle(localThread.op, loadable))
        }

        for (post in localThread.posts) {
            post.title = loadable.title
        }

        lastLoadTime = System.currentTimeMillis()
        val postCount = localThread.postsCount

        if (postCount > lastPostCount) {
            lastPostCount = postCount
            currentTimeout = 0
        } else {
            currentTimeout = min(currentTimeout + 1, WATCH_TIMEOUTS.size - 1)
        }

        BackgroundUtils.runOnMainThread {
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

        if (loadable.isThreadMode && localThread.postsCount > 0) {
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

        val disposable = tryLoadSavedThread(error)
                .subscribeOn(backgroundScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ loaded ->
                    if (loaded) {
                        return@subscribe
                    }

                    Logger.e(TAG, "Loading error", error)
                    notifyAboutError(error)
                }, { throwable ->
                    Logger.e(TAG, "Loading unhandled error", throwable)
                    notifyAboutError(ChanLoaderException(throwable))
                })

        compositeDisposable.add(disposable)
    }

    private fun tryLoadSavedThread(error: ChanLoaderException): Single<Boolean> {
        return Single.fromCallable {
            BackgroundUtils.ensureBackgroundThread()

            // Thread was deleted (404), try to load a saved copy (if we have it)
            if (error.isNotFound && loadable.mode == Loadable.Mode.THREAD) {
                Logger.d(TAG, "Got 404 status for a thread " + StringUtils.maskPostNo(loadable.no))

                val chanThread = loadSavedThreadIfItExists()
                if (chanThread != null && chanThread.postsCount > 0) {
                    synchronized(this) { thread = chanThread }

                    Logger.d(TAG,
                            "Successfully loaded local thread " + StringUtils.maskPostNo(loadable.no) +
                                    " from disk, isClosed = " + chanThread.isClosed +
                                    ", isArchived = " + chanThread.isArchived
                    )

                    onPreparedResponseInternal(chanThread,
                            LoadableDownloadingState.AlreadyDownloaded,
                            chanThread.isClosed,
                            chanThread.isArchived
                    )

                    // We managed to load local thread, do no need to show the error screen
                    return@fromCallable true
                }

                // Fallthrough
            }

            return@fromCallable false
        }
    }

    private fun notifyAboutError(error: ChanLoaderException) {
        BackgroundUtils.ensureMainThread()
        clearTimer()

        listeners.forEach { listener -> listener.onChanLoaderError(error) }
    }

    /**
     * Loads a saved thread if it exists
     */
    private fun loadSavedThreadIfItExists(): ChanThread? {
        BackgroundUtils.ensureBackgroundThread()
        val loadable = loadable

        // FIXME(synchronization): Not thread safe! findPinByLoadableId is not synchronized.
        val pin = watchManager.findPinByLoadableId(loadable.id)
        if (pin == null) {
            Logger.d(TAG, "Could not find pin for loadable $loadable")
            return null
        }

        if (!PinType.hasDownloadFlag(pin.pinType)) {
            Logger.d(TAG, "Pin has no DownloadPosts flag")
            return null
        }

        val savedThread = getSavedThreadByThreadLoadable(loadable)
        if (savedThread == null) {
            Logger.d(TAG, "Could not find savedThread for loadable $loadable")
            return null
        }

        return savedThreadLoaderManager.loadSavedThread(loadable)
    }

    private fun getSavedThreadByThreadLoadable(loadable: Loadable): SavedThread? {
        BackgroundUtils.ensureBackgroundThread()

        return databaseManager.runTask {
            val pin = databaseManager.databasePinManager.getPinByLoadableId(loadable.id).call()
            if (pin == null) {
                Logger.e(TAG, "Could not find pin by loadableId = " + loadable.id)
                return@runTask null
            }

            return@runTask databaseManager.databaseSavedThreadManager.getSavedThreadByLoadableId(
                    pin.loadable.id
            ).call()
        }
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
        fun onChanLoaderData(result: ChanThread)
        fun onChanLoaderError(error: ChanLoaderException)
    }

    private class ThreadAlreadyArchivedException : Exception("Thread already archived")

    companion object {
        private const val TAG = "ChanThreadLoader"
        private val executor = Executors.newSingleThreadScheduledExecutor()
        private val WATCH_TIMEOUTS = intArrayOf(10, 15, 20, 30, 60, 90, 120, 180, 240, 300, 600, 1800, 3600)
        private val backgroundScheduler = Schedulers.from(executor)
    }

}