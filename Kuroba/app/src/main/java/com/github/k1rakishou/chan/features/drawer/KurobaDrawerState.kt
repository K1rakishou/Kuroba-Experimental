package com.github.k1rakishou.chan.features.drawer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.drawer.data.HistoryControllerState
import com.github.k1rakishou.chan.features.drawer.data.NavHistoryBookmarkAdditionalInfo
import com.github.k1rakishou.chan.features.drawer.data.NavigationHistoryEntry
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.indexedIteration
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.toHashSetBy
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.navigation.NavHistoryElement
import com.github.k1rakishou.model.util.ChanPostUtils
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

@Stable
class KurobaDrawerState(
  private val siteManagerLazy: Lazy<SiteManager>,
  private val historyNavigationManagerLazy: Lazy<HistoryNavigationManager>,
  private val bookmarksManagerLazy: Lazy<BookmarksManager>,
  private val archivesManagerLazy: Lazy<ArchivesManager>,
  private val chanThreadManagerLazy: Lazy<ChanThreadManager>,
  private val pageRequestManagerLazy: Lazy<PageRequestManager>
) {
  private val siteManager: SiteManager
    get() = siteManagerLazy.get()
  private val historyNavigationManager: HistoryNavigationManager
    get() = historyNavigationManagerLazy.get()
  private val bookmarksManager: BookmarksManager
    get() = bookmarksManagerLazy.get()
  private val archivesManager: ArchivesManager
    get() = archivesManagerLazy.get()
  private val chanThreadManager: ChanThreadManager
    get() = chanThreadManagerLazy.get()
  private val pageRequestManager: PageRequestManager
    get() = pageRequestManagerLazy.get()

  private val _historyControllerState = mutableStateOf<HistoryControllerState>(HistoryControllerState.Loading)
  val historyControllerState: State<HistoryControllerState>
    get() = _historyControllerState

  private var _showDeleteButtonShortcut = mutableStateOf(ChanSettings.drawerShowDeleteButtonShortcut.get())
  val showDeleteButtonShortcut: State<Boolean>
    get() = _showDeleteButtonShortcut

  private val _navigationHistoryEntryList = mutableStateListOf<NavigationHistoryEntry>()
  val navigationHistoryEntryList: List<NavigationHistoryEntry>
    get() = _navigationHistoryEntryList

  private val _selectedHistoryEntries = mutableStateMapOf<ChanDescriptor, Unit>()
  val selectedHistoryEntries: Map<ChanDescriptor, Unit>
    get() = _selectedHistoryEntries

  private val _resetScrollPositionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val resetScrollPositionEvent: SharedFlow<Unit>
    get() = _resetScrollPositionEvents.asSharedFlow()

  val searchTextFieldState = TextFieldState(initialText = "")

  val drawerGridMode = mutableStateOf(ChanSettings.drawerGridMode.get())

  private val _drawerOpenedState = mutableStateOf(false)
  val drawerOpenedState: State<Boolean>
    get() = _drawerOpenedState

  fun updateDrawerOpened(opened: Boolean) {
    _drawerOpenedState.value = opened
  }

  fun updateHistoryControllerState(state: HistoryControllerState) {
    _historyControllerState.value = state
  }

  fun getSelectedDescriptors(): List<ChanDescriptor> {
    return _selectedHistoryEntries.keys.toList()
  }

  fun updateDeleteButtonShortcut(show: Boolean) {
    _showDeleteButtonShortcut.value = show
  }

  fun updateNavigationHistoryEntryList(navigationHistoryEntryList: List<NavigationHistoryEntry>) {
    _navigationHistoryEntryList.clear()
    _navigationHistoryEntryList.addAll(navigationHistoryEntryList)
  }

  fun selectUnselect(navHistoryEntry: NavigationHistoryEntry, select: Boolean) {
    if (select) {
      _selectedHistoryEntries.put(navHistoryEntry.descriptor, Unit)
    } else {
      _selectedHistoryEntries.remove(navHistoryEntry.descriptor)
    }
  }

  fun toggleSelection(navHistoryEntry: NavigationHistoryEntry) {
    if (_selectedHistoryEntries.containsKey(navHistoryEntry.descriptor)) {
      _selectedHistoryEntries.remove(navHistoryEntry.descriptor)
    } else {
      _selectedHistoryEntries.put(navHistoryEntry.descriptor, Unit)
    }
  }

  fun selectAll() {
    val allNavigationHistoryEntries = _navigationHistoryEntryList
      .filter { navigationHistoryEntry ->
        return@filter historyNavigationManager.canCreateNavElement(
          bookmarksManager = bookmarksManager,
          chanDescriptor = navigationHistoryEntry.descriptor
        )
      }
      .map { navigationHistoryEntry -> Pair(navigationHistoryEntry.descriptor, Unit) }

    _selectedHistoryEntries.putAll(allNavigationHistoryEntries)
  }

  fun clearSelection() {
    _selectedHistoryEntries.clear()
  }

  suspend fun reloadNavigationHistory() {
    ModularResult.Try {
      val navigationHistoryList = withContext(Dispatchers.Default) {
        updateHistoryControllerState(HistoryControllerState.Loading)

        siteManager.awaitUntilInitialized()
        bookmarksManager.awaitUntilInitialized()

        return@withContext historyNavigationManager.getAll()
          .mapNotNull { navigationElement -> navHistoryElementToNavigationHistoryEntryOrNull(navigationElement) }
      }

      updateNavigationHistoryEntryList(navigationHistoryList)
    }
      .onError { error ->
        Logger.e(TAG, "loadNavigationHistoryInitial() error", error)
        updateHistoryControllerState(HistoryControllerState.Error(error.errorMessageOrClassName()))
      }
      .onSuccess {
        Logger.d(TAG, "loadNavigationHistoryInitial() success")
        updateHistoryControllerState(HistoryControllerState.Data)
      }
      .ignore()
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

  suspend fun onBookmarksUpdated(bookmarkChange: BookmarksManager.BookmarkChange) {
    BackgroundUtils.ensureMainThread()

    if (bookmarkChange is BookmarksManager.BookmarkChange.BookmarksInitialized) {
      return
    }

    val chanDescriptorsSet = when (bookmarkChange) {
      is BookmarksManager.BookmarkChange.BookmarksDeleted -> {
        val deletedBookmarks = bookmarkChange.threadDescriptors.toSet()

        if (deletedBookmarks.isNotEmpty()) {
          historyNavigationManager.onBookmarksDeleted(deletedBookmarks)
        }

        deletedBookmarks
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

    _navigationHistoryEntryList.indexedIteration { index, navigationHistoryEntry ->
      if (navigationHistoryEntry.descriptor !in chanDescriptorsSet) {
        return@indexedIteration true
      }

      val navHistoryElement = historyNavigationManager.getNavHistoryElementByDescriptor(navigationHistoryEntry.descriptor)
        ?: return@indexedIteration true

      val updatedNavigationHistoryEntry = navHistoryElementToNavigationHistoryEntryOrNull(navHistoryElement)
        ?: return@indexedIteration true

      toUpdate += Pair(index, updatedNavigationHistoryEntry)
      return@indexedIteration true
    }

    toUpdate.forEach { (index, navigationHistoryEntry) ->
      _navigationHistoryEntryList[index] = navigationHistoryEntry
    }
  }

  private fun navHistoryElementToNavigationHistoryEntryOrNull(
    navigationElement: NavHistoryElement,
  ): NavigationHistoryEntry? {
    val descriptor = when (navigationElement) {
      is NavHistoryElement.CompositeCatalog -> navigationElement.descriptor
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

    val isSiteArchive = if (descriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      false
    } else {
      archivesManager.isSiteArchive(descriptor.siteDescriptor())
    }

    val additionalInfo = if (canShowBookmarkInfo(descriptor, isSiteArchive)) {
      val threadDescriptor = descriptor as ChanDescriptor.ThreadDescriptor

      bookmarksManager.mapBookmark(threadDescriptor) { threadBookmarkView ->
        val boardPage = pageRequestManager.getPage(
          threadDescriptor = threadBookmarkView.threadDescriptor,
          requestPagesIfNotCached = false
        )

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
      siteManager.bySiteDescriptorAndActive(descriptor.siteDescriptor())?.icon()?.url
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

  suspend fun onNavigationStackUpdated(updateEvent: HistoryNavigationManager.UpdateEvent) {
    BackgroundUtils.ensureMainThread()

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
            var atLeastOneCreated = false

            toCreate.forEach { navHistoryElement ->
              val navHistoryElementIndex = navStack.indexOf(navHistoryElement)
              if (navHistoryElementIndex < 0) {
                return@forEach
              }

              val navigationHistoryEntry = navHistoryElementToNavigationHistoryEntryOrNull(navHistoryElement)
                ?: return@forEach

              if (navHistoryElementIndex >= _navigationHistoryEntryList.size) {
                _navigationHistoryEntryList.add(navigationHistoryEntry)
                return@forEach
              }

              _navigationHistoryEntryList.add(navHistoryElementIndex, navigationHistoryEntry)
              atLeastOneCreated = true
            }

            if (atLeastOneCreated) {
              _resetScrollPositionEvents.emit(Unit)
            }
          }
        }
        is HistoryNavigationManager.UpdateEvent.Deleted -> {
          val descriptorsToDelete = updateEvent.navHistoryElements
            .toHashSetBy { navHistoryElement -> navHistoryElement.descriptor() }

          _navigationHistoryEntryList.mutableIteration { mutableIterator, navigationHistoryEntry ->
            if (navigationHistoryEntry.descriptor in descriptorsToDelete) {
              mutableIterator.remove()
            }

            return@mutableIteration true
          }
        }
        is HistoryNavigationManager.UpdateEvent.Moved -> {
          historyNavigationManager.doWithLockedNavStack { navStack ->
            val movedTo = navStack.indexOf(updateEvent.navHistoryElement)
            if (movedTo < 0 || movedTo >= _navigationHistoryEntryList.size) {
              return@doWithLockedNavStack
            }

            val movedFrom = _navigationHistoryEntryList.indexOfFirst { navigationHistoryEntry ->
              navigationHistoryEntry.descriptor == updateEvent.navHistoryElement.descriptor()
            }

            if (movedFrom < 0 || movedFrom >= _navigationHistoryEntryList.size || movedTo == movedFrom) {
              return@doWithLockedNavStack
            }

            _navigationHistoryEntryList.add(movedTo, _navigationHistoryEntryList.removeAt(movedFrom))
            _resetScrollPositionEvents.emit(Unit)
          }
        }
        is HistoryNavigationManager.UpdateEvent.PinnedOrUnpinned -> {
          historyNavigationManager.doWithLockedNavStack { navStack ->
            updateEvent.navHistoryElements.forEach { navHistoryElement ->
              val newNavHistoryElementIndex = navStack.indexOf(navHistoryElement)
              if (newNavHistoryElementIndex < 0 || newNavHistoryElementIndex >= _navigationHistoryEntryList.size) {
                return@forEach
              }

              val oldNavHistoryElementIndex = _navigationHistoryEntryList.indexOfFirst { navigationHistoryEntry ->
                navigationHistoryEntry.descriptor == navHistoryElement.descriptor()
              }

              if (oldNavHistoryElementIndex < 0) {
                return@forEach
              }

              val navigationHistoryEntry = navHistoryElementToNavigationHistoryEntryOrNull(navHistoryElement)
                ?: return@forEach

              if (oldNavHistoryElementIndex == newNavHistoryElementIndex) {
                _navigationHistoryEntryList[newNavHistoryElementIndex] = navigationHistoryEntry
              } else {
                _navigationHistoryEntryList.removeAt(oldNavHistoryElementIndex)
                _navigationHistoryEntryList.add(newNavHistoryElementIndex, navigationHistoryEntry)
              }
            }
          }
        }
        HistoryNavigationManager.UpdateEvent.Cleared -> {
          _navigationHistoryEntryList.clear()
        }
      }

      _historyControllerState.value = HistoryControllerState.Data
    } catch (error: Throwable) {
      _historyControllerState.value = HistoryControllerState.Error(errorText = error.errorMessageOrClassName())
    }
  }

  companion object {
    private const val TAG = "KurobaDrawerState"
  }

}