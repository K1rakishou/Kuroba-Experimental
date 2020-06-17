package com.github.adamantcheese.chan.core.manager

import androidx.annotation.GuardedBy
import androidx.core.util.Pools
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.SuspendableInitializer
import com.github.adamantcheese.model.data.bookmark.ThreadBookmark
import com.github.adamantcheese.model.data.bookmark.ThreadBookmarkView
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.repository.BookmarksRepository
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class BookmarksManager(
  private val isDevAppFlavor: Boolean,
  private val appScope: CoroutineScope,
  private val applicationVisibilityManager: ApplicationVisibilityManager,
  private val bookmarksRepository: BookmarksRepository
) {
  private val lock = ReentrantReadWriteLock()
  private val persistTaskSubject = PublishProcessor.create<Unit>()
  private val bookmarksChangedSubject = PublishProcessor.create<Unit>()
  private val suspendableInitializer = SuspendableInitializer<Unit>("BookmarksManager")
  private val persistRunning = AtomicBoolean(false)

  @GuardedBy("lock")
  private val bookmarks = mutableMapOf<ChanDescriptor.ThreadDescriptor, ThreadBookmark>()
  @GuardedBy("lock")
  private val orders = mutableListOf<ChanDescriptor.ThreadDescriptor>()
  @GuardedBy("lock")
  private val threadBookmarkPools = Pools.SynchronizedPool<ThreadBookmark>(512)

  init {
    appScope.launch {
      suspendableInitializer.awaitUntilInitialized()

      applicationVisibilityManager.listenForAppVisibilityUpdates()
        .asFlow()
        .filter { visibility -> visibility == ApplicationVisibility.Background }
        .collect { persistBookmarks(true) }
    }

    appScope.launch {
      persistTaskSubject
        .throttleFirst(5, TimeUnit.SECONDS)
        .collect { persistBookmarks() }
    }

    appScope.launch {
      @Suppress("MoveVariableDeclarationIntoWhen")
      val bookmarksResult = bookmarksRepository.initialize()
      when (bookmarksResult) {
        is ModularResult.Value -> {
          BackgroundUtils.ensureMainThread()

          lock.write {
            bookmarksResult.value.forEach { threadBookmark ->
              bookmarks[threadBookmark.threadDescriptor] = threadBookmark
              orders.add(threadBookmark.threadDescriptor)
            }
          }

          suspendableInitializer.initWithValue(Unit)

          Logger.d(TAG, "BookmarksManager initialized!")
        }
        is ModularResult.Error -> {
          Logger.e(TAG, "Exception while initializing BookmarksManager", bookmarksResult.error)
          suspendableInitializer.initWithError(bookmarksResult.error)
        }
      }

      bookmarksChanged()
    }
  }

  fun listenForBookmarksChanges(): Flowable<Unit> {
    return bookmarksChangedSubject
      .observeOn(AndroidSchedulers.mainThread())
      .hide()
  }

  suspend fun awaitUntilInitialized() = suspendableInitializer.awaitUntilInitialized()

  fun isReady() = suspendableInitializer.isInitialized()

  fun exists(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean {
    return lock.read { bookmarks.containsKey(threadDescriptor) }
  }

  @JvmOverloads
  fun createBookmark(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    title: String? = null,
    thumbnailUrl: HttpUrl? = null
  ) {
    lock.write {
      require(!bookmarks.containsKey(threadDescriptor)) {
        "Bookmark already exists ($threadDescriptor)"
      }

      val threadBookmark = ThreadBookmark.create(threadDescriptor).apply {
        this.title = title
        this.thumbnailUrl = thumbnailUrl
      }

      if (isDevAppFlavor) {
        check(!orders.contains(threadDescriptor)) { "orders already contains $threadDescriptor" }
      }

      orders.add(0, threadDescriptor)
      bookmarks[threadDescriptor] = threadBookmark

      bookmarksChanged()
      Logger.d(TAG, "Bookmark created ($threadDescriptor)")
    }
  }

  fun deleteBookmark(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    lock.write {
      require(bookmarks.containsKey(threadDescriptor)) {
        "Bookmark does not exist ($threadDescriptor)"
      }

      bookmarks.remove(threadDescriptor)
      orders.remove(threadDescriptor)

      bookmarksChanged()
      Logger.d(TAG, "Bookmark deleted ($threadDescriptor)")
    }
  }

  fun updateBookmark(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    mutator: (ThreadBookmark) -> Unit
  ): Boolean {
    return lock.write {
      if (!bookmarks.containsKey(threadDescriptor)) {
        ensureNotContainsOrder(threadDescriptor)
        return@write false
      }

      ensureContainsOrder(threadDescriptor)
      val oldThreadBookmark = bookmarks[threadDescriptor]!!

      withThreadBookmark(threadDescriptor) { mutatedBookmark ->
        mutator(mutatedBookmark)

        if (oldThreadBookmark == mutatedBookmark) {
          return@withThreadBookmark
        }

        bookmarks[threadDescriptor]!!.fill(mutatedBookmark)
      }

      bookmarksChanged()
      Logger.d(TAG, "Bookmark updated ($threadDescriptor)")

      return@write true
    }
  }

  fun viewBookmark(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    viewer: (ThreadBookmarkView) -> Unit
  ) {
    lock.read {
      if (!bookmarks.containsKey(threadDescriptor)) {
        ensureNotContainsOrder(threadDescriptor)
        return@read
      }

      ensureContainsOrder(threadDescriptor)

      val threadBookmark = bookmarks[threadDescriptor]!!
      viewer(ThreadBookmarkView.fromThreadBookmark(threadBookmark))
    }
  }

  fun viewAllBookmarksOrdered(viewer: (ThreadBookmarkView) -> Unit) {
    lock.read {
      orders.forEach { threadDescriptor ->
        val threadBookmark = checkNotNull(bookmarks[threadDescriptor]) {
          "Bookmarks does not contain ${threadDescriptor} even though orders does"
        }

        viewer(ThreadBookmarkView.fromThreadBookmark(threadBookmark))
      }
    }
  }

  fun <T> mapBookmarksOrdered(mapper: (ThreadBookmarkView) -> T): List<T> {
    return lock.read {
      return@read orders.map { threadDescriptor ->
        val threadBookmark = checkNotNull(bookmarks[threadDescriptor]) {
          "Bookmarks does not contain ${threadDescriptor} even though orders does"
        }

        return@map mapper(ThreadBookmarkView.fromThreadBookmark(threadBookmark))
      }
    }
  }

  fun onBookmarkMoved(from: Int, to: Int) {
    require(from >= 0) { "Bad from: $from" }
    require(to >= 0) { "Bad to: $to" }

    lock.write {
      orders.add(to, orders.removeAt(from))
      bookmarksChanged()

      Logger.d(TAG, "Bookmark moved (from=$from, to=$to)")
    }
  }

  fun bookmarksCount(): Int {
    return lock.read {
      if (isDevAppFlavor) {
        check(bookmarks.size == orders.size) {
          "Inconsistency detected! bookmarks.size (${bookmarks.size}) != orders.size (${orders.size})"
        }
      }

      return@read bookmarks.size
    }
  }

  private fun ensureNotContainsOrder(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (isDevAppFlavor) {
      check(!orders.contains(threadDescriptor)) {
        "Orders contains ($threadDescriptor) when bookmarks doesn't!"
      }
    }
  }

  private fun ensureContainsOrder(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (isDevAppFlavor) {
      check(orders.contains(threadDescriptor)) {
        "Orders does not contain ($threadDescriptor) when bookmarks does!"
      }
    }
  }

  private fun withThreadBookmark(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    func: (ThreadBookmark) -> Unit
  ) {
    val oldThreadBookmark = bookmarks[threadDescriptor]!!
    val threadBookmark = threadBookmarkPools.acquire()
      ?: ThreadBookmark.create(threadDescriptor)

    threadBookmark.fill(oldThreadBookmark)

    try {
      func(threadBookmark)
    } finally {
      threadBookmarkPools.release(threadBookmark)
    }

  }

  private fun bookmarksChanged() {
    persistTaskSubject.onNext(Unit)
    bookmarksChangedSubject.onNext(Unit)
  }

  private fun persistBookmarks(blocking: Boolean = false) {
    BackgroundUtils.ensureMainThread()

    if (!persistRunning.compareAndSet(false, true)) {
      return
    }

    if (blocking) {
      runBlocking {
        Logger.d(TAG, "persistBookmarks blocking called")

        try {
          bookmarksRepository.persist(getBookmarksOrdered()).safeUnwrap { error ->
            Logger.e(TAG, "Failed to persist bookmarks blockingly", error)
            return@runBlocking
          }
        } finally {
          Logger.d(TAG, "persistBookmarks blocking finished")
          persistRunning.set(false)
        }
      }
    } else {
      Logger.d(TAG, "persistBookmarks async called")

      appScope.launch {
        try {
          bookmarksRepository.persist(getBookmarksOrdered()).safeUnwrap { error ->
            Logger.e(TAG, "Failed to persist bookmarks async", error)
            return@launch
          }
        } finally {
          Logger.d(TAG, "persistBookmarks async finished")
          persistRunning.set(false)
        }
      }
    }
  }

  private fun getBookmarksOrdered(): List<ThreadBookmark> {
    return lock.read {
      return@read orders.map { threadDescriptor ->
        val threadBookmark = checkNotNull(bookmarks[threadDescriptor]) {
          "Bookmarks does not contain ${threadDescriptor} even though orders does"
        }

        return@map threadBookmark.copy()
      }
    }
  }

  companion object {
    private const val TAG = "BookmarksManager"
  }
}
