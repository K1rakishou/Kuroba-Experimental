package com.github.k1rakishou.chan.features.drawer

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.drawer.data.HistoryControllerState
import com.github.k1rakishou.chan.features.drawer.data.NavHistoryBookmarkAdditionalInfo
import com.github.k1rakishou.chan.features.drawer.data.NavigationHistoryEntry
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.navigation.NavHistoryElement
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

class DrawerPresenter(
  private val isDevFlavor: Boolean,
  private val historyNavigationManager: HistoryNavigationManager,
  private val siteManager: SiteManager,
  private val bookmarksManager: BookmarksManager,
  private val pageRequestManager: PageRequestManager,
  private val archivesManager: ArchivesManager
) : BasePresenter<DrawerView>() {

  private val historyControllerStateSubject = PublishProcessor.create<HistoryControllerState>()
    .toSerialized()
  private val bookmarksBadgeStateSubject = BehaviorProcessor.createDefault(BookmarksBadgeState(0, false))

  private val reloadNavHistoryDebouncer = DebouncingCoroutineExecutor(scope)

  @OptIn(ExperimentalTime::class)
  override fun onCreate(view: DrawerView) {
    super.onCreate(view)

    scope.launch {
      setState(HistoryControllerState.Loading)

      historyNavigationManager.listenForNavigationStackChanges()
        .asFlow()
        .collect { showNavigationHistory() }
    }

    scope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .onBackpressureLatest()
        .asFlow()
        .debounce(1.seconds)
        .collect { bookmarkChange ->
          bookmarksManager.awaitUntilInitialized()

          updateBadge()
          reloadNavigationHistory(bookmarkChange)
        }
    }
  }

  fun onThemeChanged() {
    updateBadge()
  }

  private fun reloadNavigationHistory(bookmarkChange: BookmarksManager.BookmarkChange) {
    val bookmarkThreadDescriptors = bookmarkChange.threadDescriptorsOrNull()?.toSet()

    val retainedThreadDescriptors = if (bookmarkThreadDescriptors == null) {
      null
    } else {
      historyNavigationManager.retainExistingThreadDescriptors(bookmarkThreadDescriptors)
    }

    // retainedThreadDescriptors == null means all bookmarks had changed (bookmarkChange is
    // Initialization change)
    if (retainedThreadDescriptors != null && retainedThreadDescriptors.isEmpty()) {
      return
    }

    showNavigationHistory()
  }

  fun listenForStateChanges(): Flowable<HistoryControllerState> {
    return historyControllerStateSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .distinctUntilChanged()
      .hide()
  }

  fun listenForBookmarksBadgeStateChanges(): Flowable<BookmarksBadgeState> {
    return bookmarksBadgeStateSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .hide()
  }

  fun isCurrentlyVisible(descriptor: ChanDescriptor): Boolean {
    return historyNavigationManager.isAtTop(descriptor)
  }

  fun onNavElementSwipedAway(descriptor: ChanDescriptor) {
    historyNavigationManager.onNavElementRemoved(descriptor)
  }

  private fun showNavigationHistory() {
    reloadNavHistoryDebouncer.post(HISTORY_NAV_ELEMENTS_DEBOUNCE_TIMEOUT_MS) {
      ModularResult.Try { showNavigationHistoryInternal() }.safeUnwrap { error ->
        Logger.e(TAG, "showNavigationHistoryInternal() error", error)
        setState(HistoryControllerState.Error(error.errorMessageOrClassName()))

        return@post
      }
    }
  }

  private suspend fun showNavigationHistoryInternal() {
    BackgroundUtils.ensureMainThread()
    historyNavigationManager.awaitUntilInitialized()

    val isWatcherEnabled = ChanSettings.watchEnabled.get()

    val navHistoryList = historyNavigationManager.getAll().mapNotNull { navigationElement ->
      val siteDescriptor = when (navigationElement) {
        is NavHistoryElement.Catalog -> navigationElement.descriptor.siteDescriptor()
        is NavHistoryElement.Thread -> navigationElement.descriptor.siteDescriptor()
      }

      val siteEnabled = siteManager.bySiteDescriptor(siteDescriptor)?.enabled() ?: false
      if (!siteEnabled) {
        return@mapNotNull null
      }

      val descriptor = when (navigationElement) {
        is NavHistoryElement.Catalog -> navigationElement.descriptor
        is NavHistoryElement.Thread -> navigationElement.descriptor
      }

      val isSiteArchive = archivesManager.isSiteArchive(descriptor.siteDescriptor())

      val additionalInfo = if (isWatcherEnabled && descriptor is ChanDescriptor.ThreadDescriptor && !isSiteArchive) {
        bookmarksManager.mapBookmark(descriptor) { threadBookmarkView ->
          val boardPage = pageRequestManager.getPage(threadBookmarkView.threadDescriptor)

          return@mapBookmark NavHistoryBookmarkAdditionalInfo(
            watching = threadBookmarkView.isWatching(),
            newPosts = threadBookmarkView.newPostsCount(),
            newQuotes = threadBookmarkView.newQuotesCount(),
            isBumpLimit = threadBookmarkView.isBumpLimit(),
            isImageLimit = threadBookmarkView.isImageLimit(),
            isLastPage = boardPage?.isLastPage() ?: false,
          )
        }
      } else {
        null
      }

      val siteThumbnailUrl = if (descriptor is ChanDescriptor.ThreadDescriptor) {
        siteManager.bySiteDescriptor(siteDescriptor)?.icon()?.url
      } else {
        null
      }

      return@mapNotNull NavigationHistoryEntry(
        descriptor,
        navigationElement.navHistoryElementInfo.thumbnailUrl,
        siteThumbnailUrl,
        navigationElement.navHistoryElementInfo.title,
        additionalInfo
      )
    }

    if (navHistoryList.isEmpty()) {
      setState(HistoryControllerState.Empty)
      return
    }

    setState(HistoryControllerState.Data(navHistoryList))
  }

  fun updateBadge() {
    if (!bookmarksManager.isReady()) {
      return
    }

    val totalUnseenPostsCount = bookmarksManager.getTotalUnseenPostsCount()
    val hasUnreadReplies = bookmarksManager.hasUnreadReplies()

    if (isDevFlavor && totalUnseenPostsCount == 0) {
      check(!hasUnreadReplies) { "Bookmarks have no unread posts but have unseen replies!" }
    }

    bookmarksBadgeStateSubject.onNext(BookmarksBadgeState(totalUnseenPostsCount, hasUnreadReplies))
  }

  private fun setState(state: HistoryControllerState) {
    historyControllerStateSubject.onNext(state)
  }

  data class BookmarksBadgeState(
    val totalUnseenPostsCount: Int,
    val hasUnreadReplies: Boolean
  )

  companion object {
    private const val TAG = "DrawerPresenter"
    private const val HISTORY_NAV_ELEMENTS_DEBOUNCE_TIMEOUT_MS = 100L
  }

}