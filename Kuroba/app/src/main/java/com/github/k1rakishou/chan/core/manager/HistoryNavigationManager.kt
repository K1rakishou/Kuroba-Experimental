package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.navigation.NavHistoryElement
import com.github.k1rakishou.model.data.navigation.NavHistoryElementInfo
import com.github.k1rakishou.model.repository.HistoryNavigationRepository
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

class HistoryNavigationManager(
  private val appScope: CoroutineScope,
  private val historyNavigationRepository: HistoryNavigationRepository,
  private val applicationVisibilityManager: ApplicationVisibilityManager
) {
  private val navigationStackChangesSubject = PublishProcessor.create<Unit>()
  private val persistTaskSubject = PublishProcessor.create<Unit>()
  private val persistRunning = AtomicBoolean(false)
  private val suspendableInitializer = SuspendableInitializer<Unit>("HistoryNavigationManager")

  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val navigationStack = mutableListWithCap<NavHistoryElement>(64)

  @OptIn(ExperimentalTime::class)
  fun initialize() {
    Logger.d(TAG, "HistoryNavigationManager.initialize()")
    serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

    appScope.launch {
      applicationVisibilityManager.addListener { visibility ->
        if (!suspendableInitializer.isInitialized()) {
          return@addListener
        }

        if (visibility != ApplicationVisibility.Background) {
          return@addListener
        }

        persisNavigationStack(true)
      }

      appScope.launch {
        persistTaskSubject
          .asFlow()
          .debounce(1.seconds)
          .collect {
            persisNavigationStack()
          }
      }

      appScope.launch(Dispatchers.Default) {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val loadedNavElementsResult = historyNavigationRepository.initialize(MAX_NAV_HISTORY_ENTRIES)
        when (loadedNavElementsResult) {
          is ModularResult.Value -> {
            lock.write {
              navigationStack.clear()
              navigationStack.addAll(loadedNavElementsResult.value)
            }

            suspendableInitializer.initWithValue(Unit)
            Logger.d(TAG, "HistoryNavigationManager initialized!")
          }
          is ModularResult.Error -> {
            Logger.e(TAG, "Exception while initializing HistoryNavigationManager", loadedNavElementsResult.error)
            suspendableInitializer.initWithError(loadedNavElementsResult.error)
          }
        }

        navStackChanged()
      }
    }
  }

  fun getAll(): List<NavHistoryElement> {
    BackgroundUtils.ensureMainThread()

    return lock.read { navigationStack.toList() }
  }

  fun getNavElementAtTop(): NavHistoryElement? {
    BackgroundUtils.ensureMainThread()

    return lock.read { navigationStack.firstOrNull() }
  }

  fun getFirstCatalogNavElement(): NavHistoryElement? {
    BackgroundUtils.ensureMainThread()

    return lock.read {
      return@read navigationStack.firstOrNull { navHistoryElement ->
        navHistoryElement is NavHistoryElement.Catalog
      }
    }
  }

  fun getFirstThreadNavElement(): NavHistoryElement? {
    BackgroundUtils.ensureMainThread()

    return lock.read {
      return@read navigationStack.firstOrNull { navHistoryElement ->
        navHistoryElement is NavHistoryElement.Thread
      }
    }
  }

  fun listenForNavigationStackChanges(): Flowable<Unit> {
    BackgroundUtils.ensureMainThread()

    return navigationStackChangesSubject
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "listenForNavigationStackChanges error", error) }
      .hide()
  }

  suspend fun awaitUntilInitialized() = suspendableInitializer.awaitUntilInitialized()

  fun createNewNavElement(
    descriptor: ChanDescriptor,
    thumbnailImageUrl: HttpUrl,
    title: String
  ) {
    BackgroundUtils.ensureMainThread()

    serializedCoroutineExecutor.post {
      BackgroundUtils.ensureMainThread()

      val navElementInfo = NavHistoryElementInfo(thumbnailImageUrl, title)
      val navElement = when (descriptor) {
        is ChanDescriptor.ThreadDescriptor -> NavHistoryElement.Thread(descriptor, navElementInfo)
        is ChanDescriptor.CatalogDescriptor -> NavHistoryElement.Catalog(descriptor, navElementInfo)
      }

      if (!addNewOrIgnore(navElement)) {
        return@post
      }

      navStackChanged()
    }
  }

  fun moveNavElementToTop(descriptor: ChanDescriptor) {
    BackgroundUtils.ensureMainThread()

    serializedCoroutineExecutor.post {
      BackgroundUtils.ensureMainThread()

      val indexOfElem = lock.read {
        return@read navigationStack.indexOfFirst { navHistoryElement ->
          return@indexOfFirst when (navHistoryElement) {
            is NavHistoryElement.Catalog -> navHistoryElement.descriptor == descriptor
            is NavHistoryElement.Thread -> navHistoryElement.descriptor == descriptor
          }
        }
      }

      if (indexOfElem < 0) {
        return@post
      }

      // Move the existing navigation element at the top of the list
      lock.write { navigationStack.add(0, navigationStack.removeAt(indexOfElem)) }
      navStackChanged()
    }
  }

  fun onNavElementRemoved(descriptor: ChanDescriptor) {
    BackgroundUtils.ensureMainThread()

    serializedCoroutineExecutor.post {
      BackgroundUtils.ensureMainThread()

      val indexOfElem = lock.read {
        return@read navigationStack.indexOfFirst { navHistoryElement ->
          return@indexOfFirst when (navHistoryElement) {
            is NavHistoryElement.Catalog -> navHistoryElement.descriptor == descriptor
            is NavHistoryElement.Thread -> navHistoryElement.descriptor == descriptor
          }
        }
      }

      if (indexOfElem < 0) {
        return@post
      }

      lock.write { navigationStack.removeAt(indexOfElem) }
      navStackChanged()
    }
  }

  fun isAtTop(descriptor: ChanDescriptor): Boolean {
    BackgroundUtils.ensureMainThread()

    val topNavElement = lock.read { navigationStack.firstOrNull() }
      ?: return false

    val topNavElementDescriptor = when (topNavElement) {
      is NavHistoryElement.Catalog -> topNavElement.descriptor
      is NavHistoryElement.Thread -> topNavElement.descriptor
    }

    return topNavElementDescriptor == descriptor
  }

  fun retainExistingThreadDescriptors(
    bookmarkThreadDescriptors: Set<ChanDescriptor.ThreadDescriptor>
  ): Set<ChanDescriptor.ThreadDescriptor> {
    val retained = hashSetWithCap<ChanDescriptor.ThreadDescriptor>(32)

    lock.read {
      navigationStack.forEach { navHistoryElement ->
        if (navHistoryElement is NavHistoryElement.Thread
          && navHistoryElement.descriptor in bookmarkThreadDescriptors) {
          retained += navHistoryElement.descriptor
        }
      }
    }

    return retained
  }

  private fun persisNavigationStack(blocking: Boolean = false) {
    BackgroundUtils.ensureMainThread()

    if (!suspendableInitializer.isInitialized()) {
      return
    }

    if (!persistRunning.compareAndSet(false, true)) {
      return
    }

    if (blocking) {
      runBlocking {
        Logger.d(TAG, "persistNavigationStack blocking called")

        try {
          val navStackCopy = lock.read { navigationStack.toList() }

          historyNavigationRepository.persist(navStackCopy)
            .safeUnwrap { error ->
              Logger.e(TAG, "Error while trying to persist navigation stack", error)
              return@runBlocking
            }
        } finally {
          Logger.d(TAG, "persistNavigationStack blocking finished")
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

  private fun addNewOrIgnore(navElement: NavHistoryElement): Boolean {
    BackgroundUtils.ensureMainThread()

    val indexOfElem = lock.read { navigationStack.indexOf(navElement) }
    if (indexOfElem >= 0) {
      return false
    }

    lock.write { navigationStack.add(0, navElement) }
    return true
  }

  private fun navStackChanged() {
    navigationStackChangesSubject.onNext(Unit)
    persistTaskSubject.onNext(Unit)
  }

  companion object {
    private const val TAG = "HistoryNavigationManager"
    private const val MAX_NAV_HISTORY_ENTRIES = 128
  }
}