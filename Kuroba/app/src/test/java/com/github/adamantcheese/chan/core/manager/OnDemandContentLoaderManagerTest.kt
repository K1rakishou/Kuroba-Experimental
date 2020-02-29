package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.core.manager.loader.OnDemandContentLoader
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4
import com.github.adamantcheese.chan.utils.AndroidUtils
import io.reactivex.Completable
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
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class OnDemandContentLoaderManagerTest {
    private val testScheduler = TestScheduler()

    @Before
    fun init() {
        AndroidUtils.init(RuntimeEnvironment.application)
        ShadowLog.stream = System.out
    }

    @Test
    fun `test simple post event should return one update in one second`() {
        val (loadable, post) = createTestData()
        val testSubscriber = TestSubscriber<OnDemandContentLoader.LoaderBatchResult>()
        val loaderManager = OnDemandContentLoaderManager(
                testScheduler,
                setOf(DummyLoader(OnDemandContentLoader.LoaderType.PrefetchLoader, false))
        )

        loaderManager.listenPostContentUpdates().subscribe(testSubscriber)
        loaderManager.onPostBind(loadable, post)

        testScheduler.advanceTimeBy(900, TimeUnit.MILLISECONDS)
        testSubscriber.assertNoValues()

        testScheduler.advanceTimeBy(200, TimeUnit.MILLISECONDS)

        testSubscriber.assertValueCount(1)
        testSubscriber.assertNoErrors()
        testSubscriber.assertNotComplete()

        val event = testSubscriber.values().first()
        assertTrue(event.results.first() is OnDemandContentLoader.LoaderResult.Success)
    }

    @Test
    fun `test should not be able to add the same post more than once`() {
        val (loadable, post) = createTestData()
        val testSubscriber = TestSubscriber<OnDemandContentLoader.LoaderBatchResult>()
        val loaderManager = OnDemandContentLoaderManager(
                testScheduler,
                setOf(DummyLoader(OnDemandContentLoader.LoaderType.PrefetchLoader, false))
        )

        loaderManager.listenPostContentUpdates().subscribe(testSubscriber)
        loaderManager.onPostBind(loadable, post)
        loaderManager.onPostBind(loadable, post)
        loaderManager.onPostBind(loadable, post)
        loaderManager.onPostBind(loadable, post)

        testScheduler.advanceTimeBy(1200, TimeUnit.MILLISECONDS)

        testSubscriber.assertValueCount(1)
        testSubscriber.assertNoErrors()
        testSubscriber.assertNotComplete()

        val event = testSubscriber.values().first()
        assertTrue(event.results.first() is OnDemandContentLoader.LoaderResult.Success)
    }

    @Test
    fun `test should not return any updates when unbind was called`() {
        val (loadable, post) = createTestData()
        val testSubscriber = TestSubscriber<OnDemandContentLoader.LoaderBatchResult>()
        val loaderManager = OnDemandContentLoaderManager(
                testScheduler,
                setOf(DummyLoader(OnDemandContentLoader.LoaderType.PrefetchLoader, false))
        )

        loaderManager.listenPostContentUpdates().subscribe(testSubscriber)
        loaderManager.onPostBind(loadable, post)

        testScheduler.advanceTimeBy(900, TimeUnit.MILLISECONDS)
        testSubscriber.assertNoValues()
        loaderManager.onPostUnbind(loadable, post)

        testScheduler.advanceTimeBy(200, TimeUnit.MILLISECONDS)

        testSubscriber.assertNoValues()
        testSubscriber.assertNoErrors()
        testSubscriber.assertNotComplete()
    }

    @Test
    fun `test should return error for loader that failed to load post content`() {
        val (loadable, post) = createTestData()
        val testSubscriber = TestSubscriber<OnDemandContentLoader.LoaderBatchResult>()
        val loaderManager = OnDemandContentLoaderManager(
                testScheduler,
                setOf(
                        DummyLoader(OnDemandContentLoader.LoaderType.PrefetchLoader, false),
                        DummyLoader(OnDemandContentLoader.LoaderType.YoutubeLinkDurationsLoader, true),
                        DummyLoader(OnDemandContentLoader.LoaderType.YoutubeLinkTitlesLoader, true),
                        DummyLoader(OnDemandContentLoader.LoaderType.InlinedFileSizeLoader, false)
                )
        )

        loaderManager.listenPostContentUpdates().subscribe(testSubscriber)
        loaderManager.onPostBind(loadable, post)

        testScheduler.advanceTimeBy(1200, TimeUnit.MILLISECONDS)

        testSubscriber.assertValueCount(1)
        testSubscriber.assertNoErrors()
        testSubscriber.assertNotComplete()

        val events = testSubscriber.values().first()
        assertEquals(4, events.results.size)

        val eventMap = hashMapOf<OnDemandContentLoader.LoaderType, Boolean>().apply {
            put(OnDemandContentLoader.LoaderType.PrefetchLoader, false)
            put(OnDemandContentLoader.LoaderType.YoutubeLinkDurationsLoader, false)
            put(OnDemandContentLoader.LoaderType.YoutubeLinkTitlesLoader, false)
            put(OnDemandContentLoader.LoaderType.InlinedFileSizeLoader, false)
        }

        events.results.forEach { event ->
            eventMap[event.loaderType] = true

            when (event.loaderType) {
                OnDemandContentLoader.LoaderType.PrefetchLoader -> {
                    assertTrue(event is OnDemandContentLoader.LoaderResult.Success)
                }
                OnDemandContentLoader.LoaderType.YoutubeLinkDurationsLoader -> {
                    assertTrue(event is OnDemandContentLoader.LoaderResult.Error)
                }
                OnDemandContentLoader.LoaderType.YoutubeLinkTitlesLoader -> {
                    assertTrue(event is OnDemandContentLoader.LoaderResult.Error)
                }
                OnDemandContentLoader.LoaderType.InlinedFileSizeLoader -> {
                    assertTrue(event is OnDemandContentLoader.LoaderResult.Success)
                }
            }
        }

        assertTrue(eventMap.values.all { eventPresent -> eventPresent })
    }

    @Test
    fun `test bind 1000 posts check no backpressure exception`() {
        val testSubscriber = TestSubscriber<OnDemandContentLoader.LoaderBatchResult>()
        val loaderManager = OnDemandContentLoaderManager(
                testScheduler,
                setOf(
                        DummyLoader(OnDemandContentLoader.LoaderType.PrefetchLoader, false),
                        DummyLoader(OnDemandContentLoader.LoaderType.YoutubeLinkDurationsLoader, true),
                        DummyLoader(OnDemandContentLoader.LoaderType.YoutubeLinkTitlesLoader, true),
                        DummyLoader(OnDemandContentLoader.LoaderType.InlinedFileSizeLoader, false)
                )
        )

        loaderManager.listenPostContentUpdates().subscribe(testSubscriber)

        repeat(1000) { i ->
            val (loadable, post) = createTestData(i + 1000)
            loaderManager.onPostBind(loadable, post)
        }

        testScheduler.advanceTimeBy(10000, TimeUnit.MILLISECONDS)

        testSubscriber.assertValueCount(1000)
        testSubscriber.assertNoErrors()
        testSubscriber.assertNotComplete()
    }

    private fun createTestData(postNo: Int = 1): TestData {
        val site = Chan4()

        val board = Board.fromSiteNameCode(site, "4chan", "test")
        val loadable = Loadable.forThread(site, board, 1, "Test")

        val post = Post.Builder()
                .board(board)
                .id(postNo)
                .opId(postNo)
                .setUnixTimestampSeconds(System.currentTimeMillis())
                .comment("Test comment")
                .build()

        return TestData(loadable, post)
    }

    data class TestData(val loadable: Loadable, val post: Post)

    class DummyLoader(loaderType: LoaderType, val failLoading: Boolean) : OnDemandContentLoader(loaderType) {

        override fun isAlreadyCached(loadable: Loadable, post: Post): Boolean {
            return false
        }

        override fun startLoading(loadable: Loadable, post: Post): Single<LoaderResult> {
            return if (failLoading) {
                Single.just(LoaderResult.Error(loaderType))
            } else {
                Single.just(LoaderResult.Success(loaderType))
            }
        }

        override fun cancelLoading(loadable: Loadable, post: Post): Completable {
            return Completable.complete()
        }
    }

}