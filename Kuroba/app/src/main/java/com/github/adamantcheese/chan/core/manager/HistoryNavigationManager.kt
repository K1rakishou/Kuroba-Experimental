package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.SuspendableInitializer
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.navigation.NavHistoryElement
import com.github.adamantcheese.model.data.navigation.NavHistoryElementInfo
import com.github.adamantcheese.model.repository.HistoryNavigationRepository
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

class HistoryNavigationManager(
  private val appScope: CoroutineScope,
  private val historyNavigationRepository: HistoryNavigationRepository,
  private val applicationVisibilityManager: ApplicationVisibilityManager
) {
  private val navigationStackChangesSubject = PublishProcessor.create<Unit>()
  private val mutex = Mutex()

  private val navigationStack = mutableListOf<NavHistoryElement>()
  private val suspendableInitializer = SuspendableInitializer<Unit>("HistoryNavigationManager")

  init {
    appScope.launch {
      suspendableInitializer.awaitUntilInitialized()

      applicationVisibilityManager.listenForAppVisibilityUpdates()
        .asFlow()
        .filter { visibility -> visibility == ApplicationVisibility.Background }
        .collect {
          withContext(NonCancellable) {
            historyNavigationRepository.persist(navigationStack)
          }
        }
    }

    appScope.launch {
      when (val loadedNavElementsResult = historyNavigationRepository.initialize()) {
        is ModularResult.Value -> {
          mutex.withLock {
            navigationStack.addAll(loadedNavElementsResult.value)
          }

          suspendableInitializer.initWithValue(Unit)
        }
        is ModularResult.Error -> {
          suspendableInitializer.initWithError(loadedNavElementsResult.error)
        }
      }
    }
  }

  suspend fun getAll(): List<NavHistoryElement> {
    return mutex.withLock { navigationStack.toList() }
  }

  fun listenForNavigationStackChanges(): Flowable<Unit> {
    return navigationStackChangesSubject
      .observeOn(AndroidSchedulers.mainThread())
      .hide()
  }

  suspend fun awaitUntilInitialized() = suspendableInitializer.awaitUntilInitialized()

  fun createNewNavElement(
    descriptor: ChanDescriptor,
    thumbnailImageUrl: HttpUrl,
    title: String
  ) {
    BackgroundUtils.ensureMainThread()

    val navElementInfo = NavHistoryElementInfo(thumbnailImageUrl, title)
    val navElement = when (descriptor) {
      is ChanDescriptor.ThreadDescriptor -> NavHistoryElement.Thread(descriptor, navElementInfo)
      is ChanDescriptor.CatalogDescriptor -> NavHistoryElement.Catalog(descriptor, navElementInfo)
    }

    if (!addNewOrIgnore(navElement)) {
      return
    }

    navigationStackChangesSubject.onNext(Unit)
  }

  fun moveNavElementToTop(descriptor: ChanDescriptor) {
    BackgroundUtils.ensureMainThread()

    val indexOfElem = navigationStack.indexOfFirst { navHistoryElement ->
      return@indexOfFirst when (navHistoryElement) {
        is NavHistoryElement.Catalog -> navHistoryElement.descriptor == descriptor
        is NavHistoryElement.Thread -> navHistoryElement.descriptor == descriptor
      }
    }

    if (indexOfElem < 0) {
      return
    }

    // Move the existing navigation element at the top of the list
    navigationStack.add(0, navigationStack.removeAt(indexOfElem))
    navigationStackChangesSubject.onNext(Unit)
  }

  private fun addNewOrIgnore(navElement: NavHistoryElement): Boolean {
    BackgroundUtils.ensureMainThread()

    val indexOfElem = navigationStack.indexOf(navElement)
    if (indexOfElem >= 0) {
      return false
    }

    navigationStack.add(0, navElement)
    return true
  }

  fun isAtTop(descriptor: ChanDescriptor): Boolean {
    BackgroundUtils.ensureMainThread()

    val topNavElement = navigationStack.firstOrNull()
      ?: return false

    val topNavElementDescriptor = when (topNavElement) {
      is NavHistoryElement.Catalog -> topNavElement.descriptor
      is NavHistoryElement.Thread -> topNavElement.descriptor
    }

    return topNavElementDescriptor == descriptor
  }

}