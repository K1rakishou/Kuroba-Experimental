package com.github.k1rakishou.chan.features.drawer

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.drawer.data.HistoryControllerState
import com.github.k1rakishou.chan.features.drawer.data.NavHistoryBookmarkAdditionalInfo
import com.github.k1rakishou.chan.features.drawer.data.NavigationHistoryEntry
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.toHashSetBy
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.navigation.NavHistoryElement
import com.github.k1rakishou.model.util.ChanPostUtils
import dagger.Lazy
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class MainControllerViewModel : BaseViewModel() {
  private val isDevFlavor = isDevBuild()

  @Inject
  lateinit var _historyNavigationManager: Lazy<HistoryNavigationManager>
  @Inject
  lateinit var _siteManager: Lazy<SiteManager>
  @Inject
  lateinit var _bookmarksManager: Lazy<BookmarksManager>
  @Inject
  lateinit var _pageRequestManager: Lazy<PageRequestManager>
  @Inject
  lateinit var _archivesManager: Lazy<ArchivesManager>
  @Inject
  lateinit var _chanThreadManager: Lazy<ChanThreadManager>

  private val historyNavigationManager: HistoryNavigationManager
    get() = _historyNavigationManager.get()
  private val siteManager: SiteManager
    get() = _siteManager.get()
  private val bookmarksManager: BookmarksManager
    get() = _bookmarksManager.get()
  private val pageRequestManager: PageRequestManager
    get() = _pageRequestManager.get()
  private val archivesManager: ArchivesManager
    get() = _archivesManager.get()
  private val chanThreadManager: ChanThreadManager
    get() = _chanThreadManager.get()

  val historyControllerState = mutableStateOf<HistoryControllerState>(HistoryControllerState.Loading)
  val navigationHistoryEntryList = mutableStateListOf<NavigationHistoryEntry>()

  private val bookmarksBadgeStateSubject = BehaviorProcessor.createDefault(BookmarksBadgeState(0, false))

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  @OptIn(ExperimentalTime::class)
  override suspend fun onViewModelReady() {
    mainScope.launch {
      historyNavigationManager.navigationStackUpdatesFlow
        .collect { updateEvent ->
          withContext(Dispatchers.Main) { onNavigationStackUpdated(updateEvent) }
        }
    }

    mainScope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .debounce(Duration.seconds(1))
        .collect { bookmarkChange ->
          bookmarksManager.awaitUntilInitialized()

          updateBadge()
          onBookmarkUpdated(bookmarkChange)
        }
    }

    reloadNavigationHistory()
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

  suspend fun deleteNavElement(descriptor: ChanDescriptor) {
    historyNavigationManager.deleteNavElement(descriptor)
  }

  suspend fun pinOrUnpin(descriptor: ChanDescriptor): HistoryNavigationManager.PinResult {
    return historyNavigationManager.pinOrUnpin(descriptor)
  }

  suspend fun reloadNavigationHistory() {
    ModularResult.Try {
      return@Try withContext(Dispatchers.Default) {
        historyControllerState.value = HistoryControllerState.Loading

        siteManager.awaitUntilInitialized()
        bookmarksManager.awaitUntilInitialized()

        val navHistoryList = historyNavigationManager.getAll()
          .mapNotNull { navigationElement -> navHistoryElementToNavigationHistoryEntryOrNull(navigationElement) }

        navigationHistoryEntryList.clear()
        navigationHistoryEntryList.addAll(navHistoryList)
      }
    }
      .peekError { error ->
        Logger.e(TAG, "loadNavigationHistoryInitial() error", error)
        historyControllerState.value = HistoryControllerState.Error(error.errorMessageOrClassName())
      }
      .peekValue {
        Logger.d(TAG, "loadNavigationHistoryInitial() success")
        historyControllerState.value = HistoryControllerState.Data
      }
      .ignore()
  }

  private fun navHistoryElementToNavigationHistoryEntryOrNull(
    navigationElement: NavHistoryElement,
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

    val additionalInfo = if (canShowBookmarkInfo(descriptor, isSiteArchive)) {
      val threadDescriptor = descriptor as ChanDescriptor.ThreadDescriptor

      bookmarksManager.mapBookmark(threadDescriptor) { threadBookmarkView ->
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
    descriptor: ChanDescriptor,
    isSiteArchive: Boolean
  ) = ChanSettings.watchEnabled.get() && descriptor is ChanDescriptor.ThreadDescriptor && !isSiteArchive

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

  private suspend fun onBookmarkUpdated(bookmarkChange: BookmarksManager.BookmarkChange) {
    if (bookmarkChange is BookmarksManager.BookmarkChange.BookmarksInitialized) {
      return
    }

    val chanDescriptorsSet = when (bookmarkChange) {
      is BookmarksManager.BookmarkChange.BookmarksDeleted -> {
        bookmarkChange.threadDescriptors.toSet()
      }
      is BookmarksManager.BookmarkChange.BookmarksCreated -> {
        val toUpdate = mutableSetOf<ChanDescriptor>()
        val toCreate = mutableSetOf<HistoryNavigationManager.NewNavigationElement>()

        val newNavigationElements = bookmarkChange.threadDescriptors
          .mapNotNull { threadDescriptor -> createNewNavigationElement(threadDescriptor) }

        newNavigationElements.forEach { newNavigationElement ->
          if (historyNavigationManager.contains(newNavigationElement.descriptor)) {
            toUpdate += newNavigationElement.descriptor
          } else {
            toCreate += newNavigationElement
          }
        }

        if (toCreate.isNotEmpty()) {
          historyNavigationManager.createNewNavElements(
            newNavigationElements = toCreate,
            canInsertAtTheBeginning = false
          )
        }

        toUpdate
      }
      is BookmarksManager.BookmarkChange.BookmarksUpdated -> {
        val bookmarkDescriptorsSet = mutableSetOf<ChanDescriptor.ThreadDescriptor>()

        if (bookmarkChange.threadDescriptors != null) {
          bookmarkDescriptorsSet.addAll(bookmarkChange.threadDescriptors)
        }

        bookmarkDescriptorsSet
      }
      else -> error("Must not be called")
    }

    if (chanDescriptorsSet.isEmpty()) {
      return
    }

    val toUpdate = mutableListOf<Pair<Int, NavigationHistoryEntry>>()

    navigationHistoryEntryList.forEachIndexed { index, navigationHistoryEntry ->
      if (navigationHistoryEntry.descriptor !in chanDescriptorsSet) {
        return@forEachIndexed
      }

      val navHistoryElement = historyNavigationManager.getNavHistoryElementByDescriptor(navigationHistoryEntry.descriptor)
        ?: return@forEachIndexed

      val updatedNavigationHistoryEntry = navHistoryElementToNavigationHistoryEntryOrNull(navHistoryElement)
        ?: return@forEachIndexed

      toUpdate += Pair(index, updatedNavigationHistoryEntry)
    }

    toUpdate.forEach { (index, navigationHistoryEntry) ->
      navigationHistoryEntryList[index] = navigationHistoryEntry
    }
  }

  private suspend fun onNavigationStackUpdated(updateEvent: HistoryNavigationManager.UpdateEvent) {
    try {
      when (updateEvent) {
        HistoryNavigationManager.UpdateEvent.Initialized -> {
          // no-op
        }
        is HistoryNavigationManager.UpdateEvent.Created -> {
          val toCreate = updateEvent.navHistoryElements
            .filter { navHistoryElement ->
              return@filter historyNavigationManager.canCreateNavElement(
                bookmarksManager = bookmarksManager,
                chanDescriptor = navHistoryElement.descriptor()
              )
            }

          if (toCreate.isEmpty()) {
            return
          }

          historyNavigationManager.doWithLockedNavStack { navStack ->
            toCreate.forEach { navHistoryElement ->
              val navHistoryElementIndex = navStack.indexOf(navHistoryElement)
              if (navHistoryElementIndex < 0) {
                return@forEach
              }

              val navigationHistoryEntry = navHistoryElementToNavigationHistoryEntryOrNull(navHistoryElement)
                ?: return@forEach

              if (navHistoryElementIndex >= navigationHistoryEntryList.size) {
                navigationHistoryEntryList.add(navigationHistoryEntry)
                return@forEach
              }

              navigationHistoryEntryList.add(navHistoryElementIndex, navigationHistoryEntry)
            }
          }
        }
        is HistoryNavigationManager.UpdateEvent.Deleted -> {
          val descriptorsToDelete = updateEvent.navHistoryElements
            .toHashSetBy { navHistoryElement -> navHistoryElement.descriptor() }

          navigationHistoryEntryList.mutableIteration { mutableIterator, navigationHistoryEntry ->
            if (navigationHistoryEntry.descriptor in descriptorsToDelete) {
              mutableIterator.remove()
            }

            return@mutableIteration true
          }
        }
        is HistoryNavigationManager.UpdateEvent.Moved -> {
          historyNavigationManager.doWithLockedNavStack { navStack ->
            val movedTo = navStack.indexOf(updateEvent.navHistoryElement)
            if (movedTo < 0 || movedTo >= navigationHistoryEntryList.size) {
              return@doWithLockedNavStack
            }

            val movedFrom = navigationHistoryEntryList.indexOfFirst { navigationHistoryEntry ->
              navigationHistoryEntry.descriptor == updateEvent.navHistoryElement.descriptor()
            }

            if (movedFrom < 0 || movedFrom >= navigationHistoryEntryList.size) {
              return@doWithLockedNavStack
            }

            navigationHistoryEntryList.add(movedTo, navigationHistoryEntryList.removeAt(movedFrom))
          }
        }
        is HistoryNavigationManager.UpdateEvent.PinnedOrUnpinned -> {
          historyNavigationManager.doWithLockedNavStack { navStack ->
            val newNavHistoryElementIndex = navStack.indexOf(updateEvent.navHistoryElement)
            if (newNavHistoryElementIndex < 0 || newNavHistoryElementIndex >= navigationHistoryEntryList.size) {
              return@doWithLockedNavStack
            }

            val oldNavHistoryElementIndex = navigationHistoryEntryList.indexOfFirst { navigationHistoryEntry ->
              navigationHistoryEntry.descriptor == updateEvent.navHistoryElement.descriptor()
            }

            if (oldNavHistoryElementIndex < 0) {
              return@doWithLockedNavStack
            }

            val navigationHistoryEntry = navHistoryElementToNavigationHistoryEntryOrNull(updateEvent.navHistoryElement)
              ?: return@doWithLockedNavStack

            navigationHistoryEntryList.removeAt(oldNavHistoryElementIndex)
            navigationHistoryEntryList.add(newNavHistoryElementIndex, navigationHistoryEntry)
          }
        }
        HistoryNavigationManager.UpdateEvent.Cleared -> {
          navigationHistoryEntryList.clear()
        }
      }

      historyControllerState.value = HistoryControllerState.Data
    } catch (error: Throwable) {
      historyControllerState.value = HistoryControllerState.Error(errorText = error.errorMessageOrClassName())
    }
  }

  data class BookmarksBadgeState(
    val totalUnseenPostsCount: Int,
    val hasUnreadReplies: Boolean
  )

  companion object {
    private const val TAG = "DrawerPresenter"
  }

}