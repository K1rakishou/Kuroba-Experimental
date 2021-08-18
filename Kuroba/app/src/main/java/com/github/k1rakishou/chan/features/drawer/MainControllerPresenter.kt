package com.github.k1rakishou.chan.features.drawer

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.drawer.data.HistoryControllerState
import com.github.k1rakishou.chan.features.drawer.data.NavHistoryBookmarkAdditionalInfo
import com.github.k1rakishou.chan.features.drawer.data.NavigationHistoryEntry
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.navigation.NavHistoryElement
import com.github.k1rakishou.model.util.ChanPostUtils
import dagger.Lazy
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

class MainControllerPresenter(
  private val isDevFlavor: Boolean,
  private val historyNavigationManager: HistoryNavigationManager,
  private val siteManager: SiteManager,
  private val bookmarksManager: BookmarksManager,
  private val pageRequestManager: Lazy<PageRequestManager>,
  private val archivesManager: ArchivesManager,
  private val chanThreadManager: ChanThreadManager
) : BasePresenter<MainControllerView>() {

  private val _historyControllerStateFlow = MutableStateFlow<HistoryControllerState>(HistoryControllerState.Empty)
  val historyControllerStateFlow: StateFlow<HistoryControllerState>
    get() = _historyControllerStateFlow.asStateFlow()

  private val navHistoryReloadPending = AtomicBoolean(false)
  private val drawerOpened = AtomicBoolean(false)
  private val bookmarksBadgeStateSubject = BehaviorProcessor.createDefault(BookmarksBadgeState(0, false))
  private val reloadNavHistoryDebouncer = DebouncingCoroutineExecutor(scope)

  @OptIn(ExperimentalTime::class)
  override fun onCreate(view: MainControllerView) {
    super.onCreate(view)

    scope.launch {
      setState(HistoryControllerState.Loading)

      // If we somehow managed to get here twice (due to some Android weirdness) we need to manually
      // reload navigation history, otherwise we will be stuck in Loading state until something
      // updates the nav history.
      if (historyNavigationManager.isReady()) {
        reloadNavigationHistory()
      }

      historyNavigationManager.listenForNavigationStackChanges()
        .asFlow()
        .collect {
          onShouldReloadNavigationHistory()
        }
    }

    scope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .debounce(1.seconds)
        .collect { bookmarkChange ->
          bookmarksManager.awaitUntilInitialized()

          updateBadge()
          handleEvents(bookmarkChange)

          onShouldReloadNavigationHistory()
        }
    }
  }

  private fun handleEvents(bookmarkChange: BookmarksManager.BookmarkChange?) {
    if (bookmarkChange is BookmarksManager.BookmarkChange.BookmarksCreated) {
      val newNavigationElements = bookmarkChange.threadDescriptors
        .mapNotNull { threadDescriptor -> createNewNavigationElement(threadDescriptor) }

      historyNavigationManager.createNewNavElements(
        newNavigationElements = newNavigationElements,
        canInsertAtTheBeginning = false
      )

      return
    }

    if (bookmarkChange is BookmarksManager.BookmarkChange.BookmarksDeleted) {
      historyNavigationManager.removeNavElements(bookmarkChange.threadDescriptors)
    }
  }

  fun mapBookmarksIntoNewNavigationElements(): List<HistoryNavigationManager.NewNavigationElement> {
    return bookmarksManager.mapNotNullAllBookmarks { threadBookmarkView ->
      createNewNavigationElement(threadBookmarkView.threadDescriptor)
    }
  }

  private fun createNewNavigationElement(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): HistoryNavigationManager.NewNavigationElement? {
    if (!historyNavigationManager.canCreateNavElement(bookmarksManager, threadDescriptor)) {
      return null
    }

    val chanOriginalPost = chanThreadManager.getChanThread(threadDescriptor)
      ?.getOriginalPost()

    var opThumbnailUrl: HttpUrl? = null
    var title: String? = null

    if (chanOriginalPost != null) {
      opThumbnailUrl = chanThreadManager.getChanThread(threadDescriptor)
        ?.getOriginalPost()
        ?.firstImage()
        ?.actualThumbnailUrl

      title = ChanPostUtils.getTitle(
        chanOriginalPost,
        threadDescriptor
      )
    } else {
      bookmarksManager.viewBookmark(threadDescriptor) { threadBookmarkView ->
        opThumbnailUrl = threadBookmarkView.thumbnailUrl
        title = threadBookmarkView.title
      }
    }

    if (opThumbnailUrl == null || title.isNullOrEmpty()) {
      return null
    }

    return HistoryNavigationManager.NewNavigationElement(
      threadDescriptor,
      opThumbnailUrl!!,
      title!!
    )
  }

  fun onThemeChanged() {
    updateBadge()
  }

  fun listenForBookmarksBadgeStateChanges(): Flowable<BookmarksBadgeState> {
    return bookmarksBadgeStateSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .hide()
  }

  fun deleteNavElement(descriptor: ChanDescriptor) {
    if (descriptor is ChanDescriptor.ThreadDescriptor) {
      if (bookmarksManager.exists(descriptor)) {
        bookmarksManager.deleteBookmark(descriptor)
      }
    }

    historyNavigationManager.deleteNavElement(descriptor)
  }

  fun pinOrUnpin(descriptor: ChanDescriptor): HistoryNavigationManager.PinResult {
    return historyNavigationManager.pinOrUnpin(descriptor)
  }

  fun onDrawerOpened() {
    drawerOpened.set(true)

    if (navHistoryReloadPending.compareAndSet(true, false)) {
      reloadNavigationHistory(useDebouncer = false)
    }
  }

  fun onDrawerClosed() {
    drawerOpened.set(false)
  }

  private fun onShouldReloadNavigationHistory() {
    if (drawerOpened.get()) {
      navHistoryReloadPending.set(false)
      reloadNavigationHistory()
      return
    }

    navHistoryReloadPending.set(true)
  }

  fun reloadNavigationHistory(useDebouncer: Boolean = true) {
    if (useDebouncer) {
      reloadNavHistoryDebouncer.post(HISTORY_NAV_ELEMENTS_DEBOUNCE_TIMEOUT_MS) {
        reloadInternal()
      }
    } else {
      scope.launch {
        reloadInternal()
      }
    }
  }

  private suspend fun reloadInternal() {
    ModularResult.Try { withContext(Dispatchers.Default) { showNavigationHistoryInternal() } }
      .peekError { error ->
        Logger.e(TAG, "showNavigationHistoryInternal() error", error)
        setState(HistoryControllerState.Error(error.errorMessageOrClassName()))
      }
      .ignore()
  }

  private suspend fun showNavigationHistoryInternal() {
    historyNavigationManager.awaitUntilInitialized()
    siteManager.awaitUntilInitialized()
    bookmarksManager.awaitUntilInitialized()

    val isWatcherEnabled = ChanSettings.watchEnabled.get()

    val navHistoryList = historyNavigationManager.getAll()
      .mapNotNull { navigationElement -> createNavHistoryElementOrNull(navigationElement, isWatcherEnabled) }

    if (navHistoryList.isEmpty()) {
      setState(HistoryControllerState.Empty)
      return
    }

    val newState = HistoryControllerState.Data(navHistoryList)

    setState(newState)
  }

  private fun createNavHistoryElementOrNull(
    navigationElement: NavHistoryElement,
    isWatcherEnabled: Boolean
  ): NavigationHistoryEntry? {
    val siteDescriptor = when (navigationElement) {
      is NavHistoryElement.Catalog -> navigationElement.descriptor.siteDescriptor()
      is NavHistoryElement.Thread -> navigationElement.descriptor.siteDescriptor()
    }

    val siteEnabled = siteManager.bySiteDescriptor(siteDescriptor)?.enabled() ?: false
    if (!siteEnabled) {
      return null
    }

    val descriptor = when (navigationElement) {
      is NavHistoryElement.Catalog -> navigationElement.descriptor
      is NavHistoryElement.Thread -> navigationElement.descriptor
    }

    val canCreateNavElement = historyNavigationManager.canCreateNavElement(
      bookmarksManager,
      descriptor
    )

    if (!canCreateNavElement) {
      return null
    }

    val isSiteArchive = archivesManager.isSiteArchive(descriptor.siteDescriptor())

    val additionalInfo = if (canShowBookmarkInfo(isWatcherEnabled, descriptor, isSiteArchive)) {
      val threadDescriptor = descriptor as ChanDescriptor.ThreadDescriptor

      bookmarksManager.mapBookmark(threadDescriptor) { threadBookmarkView ->
        val boardPage = pageRequestManager.get().getPage(threadBookmarkView.threadDescriptor)

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

    return NavigationHistoryEntry(
      descriptor = descriptor,
      threadThumbnailUrl = navigationElement.navHistoryElementInfo.thumbnailUrl,
      siteThumbnailUrl = siteThumbnailUrl,
      title = navigationElement.navHistoryElementInfo.title,
      pinned = navigationElement.navHistoryElementInfo.pinned,
      additionalInfo = additionalInfo
    )
  }

  private fun canShowBookmarkInfo(
    isWatcherEnabled: Boolean,
    descriptor: ChanDescriptor,
    isSiteArchive: Boolean
  ) = isWatcherEnabled && descriptor is ChanDescriptor.ThreadDescriptor && !isSiteArchive

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
    _historyControllerStateFlow.value = state
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