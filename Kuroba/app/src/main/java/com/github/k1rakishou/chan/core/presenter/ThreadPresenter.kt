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
import android.widget.Toast
import androidx.annotation.StringRes
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.base.ThrottlingCoroutineExecutorWithAccumulator
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressEvent
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.helper.ChanThreadTicker
import com.github.k1rakishou.chan.core.helper.LastViewedPostNoInfoHolder
import com.github.k1rakishou.chan.core.helper.PostHideHelper
import com.github.k1rakishou.chan.core.helper.ThumbnailLongtapOptionsHelper
import com.github.k1rakishou.chan.core.loader.LoaderBatchResult
import com.github.k1rakishou.chan.core.loader.LoaderResult.Succeeded
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.OnDemandContentLoaderManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.PostHighlightManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.manager.SeenPostsManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.report.PostReportData
import com.github.k1rakishou.chan.core.site.http.report.PostReportResult
import com.github.k1rakishou.chan.core.site.loader.ChanLoaderException
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.chan.features.drawer.data.NavigationHistoryEntry
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerGoToPostHelper
import com.github.k1rakishou.chan.ui.adapter.PostAdapter.PostAdapterCallback
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.ui.cell.GenericPostCell
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.ThreadStatusCell
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.PostOmittedImagesController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.helper.PostLinkableClickHelper
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper
import com.github.k1rakishou.chan.ui.layout.ThreadListLayout.ThreadListLayoutPresenterCallback
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.HeaderFloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openLink
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.shareLink
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.bidirectionalSequence
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeParser
import com.github.k1rakishou.model.data.board.pages.BoardPage
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.filter.FilterType
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.options.ChanReadOptions
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.thread.ChanThread
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import com.github.k1rakishou.model.util.ChanPostUtils
import com.github.k1rakishou.model.util.ChanPostUtils.getReadableFileSize
import com.github.k1rakishou.persist_state.IndexAndTop
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class ThreadPresenter @Inject constructor(
  private val _bookmarksManager: Lazy<BookmarksManager>,
  private val _pageRequestManager: Lazy<PageRequestManager>,
  private val _siteManager: Lazy<SiteManager>,
  private val _boardManager: Lazy<BoardManager>,
  private val _savedReplyManager: Lazy<SavedReplyManager>,
  private val _chanPostRepository: Lazy<ChanPostRepository>,
  private val _archivesManager: Lazy<ArchivesManager>,
  private val _onDemandContentLoaderManager: Lazy<OnDemandContentLoaderManager>,
  private val _seenPostsManager: Lazy<SeenPostsManager>,
  private val _historyNavigationManager: Lazy<HistoryNavigationManager>,
  private val _postFilterManager: Lazy<PostFilterManager>,
  private val _chanFilterManager: Lazy<ChanFilterManager>,
  private val _lastViewedPostNoInfoHolder: Lazy<LastViewedPostNoInfoHolder>,
  private val _chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>,
  private val _postHideHelper: Lazy<PostHideHelper>,
  private val _postHideManager: Lazy<PostHideManager>,
  private val _chanThreadManager: Lazy<ChanThreadManager>,
  private val _globalWindowInsetsManager: Lazy<GlobalWindowInsetsManager>,
  private val _thumbnailLongtapOptionsHelper: Lazy<ThumbnailLongtapOptionsHelper>,
  private val _mediaViewerGoToPostHelper: Lazy<MediaViewerGoToPostHelper>,
  private val _themeEngine: Lazy<ThemeEngine>,
  private val _chanLoadProgressNotifier: Lazy<ChanLoadProgressNotifier>,
  private val _postHighlightManager: Lazy<PostHighlightManager>,
  private val _currentOpenedDescriptorStateManager: Lazy<CurrentOpenedDescriptorStateManager>,
  private val _chanCatalogSnapshotCache: Lazy<ChanCatalogSnapshotCache>,
  private val _compositeCatalogManager: Lazy<CompositeCatalogManager>
) : PostAdapterCallback,
  PostCellCallback,
  ThreadStatusCell.Callback,
  ThreadListLayoutPresenterCallback,
  CoroutineScope {

  private val bookmarksManager: BookmarksManager
    get() = _bookmarksManager.get()
  private val pageRequestManager: PageRequestManager
    get() = _pageRequestManager.get()
  private val siteManager: SiteManager
    get() = _siteManager.get()
  private val boardManager: BoardManager
    get() = _boardManager.get()
  private val savedReplyManager: SavedReplyManager
    get() = _savedReplyManager.get()
  private val chanPostRepository: ChanPostRepository
    get() = _chanPostRepository.get()
  private val archivesManager: ArchivesManager
    get() = _archivesManager.get()
  private val onDemandContentLoaderManager: OnDemandContentLoaderManager
    get() = _onDemandContentLoaderManager.get()
  private val seenPostsManager: SeenPostsManager
    get() = _seenPostsManager.get()
  private val historyNavigationManager: HistoryNavigationManager
    get() = _historyNavigationManager.get()
  private val postFilterManager: PostFilterManager
    get() = _postFilterManager.get()
  private val chanFilterManager: ChanFilterManager
    get() = _chanFilterManager.get()
  private val lastViewedPostNoInfoHolder: LastViewedPostNoInfoHolder
    get() = _lastViewedPostNoInfoHolder.get()
  private val chanThreadViewableInfoManager: ChanThreadViewableInfoManager
    get() = _chanThreadViewableInfoManager.get()
  private val postHideHelper: PostHideHelper
    get() = _postHideHelper.get()
  private val postHideManager: PostHideManager
    get() = _postHideManager.get()
  private val chanThreadManager: ChanThreadManager
    get() = _chanThreadManager.get()
  private val globalWindowInsetsManager: GlobalWindowInsetsManager
    get() = _globalWindowInsetsManager.get()
  private val thumbnailLongtapOptionsHelper: ThumbnailLongtapOptionsHelper
    get() = _thumbnailLongtapOptionsHelper.get()
  private val themeEngine: ThemeEngine
    get() = _themeEngine.get()
  private val chanLoadProgressNotifier: ChanLoadProgressNotifier
    get() = _chanLoadProgressNotifier.get()
  private val postHighlightManager: PostHighlightManager
    get() = _postHighlightManager.get()
  private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
    get() = _currentOpenedDescriptorStateManager.get()
  private val chanCatalogSnapshotCache: ChanCatalogSnapshotCache
    get() = _chanCatalogSnapshotCache.get()
  private val compositeCatalogManager: CompositeCatalogManager
    get() = _compositeCatalogManager.get()
  private val mediaViewerGoToPostHelper: MediaViewerGoToPostHelper
    get() = _mediaViewerGoToPostHelper.get()

  private val chanThreadTicker by lazy {
    ChanThreadTicker(
      scope = this,
      isDevFlavor = isDevBuild(),
      _archivesManager = _archivesManager,
      _chanThreadManager = _chanThreadManager,
      action = this::onChanTickerTick
    )
  }

  private var threadPresenterCallback: ThreadPresenterCallback? = null
  private var forcePageUpdate = false
  private val alreadyCreatedNavElement = AtomicBoolean(false)
  private var currentFocusedController = CurrentFocusedController.None
  private var currentNormalLoadThreadJob: Job? = null
  private var currentFullLoadThreadJob: Job? = null

  override val endOfCatalogReached: Boolean
    get() {
      val descriptor = currentChanDescriptor
        ?: return false

      if (descriptor !is ChanDescriptor.ICatalogDescriptor) {
        return false
      }

      return chanCatalogSnapshotCache.get(descriptor)?.isEndReached ?: true
    }

  val isUnlimitedCatalog: Boolean
    get() {
      val descriptor = currentChanDescriptor
        ?: return false

      if (descriptor !is ChanDescriptor.ICatalogDescriptor) {
        return false
      }

      if (descriptor is ChanDescriptor.CompositeCatalogDescriptor) {
        return false
      }

      val isUnlimitedCatalog = chanCatalogSnapshotCache.get(descriptor)?.isUnlimitedCatalog
      if (isUnlimitedCatalog != null) {
        return isUnlimitedCatalog
      }

      if (!boardManager.isReady()) {
        return false
      }

      return boardManager.byBoardDescriptor(descriptor.boardDescriptor())?.isUnlimitedCatalog ?: false
    }

  override val isUnlimitedOrCompositeCatalog: Boolean
    get() {
      val descriptor = currentChanDescriptor
        ?: return false

      if (descriptor !is ChanDescriptor.ICatalogDescriptor) {
        return false
      }

      if (descriptor is ChanDescriptor.CompositeCatalogDescriptor) {
        return true
      }

      val isUnlimitedOrCompositeCatalog = chanCatalogSnapshotCache.get(descriptor)?.isUnlimitedOrCompositeCatalog
      if (isUnlimitedOrCompositeCatalog != null) {
        return isUnlimitedOrCompositeCatalog
      }

      if (!boardManager.isReady()) {
        return false
      }

      return boardManager.byBoardDescriptor(descriptor.boardDescriptor())?.isUnlimitedCatalog ?: false
    }

  override val unlimitedOrCompositeCatalogEndReached: Boolean
    get() {
      val descriptor = currentChanDescriptor
        ?: return false

      if (descriptor !is ChanDescriptor.ICatalogDescriptor) {
        return false
      }

      return chanCatalogSnapshotCache.get(descriptor)?.isEndReached ?: false
    }

  var chanThreadLoadingState = ChanThreadLoadingState.Uninitialized
    private set

  private val verboseLogs by lazy { ChanSettings.verboseLogs.get() }
  private val postLinkableClickHelper by lazy {
    PostLinkableClickHelper(
      siteManager = siteManager,
      boardManager = boardManager,
      archivesManager = archivesManager
    )
  }

  private val job = SupervisorJob()

  private lateinit var postOptionsClickExecutor: RendezvousCoroutineExecutor
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor
  private lateinit var postBindExecutor: SerializedCoroutineExecutor
  private lateinit var postUnbindExecutor: SerializedCoroutineExecutor
  private lateinit var afterCatalogOrThreadLoadedExecutor: SerializedCoroutineExecutor
  private lateinit var postUpdatesExecutor: ThrottlingCoroutineExecutorWithAccumulator<PostDescriptor>
  private lateinit var context: Context

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main

  val isBound: Boolean
    get() {
      val currentDescriptor = chanThreadTicker.currentChanDescriptor
      if (currentDescriptor == null) {
        if (verboseLogs) {
          Logger.e(TAG, "isBound() currentChanDescriptor == null")
        }

        return false
      }

      return true
    }

  val isBoundAndCached: Boolean
    get() {
      if (!isBound) {
        return false
      }

      val currentDescriptor = chanThreadTicker.currentChanDescriptor
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

      return bookmarksManager.contains(threadDescriptor)
    }

  override val currentChanDescriptor: ChanDescriptor?
    get() = chanThreadTicker.currentChanDescriptor

  init {
    launch {
      chanFilterManager.listenForFiltersChanges()
        .debounce(1000L)
        .collect { filterEvent -> onFiltersChanged(filterEvent) }
    }

    launch {
      onDemandContentLoaderManager.postUpdateFlow
        .collect { batchResult -> onPostUpdatedWithNewContent(batchResult) }
    }

    launch {
      mediaViewerGoToPostHelper.mediaViewerGoToPostEventsFlow
        .collect { postDescriptor ->
          if (postDescriptor.descriptor != currentChanDescriptor) {
            return@collect
          }

          scrollToPost(needle = postDescriptor)
          highlightPost(postDescriptor = postDescriptor, blink = true)
        }
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

    Logger.d(TAG, "bindChanDescriptor(chanDescriptor=$chanDescriptor)")

    threadPresenterCallback?.showLoading()

    this.currentNormalLoadThreadJob?.cancel()
    this.currentNormalLoadThreadJob = null

    this.currentFullLoadThreadJob?.cancel()
    this.currentFullLoadThreadJob = null

    postOptionsClickExecutor = RendezvousCoroutineExecutor(this)
    serializedCoroutineExecutor = SerializedCoroutineExecutor(this)
    postBindExecutor = SerializedCoroutineExecutor(this, dispatcher = Dispatchers.Default)
    postUnbindExecutor = SerializedCoroutineExecutor(this, dispatcher = Dispatchers.Default)
    afterCatalogOrThreadLoadedExecutor = SerializedCoroutineExecutor(this, dispatcher = Dispatchers.Default)
    postUpdatesExecutor = ThrottlingCoroutineExecutorWithAccumulator(this)

    if (chanThreadTicker.currentChanDescriptor != null) {
      unbindChanDescriptor(false)
    }

    if (chanDescriptor is ChanDescriptor.CatalogDescriptor && !ChanSettings.neverShowPages.get()) {
      pageRequestManager.getBoardPages(chanDescriptor.boardDescriptor())
    }

    chanThreadManager.bindChanDescriptor(chanDescriptor)

    when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        currentOpenedDescriptorStateManager.updateCatalogDescriptor(chanDescriptor)
      }
      is ChanDescriptor.ThreadDescriptor -> {
        currentOpenedDescriptorStateManager.updateThreadDescriptor(chanDescriptor)
      }
    }

    Logger.d(TAG, "chanThreadTicker.startTicker($chanDescriptor)")
    chanThreadTicker.startTicker(chanDescriptor)
  }

  fun unbindChanDescriptor(isDestroying: Boolean) {
    BackgroundUtils.ensureMainThread()

    val currentChanDescriptor = chanThreadTicker.currentChanDescriptor
    Logger.d(TAG, "unbindChanDescriptor(isDestroying=$isDestroying) currentChanDescriptor=$currentChanDescriptor")

    alreadyCreatedNavElement.set(false)

    if (currentChanDescriptor != null) {
      onDemandContentLoaderManager.cancelAllForDescriptor(currentChanDescriptor)

      when (currentChanDescriptor) {
        is ChanDescriptor.ICatalogDescriptor -> {
          currentOpenedDescriptorStateManager.updateCatalogDescriptor(null)
          chanCatalogSnapshotCache.delete(currentChanDescriptor)
        }
        is ChanDescriptor.ThreadDescriptor -> {
          currentOpenedDescriptorStateManager.updateThreadDescriptor(null)
        }
      }

      Logger.d(TAG, "chanThreadTicker.stopTicker($currentChanDescriptor)")
      chanThreadTicker.stopTicker(resetCurrentChanDescriptor = true)

      postHighlightManager.cleanup(currentChanDescriptor)
    }

    if (isDestroying) {
      job.cancelChildren()

      if (::postOptionsClickExecutor.isInitialized) {
        postOptionsClickExecutor.stop()
      }

      if (::serializedCoroutineExecutor.isInitialized) {
        serializedCoroutineExecutor.stop()
      }

      if (::postBindExecutor.isInitialized) {
        postBindExecutor.stop()
      }

      if (::postUnbindExecutor.isInitialized) {
        postUnbindExecutor.stop()
      }

      if (::afterCatalogOrThreadLoadedExecutor.isInitialized) {
        afterCatalogOrThreadLoadedExecutor.stop()
      }

      if (::postUpdatesExecutor.isInitialized) {
        postUpdatesExecutor.cancelAll()
      }
    }

    chanThreadLoadingState = ChanThreadLoadingState.Uninitialized
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun onChanTickerTick(chanDescriptor: ChanDescriptor) {
    Logger.d(TAG, "onChanTickerTick($chanDescriptor)")

    chanPostRepository.awaitUntilInitialized()
    normalLoad()
  }

  override fun quickReloadFromMemoryCache(chanLoadOptions: ChanLoadOptions) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "quickReloadFromMemoryCache()")

    val currentChanDescriptor = chanThreadTicker.currentChanDescriptor
    if (currentChanDescriptor == null) {
      Logger.d(TAG, "quickReloadFromMemoryCache() chanThreadTicker.currentChanDescriptor==null")
      return
    }

    if (isUnlimitedOrCompositeCatalog) {
      showToast(context, R.string.reload_composite_or_unlimited_catalog_manually)
      Logger.d(TAG, "quickReloadFromMemoryCache() isUnlimitedOrCompositeCatalog == true")
      return
    }

    normalLoad(
      showLoading = false,
      chanCacheUpdateOptions = ChanCacheUpdateOptions.DoNotUpdateCache,
      chanLoadOptions = chanLoadOptions
    )
  }

  fun resetTicker() {
    chanThreadTicker.resetTicker()
  }

  override fun loadCatalogPage(overridePage: Int?) {
    val descriptor = currentChanDescriptor as ChanDescriptor.ICatalogDescriptor?
      ?: return

    val catalogSnapshot = chanCatalogSnapshotCache.get(descriptor)
      ?: return

    if (!catalogSnapshot.isUnlimitedOrCompositeCatalog) {
      return
    }

    if (overridePage != null) {
      Logger.d(TAG, "loadCatalogPage() overriding catalog page: overridePage=$overridePage")
      catalogSnapshot.updateCatalogPage(overridePage)
    }

    val isEndReached = catalogSnapshot.isEndReached
    if (isEndReached) {
      Logger.d(TAG, "loadCatalogPage() isEndReached == true")
      return
    }

    val catalogPage = catalogSnapshot.catalogPage
    val nextCatalogPage = catalogSnapshot.getNextCatalogPage()

    Logger.d(TAG, "loadCatalogPage() catalogPage=${catalogPage} (overridePage=$overridePage), " +
      "nextCatalogPage=${nextCatalogPage}, isEndReached=$isEndReached")

    if (overridePage != null) {
      normalLoad(showLoading = true, deleteChanCatalogSnapshot = false)
    } else {
      normalLoad(showLoading = false)
    }
  }

  override fun getNextPage(): Int? {
    val descriptor = currentChanDescriptor as ChanDescriptor.ICatalogDescriptor?
      ?: return null

    val catalogSnapshot = chanCatalogSnapshotCache.get(descriptor)
      ?: return null

    if (!catalogSnapshot.isUnlimitedOrCompositeCatalog) {
      return null
    }

    return catalogSnapshot.getNextCatalogPage()
  }

  override fun onPostCellBound(postCell: GenericPostCell) {
    // no-op
  }

  suspend fun loadWholeCompositeCatalog() {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "loadWholeCompositeCatalog() start")

    if (currentFullLoadThreadJob != null) {
      Logger.d(TAG, "loadWholeCompositeCatalog() currentFullLoadThreadJob != null")
      return
    }

    if (chanThreadLoadingState == ChanThreadLoadingState.Loading) {
      Logger.d(TAG, "loadWholeCompositeCatalog() chanThreadLoadingState == Loading")
      return
    }

    val currentChanDescriptor = chanThreadTicker.currentChanDescriptor
    if (currentChanDescriptor == null) {
      Logger.d(TAG, "loadWholeCompositeCatalog() chanThreadTicker.currentChanDescriptor==null")
      return
    }

    if (currentChanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor) {
      Logger.d(TAG, "loadWholeCompositeCatalog() currentChanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor")
      return
    }

    if (chanCatalogSnapshotCache.get(currentChanDescriptor)?.isEndReached == true) {
      Logger.d(TAG, "loadWholeCompositeCatalog() isEndReached == true")
      showToast(context, R.string.end_post_composite_catalog_already_reached)
      return
    }

    chanThreadLoadingState = ChanThreadLoadingState.Loading

    val alreadyPresenting = threadPresenterCallback
      ?.isAlreadyPresentingController { controller -> controller is LoadingViewController } == true

    val loadingController = if (!alreadyPresenting) {
      val loadingController = LoadingViewController(context, false)
      loadingController.enableCancellation {
        currentFullLoadThreadJob?.cancel()
        currentFullLoadThreadJob = null
      }

      threadPresenterCallback?.presentController(loadingController, true)

      loadingController
    } else {
      null
    }

    @Suppress("SuspendFunctionOnCoroutineScope")
    currentFullLoadThreadJob = launch {
      this.coroutineContext[Job.Key]?.invokeOnCompletion { cause ->
        if (cause is CancellationException) {
          Logger.d(TAG, "loadWholeCompositeCatalog() canceled")
        }

        chanThreadManager.removeRequestedChanDescriptor(currentChanDescriptor)

        chanThreadLoadingState = ChanThreadLoadingState.Loaded
        currentFullLoadThreadJob = null

        loadingController?.stopPresenting()
      }

      if (!chanThreadManager.addRequestedChanDescriptor(currentChanDescriptor)) {
        if (verboseLogs) {
          Logger.d(TAG, "loadWholeCompositeCatalog() skipping $currentChanDescriptor because it was already requested")
        }

        return@launch
      }

      var lastThreadLoadResult: ThreadLoadResult? = null

      while (isActive) {
        ensureActive()

        val catalogSnapshot = chanCatalogSnapshotCache.get(currentChanDescriptor)
        if (catalogSnapshot == null) {
          Logger.d(TAG, "loadWholeCompositeCatalog() catalogSnapshot == null exiting")
          break
        }

        if (catalogSnapshot.isEndReached) {
          Logger.d(TAG, "loadWholeCompositeCatalog() isEndReached == true exiting")
          break
        }

        val catalogPageToLoad = catalogSnapshot.getNextCatalogPage()
        val totalCatalogPages = (currentChanDescriptor.catalogDescriptors.size - 1)

        Logger.d(TAG, "loadWholeCompositeCatalog() loading ${catalogPageToLoad}/${totalCatalogPages}...")

        val nextDescriptorToLoad = currentChanDescriptor.catalogDescriptors.getOrNull(catalogPageToLoad ?: 0)
        if (nextDescriptorToLoad == null) {
          Logger.d(TAG, "loadWholeCompositeCatalog() nextDescriptorToLoad == null exiting")
          break
        }

        val message = "Loading catalog '${nextDescriptorToLoad.userReadableString()}' " +
          "${catalogPageToLoad}/${totalCatalogPages}"
        loadingController?.updateWithText(message)

        lastThreadLoadResult = chanThreadManager.loadThreadOrCatalog(
          page = catalogPageToLoad,
          compositeCatalogDescriptor = currentChanDescriptor,
          chanDescriptor = nextDescriptorToLoad,
          chanCacheUpdateOptions = ChanCacheUpdateOptions.UpdateCache,
          chanLoadOptions = ChanLoadOptions.retainAll(),
          chanCacheOptions = ChanCacheOptions.onlyCacheInMemory(),
          chanReadOptions = ChanReadOptions.default()
        )

        when (lastThreadLoadResult) {
          is ThreadLoadResult.Loaded -> {
            Logger.d(TAG, "loadWholeCompositeCatalog() ${nextDescriptorToLoad} success")
          }
          is ThreadLoadResult.Error -> {
            Logger.e(TAG, "loadWholeCompositeCatalog() ${nextDescriptorToLoad} error. " +
              "Reason: ${lastThreadLoadResult.exception.errorMessage}")
          }
        }

        catalogSnapshot.onCatalogLoaded(catalogPageToLoad)
        Logger.d(TAG, "loadWholeCompositeCatalog() loading ${catalogPageToLoad}/${totalCatalogPages}...done")
      }

      when (lastThreadLoadResult) {
        is ThreadLoadResult.Error -> {
          onChanLoaderError(lastThreadLoadResult.chanDescriptor, lastThreadLoadResult.exception)
        }
        is ThreadLoadResult.Loaded -> {
          val successfullyProcessedNewPosts = onChanLoaderData(
            loadedChanDescriptor = lastThreadLoadResult.chanDescriptor,
            refreshPostPopupHelperPosts = false
          )
          if (!successfullyProcessedNewPosts) {
            val error = getPossibleChanLoadError(currentChanDescriptor)
            onChanLoaderError(lastThreadLoadResult.chanDescriptor, error)
          } else {
            chanCatalogSnapshotCache.get(currentChanDescriptor)
              ?.onEndOfUnlimitedCatalogReached()
          }
        }
        null -> {
          chanCatalogSnapshotCache.get(currentChanDescriptor)
            ?.onEndOfUnlimitedCatalogReached()
        }
      }

      Logger.d(TAG, "loadWholeCompositeCatalog() end")
    }
  }

  @OptIn(ExperimentalTime::class)
  fun normalLoad(
    showLoading: Boolean = false,
    chanCacheUpdateOptions: ChanCacheUpdateOptions = ChanCacheUpdateOptions.UpdateCache,
    chanLoadOptions: ChanLoadOptions = ChanLoadOptions.retainAll(),
    chanCacheOptions: ChanCacheOptions = ChanCacheOptions.onlyCacheInMemory(),
    chanReadOptions: ChanReadOptions = ChanReadOptions.default(),
    deleteChanCatalogSnapshot: Boolean = showLoading,
    refreshPostPopupHelperPosts: Boolean = false
  ) {
    if (currentNormalLoadThreadJob != null) {
      Logger.d(TAG, "normalLoad() currentNormalLoadThreadJob != null")
      return
    }

    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "normalLoad() start")

    val currentChanDescriptor = chanThreadTicker.currentChanDescriptor
    if (currentChanDescriptor == null) {
      Logger.d(TAG, "normalLoad() chanThreadTicker.currentChanDescriptor==null")
      return
    }

    Logger.d(TAG, "normalLoad(currentChanDescriptor=$currentChanDescriptor\nshowLoading=$showLoading\n" +
      "chanCacheUpdateOptions=$chanCacheUpdateOptions\nchanLoadOptions=$chanLoadOptions\n" +
      "chanCacheOptions=$chanCacheOptions\nchanReadOptions=$chanReadOptions)")

    chanThreadLoadingState = ChanThreadLoadingState.Loading

    currentNormalLoadThreadJob = launch {
      if (!chanThreadManager.addRequestedChanDescriptor(currentChanDescriptor)) {
        if (verboseLogs) {
          Logger.d(TAG, "normalLoad() skipping $currentChanDescriptor because it was already requested")
        }

        return@launch
      }

      this.coroutineContext[Job.Key]?.invokeOnCompletion {
        chanThreadManager.removeRequestedChanDescriptor(currentChanDescriptor)

        chanThreadLoadingState = ChanThreadLoadingState.Loaded
        currentNormalLoadThreadJob = null
      }

      if (showLoading) {
        threadPresenterCallback?.showLoading()
        alreadyCreatedNavElement.set(false)
      }

      if (deleteChanCatalogSnapshot) {
        if (currentChanDescriptor is ChanDescriptor.ICatalogDescriptor) {
          chanCatalogSnapshotCache.delete(currentChanDescriptor)
        }
      }

      val isEndReached = if (currentChanDescriptor is ChanDescriptor.ICatalogDescriptor) {
        chanCatalogSnapshotCache.get(currentChanDescriptor)?.isEndReached ?: false
      } else {
        false
      }

      if (isEndReached && chanCacheUpdateOptions !is ChanCacheUpdateOptions.DoNotUpdateCache) {
        Logger.d(TAG, "normalLoad() isEndReached == true, chanCacheUpdateOptions=${chanCacheUpdateOptions}")
        return@launch
      }

      chanLoadProgressNotifier.sendProgressEvent(ChanLoadProgressEvent.Begin(currentChanDescriptor))

      val catalogPageToLoad = if (currentChanDescriptor is ChanDescriptor.ICatalogDescriptor) {
        if (chanCacheUpdateOptions is ChanCacheUpdateOptions.DoNotUpdateCache) {
          chanCatalogSnapshotCache.get(currentChanDescriptor)?.catalogPage
        } else {
          chanCatalogSnapshotCache.get(currentChanDescriptor)?.getNextCatalogPage()
        }
      } else {
        null
      }

      val nextDescriptorToLoad = if (currentChanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
        currentChanDescriptor.catalogDescriptors.getOrNull(catalogPageToLoad ?: 0)
      } else {
        currentChanDescriptor
      }

      val compositeCatalogDescriptor = if (currentChanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
        currentChanDescriptor
      } else {
        null
      }

      checkNotNull(nextDescriptorToLoad) { "nextDescriptorToLoad is null" }

      val threadLoadResult = chanThreadManager.loadThreadOrCatalog(
        page = catalogPageToLoad,
        compositeCatalogDescriptor = compositeCatalogDescriptor,
        chanDescriptor = nextDescriptorToLoad,
        chanCacheUpdateOptions = chanCacheUpdateOptions,
        chanLoadOptions = chanLoadOptions,
        chanCacheOptions = chanCacheOptions,
        chanReadOptions = chanReadOptions
      )

      Logger.d(TAG, "normalLoad() threadLoadResult=$threadLoadResult (currentChanDescriptor=$currentChanDescriptor)")

      when (threadLoadResult) {
        is ThreadLoadResult.Error -> {
          onChanLoaderError(threadLoadResult.chanDescriptor, threadLoadResult.exception)
        }
        is ThreadLoadResult.Loaded -> {
          val (successfullyProcessedNewPosts, time) = measureTimedValue {
            onChanLoaderData(
              loadedChanDescriptor = threadLoadResult.chanDescriptor,
              refreshPostPopupHelperPosts = refreshPostPopupHelperPosts
            )
          }

          Logger.d(TAG, "onChanLoaderData(${threadLoadResult.chanDescriptor}) end, took $time")

          if (!successfullyProcessedNewPosts) {
            val error = getPossibleChanLoadError(currentChanDescriptor)
            onChanLoaderError(threadLoadResult.chanDescriptor, error)
          } else if (currentChanDescriptor is ChanDescriptor.ICatalogDescriptor) {
            chanCatalogSnapshotCache.get(currentChanDescriptor)
              ?.onCatalogLoaded(catalogPageToLoad)
          }
        }
      }

      Logger.d(TAG, "normalLoad() end")
    }
  }

  private fun getPossibleChanLoadError(currentChanDescriptor: ChanDescriptor?): ClientException {
    if (currentChanDescriptor is ChanDescriptor.ICatalogDescriptor) {
      val catalogPostsCount = chanThreadManager.getChanCatalog(currentChanDescriptor)?.postsCount() ?: 0
      if (catalogPostsCount <= 0) {
        return ClientException("Catalog is empty")
      }

      // fallthrough
    }

    return ClientException("Failed to load catalog/thread because of unknown error. See logs for more info.")
  }

  fun onForegroundChanged(foreground: Boolean) {
    if (!isBoundAndCached) {
      return
    }

    if (foreground && isWatching()) {
      Logger.d(TAG, "onForegroundChanged(descriptor=${currentChanDescriptor}) calling resetTicker()")
      chanThreadTicker.resetTicker()
    } else {
      Logger.d(TAG, "onForegroundChanged(descriptor=${currentChanDescriptor}) calling stopTicker()")
      chanThreadTicker.stopTicker(resetCurrentChanDescriptor = false)
    }
  }

  suspend fun pin(): Boolean {
    val threadDescriptor = currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
      ?: return false

    if (!bookmarksManager.isReady()) {
      return false
    }

    if (bookmarksManager.contains(threadDescriptor)) {
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
    if (isBound) {
      if (isManuallyChangedOrder) {
        scrollTo(0, false)
      }

      showPosts()
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

    val foundPostDescriptor = postDescriptors
      .bidirectionalSequence(startPosition = position.index)
      .firstOrNull { postDescriptor ->
        val postImages = chanThreadManager.getPost(postDescriptor)?.postImages
          ?: return@firstOrNull false

        return@firstOrNull postImages.firstOrNull()?.imageUrl != null
      }

    val initialImageUrl = foundPostDescriptor
      ?.let { postDescriptor -> chanThreadManager.getPost(postDescriptor)?.firstImage()?.imageUrl }

    threadPresenterCallback?.showAlbum(initialImageUrl, postDescriptors)
  }

  override fun onPostBind(postCellData: PostCellData) {
    BackgroundUtils.ensureMainThread()

    val currentDescriptor = currentChanDescriptor
      ?: return

    val catalogMode = currentDescriptor is ChanDescriptor.ICatalogDescriptor

    postBindExecutor.post {
      BackgroundUtils.ensureBackgroundThread()

      val postDescriptor = postCellData.postDescriptor
      onDemandContentLoaderManager.onPostBind(postDescriptor, catalogMode)
      seenPostsManager.onPostBind(postCellData.isViewingThread, postDescriptor)
      threadBookmarkViewPost(postCellData)
    }
  }

  override fun onPostUnbind(postCellData: PostCellData, isActuallyRecycling: Boolean) {
    BackgroundUtils.ensureMainThread()

    postUnbindExecutor.post {
      BackgroundUtils.ensureBackgroundThread()

      val postDescriptor = postCellData.postDescriptor

      postUpdatesExecutor.cancel(postCellData.postDescriptor)
      onDemandContentLoaderManager.onPostUnbind(postDescriptor, isActuallyRecycling)
      seenPostsManager.onPostUnbind(postCellData.isViewingThread, postDescriptor)
      threadBookmarkViewPost(postCellData)
    }
  }

  private fun threadBookmarkViewPost(postCellData: PostCellData) {
    if (!postCellData.chanDescriptor.isThreadDescriptor() || postCellData.isInPopup) {
      return
    }

    val threadDescriptor = postCellData.chanDescriptor.threadDescriptorOrNull()
    val postNo = postCellData.postDescriptor.postNo

    if (threadDescriptor != null && postCellData.postIndex >= 0) {
      val unseenPostsCount = chanThreadManager.getNewPostsCount(
        threadDescriptor,
        postNo
      )

      bookmarksManager.onPostViewed(threadDescriptor, postNo, unseenPostsCount)
      lastViewedPostNoInfoHolder.setLastViewedPostNo(threadDescriptor, postNo)
    }
  }

  private suspend fun onFiltersChanged(filterEvent: ChanFilterManager.FilterEvent) {
    if (filterEvent is ChanFilterManager.FilterEvent.Initialized) {
      return
    }

    chanPostRepository.awaitUntilInitialized()
    Logger.d(TAG, "onFiltersChanged($currentChanDescriptor) force reloading posts from the cache")

    val catalogDescriptor = currentOpenedDescriptorStateManager.currentCatalogDescriptor
    val threadDescriptor = currentOpenedDescriptorStateManager.currentThreadDescriptor

    if (catalogDescriptor == currentChanDescriptor || threadDescriptor == currentChanDescriptor) {
      quickReloadFromMemoryCache(chanLoadOptions = ChanLoadOptions.forceUpdateAllPosts())
    }
  }

  private fun onPostUpdatedWithNewContent(batchResult: LoaderBatchResult) {
    BackgroundUtils.ensureMainThread()

    if (threadPresenterCallback != null && needUpdatePost(batchResult) && ::postUpdatesExecutor.isInitialized) {
      postUpdatesExecutor.post(item = batchResult.postDescriptor, timeout = 200) { collectedPostDescriptors ->
        val updatedPosts = chanThreadManager.getPosts(collectedPostDescriptors)
        if (updatedPosts.isEmpty()) {
          return@post
        }

        threadPresenterCallback?.onPostsUpdated(updatedPosts)
      }
    }
  }

  private fun needUpdatePost(batchResult: LoaderBatchResult): Boolean {
    return batchResult.results.any { it is Succeeded && it.needUpdateView }
  }

  private suspend fun onChanLoaderError(chanDescriptor: ChanDescriptor, error: ChanLoaderException) {
    BackgroundUtils.ensureMainThread()

    if (error.isCoroutineCancellationError()) {
      return
    }

    when {
      error is ClientException -> {
        Logger.e(TAG, "onChanLoaderError($chanDescriptor) called, error=${error.errorMessageOrClassName()}")
      }
      error.isFirewallError() -> {
        Logger.e(TAG, "onChanLoaderError($chanDescriptor) called FirewallDetectedException")
      }
      else -> {
        Logger.e(TAG, "onChanLoaderError($chanDescriptor) called", error)
      }
    }

    threadPresenterCallback?.showError(chanDescriptor, error)
  }

  private suspend fun onChanLoaderData(
    loadedChanDescriptor: ChanDescriptor,
    refreshPostPopupHelperPosts: Boolean
  ): Boolean {
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

    if (localChanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      if (!localChanDescriptor.catalogDescriptors.contains(loadedChanDescriptor)) {
        Logger.e(TAG, "onChanLoaderData() localChanDescriptor.contains(loadedChanDescriptor) == false " +
          "($localChanDescriptor, $loadedChanDescriptor)")
        return false
      }
    } else {
      if (localChanDescriptor != loadedChanDescriptor) {
        Logger.e(TAG, "onChanLoaderData() localChanDescriptor " +
          "($localChanDescriptor) != loadedChanDescriptor ($loadedChanDescriptor)")
        return false
      }
    }

    val newPostsCount = getNewPostsCount(localChanDescriptor)
    val chanThread = if (localChanDescriptor is ChanDescriptor.ThreadDescriptor) {
      chanThreadManager.getChanThread(localChanDescriptor)
    } else {
      null
    }

    if (isWatching()) {
      val shouldResetTimer = newPostsCount > 0
      chanThreadTicker.kickTicker(resetTimer = shouldResetTimer)
    }

    threadPresenterCallback?.hideError(loadedChanDescriptor)
    showPosts(refreshPostPopupHelperPosts = refreshPostPopupHelperPosts)

    if (localChanDescriptor is ChanDescriptor.ThreadDescriptor) {
      if (
        localChanDescriptor.threadNo == loadedChanDescriptor.threadNoOrNull()
        && threadPresenterCallback?.canShowSnackBar() == true
      ) {
        showThreadStatusSnackbar(chanThread, newPostsCount)
      }

      if (localChanDescriptor.threadNo == loadedChanDescriptor.threadNoOrNull()) {
        if (forcePageUpdate) {
          pageRequestManager.forceUpdateForBoard(localChanDescriptor.boardDescriptor)
          forcePageUpdate = false
        }
      }
    }

    handleMarkedPost()

    afterCatalogOrThreadLoadedExecutor.post {
      BackgroundUtils.ensureBackgroundThread()

      createNewOrMoveOldNavHistoryElement(
        localChanDescriptor = localChanDescriptor,
        forced = false
      )

      if (localChanDescriptor is ChanDescriptor.ThreadDescriptor) {
        updateBookmarkInfoIfNecessary(localChanDescriptor)
      }
    }

    return true
  }

  private fun showThreadStatusSnackbar(chanThread: ChanThread?, newPostsCount: Int) {
    val isNowSticky = chanThread?.getAndConsumeIsStickyForUi()
    val isNowArchived = chanThread?.getAndConsumeIsArchivedForUi()
    val isNowDeleted = chanThread?.getAndConsumeIsDeletedForUi()
    val isNowClosed = chanThread?.getAndConsumeIsClosedForUi()

    // Thread status updates are in priority
    if (isNowSticky != null || isNowArchived == true || isNowClosed == true || isNowDeleted == true) {
      threadPresenterCallback?.showThreadStatusNotification(
        show = true,
        nowSticky = isNowSticky,
        nowArchived = isNowArchived,
        nowDeleted = isNowDeleted,
        nowClosed = isNowClosed
      )

      return
    }

    val deletedPostsCount = chanThread?.getAndConsumeDeletedPostsForUi() ?: 0

    if (newPostsCount > 0 || deletedPostsCount > 0) {
      threadPresenterCallback?.showNewPostsNotification(
        show = true,
        newPostsCount = newPostsCount,
        deletedPostsCount = deletedPostsCount
      )
    }
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

  fun handleMarkedPost() {
    val localChanDescriptor = currentChanDescriptor
      ?: return

    chanThreadViewableInfoManager.getAndConsumeMarkedPostNo(localChanDescriptor) { markedPostNo ->
      val markedPost = chanThreadManager.findPostByPostNo(currentChanDescriptor, markedPostNo)
      if (markedPost == null) {
        Logger.e(TAG, "handleMarkedPost() Failed to find post ($currentChanDescriptor, $markedPostNo)")
        showToast(context, getString(R.string.failed_post_find_post_to_scroll_to, markedPostNo), Toast.LENGTH_LONG)
        return@getAndConsumeMarkedPostNo
      }

      highlightPost(markedPost.postDescriptor, blink = true)

      if (BackgroundUtils.isInForeground()) {
        BackgroundUtils.runOnMainThread(
          { scrollToPost(markedPost.postDescriptor, false) },
          SCROLL_TO_POST_DELAY_MS
        )
      }
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

  private suspend fun createNewOrMoveOldNavHistoryElement(
    localChanDescriptor: ChanDescriptor,
    canInsertAtTheBeginning: Boolean = true,
    forced: Boolean
  ) {
    val canCreateNavElement = historyNavigationManager.canCreateNavElement(
      bookmarksManager,
      localChanDescriptor
    )

    if (!canCreateNavElement) {
      return
    }

    if (historyNavigationManager.contains(localChanDescriptor)) {
      // Move old
      val canMoveToTop = when (currentFocusedController()) {
        CurrentFocusedController.Catalog -> localChanDescriptor is ChanDescriptor.ICatalogDescriptor
        CurrentFocusedController.Thread -> localChanDescriptor is ChanDescriptor.ThreadDescriptor
        CurrentFocusedController.None -> ChanSettings.isSplitLayoutMode()
      }

      if (canMoveToTop) {
        historyNavigationManager.moveNavElementToTop(
          descriptor = localChanDescriptor,
          canMoveAtTheBeginning = canInsertAtTheBeginning
        )
      }

      return
    }

    if (!forced && !alreadyCreatedNavElement.compareAndSet(false, true)) {
      return
    }

    // Create new
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
          canInsertAtTheBeginning = canInsertAtTheBeginning
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
            canInsertAtTheBeginning = canInsertAtTheBeginning
          )
        }
      }
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        val title = compositeCatalogManager.byCompositeCatalogDescriptor(localChanDescriptor)
          ?.name
          ?: localChanDescriptor.userReadableString()

        historyNavigationManager.createNewNavElement(
          descriptor = localChanDescriptor,
          thumbnailImageUrl = NavigationHistoryEntry.COMPOSITE_ICON_URL,
          title = title,
          canInsertAtTheBeginning = canInsertAtTheBeginning
        )
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

    threadPresenterCallback?.showNewPostsNotification(false, -1, -1)
    threadPresenterCallback?.showThreadStatusNotification(false, null, null, null, null)

    // Update the last seen indicator
    showPosts()
  }

  fun onNewPostsViewClicked() {
    if (!isBound) {
      return
    }

    val chanDescriptor = currentChanDescriptor
      ?: return

    chanThreadViewableInfoManager.view(chanDescriptor) { chanThreadViewableInfoView ->
      val post = chanThreadManager.findPostByPostNo(
        chanDescriptor,
        chanThreadViewableInfoView.lastViewedPostNo
      )

      var position = -1

      if (post != null) {
        val posts = threadPresenterCallback?.displayingPostDescriptorsInThread
          ?: return@view

        for (i in posts.indices) {
          val needle = posts[i]
          if (post.postDescriptor == needle) {
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
    val postDescriptors = threadPresenterCallback?.displayingPostDescriptorsInThread
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

  @JvmOverloads
  fun scrollToPost(needle: PostDescriptor, smooth: Boolean = true) {
    var position = -1

    val posts = threadPresenterCallback?.displayingPostDescriptorsInThread
    if (posts == null || posts.isEmpty()) {
      Logger.e(TAG, "scrollToPost($needle) posts are null or empty")
      return
    }

    for (i in posts.indices) {
      val postDescriptor = posts[i]
      if (postDescriptor == needle) {
        position = i
        break
      }
    }

    if (position >= 0) {
      scrollTo(position, smooth)
    }
  }

  fun highlightPost(postDescriptor: PostDescriptor?, blink: Boolean) {
    threadPresenterCallback?.highlightPost(postDescriptor, blink)
  }

  fun selectPostImage(postImage: ChanPostImage) {
    val postDescriptors = threadPresenterCallback?.displayingPostDescriptorsInThread
      ?: return

    for (postDescriptor in postDescriptors) {
      val post = chanThreadManager.getPost(postDescriptor)
        ?: continue

      for (image in post.postImages) {
        if (image.equalUrl(postImage)) {
          scrollToPost(post.postDescriptor, false)
          highlightPost(post.postDescriptor, blink = true)
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
      val newThreadDescriptor = postDescriptor.descriptor.threadDescriptorOrNull()
      if (newThreadDescriptor == null) {
        error("Failed to convert post internal descriptor to thread descriptor: descriptor=${postDescriptor.descriptor}")
      }

      highlightPost(postDescriptor, blink = false)
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
        // Only scroll to post if the user clicked a non OP post. If the clicked post was OP then
        // use the last viewed post position.
        val scrollToPost = !post.isOP()

        threadPresenterCallback?.openExternalThread(
          postDescriptor = post.postDescriptor,
          scrollToPost = scrollToPost
        )

        return@post
      }

      threadPresenterCallback?.postClicked(post.postDescriptor)
    }
  }

  override fun onGoToPostButtonLongClicked(post: ChanPost, postViewMode: PostCellData.PostViewMode) {
    threadPresenterCallback?.hidePostsPopup()

    scrollToPost(needle = post.postDescriptor, smooth = true)
    highlightPost(post.postDescriptor, blink = true)
  }

  override fun onThumbnailClicked(
    postCellData: PostCellData,
    postImage: ChanPostImage
  ) {
    if (!isBound) {
      return
    }

    onThumbnailClickedInternal(postCellData, postImage)
  }

  override fun onThumbnailOmittedFilesClicked(postCellData: PostCellData, postImage: ChanPostImage) {
    if (!isBound) {
      return
    }

    val postOmittedImagesController = PostOmittedImagesController(
      postImages = postCellData.postImages,
      onImageClicked = { clickedPostImage -> onThumbnailClickedInternal(postCellData, clickedPostImage) },
      onImageLongClicked = { clickedPostImage -> onThumbnailLongClicked(postCellData.chanDescriptor, clickedPostImage) },
      context = context
    )

    threadPresenterCallback?.presentController(postOmittedImagesController, animate = true)
  }

  private fun onThumbnailClickedInternal(postCellData: PostCellData, postImage: ChanPostImage) {
    if (!isBound) {
      return
    }

    val initialImageUrl = postImage.imageUrl?.toString()
      ?: return
    val transitionThumbnailUrl = postImage.getThumbnailUrl()?.toString()
      ?: return

    threadPresenterCallback?.showImages(
      chanDescriptor = postCellData.chanDescriptor,
      initialImageUrl = initialImageUrl,
      transitionThumbnailUrl = transitionThumbnailUrl
    )
  }

  override fun onThumbnailLongClicked(chanDescriptor: ChanDescriptor, postImage: ChanPostImage) {
    if (!isBound) {
      return
    }

    thumbnailLongtapOptionsHelper.onThumbnailLongTapped(
      context = context,
      chanDescriptor = chanDescriptor,
      isCurrentlyInAlbum = false,
      postImage = postImage,
      presentControllerFunc = { controller ->
        threadPresenterCallback?.presentController(controller, true)
      },
      showFiltersControllerFunc = { chanFilterMutable ->
        threadPresenterCallback?.openFiltersController(chanFilterMutable)
      },
      openThreadFunc = { },
      goToPostFunc = { }
    )

  }

  override fun onPopulatePostOptions(post: ChanPost, menu: MutableList<FloatingListMenuItem>, inPopup: Boolean) {
    if (!isBound) {
      return
    }

    val chanDescriptor = currentChanDescriptor
      ?: return

    val site = siteManager.bySiteDescriptor(post.postDescriptor.siteDescriptor())

    if (chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
      val threadDescriptor = post.postDescriptor.threadDescriptor()

      if (!bookmarksManager.contains(threadDescriptor)) {
        menu.add(createMenuItem(POST_OPTION_BOOKMARK, R.string.action_pin))
      }

      if (historyNavigationManager.canCreateNavElement(bookmarksManager, threadDescriptor)) {
        val alreadyContains = runBlocking { historyNavigationManager.contains(threadDescriptor) }
        if (!alreadyContains) {
          menu.add(createMenuItem(POST_OPTION_ADD_TO_NAV_HISTORY, R.string.post_add_to_nav_history))
        }
      }
    } else {
      menu.add(createMenuItem(POST_OPTION_QUOTE, R.string.post_quote))
      menu.add(createMenuItem(POST_OPTION_QUOTE_TEXT, R.string.post_quote_text))
    }

    if (site?.siteFeature(Site.SiteFeature.POST_REPORT) == true) {
      menu.add(createMenuItem(POST_OPTION_REPORT, R.string.post_report))
    }

    if (!inPopup && (chanDescriptor.isCatalogDescriptor() || (chanDescriptor.isThreadDescriptor() && !post.postDescriptor.isOP()))) {
      menu.add(createMenuItem(POST_OPTION_HIDE, R.string.post_hide))
      menu.add(createMenuItem(POST_OPTION_REMOVE, R.string.post_remove))
    }

    if (chanDescriptor.isThreadDescriptor()) {
      if (!TextUtils.isEmpty(post.tripcode)) {
        menu.add(createMenuItem(POST_OPTION_FILTER_TRIPCODE, R.string.post_filter_tripcode))
      }

      if (!TextUtils.isEmpty(post.name)) {
        menu.add(createMenuItem(POST_OPTION_FILTER_NAME, R.string.post_filter_name))
      }

      if (!TextUtils.isEmpty(post.posterId)) {
        menu.add(createMenuItem(POST_OPTION_FILTER_POSTER_ID, R.string.post_filter_poster_id))
      }
    }

    val siteDescriptor = post.postDescriptor.boardDescriptor().siteDescriptor
    val containsSite = siteManager.bySiteDescriptor(siteDescriptor) != null

    if (site?.siteFeature(Site.SiteFeature.POST_DELETE) == true) {
      if (containsSite && !post.isOP()) {
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

    if (archivesManager.supports(post.postDescriptor.boardDescriptor())) {
      menu.add(createMenuItem(POST_OPTION_OPEN_IN_ARCHIVE, R.string.action_open_in_archive))
      menu.add(createMenuItem(POST_OPTION_PREVIEW_IN_ARCHIVE, R.string.action_preview_thread_in_archive))
    }

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
          threadPresenterCallback?.quote(post, false)
        }
        POST_OPTION_QUOTE_TEXT -> {
          threadPresenterCallback?.quote(post, true)
        }
        POST_OPTION_INFO -> showPostInfo(post)
        POST_OPTION_LINKS -> {
          if (post.postComment.linkables.isNotEmpty()) {
            threadPresenterCallback?.showPostLinkables(post, inPopup)
          }
        }
        POST_OPTION_COPY_TEXT -> threadPresenterCallback?.clipboardPost(post)
        POST_OPTION_REPORT -> {
          if (inPopup) {
            threadPresenterCallback?.hidePostsPopup()
          }
          threadPresenterCallback?.openReportView(post)
        }
        POST_OPTION_ADD_TO_NAV_HISTORY -> {
          val descriptor = post.postDescriptor.descriptor

          if (historyNavigationManager.contains(descriptor)) {
            historyNavigationManager.moveNavElementToTop(
              descriptor = descriptor,
              canMoveAtTheBeginning = false
            )
          } else {
            createNewOrMoveOldNavHistoryElement(
              localChanDescriptor = post.postDescriptor.descriptor,
              canInsertAtTheBeginning = false,
              forced = true
            )
          }
        }
        POST_OPTION_FILTER_TRIPCODE -> {
          val tripcode = post.tripcode
            ?: return@post

          threadPresenterCallback?.filterPostTripcode(tripcode)
        }
        POST_OPTION_FILTER_NAME -> {
          val posterName = post.name
            ?: return@post

          threadPresenterCallback?.filterPostName(posterName)
        }
        POST_OPTION_FILTER_POSTER_ID -> {
          val posterId = post.posterId
            ?: return@post

          threadPresenterCallback?.filterPosterId(posterId)
        }
        POST_OPTION_DELETE -> requestDeletePost(post)
        POST_OPTION_SAVE -> saveUnsavePost(post)
        POST_OPTION_BOOKMARK -> {
          if (post !is ChanOriginalPost) {
            return@post
          }

          val threadDescriptor = post.postDescriptor.threadDescriptor()

          chanPostRepository.createEmptyThreadIfNotExists(threadDescriptor)
            .safeUnwrap { error ->
              Logger.e(TAG, "createEmptyThreadIfNotExists($threadDescriptor) error", error)
              return@post
            }

          bookmarksManager.createBookmark(
            threadDescriptor,
            ChanPostUtils.getTitle(post, currentChanDescriptor),
            post.firstImage()?.actualThumbnailUrl
          )
        }
        POST_OPTION_OPEN_BROWSER -> if (isBound) {
          val site = siteManager.bySiteDescriptor(post.postDescriptor.siteDescriptor())
            ?: return@post

          val url = site.resolvable().desktopUrl(post.postDescriptor.descriptor, post.postNo())
          openLink(url)
        }
        POST_OPTION_OPEN_IN_ARCHIVE -> {
          if (isBound) {
            threadPresenterCallback?.showAvailableArchivesList(post.postDescriptor, preview = false)
          }
        }
        POST_OPTION_PREVIEW_IN_ARCHIVE -> {
          if (isBound) {
            threadPresenterCallback?.showAvailableArchivesList(post.postDescriptor, preview = true)
          }
        }
        POST_OPTION_SHARE -> if (isBound) {
          val site = siteManager.bySiteDescriptor(post.postDescriptor.siteDescriptor())
            ?: return@post

          val url = site.resolvable().desktopUrl(post.postDescriptor.descriptor, post.postNo())
          shareLink(url)
        }
        POST_OPTION_REMOVE,
        POST_OPTION_HIDE -> {
          val hide = index == POST_OPTION_HIDE
          val chanDescriptor = currentChanDescriptor
            ?: return@post

          if (chanDescriptor.isCatalogDescriptor()) {
            threadPresenterCallback?.hideThread(post, hide)
            return@post
          }

          chanDescriptor as ChanDescriptor.ThreadDescriptor

          threadPresenterCallback?.showHideOrRemoveWholeChainDialog(
            hide = hide,
            hasReplies = post.repliesFromCount > 0,
            post = post
          )
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
          showToast(context, "Failed to apply theme \'$themeName\'")
          return@launch
        }
        is ThemeParser.ThemeParseResult.Success -> {
          showToast(context, "Done")
        }
      }
    }
  }

  override fun onPostLinkableClicked(post: ChanPost, linkable: PostLinkable, inPopup: Boolean) {
    serializedCoroutineExecutor.post {
      if (!isBound || currentChanDescriptor == null) {
        return@post
      }

      val currentDescriptor = currentChanDescriptor
        ?: return@post

      val currentThreadDescriptor = post.postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
        ?: return@post

      val isExternalThread = post.postDescriptor.descriptor != currentDescriptor

      postLinkableClickHelper.onPostLinkableClicked(
        context = context,
        post = post,
        currentChanDescriptor = currentDescriptor,
        linkable = linkable,
        onQuoteClicked = { postNo ->
          val chanPost = chanThreadManager.findPostByPostNo(currentThreadDescriptor, postNo)
          if (chanPost == null) {
            return@onPostLinkableClicked
          }

          if (postHideManager.hiddenOrRemoved(chanPost.postDescriptor)) {
            return@onPostLinkableClicked
          }

          val postViewMode = if (isExternalThread) {
            PostCellData.PostViewMode.ExternalPostsPopup
          } else {
            PostCellData.PostViewMode.RepliesPopup
          }

          threadPresenterCallback?.showPostsPopup(
            currentThreadDescriptor,
            postViewMode,
            post.postDescriptor,
            listOf(chanPost)
          )
        },
        onQuoteToHiddenOrRemovedPostClicked = { postNo ->
          val chanPost = chanThreadManager.findPostByPostNo(currentThreadDescriptor, postNo)
          if (chanPost == null) {
            return@onPostLinkableClicked
          }

          threadPresenterCallback?.unhideOrUnremovePost(chanPost)
        },
        onLinkClicked = { link ->
          threadPresenterCallback?.openLink(link)
        },
        onCrossThreadLinkClicked = { postDescriptor ->
          threadPresenterCallback?.showPostInExternalThread(postDescriptor)
        },
        onBoardLinkClicked = { catalogDescriptor ->
          threadPresenterCallback?.showCatalog(catalogDescriptor, true)
        },
        onSearchLinkClicked = { catalogDescriptor, query ->
          threadPresenterCallback?.setCatalogWithSearchQuery(catalogDescriptor, query, true)
        },
        onDeadQuoteClicked = { postDescriptor, preview ->
          threadPresenterCallback?.showAvailableArchivesList(
            postDescriptor = postDescriptor,
            preview = preview
          )
        },
        onArchiveQuoteClicked = { postDescriptor ->
          threadPresenterCallback?.showPostInExternalThread(postDescriptor)
        }
      )
    }
  }

  override fun onPostLinkableLongClicked(post: ChanPost, linkable: PostLinkable, inPopup: Boolean) {
    serializedCoroutineExecutor.post {
      if (!isBound || currentChanDescriptor == null) {
        return@post
      }

      Logger.d(TAG, "onPostLinkableLongClicked, postDescriptor: ${post.postDescriptor}, linkable: '${linkable}'")

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
        PostLinkable.Type.QUOTE_TO_HIDDEN_OR_REMOVED_POST,
        PostLinkable.Type.QUOTE -> {
          floatingListMenuItems += createMenuItem(
            menuItemId = COPY_LINK_TEXT,
            stringId = R.string.action_copy_link_text,
            value = linkable.key
          )

          when (val postLinkableValue = linkable.linkableValue) {
            is PostLinkable.Value.LongValue -> {
              val postNo = postLinkableValue.extractValueOrNull()
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
              if (postLinkableValue.threadId <= 0 || postLinkableValue.postId <= 0) {
                return@post
              }

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
          if (link == null) {
            Logger.e(TAG, "PostLinkable is not valid: linkableValue is not StringValue, linkableValue=${linkable.linkableValue}")
            return@post
          }

          floatingListMenuItems += createMenuItem(
            menuItemId = COPY_LINK_VALUE,
            stringId = R.string.action_copy_link_value,
            value = link
          )
        }
        PostLinkable.Type.THREAD -> {
          val threadLink = linkable.linkableValue as? PostLinkable.Value.ThreadOrPostLink
          if (threadLink != null) {
            if (!threadLink.isValid()) {
              Logger.e(TAG, "PostLinkable is not valid: threadLink = ${threadLink}")
              return@post
            }

            val boardDescriptor = BoardDescriptor.create(site.name(), threadLink.board)
            val board = boardManager.byBoardDescriptor(boardDescriptor)

            if (board == null) {
              Logger.e(TAG, "PostLinkable is not valid: board with descriptor ${boardDescriptor} is null")
              return@post
            }

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
          if (searchLink == null) {
            Logger.e(TAG, "PostLinkable is not valid: searchLink is null")
            return@post
          }

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
        PostLinkable.Type.ARCHIVE -> {
          val archiveThreadLink = (linkable.linkableValue as? PostLinkable.Value.ArchiveThreadLink)
          if (archiveThreadLink != null) {
            if (!archiveThreadLink.isValid()) {
              Logger.e(TAG, "PostLinkable is not valid: archiveThreadLink = ${archiveThreadLink}")
              return@post
            }

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
              onPopulatePostOptions(post, postMenuOptions, inPopup)

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
    threadPresenterCallback?.quote(post, withText = false)
  }

  override fun onPostSelectionQuoted(postDescriptor: PostDescriptor, selection: CharSequence) {
    threadPresenterCallback?.quote(postDescriptor, selection)
  }

  override fun onPostSelectionFilter(postDescriptor: PostDescriptor, selection: CharSequence) {
    if (selection.isEmpty()) {
      showToast(context, R.string.selected_text_is_empty)
      return
    }

    val pattern = if (selection.any { ch -> ch.isWhitespace() }) {
      "\"${selection}\""
    } else {
      "/${selection}/"
    }

    val chanFilterMutable = ChanFilterMutable()
    chanFilterMutable.type = FilterType.COMMENT.flag
    chanFilterMutable.pattern = pattern

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

  override fun onShowPostReplies(post: ChanPost) {
    if (!isBound || currentChanDescriptor == null) {
      return
    }

    serializedCoroutineExecutor.post {
      val threadDescriptor = post.postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
        ?: return@post

      val isExternalThread = post.postDescriptor.descriptor != currentChanDescriptor
      val repliesFromCopy = post.repliesFromCopy

      val posts = withContext(Dispatchers.Default) {
        val posts = ArrayList<ChanPost>()

        repliesFromCopy.forEach { replyPostDescriptor ->
          val replyPost = chanThreadManager.findPostByPostDescriptor(replyPostDescriptor)
          if (replyPost != null) {
            posts.add(replyPost)
          }
        }

        return@withContext posts
      }

      if (posts.size <= 0) {
        return@post
      }

      val postViewMode = if (isExternalThread) {
        PostCellData.PostViewMode.ExternalPostsPopup
      } else {
        PostCellData.PostViewMode.RepliesPopup
      }

      threadPresenterCallback?.showPostsPopup(
        threadDescriptor = threadDescriptor,
        postViewMode = postViewMode,
        postDescriptor = post.postDescriptor,
        posts = posts
      )
    }
  }

  override fun onPostPosterIdClicked(post: ChanPost) {
    if (!isBound || currentChanDescriptor == null || post.posterId.isNullOrEmpty()) {
      return
    }

    onMarkerSpanClicked(
      post = post,
      filterFunc = { chanPost -> chanPost.posterId == post.posterId }
    )
  }

  override fun onPostPosterNameClicked(post: ChanPost) {
    if (!isBound || currentChanDescriptor == null || post.name.isNullOrEmpty()) {
      return
    }

    onMarkerSpanClicked(
      post = post,
      filterFunc = { chanPost -> chanPost.name == post.name }
    )
  }

  override fun onPostPosterTripcodeClicked(post: ChanPost) {
    if (!isBound || currentChanDescriptor == null || post.tripcode.isNullOrEmpty()) {
      return
    }

    onMarkerSpanClicked(
      post = post,
      filterFunc = { chanPost -> chanPost.tripcode == post.tripcode }
    )
  }

  private fun onMarkerSpanClicked(post: ChanPost, filterFunc: (ChanPost) -> Boolean) {
    val threadDescriptor = post.postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    val chanThread = chanThreadManager.getChanThread(threadDescriptor)
      ?: return

    val postsOfTheSamePoster = mutableListOf<ChanPost>()

    chanThread.iteratePostsOrdered { chanPost ->
      if (filterFunc(chanPost)) {
        postsOfTheSamePoster += chanPost
      }
    }

    if (postsOfTheSamePoster.isEmpty()) {
      showToast(context, R.string.thread_presenter_no_posts_of_the_same_poster_found)
      return
    }

    if (postsOfTheSamePoster.size == 1) {
      showToast(context, R.string.thread_presenter_only_one_post_of_the_same_poster_found)
      return
    }

    threadPresenterCallback?.showPostsPopup(
      threadDescriptor = threadDescriptor,
      postViewMode = PostCellData.PostViewMode.RepliesPopup,
      postDescriptor = post.postDescriptor,
      posts = postsOfTheSamePoster
    )
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

  override fun getBoardPages(boardDescriptor: BoardDescriptor): BoardPages? {
    return pageRequestManager.getBoardPages(boardDescriptor)
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

  override fun onUnhidePostClick(post: ChanPost, inPopup: Boolean) {
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
        chanLoadOptions = ChanLoadOptions.forceUpdateAllPosts(),
        chanCacheUpdateOptions = ChanCacheUpdateOptions.UpdateCache,
        refreshPostPopupHelperPosts = true
      )
    } else {
      val postsToUpdate = chanThread
        .getPostWithRepliesToThisPost(post.postDescriptor)
        .toSet()

      normalLoad(
        showLoading = false,
        chanLoadOptions = ChanLoadOptions.forceUpdatePosts(postsToUpdate),
        chanCacheUpdateOptions = ChanCacheUpdateOptions.DoNotUpdateCache,
        refreshPostPopupHelperPosts = true
      )
    }
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
            if (chanThreadManager.deletePost(post.postDescriptor)) {
              quickReloadFromMemoryCache(
                chanLoadOptions = ChanLoadOptions.forceUpdatePost(post.postDescriptor)
              )
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
    val text = StringBuilder(128)
    val descriptor = post.postDescriptor.descriptor

    text
      .append("Site name: ")
      .appendLine(descriptor.siteName())
      .append("Board code: ")
      .appendLine(descriptor.boardCode())

    when (descriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        text
          .append("Thread id: ")
          .appendLine(descriptor.threadNo)

        text
          .append("Post id: ")
          .appendLine(post.postNo())

        if (post.postSubNo() > 0) {
          text
            .append("Post sub id: ")
            .appendLine(post.postSubNo())
        }
      }
      is ChanDescriptor.CompositeCatalogDescriptor,
      is ChanDescriptor.CatalogDescriptor -> {
        text
          .append("Thread id: ")
          .appendLine(post.postNo())
      }
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
        .appendLine()
        .append("Thumbnail url: ")
        .appendLine(image.actualThumbnailUrl)
        .append("Full image url: ")
        .appendLine(image.imageUrl)
        .appendLine()
        .append("Dimensions: ")
        .append(image.imageWidth)
        .append("x")
        .appendLine(image.imageHeight)
        .append("Size: ")
        .append(getReadableFileSize(image.size))

      if (image.imageSpoilered && image.isInlined) {
        // all linked files are spoilered, don't say that
        text.append("\nSpoilered")
      }

      text.append("\n")
    }

    text.appendLine()

    text
      .append("Posted: ")
      .append(ChanPostUtils.getLocalDate(post, ChanSettings.postFullDateUseLocalLocale.get()))

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
            .append("\nPosts in this thread: ")
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
        val iconUrl = icon.iconUrl.toString()

        when {
          iconUrl.contains("troll") -> {
            text.append("\nTroll Country: ").append(icon.iconName)
          }
          iconUrl.contains("country") -> {
            text.append("\nCountry: ").append(icon.iconName)
          }
          iconUrl.contains("minileaf") -> {
            text.append("\n4chan Pass Year: ").append(icon.iconName)
          }
          else -> {
            text.append("\nIcon: ").append(icon.iconName)
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

  private suspend fun showPosts(refreshPostPopupHelperPosts: Boolean = false) {
    if (!isBound) {
      Logger.d(TAG, "showPosts() isBound==false")
      return
    }

    val descriptor = currentChanDescriptor
    if (descriptor == null) {
      Logger.d(TAG, "showPosts() currentChanDescriptor==null")
      return
    }

    val order = PostsFilter.Order.find(ChanSettings.boardOrder.get())

    // When processing filters which create new post hides we need to reparse those posts so that
    // their replies have the correct postlinkable types (QUOTE_TO_HIDDEN_OR_REMOVED_POST)
    val additionalPostsToReparse = mutableSetOf<PostDescriptor>()

    threadPresenterCallback?.showPostsForChanDescriptor(
      descriptor = descriptor,
      filter = PostsFilter(
        chanLoadProgressNotifier = chanLoadProgressNotifier,
        postHideHelper = postHideHelper,
        order = order
      ),
      refreshPostPopupHelperPosts = refreshPostPopupHelperPosts,
      additionalPostsToReparse = additionalPostsToReparse
    )

    if (additionalPostsToReparse.isNotEmpty()) {
      // Launch a new coroutine here and wait a little bit because otherwise normalLoad() call
      // will just end right away since it's not supposed to have multiple instances running at
      // the same time
      launch {
        delay(125L)
        Logger.d(TAG, "showPosts() additionalPostsToReparse=${additionalPostsToReparse.size}")

        // God I hope this won't cause an infinite recursion
        reparsePostsWithReplies(additionalPostsToReparse)
      }
    }
  }

  suspend fun reparsePostsWithReplies(
    postDescriptors: Collection<PostDescriptor>,
    func: (suspend (Collection<PostDescriptor>) -> Unit)? = null
  ) {
    val totalPostsWithReplies = withContext(Dispatchers.Default) {
      val totalPostsWithReplies = hashSetWithCap<PostDescriptor>(16)

      postDescriptors.forEach { postDescriptor ->
        totalPostsWithReplies += chanThreadManager.findPostWithReplies(
          postDescriptor = postDescriptor,
          includeRepliesFrom = true,
          includeRepliesTo = true,
          maxRecursion = 1
        ).map { it.postDescriptor }
      }

      return@withContext totalPostsWithReplies
    }

    Logger.d(TAG, "reparsePostsWithReplies() " +
      "postDescriptorsCount=${postDescriptors.size}, " +
      "totalPostsWithRepliesCount=${totalPostsWithReplies.size}")

    func?.invoke(totalPostsWithReplies)

    normalLoad(
      showLoading = false,
      chanLoadOptions = ChanLoadOptions.forceUpdatePosts(totalPostsWithReplies.toSet()),
      chanCacheUpdateOptions = ChanCacheUpdateOptions.DoNotUpdateCache,
      refreshPostPopupHelperPosts = true
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

  fun hideOrRemovePosts(hide: Boolean, wholeChain: Boolean, post: ChanPost) {
    if (!isBound) {
      return
    }

    val descriptor = currentChanDescriptor
      ?: return

    val posts: MutableSet<PostDescriptor> = HashSet()

    if (wholeChain) {
      val foundPosts = chanThreadManager.findPostWithReplies(post.postDescriptor)
        .map { chanPost -> chanPost.postDescriptor }

      posts.addAll(foundPosts)
    } else {
      val foundPost = chanThreadManager.findPostByPostDescriptor(post.postDescriptor)
      if (foundPost != null) {
        posts.add(foundPost.postDescriptor)
      }
    }

    threadPresenterCallback?.hideOrRemovePosts(hide, wholeChain, posts)
  }

  fun showRemovedPostsDialog() {
    if (!isBound) {
      return
    }

    val chanDescriptor = currentChanDescriptor
      ?: return

    threadPresenterCallback?.viewHiddenOrRemovedPosts(chanDescriptor)
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

  fun processDvachPostReport(reason: String, post: ChanPost, site: Site, retrying: Boolean = false) {
    if (reason.isEmpty()) {
      showToast(context, R.string.dvach_report_post_reason_cannot_be_empty)
      return
    }

    launch {
      val postReportData = PostReportData.Dvach(post.postDescriptor, reason)
      showToast(context, R.string.dvach_report_post_sending)

      when (val postReportResult = site.actions().reportPost(postReportData)) {
        is PostReportResult.NotSupported -> {
          showToast(context, R.string.post_report_not_supported)
        }
        is PostReportResult.Success -> {
          showToast(context, getString(R.string.post_reported, post.postDescriptor.userReadableString()))
        }
        is PostReportResult.CaptchaRequired -> {
          // 2ch.hk does not require captcha?
        }
        is PostReportResult.AuthRequired,
        is PostReportResult.CloudFlareDetected -> {
          threadPresenterCallback?.showCaptchaController(
            chanDescriptor = post.postDescriptor.descriptor,
            replyMode = ReplyMode.ReplyModeSendWithoutCaptcha,
            autoReply = false,
            afterPostingAttempt = true,
            onFinished = { success ->
              if (success && !retrying) {
                processDvachPostReport(
                  reason = reason,
                  post = post,
                  site = site,
                  retrying = true
                )
              }
            }
          )
        }
        is PostReportResult.Error -> {
          showToast(context, getString(R.string.dvach_report_post_error, postReportResult.errorMessage))
        }
      }
    }
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
    val displayingPostDescriptorsInThread: List<PostDescriptor>
    val currentPosition: IndexAndTop?

    suspend fun showPostsForChanDescriptor(
      descriptor: ChanDescriptor?,
      filter: PostsFilter,
      refreshPostPopupHelperPosts: Boolean,
      additionalPostsToReparse: MutableSet<PostDescriptor>
    )
    fun postClicked(postDescriptor: PostDescriptor)
    fun hideError(chanDescriptor: ChanDescriptor)
    fun showError(chanDescriptor: ChanDescriptor, error: ChanLoaderException)
    fun showLoading()
    fun showLoading(animateTransition: Boolean)
    fun showEmpty()
    fun showPostInfo(info: String)
    fun showPostLinkables(post: ChanPost, inPopup: Boolean)
    fun clipboardPost(post: ChanPost)
    suspend fun showThread(threadDescriptor: ChanDescriptor.ThreadDescriptor)
    suspend fun showPostInExternalThread(postDescriptor: PostDescriptor)
    suspend fun previewCatalogThread(postDescriptor: PostDescriptor)
    suspend fun openExternalThread(postDescriptor: PostDescriptor, scrollToPost: Boolean)
    suspend fun showCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean)
    suspend fun setCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean)

    suspend fun setCatalogWithSearchQuery(
      catalogDescriptor: ChanDescriptor.ICatalogDescriptor,
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

    fun pushController(controller: Controller)
    fun showImages(chanDescriptor: ChanDescriptor, initialImageUrl: String?, transitionThumbnailUrl: String)
    fun showAlbum(initialImageUrl: HttpUrl?, displayingPostDescriptors: List<PostDescriptor>)
    fun scrollTo(displayPosition: Int, smooth: Boolean)
    fun smoothScrollNewPosts(displayPosition: Int)
    fun filterPostTripcode(tripcode: CharSequence)
    fun filterPostName(posterName: CharSequence)
    fun filterPosterId(postererId: String)
    fun highlightPost(postDescriptor: PostDescriptor?, blink: Boolean)
    fun quote(post: ChanPost, withText: Boolean)
    fun quote(postDescriptor: PostDescriptor, text: CharSequence)
    fun confirmPostDelete(post: ChanPost)
    fun showDeleting()
    fun hideDeleting(message: String)
    fun showNewPostsNotification(show: Boolean, newPostsCount: Int, deletedPostsCount: Int)
    fun showThreadStatusNotification(
      show: Boolean,
      nowSticky: Boolean?,
      nowArchived: Boolean?,
      nowDeleted: Boolean?,
      nowClosed: Boolean?
    )
    fun canShowSnackBar(): Boolean
    fun showImageReencodingWindow(
      fileUuid: UUID,
      chanDescriptor: ChanDescriptor,
      supportsReencode: Boolean
    )

    fun hideThread(post: ChanPost, hide: Boolean)
    fun hideOrRemovePosts(hide: Boolean, wholeChain: Boolean, postDescriptors: Set<PostDescriptor>)
    fun showHideOrRemoveWholeChainDialog(hide: Boolean, hasReplies: Boolean, post: ChanPost)

    fun unhideOrUnremovePost(post: ChanPost)
    fun viewHiddenOrRemovedPosts(chanDescriptor: ChanDescriptor)

    fun onRestoreRemovedPostsClicked(
      chanDescriptor: ChanDescriptor,
      selectedPosts: List<PostDescriptor>
    )

    suspend fun onPostsUpdated(updatedPosts: List<ChanPost>)

    fun isAlreadyPresentingController(predicate: (Controller) -> Boolean): Boolean
    fun presentController(controller: Controller, animate: Boolean)
    fun showToolbar()
    fun showAvailableArchivesList(postDescriptor: PostDescriptor, preview: Boolean)
    fun currentSpanCount(): Int
    fun getTopPostRepliesDataOrNull(): PostPopupHelper.PostPopupData?
    fun openFiltersController(chanFilterMutable: ChanFilterMutable)

    fun showCaptchaController(
      chanDescriptor: ChanDescriptor,
      replyMode: ReplyMode,
      autoReply: Boolean,
      afterPostingAttempt: Boolean,
      onFinished: ((Boolean) -> Unit)? = null
    )
  }

  companion object {
    private const val TAG = "ThreadPresenter"
    private const val POST_OPTION_QUOTE = 0
    private const val POST_OPTION_QUOTE_TEXT = 1
    private const val POST_OPTION_INFO = 2
    private const val POST_OPTION_LINKS = 3
    private const val POST_OPTION_COPY_TEXT = 4
    private const val POST_OPTION_REPORT = 5
    private const val POST_OPTION_DELETE = 7
    private const val POST_OPTION_SAVE = 8
    private const val POST_OPTION_BOOKMARK = 9
    private const val POST_OPTION_SHARE = 10
    private const val POST_OPTION_HIDE = 12
    private const val POST_OPTION_OPEN_BROWSER = 13
    private const val POST_OPTION_OPEN_IN_ARCHIVE = 14
    private const val POST_OPTION_PREVIEW_IN_ARCHIVE = 16
    private const val POST_OPTION_REMOVE = 17
    private const val POST_OPTION_APPLY_THEME = 18
    private const val POST_OPTION_ADD_TO_NAV_HISTORY = 21

    private const val POST_OPTION_FILTER_TRIPCODE = 100
    private const val POST_OPTION_FILTER_NAME = 101
    private const val POST_OPTION_FILTER_POSTER_ID = 102

    private const val POST_OPTION_APPLY_THEME_IDX = 1000
    // Let's assume a post cannot contain more than 500 themes
    private const val POST_OPTION_APPLY_THEME_IDX_MAX = 1500

    private const val COPY_LINK_TEXT = 2000
    private const val COPY_LINK_VALUE = 2001
    private const val SHOW_POST_MENU_OPTIONS = 2002

    private const val POST_LINKABLE_LONG_CLICK_MENU_HEADER = "post_linkable_long_click_menu_header"

    const val SCROLL_TO_POST_DELAY_MS = 125L
  }

}