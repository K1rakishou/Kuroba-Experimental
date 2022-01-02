package com.github.k1rakishou.chan.features.drawer

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.drawer.data.HistoryControllerState
import com.github.k1rakishou.chan.features.drawer.data.NavHistoryBookmarkAdditionalInfo
import com.github.k1rakishou.chan.features.drawer.data.NavigationHistoryEntry
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
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
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import javax.inject.Inject
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
  @Inject
  lateinit var _compositeCatalogManager: Lazy<CompositeCatalogManager>

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
  private val compositeCatalogManager: CompositeCatalogManager
    get() = _compositeCatalogManager.get()

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

  val drawerGridMode = mutableStateOf(ChanSettings.drawerGridMode.get())

  private val bookmarksBadgeStateSubject = BehaviorProcessor.createDefault(BookmarksBadgeState(0, false))
  private val updateNavigationHistoryEntryListExecutor = SerializedCoroutineExecutor(scope = mainScope)

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  @OptIn(ExperimentalTime::class)
  override suspend fun onViewModelReady() {
    mainScope.launch {
      historyNavigationManager.navigationStackUpdatesFlow
        .collect { updateEvent ->
          updateNavigationHistoryEntryListExecutor.post {
            onNavigationStackUpdated(updateEvent)
          }
        }
    }

    mainScope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .collect { bookmarkChange ->
          updateBadge()

          updateNavigationHistoryEntryListExecutor.post {
            onBookmarksUpdated(bookmarkChange)
          }
        }
    }

    mainScope.launch {
      compositeCatalogManager.compositeCatalogUpdateEventsFlow
        .collect { event ->
          updateNavigationHistoryEntryListExecutor.post {
            onCompositeCatalogsUpdated(event)
          }
        }
    }
  }

  fun firstLoadDrawerData() {
    mainScope.launch {
      delay(500L)
      reloadNavigationHistory()
    }
  }

  fun getSelectedDescriptors(): List<ChanDescriptor> {
    return _selectedHistoryEntries.keys.toList()
  }

  fun updateDeleteButtonShortcut(show: Boolean) {
    _showDeleteButtonShortcut.value = show
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

  fun onThemeChanged() {
    updateBadge()
  }

  fun listenForBookmarksBadgeStateChanges(): Flowable<BookmarksBadgeState> {
    return bookmarksBadgeStateSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .hide()
  }

  suspend fun deleteNavElement(navigationHistoryEntry: NavigationHistoryEntry) {
    deleteNavElements(listOf(navigationHistoryEntry))
  }

  suspend fun deleteNavElements(navigationHistoryEntries: Collection<NavigationHistoryEntry>) {
    deleteNavElementsByDescriptors(navigationHistoryEntries.map { it.descriptor })
  }

  suspend fun deleteNavElementsByDescriptors(descriptors: Collection<ChanDescriptor>) {
    historyNavigationManager.deleteNavElements(descriptors)

    if (ChanSettings.drawerDeleteBookmarksWhenDeletingNavHistory.get()) {
      val bookmarkDescriptors = descriptors
        .mapNotNull { chanDescriptor -> chanDescriptor.threadDescriptorOrNull() }

      if (bookmarkDescriptors.isNotEmpty()) {
        bookmarksManager.deleteBookmarks(bookmarkDescriptors)
      }
    }
  }

  fun deleteBookmarkedNavHistoryElements() {
    mainScope.launch {
      ChanSettings.drawerShowBookmarkedThreads.toggle()

      reloadNavigationHistory()
    }
  }

  suspend fun pinOrUnpin(descriptors: Collection<ChanDescriptor>): HistoryNavigationManager.PinResult {
    return historyNavigationManager.pinOrUnpin(descriptors)
  }

  suspend fun reloadNavigationHistory() {
    ModularResult.Try {
      val navigationHistoryList = withContext(Dispatchers.Default) {
        _historyControllerState.value = HistoryControllerState.Loading

        siteManager.awaitUntilInitialized()
        bookmarksManager.awaitUntilInitialized()

        return@withContext historyNavigationManager.getAll()
          .mapNotNull { navigationElement -> navHistoryElementToNavigationHistoryEntryOrNull(navigationElement) }
      }


      _navigationHistoryEntryList.clear()
      _navigationHistoryEntryList.addAll(navigationHistoryList)
    }
      .peekError { error ->
        Logger.e(TAG, "loadNavigationHistoryInitial() error", error)
        _historyControllerState.value = HistoryControllerState.Error(error.errorMessageOrClassName())
      }
      .peekValue {
        Logger.d(TAG, "loadNavigationHistoryInitial() success")
        _historyControllerState.value = HistoryControllerState.Data
      }
      .ignore()
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
      siteManager.bySiteDescriptor(descriptor.siteDescriptor())?.icon()?.url
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

  private suspend fun onCompositeCatalogsUpdated(event: CompositeCatalogManager.Event) {
    compositeCatalogManager.doWithLockedCompositeCatalogs { compositeCatalogs ->
      when (event) {
        is CompositeCatalogManager.Event.Created,
        is CompositeCatalogManager.Event.Deleted -> {
          // no-op
        }
        is CompositeCatalogManager.Event.Updated -> {
          val newCompositeCatalog = compositeCatalogs
            .firstOrNull { catalog -> catalog.compositeCatalogDescriptor == event.newCatalogDescriptor }

          if (newCompositeCatalog == null) {
            return@doWithLockedCompositeCatalogs
          }

          val title = compositeCatalogs
            .firstOrNull { compositeCatalog ->
              return@firstOrNull compositeCatalog.compositeCatalogDescriptor.asSet ==
                newCompositeCatalog.compositeCatalogDescriptor.asSet
            }
            ?.name
            ?: newCompositeCatalog.compositeCatalogDescriptor.userReadableString()

          historyNavigationManager.updateNavElement(
            chanDescriptor = event.prevCatalogDescriptor,
            newNavigationElement = HistoryNavigationManager.NewNavigationElement(
              descriptor = newCompositeCatalog.compositeCatalogDescriptor,
              thumbnailImageUrl = NavigationHistoryEntry.COMPOSITE_ICON_URL,
              title = title
            )
          )
        }
      }
    }
  }

  private suspend fun onBookmarksUpdated(bookmarkChange: BookmarksManager.BookmarkChange) {
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

  private suspend fun onNavigationStackUpdated(updateEvent: HistoryNavigationManager.UpdateEvent) {
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

  data class BookmarksBadgeState(
    val totalUnseenPostsCount: Int,
    val hasUnreadReplies: Boolean
  )

  companion object {
    private const val TAG = "DrawerPresenter"
  }

}