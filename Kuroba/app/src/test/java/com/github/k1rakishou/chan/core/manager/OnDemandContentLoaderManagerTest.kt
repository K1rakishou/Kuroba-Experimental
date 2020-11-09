package com.github.k1rakishou.chan.core.manager

import android.os.Looper
import com.github.k1rakishou.chan.core.loader.LoaderBatchResult
import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.PostLoaderData
import com.github.k1rakishou.chan.core.model.ChanPostBuilder
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.LoaderType
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.reactivex.Single
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subscribers.TestSubscriber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class OnDemandContentLoaderManagerTest {
  private val workerScheduler = TestScheduler()

  @Before
  fun init() {
    AndroidUtils.init(RuntimeEnvironment.application)
    ShadowLog.stream = System.out
  }

  @Test
  fun `test simple post event should return one update in two seconds`() {
    val (loadable, post) = createTestData()
    val testSubscriber = TestSubscriber<LoaderBatchResult>()
    val loaderManager = OnDemandContentLoaderManager(
      workerScheduler,
      setOf(
        DummyLoader(LoaderType.PrefetchLoader),
        DummyLoader(LoaderType.PostExtraContentLoader),
        DummyLoader(LoaderType.InlinedFileInfoLoader)
      )
    )

    loaderManager.listenPostContentUpdates().subscribe(testSubscriber)
    loaderManager.onPostBind(loadable, post)

    workerScheduler.advanceTimeBy(200, TimeUnit.MILLISECONDS)
    testSubscriber.assertNoValues()
    workerScheduler.advanceTimeBy(
      OnDemandContentLoaderManager.LOADING_DELAY_TIME_MS,
      TimeUnit.MILLISECONDS
    )

    Shadows.shadowOf(Looper.getMainLooper()).idle()

    testSubscriber.assertValueCount(1)
    testSubscriber.assertNoErrors()
    testSubscriber.assertNotComplete()

    val event = testSubscriber.values().first()
    assertTrue(event.results.first() is LoaderResult.Succeeded)
  }

  @Test
  fun `test should return update right away when every loader has cached data`() {
    val (loadable, post) = createTestData()
    val testSubscriber = TestSubscriber<LoaderBatchResult>()
    val loaderManager = OnDemandContentLoaderManager(
      workerScheduler,
      setOf(
        DummyLoader(LoaderType.PrefetchLoader, cached = true),
        DummyLoader(LoaderType.PostExtraContentLoader, cached = true),
        DummyLoader(LoaderType.InlinedFileInfoLoader, cached = true)
      )
    )

    loaderManager.listenPostContentUpdates().subscribe(testSubscriber)
    loaderManager.onPostBind(loadable, post)
    workerScheduler.advanceTimeBy(200, TimeUnit.MILLISECONDS)

    Shadows.shadowOf(Looper.getMainLooper()).idle()

    testSubscriber.assertValueCount(1)
    testSubscriber.assertNoErrors()
    testSubscriber.assertNotComplete()

    val event = testSubscriber.values().first()
    assertTrue(event.results.first() is LoaderResult.Succeeded)
  }

  @Test
  fun `test should not be able to add the same post more than once`() {
    val (loadable, post) = createTestData()
    val testSubscriber = TestSubscriber<LoaderBatchResult>()
    val loaderManager = OnDemandContentLoaderManager(
      workerScheduler,
      setOf(
        DummyLoader(LoaderType.PrefetchLoader),
        DummyLoader(LoaderType.PostExtraContentLoader),
        DummyLoader(LoaderType.InlinedFileInfoLoader)
      )
    )

    loaderManager.listenPostContentUpdates().subscribe(testSubscriber)
    loaderManager.onPostBind(loadable, post)
    loaderManager.onPostBind(loadable, post)
    loaderManager.onPostBind(loadable, post)
    loaderManager.onPostBind(loadable, post)
    workerScheduler.advanceTimeBy(
      advanceByALot(),
      TimeUnit.MILLISECONDS
    )

    Shadows.shadowOf(Looper.getMainLooper()).idle()

    testSubscriber.assertValueCount(1)
    testSubscriber.assertNoErrors()
    testSubscriber.assertNotComplete()

    val event = testSubscriber.values().first()
    assertTrue(event.results.first() is LoaderResult.Succeeded)
  }

  @Test
  fun `test should not return any updates when unbind was called`() {
    val (loadable, post) = createTestData()
    val testSubscriber = TestSubscriber<LoaderBatchResult>()
    val loaderManager = OnDemandContentLoaderManager(
      workerScheduler,
      setOf(
        DummyLoader(LoaderType.PrefetchLoader),
        DummyLoader(LoaderType.PostExtraContentLoader),
        DummyLoader(LoaderType.InlinedFileInfoLoader)
      )
    )

    loaderManager.listenPostContentUpdates().subscribe(testSubscriber)
    loaderManager.onPostBind(loadable, post)

    workerScheduler.advanceTimeBy(900, TimeUnit.MILLISECONDS)
    testSubscriber.assertNoValues()
    loaderManager.onPostUnbind(loadable, post, true)
    workerScheduler.advanceTimeBy(
      OnDemandContentLoaderManager.LOADING_DELAY_TIME_MS,
      TimeUnit.MILLISECONDS
    )

    Shadows.shadowOf(Looper.getMainLooper()).idle()

    testSubscriber.assertNoValues()
    testSubscriber.assertNoErrors()
    testSubscriber.assertNotComplete()
  }

  @Test
  fun `test should return error for loader that failed to load post content`() {
    val (loadable, post) = createTestData()
    val testSubscriber = TestSubscriber<LoaderBatchResult>()
    val loaderManager = OnDemandContentLoaderManager(
      workerScheduler,
      setOf(
        DummyLoader(LoaderType.PrefetchLoader),
        DummyLoader(LoaderType.PostExtraContentLoader, failLoading = true)
      )
    )

    loaderManager.listenPostContentUpdates().subscribe(testSubscriber)
    loaderManager.onPostBind(loadable, post)

    workerScheduler.advanceTimeBy(
      advanceByALot(),
      TimeUnit.MILLISECONDS
    )

    Shadows.shadowOf(Looper.getMainLooper()).idle()

    testSubscriber.assertValueCount(1)
    testSubscriber.assertNoErrors()
    testSubscriber.assertNotComplete()

    val events = testSubscriber.values().first()
    assertEquals(2, events.results.size)

    val eventMap = hashMapOf<LoaderType, Boolean>().apply {
      put(LoaderType.PrefetchLoader, false)
      put(LoaderType.PostExtraContentLoader, false)
    }

    events.results.forEach { event ->
      eventMap[event.loaderType] = true

      when (event.loaderType) {
        LoaderType.PrefetchLoader -> assertTrue(event is LoaderResult.Succeeded)
        LoaderType.PostExtraContentLoader -> assertTrue(event is LoaderResult.Failed)
        LoaderType.InlinedFileInfoLoader,
        LoaderType.Chan4CloudFlareImagePreLoader -> throw RuntimeException("Shouldn't happen")
      }.exhaustive
    }

    assertTrue(eventMap.values.all { eventPresent -> eventPresent })
  }

  @Test
  fun `test bind 1000 posts check no backpressure exception`() {
    val testSubscriber = TestSubscriber<LoaderBatchResult>()
    val loaderManager = OnDemandContentLoaderManager(
      workerScheduler,
      setOf(
        DummyLoader(LoaderType.PrefetchLoader),
        DummyLoader(LoaderType.PostExtraContentLoader, failLoading = true),
        DummyLoader(LoaderType.InlinedFileInfoLoader)
      )
    )

    loaderManager.listenPostContentUpdates().subscribe(testSubscriber)

    repeat(1000) { i ->
      val (loadable, post) = createTestData(i + 1000L)
      loaderManager.onPostBind(loadable, post)
    }

    workerScheduler.advanceTimeBy(15000, TimeUnit.MILLISECONDS)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    testSubscriber.assertValueCount(1000)
    testSubscriber.assertNoErrors()
    testSubscriber.assertNotComplete()
  }

  @Test
  fun `test startLoading is never called when canceling before delay passed`() {
    val testSubscriber = TestSubscriber<LoaderBatchResult>()
    val loaders = setOf(
      spy(DummyLoader(LoaderType.PrefetchLoader)),
      spy(DummyLoader(LoaderType.PostExtraContentLoader))
    )
    val loaderManager = OnDemandContentLoaderManager(
      workerScheduler,
      loaders
    )

    loaderManager.listenPostContentUpdates().subscribe(testSubscriber)

    repeat(10) { i ->
      val (loadable, post) = createTestData(i + 1000L)
      loaderManager.onPostBind(loadable, post)
    }

    val (threadDescriptor, _) = createTestData()
    loaderManager.cancelAllForDescriptor(threadDescriptor)

    workerScheduler.advanceTimeBy(
      advanceByALot(),
      TimeUnit.MILLISECONDS
    )

    loaders.forEach { loader ->
      verify(loader, never()).startLoading(any())
      verify(loader, times(10)).cancelLoading(any())
    }

    Shadows.shadowOf(Looper.getMainLooper()).idle()

    testSubscriber.assertNoValues()
    testSubscriber.assertNoErrors()
    testSubscriber.assertNotComplete()
  }

  @Test
  fun `test startLoading and cancelLoading are called when canceling after delay passed`() {
    val testSubscriber = TestSubscriber<LoaderBatchResult>()
    val loaders = setOf(
      spy(DummyLoader(LoaderType.PrefetchLoader)),
      spy(DummyLoader(LoaderType.PostExtraContentLoader))
    )
    val loaderManager = OnDemandContentLoaderManager(
      workerScheduler,
      loaders
    )

    loaderManager.listenPostContentUpdates().subscribe(testSubscriber)

    repeat(10) { i ->
      val (loadable, post) = createTestData(i + 1000L)
      loaderManager.onPostBind(loadable, post)
    }

    val (threadDescriptor, _) = createTestData()

    workerScheduler.advanceTimeBy(
      advanceByALot(),
      TimeUnit.MILLISECONDS
    )
    loaderManager.cancelAllForDescriptor(threadDescriptor)

    loaders.forEach { loader ->
      verify(loader, times(10)).startLoading(any())
      verify(loader, times(10)).cancelLoading(any())
    }

    Shadows.shadowOf(Looper.getMainLooper()).idle()

    testSubscriber.assertValueCount(10)
    testSubscriber.assertNoErrors()
    testSubscriber.assertNotComplete()
  }

  private fun advanceByALot() = OnDemandContentLoaderManager.LOADING_DELAY_TIME_MS + 300

  private fun createTestData(postNo: Long = 1): TestData {
    val threadDescriptor = ChanDescriptor.ThreadDescriptor.create("4chan", "test", 1234)

    val post = ChanPostBuilder()
      .boardDescriptor(threadDescriptor.boardDescriptor)
      .id(postNo)
      .opId(postNo)
      .setUnixTimestampSeconds(System.currentTimeMillis())
      .comment("Test comment")
      .build()

    return TestData(threadDescriptor, post)
  }

  data class TestData(val threadDescriptor: ChanDescriptor.ThreadDescriptor, val post: ChanPost)

  open class DummyLoader(
    loaderType: LoaderType,
    private val failLoading: Boolean = false,
    private val cached: Boolean = false
  ) : OnDemandContentLoader(loaderType) {

    override fun isCached(postLoaderData: PostLoaderData): Single<Boolean> {
      return Single.just(cached)
    }

    override fun startLoading(postLoaderData: PostLoaderData): Single<LoaderResult> {
      return if (failLoading) {
        Single.just(LoaderResult.Failed(loaderType))
      } else {
        Single.just(LoaderResult.Succeeded(loaderType, true))
      }
    }

    override fun cancelLoading(postLoaderData: PostLoaderData) {
    }
  }

}