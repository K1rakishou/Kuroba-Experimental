package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.helper.OneShotRunnable
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.navigation.NavHistoryElement
import com.github.k1rakishou.model.data.navigation.NavHistoryElementInfo
import com.github.k1rakishou.model.repository.HistoryNavigationRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class HistoryNavigationManager(
  private val appScope: CoroutineScope,
  private val _historyNavigationRepository: Lazy<HistoryNavigationRepository>,
  private val _applicationVisibilityManager: Lazy<ApplicationVisibilityManager>,
  private val _currentOpenedDescriptorStateManager: Lazy<CurrentOpenedDescriptorStateManager>
) {
  private val _navigationStackUpdatesFlow = MutableSharedFlow<UpdateEvent>(extraBufferCapacity = 64)
  val navigationStackUpdatesFlow: SharedFlow<UpdateEvent>
    get() = _navigationStackUpdatesFlow.asSharedFlow()

  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(appScope)

  private val mutex = Mutex()
  @GuardedBy("mutex")
  private val navigationStack = mutableListWithCap<NavHistoryElement>(MAX_NAV_HISTORY_ENTRIES)

  private val initializationRunnable = OneShotRunnable()

  private val historyNavigationRepository: HistoryNavigationRepository
    get() = _historyNavigationRepository.get()
  private val applicationVisibilityManager: ApplicationVisibilityManager
    get() = _applicationVisibilityManager.get()
  private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
    get() = _currentOpenedDescriptorStateManager.get()

  val isInitialized: Boolean
    get() = initializationRunnable.alreadyRun

  @OptIn(ExperimentalTime::class)
  fun initialize() {
    Logger.d(TAG, "HistoryNavigationManager.initialize()")
    startListeningForAppVisibilityUpdates()
  }

  suspend fun <T: Any?> doWithLockedNavStack(func: suspend (List<NavHistoryElement>) -> T): T {
    return mutex.withLock { func(navigationStack) }
  }

  suspend fun contains(descriptor: ChanDescriptor): Boolean {
    ensureInitialized()

    return mutex.withLock {
      navigationStack.any { navHistoryElement -> navHistoryElement.descriptor() == descriptor }
    }
  }

  suspend fun getAll(reversed: Boolean = false): List<NavHistoryElement> {
    ensureInitialized()

    return mutex.withLock {
      if (reversed) {
        return@withLock navigationStack.toList().reversed()
      } else {
        return@withLock navigationStack.toList()
      }
    }
  }

  suspend fun getNavElementAtTop(): NavHistoryElement? {
    if (initializationRunnable.alreadyRun) {
      return mutex.withLock { navigationStack.firstOrNull() }
    } else {
      return historyNavigationRepository.getFirstNavElement()
        .peekError { error -> Logger.e(TAG, "historyNavigationRepository.getFirstNavElement() error", error) }
        .valueOrNull()
    }
  }

  suspend fun getNavHistoryElementByDescriptor(chanDescriptor: ChanDescriptor): NavHistoryElement? {
    return mutex.withLock {
      return@withLock navigationStack
        .firstOrNull { navHistoryElement -> navHistoryElement.descriptor() == chanDescriptor }
    }
  }

  suspend fun getFirstThreadNavElement(): NavHistoryElement? {
    if (initializationRunnable.alreadyRun) {
      return mutex.withLock {
        return@withLock navigationStack
          .firstOrNull { navHistoryElement -> navHistoryElement is NavHistoryElement.Thread }
      }
    } else {
      return historyNavigationRepository.getFirstThreadNavElement()
        .peekError { error -> Logger.e(TAG, "historyNavigationRepository.getFirstThreadNavElement() error", error) }
        .valueOrNull()
    }
  }

  suspend fun getFirstCatalogNavElement(): NavHistoryElement? {
    if (initializationRunnable.alreadyRun) {
      return mutex.withLock {
        return@withLock navigationStack
          .firstOrNull { navHistoryElement -> navHistoryElement is NavHistoryElement.Catalog }
      }
    } else {
      return historyNavigationRepository.getFirstCatalogNavElement()
        .peekError { error -> Logger.e(TAG, "historyNavigationRepository.getFirstCatalogNavElement() error", error) }
        .valueOrNull()
    }
  }

  fun canCreateNavElement(
    bookmarksManager: BookmarksManager,
    chanDescriptor: ChanDescriptor
  ): Boolean {
    if (chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
      return ChanSettings.drawerShowNavigationHistory.get()
    }

    val threadDescriptor = chanDescriptor as ChanDescriptor.ThreadDescriptor

    if (!ChanSettings.drawerShowBookmarkedThreads.get() && !ChanSettings.drawerShowNavigationHistory.get()) {
      return false
    }

    if (!ChanSettings.drawerShowBookmarkedThreads.get()) {
      return !bookmarksManager.contains(threadDescriptor)
    }

    if (!ChanSettings.drawerShowNavigationHistory.get()) {
      return bookmarksManager.contains(threadDescriptor)
    }

    return true
  }

  suspend fun onBookmarksDeleted(threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>) {
    if (threadDescriptors.isEmpty()) {
      return
    }

    val canDelete = ChanSettings.drawerDeleteNavHistoryWhenBookmarkDeleted.get()
      || !ChanSettings.drawerShowNavigationHistory.get()

    if (canDelete) {
      deleteNavElements(threadDescriptors)
    }
  }

  suspend fun createNewNavElement(
    descriptor: ChanDescriptor,
    thumbnailImageUrl: HttpUrl,
    title: String,
    canInsertAtTheBeginning: Boolean
  ) {
    ensureInitialized()

    val newNavigationElement = NewNavigationElement(descriptor, thumbnailImageUrl, title)
    createNewNavElements(listOf(newNavigationElement), canInsertAtTheBeginning)
  }

  /**
   * When [canInsertAtTheBeginning] is true (mostly the default state) that means we can insert this
   * NavHistoryElement at the very beginning of the navigationStack (navigationStack[0]).
   * Otherwise we will insert it at the next position after the beginning (navigationStack[1]).
   * We set [canInsertAtTheBeginning] to false when this NavHistoryElement was created automatically
   * after a bookmark was created or when the user creates a new nav element by using
   * "Add to nav history" catalog thread option. This is necessary because otherwise after the app
   * restart we will attempt to open last bookmarked thread/thread added to nav history instead of
   * the catalog navigation element.
   * */
  suspend fun createNewNavElements(
    newNavigationElements: Collection<NewNavigationElement>,
    canInsertAtTheBeginning: Boolean
  ) {
    if (newNavigationElements.isEmpty()) {
      return
    }

    ensureInitialized()

    var created = false

    val mappedNavElements = newNavigationElements.mapNotNull { newNavigationElement ->
      val descriptor = newNavigationElement.descriptor
      val thumbnailImageUrl = newNavigationElement.thumbnailImageUrl
      val title = newNavigationElement.title

      val navElementInfo = NavHistoryElementInfo(
        thumbnailUrl = thumbnailImageUrl,
        title = title,
        pinned = false
      )

      return@mapNotNull when (descriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          NavHistoryElement.Thread(descriptor, navElementInfo)
        }
        is ChanDescriptor.CatalogDescriptor -> {
          NavHistoryElement.Catalog(descriptor, navElementInfo)
        }
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          NavHistoryElement.CompositeCatalog(descriptor, navElementInfo)
        }
      }
    }

    mappedNavElements.forEach { navElement ->
      if (addNewOrIgnore(navElement, canInsertAtTheBeginning)) {
        created = true
      }
    }

    if (!created) {
      return
    }

    _navigationStackUpdatesFlow.emit(UpdateEvent.Created(mappedNavElements))
    persistNavigationStack()
  }

  suspend fun updateNavElement(
    chanDescriptor: ChanDescriptor,
    newNavigationElement: NewNavigationElement
  ) {
    ensureInitialized()

    mutex.withLock {
      val indexOfElem = navigationStack.indexOfFirst { navHistoryElement ->
        return@indexOfFirst when (navHistoryElement) {
          is NavHistoryElement.CompositeCatalog -> navHistoryElement.descriptor == chanDescriptor
          is NavHistoryElement.Catalog -> navHistoryElement.descriptor == chanDescriptor
          is NavHistoryElement.Thread -> navHistoryElement.descriptor == chanDescriptor
        }
      }

      if (indexOfElem < 0) {
        return@withLock
      }

      val prevElement = navigationStack.getOrNull(indexOfElem)
        ?: return@withLock

      val wasPinned = prevElement.navHistoryElementInfo.pinned

      val navigationHistoryElementInfo = NavHistoryElementInfo(
        thumbnailUrl = newNavigationElement.thumbnailImageUrl,
        title = newNavigationElement.title,
        pinned = wasPinned
      )

      val newElement = when (val newDescriptor = newNavigationElement.descriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          NavHistoryElement.Catalog(newDescriptor, navigationHistoryElementInfo)
        }
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          NavHistoryElement.CompositeCatalog(newDescriptor, navigationHistoryElementInfo)
        }
        is ChanDescriptor.ThreadDescriptor -> {
          NavHistoryElement.Thread(newDescriptor, navigationHistoryElementInfo)
        }
      }

      navigationStack[indexOfElem] = newElement
      _navigationStackUpdatesFlow.emit(UpdateEvent.Deleted(listOf(prevElement)))
      _navigationStackUpdatesFlow.emit(UpdateEvent.Created(listOf(newElement)))
    }

    persistNavigationStack()
  }

  suspend fun moveNavElementToTop(descriptor: ChanDescriptor, canMoveAtTheBeginning: Boolean = true) {
    if (!ChanSettings.drawerMoveLastAccessedThreadToTop.get()) {
      return
    }

    ensureInitialized()

    mutex.withLock {
      val indexOfElem = navigationStack.indexOfFirst { navHistoryElement ->
        return@indexOfFirst when (navHistoryElement) {
          is NavHistoryElement.CompositeCatalog -> navHistoryElement.descriptor == descriptor
          is NavHistoryElement.Catalog -> navHistoryElement.descriptor == descriptor
          is NavHistoryElement.Thread -> navHistoryElement.descriptor == descriptor
        }
      }

      if (indexOfElem < 0) {
        return@withLock
      }

      val movedElement = navigationStack.getOrNull(indexOfElem)
        ?: return@withLock

      if (movedElement.navHistoryElementInfo.pinned) {
        return@withLock
      }

      val lastPinnedElementPosition = navigationStack
        .indexOfLast { navHistoryElement -> navHistoryElement.navHistoryElementInfo.pinned }

      val newIndex = when {
        navigationStack.isEmpty() -> 0
        canMoveAtTheBeginning -> lastPinnedElementPosition + 1
        else -> lastPinnedElementPosition + 2
      }

      if (newIndex == indexOfElem) {
        return@withLock
      }

      navigationStack.addSafe(newIndex, navigationStack.removeAt(indexOfElem))
      _navigationStackUpdatesFlow.emit(UpdateEvent.Moved(movedElement))
    }

    persistNavigationStack()
  }

  private suspend fun addNewOrIgnore(navElement: NavHistoryElement, canInsertAtTheBeginning: Boolean): Boolean {
    ensureInitialized()

    return mutex.withLock {
      val indexOfElem = navigationStack.indexOf(navElement)
      if (indexOfElem >= 0) {
        return@withLock false
      }

      if (navigationStack.getOrNull(indexOfElem)?.navHistoryElementInfo?.pinned == true) {
        return@withLock false
      }

      val lastPinnedElementPosition = navigationStack
        .indexOfLast { navHistoryElement -> navHistoryElement.navHistoryElementInfo.pinned }

      val newIndex = when {
        navigationStack.isEmpty() -> 0
        canInsertAtTheBeginning -> lastPinnedElementPosition + 1
        else -> {
          // Do not overwrite the top of nav stack that we use to restore previously opened thread.
          // Otherwise this may lead to unexpected behaviors, for example, a situation when starting
          // the app after a bookmark was created when the app was in background (filter watching)
          // the user will see the last bookmarked thread instead of last opened thread.
          lastPinnedElementPosition + 2
        }
      }

      if (newIndex == indexOfElem) {
        return@withLock false
      }

      navigationStack.addSafe(newIndex, navElement)
      return@withLock true
    }
  }

  suspend fun pinOrUnpin(chanDescriptors: Collection<ChanDescriptor>): PinResult {
    ensureInitialized()

    if (chanDescriptors.isEmpty()) {
      return PinResult.Failure
    }

    val pinResult = mutex.withLock {
      val doPin = chanDescriptors.any { chanDescriptor ->
        return@any navigationStack.firstOrNull { navHistoryElement ->
          navHistoryElement.descriptor() == chanDescriptor
        }
          ?.navHistoryElementInfo
          ?.pinned == false
      }

      val pinnedUnpinned = mutableListOf<NavHistoryElement>()

      for (chanDescriptor in chanDescriptors) {
        val indexOfElem = navigationStack.indexOfFirst { navHistoryElement ->
          return@indexOfFirst when (navHistoryElement) {
            is NavHistoryElement.CompositeCatalog -> navHistoryElement.descriptor == chanDescriptor
            is NavHistoryElement.Catalog -> navHistoryElement.descriptor == chanDescriptor
            is NavHistoryElement.Thread -> navHistoryElement.descriptor == chanDescriptor
          }
        }

        if (indexOfElem < 0) {
          return@withLock PinResult.Failure
        }

        val lastPinnedElementIndex = navigationStack
          .indexOfLast { navHistoryElement -> navHistoryElement.navHistoryElementInfo.pinned }

        val navHistoryElement = navigationStack.get(indexOfElem)
        val navHistoryElementDescriptor = navHistoryElement.descriptor()
        val navHistoryElementInfo = navHistoryElement.navHistoryElementInfo
        navHistoryElementInfo.pinned = doPin

        val isDescriptorCurrentlyOpened =
          navHistoryElementDescriptor == currentOpenedDescriptorStateManager.currentFocusedDescriptor

        val nextPinnedElementIndex = if (lastPinnedElementIndex < 0) {
          0
        } else {
          if (!navHistoryElementInfo.pinned && isDescriptorCurrentlyOpened) {
            lastPinnedElementIndex
          } else {
            lastPinnedElementIndex + 1
          }
        }

        navigationStack.addSafe(nextPinnedElementIndex, navigationStack.removeAt(indexOfElem))
        pinnedUnpinned += navHistoryElement
      }

      if (pinnedUnpinned.isNotEmpty()) {
        _navigationStackUpdatesFlow.emit(UpdateEvent.PinnedOrUnpinned(pinnedUnpinned))
      }

      if (doPin) {
        return@withLock PinResult.Pinned
      }

      return@withLock PinResult.Unpinned
    }

    if (!pinResult.success) {
      return pinResult
    }

    persistNavigationStack()
    return pinResult
  }

  suspend fun deleteNavElement(descriptor: ChanDescriptor) {
    ensureInitialized()

    deleteNavElements(listOf(descriptor))
  }

  suspend fun deleteNavElements(descriptors: Collection<ChanDescriptor>) {
    if (descriptors.isEmpty()) {
      return
    }

    ensureInitialized()

    val removedElements = mutex.withLock {
      val removedElements = mutableListWithCap<NavHistoryElement>(descriptors.size)

      descriptors.forEach { chanDescriptor ->
        val indexOfElem = navigationStack.indexOfFirst { navHistoryElement ->
          return@indexOfFirst when (navHistoryElement) {
            is NavHistoryElement.CompositeCatalog -> navHistoryElement.descriptor == chanDescriptor
            is NavHistoryElement.Catalog -> navHistoryElement.descriptor == chanDescriptor
            is NavHistoryElement.Thread -> navHistoryElement.descriptor == chanDescriptor
          }
        }

        if (indexOfElem < 0) {
          return@forEach
        }

        removedElements += navigationStack.removeAt(indexOfElem)
      }

      return@withLock removedElements
    }

    if (removedElements.isEmpty()) {
      return
    }

    _navigationStackUpdatesFlow.emit(UpdateEvent.Deleted(removedElements))
    persistNavigationStack()
  }

  suspend fun clear() {
    ensureInitialized()

    val cleared = mutex.withLock {
      if (navigationStack.isEmpty()) {
        return@withLock false
      }

      navigationStack.clear()
      return@withLock true
    }

    if (!cleared) {
      return
    }

    _navigationStackUpdatesFlow.emit(UpdateEvent.Cleared)
    persistNavigationStack()
  }

  private fun persistNavigationStack() {
    rendezvousCoroutineExecutor.post {
      Logger.d(TAG, "persistNavigationStack async called")
      persistNavigationStackInternal()
      Logger.d(TAG, "persistNavigationStack async finished")
    }
  }

  private suspend fun persistNavigationStackInternal() {
    if (!initializationRunnable.alreadyRun) {
      Logger.d(TAG, "persistNavigationStackInternal not initialized yet, can't persist")
      return
    }

    val navStackCopy = mutex.withLock { navigationStack.toList() }
    Logger.d(TAG, "persistNavigationStackInternal navStackCopy.size=${navStackCopy.size}")

    historyNavigationRepository.persist(navStackCopy)
      .safeUnwrap { error ->
        Logger.e(TAG, "Error while trying to persist navigation stack", error)
        return
      }
  }

  private suspend fun ensureInitialized() {
    initializationRunnable.runIfNotYet { initializeHistoryNavigationManagerInternal() }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun initializeHistoryNavigationManagerInternal() {
    withContext(Dispatchers.IO) {
      Logger.d(TAG, "initializeHistoryNavigationManagerInternal() start")

      val time = measureTime {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val loadedNavElementsResult = historyNavigationRepository.initialize(MAX_NAV_HISTORY_ENTRIES)
        when (loadedNavElementsResult) {
          is ModularResult.Value -> {
            mutex.withLock {
              navigationStack.clear()
              navigationStack.addAll(loadedNavElementsResult.value)
            }

            Logger.d(TAG, "initializeHistoryNavigationManagerInternal() done. " +
                "Loaded ${loadedNavElementsResult.value.size} history nav elements")
          }
          is ModularResult.Error -> {
            Logger.e(TAG, "initializeHistoryNavigationManagerInternal() error", loadedNavElementsResult.error)
          }
        }

        _navigationStackUpdatesFlow.emit(UpdateEvent.Initialized)
      }

      Logger.d(TAG, "initializeHistoryNavigationManagerInternal() end, took $time")
    }
  }

  private fun startListeningForAppVisibilityUpdates() {
    applicationVisibilityManager.addListener { visibility ->
      if (visibility != ApplicationVisibility.Background) {
        return@addListener
      }

      persistNavigationStack()
    }
  }

  private fun <T> MutableList<T>.addSafe(index: Int, element: T) {
    require(index >= 0) { "Bad index: ${index}" }

    if (isEmpty()) {
      add(element)
      return
    }

    if (index <= lastIndex) {
      add(index, element)
      return
    }

    add(element)
  }

  enum class PinResult(val success: Boolean) {
    Pinned(true),
    Unpinned(true),
    Failure(false),
  }

  sealed class UpdateEvent {
    object Initialized : UpdateEvent()
    data class Created(val navHistoryElements: Collection<NavHistoryElement>) : UpdateEvent()
    data class PinnedOrUnpinned(val navHistoryElements: Collection<NavHistoryElement>) : UpdateEvent()
    data class Moved(val navHistoryElement: NavHistoryElement) : UpdateEvent()
    data class Deleted(val navHistoryElements: Collection<NavHistoryElement>) : UpdateEvent()
    object Cleared : UpdateEvent()
  }

  data class NewNavigationElement(
    val descriptor: ChanDescriptor,
    val thumbnailImageUrl: HttpUrl,
    val title: String
  )

  companion object {
    private const val TAG = "HistoryNavigationManager"

    // Only used when reloading navigation history back from the database.
    // Can grow unlimited until the app restart.
    private const val MAX_NAV_HISTORY_ENTRIES = 256
  }
}