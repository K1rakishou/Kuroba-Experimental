package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.navigation.NavHistoryElement
import com.github.k1rakishou.model.data.navigation.NavHistoryElementInfo
import com.github.k1rakishou.model.repository.HistoryNavigationRepository
import dagger.Lazy
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

class HistoryNavigationManager(
  private val appScope: CoroutineScope,
  private val _historyNavigationRepository: Lazy<HistoryNavigationRepository>,
  private val _applicationVisibilityManager: Lazy<ApplicationVisibilityManager>
) {
  private val navigationStackChangesSubject = PublishProcessor.create<Unit>()
  private val persistTaskSubject = PublishProcessor.create<Unit>()
  private val persistRunning = AtomicBoolean(false)
  private val suspendableInitializer = SuspendableInitializer<Unit>("HistoryNavigationManager")

  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val navigationStack = mutableListWithCap<NavHistoryElement>(MAX_NAV_HISTORY_ENTRIES)

  private val historyNavigationRepository: HistoryNavigationRepository
    get() = _historyNavigationRepository.get()
  private val applicationVisibilityManager: ApplicationVisibilityManager
    get() = _applicationVisibilityManager.get()

  @OptIn(ExperimentalTime::class)
  fun initialize() {
    Logger.d(TAG, "HistoryNavigationManager.initialize()")
    startListeningForAppVisibilityUpdates()

    appScope.launch {
      persistTaskSubject
        .onBackpressureLatest()
        .asFlow()
        .debounce(1.seconds)
        .collect {
          persisNavigationStack(eager = false)
        }
    }

    appScope.launch(Dispatchers.IO) {
      Logger.d(TAG, "initializeHistoryNavigationManagerInternal() start")
      initializeHistoryNavigationManagerInternal()
      Logger.d(TAG, "initializeHistoryNavigationManagerInternal() end")
    }
  }

  private suspend fun initializeHistoryNavigationManagerInternal() {
    @Suppress("MoveVariableDeclarationIntoWhen")
    val loadedNavElementsResult = historyNavigationRepository.initialize(MAX_NAV_HISTORY_ENTRIES)
    when (loadedNavElementsResult) {
      is ModularResult.Value -> {
        lock.write {
          navigationStack.clear()
          navigationStack.addAll(loadedNavElementsResult.value)
        }

        suspendableInitializer.initWithValue(Unit)
        Logger.d(TAG, "initializeHistoryNavigationManagerInternal() done. " +
          "Loaded ${loadedNavElementsResult.value.size} history nav elements")
      }
      is ModularResult.Error -> {
        suspendableInitializer.initWithError(loadedNavElementsResult.error)
        Logger.e(TAG, "initializeHistoryNavigationManagerInternal() error", loadedNavElementsResult.error)
      }
    }

    navStackChanged()
  }

  private fun startListeningForAppVisibilityUpdates() {
    applicationVisibilityManager.addListener { visibility ->
      if (!suspendableInitializer.isInitialized()) {
        return@addListener
      }

      if (visibility != ApplicationVisibility.Background) {
        return@addListener
      }

      persisNavigationStack(eager = true)
    }
  }

  fun contains(descriptor: ChanDescriptor): Boolean {
    return lock.read {
      navigationStack.any { navHistoryElement -> navHistoryElement.descriptor() == descriptor }
    }
  }

  fun getAll(reversed: Boolean = false): List<NavHistoryElement> {
    return lock.read {
      if (reversed) {
        return@read navigationStack.toList().reversed()
      } else {
        return@read navigationStack.toList()
      }
    }
  }

  fun getNavElementAtTop(): NavHistoryElement? {
    return lock.read { navigationStack.firstOrNull() }
  }

  fun getFirstCatalogNavElement(): NavHistoryElement? {
    return lock.read {
      return@read navigationStack.firstOrNull { navHistoryElement ->
        navHistoryElement is NavHistoryElement.Catalog
      }
    }
  }

  fun listenForNavigationStackChanges(): Flowable<Unit> {
    return navigationStackChangesSubject
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "listenForNavigationStackChanges error", error) }
      .hide()
  }

  suspend fun awaitUntilInitialized() = suspendableInitializer.awaitUntilInitialized()

  fun isReady() = suspendableInitializer.isInitialized()

  fun canCreateNavElement(
    bookmarksManager: BookmarksManager,
    chanDescriptor: ChanDescriptor
  ): Boolean {
    if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
      return ChanSettings.drawerShowNavigationHistory.get()
    }

    val threadDescriptor = chanDescriptor as ChanDescriptor.ThreadDescriptor

    if (!ChanSettings.drawerShowBookmarkedThreads.get() && !ChanSettings.drawerShowNavigationHistory.get()) {
      return false
    }

    if (!ChanSettings.drawerShowBookmarkedThreads.get()) {
      return !bookmarksManager.exists(threadDescriptor)
    }

    if (!ChanSettings.drawerShowNavigationHistory.get()) {
      return bookmarksManager.exists(threadDescriptor)
    }

    return true
  }

  fun createNewNavElement(
    descriptor: ChanDescriptor,
    thumbnailImageUrl: HttpUrl,
    title: String,
    canInsertAtTheBeginning: Boolean
  ) {
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
  fun createNewNavElements(
    newNavigationElements: Collection<NewNavigationElement>,
    canInsertAtTheBeginning: Boolean
  ) {
    if (newNavigationElements.isEmpty()) {
      return
    }

    var created = false

    newNavigationElements.forEach { newNavigationElement ->
      val descriptor = newNavigationElement.descriptor
      val thumbnailImageUrl = newNavigationElement.thumbnailImageUrl
      val title = newNavigationElement.title

      val navElementInfo = NavHistoryElementInfo(
        thumbnailUrl = thumbnailImageUrl,
        title = title,
        pinned = false
      )

      val navElement = when (descriptor) {
        is ChanDescriptor.ThreadDescriptor -> NavHistoryElement.Thread(descriptor, navElementInfo)
        is ChanDescriptor.CatalogDescriptor -> NavHistoryElement.Catalog(descriptor, navElementInfo)
      }

      if (addNewOrIgnore(navElement, canInsertAtTheBeginning)) {
        created = true
      }
    }

    if (!created) {
      return
    }

    navStackChanged()
  }

  fun moveNavElementToTop(descriptor: ChanDescriptor, canMoveAtTheBeginning: Boolean = true) {
    if (!ChanSettings.drawerMoveLastAccessedThreadToTop.get()) {
      return
    }

    lock.write {
      val indexOfElem = navigationStack.indexOfFirst { navHistoryElement ->
        return@indexOfFirst when (navHistoryElement) {
          is NavHistoryElement.Catalog -> navHistoryElement.descriptor == descriptor
          is NavHistoryElement.Thread -> navHistoryElement.descriptor == descriptor
        }
      }

      if (indexOfElem < 0) {
        return@write
      }

      if (navigationStack.getOrNull(indexOfElem)?.navHistoryElementInfo?.pinned == true) {
        return@write
      }

      val lastPinnedElementPosition = navigationStack
        .indexOfLast { navHistoryElement -> navHistoryElement.navHistoryElementInfo.pinned }

      val newIndex = when {
        navigationStack.isEmpty() -> 0
        canMoveAtTheBeginning -> lastPinnedElementPosition + 1
        else -> lastPinnedElementPosition + 2
      }

      if (newIndex == indexOfElem) {
        return@write
      }

      navigationStack.addSafe(newIndex, navigationStack.removeAt(indexOfElem))
    }

    navStackChanged()
  }

  private fun addNewOrIgnore(navElement: NavHistoryElement, canInsertAtTheBeginning: Boolean): Boolean {
    return lock.write {
      val indexOfElem = navigationStack.indexOf(navElement)
      if (indexOfElem >= 0) {
        return@write false
      }

      if (navigationStack.getOrNull(indexOfElem)?.navHistoryElementInfo?.pinned == true) {
        return@write false
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
        return@write false
      }

      navigationStack.addSafe(newIndex, navElement)
      return@write true
    }
  }

  fun pinOrUnpin(chanDescriptor: ChanDescriptor): PinResult {
    val pinResult = lock.write {
      val indexOfElem = navigationStack.indexOfFirst { navHistoryElement ->
        return@indexOfFirst when (navHistoryElement) {
          is NavHistoryElement.Catalog -> navHistoryElement.descriptor == chanDescriptor
          is NavHistoryElement.Thread -> navHistoryElement.descriptor == chanDescriptor
        }
      }

      if (indexOfElem < 0) {
        return@write PinResult.Failure
      }

      val lastPinnedElementIndex = navigationStack
        .indexOfLast { navHistoryElement -> navHistoryElement.navHistoryElementInfo.pinned }

      if (lastPinnedElementIndex >= navigationStack.lastIndex) {
        return@write PinResult.NoSpaceToPin
      }

      val nextPinnedElementIndex = if (lastPinnedElementIndex < 0) {
        0
      } else {
        lastPinnedElementIndex + 1
      }

      val navHistoryElementInfo = navigationStack.get(indexOfElem).navHistoryElementInfo
      navHistoryElementInfo.pinned = navHistoryElementInfo.pinned.not()

      navigationStack.addSafe(nextPinnedElementIndex, navigationStack.removeAt(indexOfElem))

      return@write if (navHistoryElementInfo.pinned) {
        PinResult.Pinned
      } else {
        PinResult.Unpinned
      }
    }

    if (!pinResult.success) {
      return pinResult
    }

    navStackChanged()
    return pinResult
  }

  fun deleteNavElement(descriptor: ChanDescriptor) {
    removeNavElements(listOf(descriptor))
  }

  fun removeNavElements(descriptors: Collection<ChanDescriptor>) {
    if (descriptors.isEmpty()) {
      return
    }

    val removed = lock.write {
      var removed = false

      descriptors.forEach { chanDescriptor ->
        val indexOfElem = navigationStack.indexOfFirst { navHistoryElement ->
          return@indexOfFirst when (navHistoryElement) {
            is NavHistoryElement.Catalog -> navHistoryElement.descriptor == chanDescriptor
            is NavHistoryElement.Thread -> navHistoryElement.descriptor == chanDescriptor
          }
        }

        if (indexOfElem < 0) {
          return@forEach
        }

        navigationStack.removeAt(indexOfElem)
        removed = true
      }

      return@write removed
    }

    if (!removed) {
      return
    }

    navStackChanged()
  }

  fun removeNonBookmarkNavElements(bookmarkDescriptors: Set<ChanDescriptor.ThreadDescriptor>) {
    if (bookmarkDescriptors.isEmpty()) {
      return
    }

    val removed = lock.write {
      var removed = false

      navigationStack.mutableIteration { mutableIterator, navHistoryElement ->
        val descriptor = navHistoryElement.descriptor()
        if (descriptor !is ChanDescriptor.ThreadDescriptor) {
          removed = true
          mutableIterator.remove()
          return@mutableIteration true
        }

        val threadDescriptor = descriptor as ChanDescriptor.ThreadDescriptor
        if (threadDescriptor !in bookmarkDescriptors) {
          removed = true
          mutableIterator.remove()
        }

        return@mutableIteration true
      }

      return@write removed
    }

    if (!removed) {
      return
    }

    navStackChanged()
  }

  fun clear() {
    val cleared = lock.write {
      if (navigationStack.isEmpty()) {
        return@write false
      }

      navigationStack.clear()
      return@write true
    }

    if (!cleared) {
      return
    }

    navStackChanged()
  }

  private fun persisNavigationStack(eager: Boolean = false) {
    if (!suspendableInitializer.isInitialized()) {
      return
    }

    if (!persistRunning.compareAndSet(false, true)) {
      return
    }

    if (eager) {
      appScope.launch(Dispatchers.Default) {
        Logger.d(TAG, "persistNavigationStack eager called")

        try {
          val navStackCopy = lock.read { navigationStack.toList() }

          historyNavigationRepository.persist(navStackCopy)
            .safeUnwrap { error ->
              Logger.e(TAG, "Error while trying to persist navigation stack", error)
              return@launch
            }
        } finally {
          Logger.d(TAG, "persistNavigationStack eager finished")
          persistRunning.set(false)
        }
      }
    } else {
      serializedCoroutineExecutor.post {
        Logger.d(TAG, "persistNavigationStack async called")

        try {
          val navStackCopy = lock.read { navigationStack.toList() }

          historyNavigationRepository.persist(navStackCopy)
            .safeUnwrap { error ->
              Logger.e(TAG, "Error while trying to persist navigation stack", error)
              return@post
            }
        } finally {
          Logger.d(TAG, "persistNavigationStack async finished")
          persistRunning.set(false)
        }
      }
    }
  }

  private fun navStackChanged() {
    navigationStackChangesSubject.onNext(Unit)
    persistTaskSubject.onNext(Unit)
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
    NoSpaceToPin(false),
    Failure(false),
  }

  data class NewNavigationElement(
    val descriptor: ChanDescriptor,
    val thumbnailImageUrl: HttpUrl,
    val title: String
  )

  companion object {
    private const val TAG = "HistoryNavigationManager"
    private const val MAX_NAV_HISTORY_ENTRIES = 256
  }
}