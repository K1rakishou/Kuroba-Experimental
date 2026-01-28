package com.github.k1rakishou.chan.features.drawer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.concurrency.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.drawer.data.NavigationHistoryEntry
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import dagger.Lazy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class MainControllerViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val historyNavigationManagerLazy: Lazy<HistoryNavigationManager>,
  private val siteManagerLazy: Lazy<SiteManager>,
  private val bookmarksManagerLazy: Lazy<BookmarksManager>,
  private val pageRequestManagerLazy: Lazy<PageRequestManager>,
  private val archivesManagerLazy: Lazy<ArchivesManager>,
  private val chanThreadManagerLazy: Lazy<ChanThreadManager>,
  private val compositeCatalogManagerLazy: Lazy<CompositeCatalogManager>,
  private val imageLoaderDeprecatedLazy: Lazy<ImageLoaderDeprecated>,
) : BaseViewModel() {
  private val isDevFlavor = AppModuleAndroidUtils.isDevBuild

  private val historyNavigationManager: HistoryNavigationManager
    get() = historyNavigationManagerLazy.get()
  private val bookmarksManager: BookmarksManager
    get() = bookmarksManagerLazy.get()
  private val compositeCatalogManager: CompositeCatalogManager
    get() = compositeCatalogManagerLazy.get()
  val imageLoaderDeprecated: ImageLoaderDeprecated
    get() = imageLoaderDeprecatedLazy.get()

  private val _currentNavigationHasDrawer = MutableStateFlow<Boolean>(true)
  val currentNavigationHasDrawer: StateFlow<Boolean>
    get() = _currentNavigationHasDrawer.asStateFlow()

  private val _bookmarksBadgeState = MutableStateFlow(BookmarksBadgeState(0, false))
  val bookmarksBadgeState: StateFlow<BookmarksBadgeState>
    get() = _bookmarksBadgeState.asStateFlow()

  private val updateNavigationHistoryEntryListExecutor = SerializedCoroutineExecutor(scope = viewModelScope)

  val kurobaDrawerState = KurobaDrawerState(
    siteManagerLazy = siteManagerLazy,
    historyNavigationManagerLazy = historyNavigationManagerLazy,
    bookmarksManagerLazy = bookmarksManagerLazy,
    archivesManagerLazy = archivesManagerLazy,
    chanThreadManagerLazy = chanThreadManagerLazy,
    pageRequestManagerLazy = pageRequestManagerLazy
  )

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    viewModelScope.launch {
      historyNavigationManager.navigationStackUpdatesFlow
        .collect { updateEvent ->
          updateNavigationHistoryEntryListExecutor.post {
            kurobaDrawerState.onNavigationStackUpdated(updateEvent)
          }
        }
    }

    viewModelScope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .collect { bookmarkChange ->
          updateBadge()

          updateNavigationHistoryEntryListExecutor.post {
            kurobaDrawerState.onBookmarksUpdated(bookmarkChange)
          }
        }
    }

    viewModelScope.launch {
      compositeCatalogManager.compositeCatalogUpdateEventsFlow
        .collect { event ->
          updateNavigationHistoryEntryListExecutor.post {
            onCompositeCatalogsUpdated(event)
          }
        }
    }
  }

  fun firstLoadDrawerData() {
    viewModelScope.launch {
      delay(500L)
      reloadNavigationHistory()
    }
  }

  fun onThemeChanged() {
    updateBadge()
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
    viewModelScope.launch {
      ChanSettings.drawerShowBookmarkedThreads.toggle()

      reloadNavigationHistory()
    }
  }

  suspend fun pinOrUnpin(descriptors: Collection<ChanDescriptor>): HistoryNavigationManager.PinResult {
    return historyNavigationManager.pinOrUnpin(descriptors)
  }

  suspend fun reloadNavigationHistory() {
    kurobaDrawerState.reloadNavigationHistory()
  }

  fun onNavigationItemDrawerInfoUpdated(hasDrawer: Boolean) {
    _currentNavigationHasDrawer.value = hasDrawer
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

    _bookmarksBadgeState.value = BookmarksBadgeState(totalUnseenPostsCount, hasUnreadReplies)
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

  data class BookmarksBadgeState(
    val totalUnseenPostsCount: Int,
    val hasUnreadReplies: Boolean
  )

  class ViewModelFactory @Inject constructor(
    private val historyNavigationManagerLazy: Lazy<HistoryNavigationManager>,
    private val siteManagerLazy: Lazy<SiteManager>,
    private val bookmarksManagerLazy: Lazy<BookmarksManager>,
    private val pageRequestManagerLazy: Lazy<PageRequestManager>,
    private val archivesManagerLazy: Lazy<ArchivesManager>,
    private val chanThreadManagerLazy: Lazy<ChanThreadManager>,
    private val compositeCatalogManagerLazy: Lazy<CompositeCatalogManager>,
    private val imageLoaderDeprecatedLazy: Lazy<ImageLoaderDeprecated>
  ) : ViewModelAssistedFactory<MainControllerViewModel> {
    override fun create(handle: SavedStateHandle): MainControllerViewModel {
      return MainControllerViewModel(
        savedStateHandle = handle,
        historyNavigationManagerLazy = historyNavigationManagerLazy,
        siteManagerLazy = siteManagerLazy,
        bookmarksManagerLazy = bookmarksManagerLazy,
        pageRequestManagerLazy = pageRequestManagerLazy,
        archivesManagerLazy = archivesManagerLazy,
        chanThreadManagerLazy = chanThreadManagerLazy,
        compositeCatalogManagerLazy = compositeCatalogManagerLazy,
        imageLoaderDeprecatedLazy = imageLoaderDeprecatedLazy
      )
    }
  }

  companion object {
    private const val TAG = "DrawerPresenter"
  }

}