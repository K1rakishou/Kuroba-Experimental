package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.core.loader.*
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.nhaarman.mockitokotlin2.*
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
    private val workerScheduler = TestScheduler()

    @Before
    fun init() {
        AndroidUtils.init(RuntimeEnvironment.application)
        ShadowLog.stream = System.out
    }

    @Test
    fun `test simple post event should return one update in one second`() {
        val (loadable, post) = createTestData()
        val testSubscriber = TestSubscriber<LoaderBatchResult>()
        val loaderManager = OnDemandContentLoaderManager(
                workerScheduler,
                setOf(DummyLoader(LoaderType.PrefetchLoader))
        )

        loaderManager.listenPostContentUpdates().subscribe(testSubscriber)
        loaderManager.onPostBind(loadable, post)

        workerScheduler.advanceTimeBy(900, TimeUnit.MILLISECONDS)
        testSubscriber.assertNoValues()
        workerScheduler.advanceTimeBy(200, TimeUnit.MILLISECONDS)

        testSubscriber.assertValueCount(1)
        testSubscriber.assertNoErrors()
        testSubscriber.assertNotComplete()

        val event = testSubscriber.values().first()
        assertTrue(event.results.first() is LoaderResult.Success)
    }

    @Test
    fun `test should not be able to add the same post more than once`() {
        val (loadable, post) = createTestData()
        val testSubscriber = TestSubscriber<LoaderBatchResult>()
        val loaderManager = OnDemandContentLoaderManager(
                workerScheduler,
                setOf(DummyLoader(LoaderType.PrefetchLoader))
        )

        loaderManager.listenPostContentUpdates().subscribe(testSubscriber)
        loaderManager.onPostBind(loadable, post)
        loaderManager.onPostBind(loadable, post)
        loaderManager.onPostBind(loadable, post)
        loaderManager.onPostBind(loadable, post)
        workerScheduler.advanceTimeBy(1200, TimeUnit.MILLISECONDS)

        testSubscriber.assertValueCount(1)
        testSubscriber.assertNoErrors()
        testSubscriber.assertNotComplete()

        val event = testSubscriber.values().first()
        assertTrue(event.results.first() is LoaderResult.Success)
    }

    @Test
    fun `test should not return any updates when unbind was called`() {
        val (loadable, post) = createTestData()
        val testSubscriber = TestSubscriber<LoaderBatchResult>()
        val loaderManager = OnDemandContentLoaderManager(
                workerScheduler,
                setOf(DummyLoader(LoaderType.PrefetchLoader))
        )

        loaderManager.listenPostContentUpdates().subscribe(testSubscriber)
        loaderManager.onPostBind(loadable, post)

        workerScheduler.advanceTimeBy(900, TimeUnit.MILLISECONDS)
        testSubscriber.assertNoValues()
        loaderManager.onPostUnbind(loadable, post)
        workerScheduler.advanceTimeBy(200, TimeUnit.MILLISECONDS)

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
                        DummyLoader(LoaderType.YoutubeLinkDurationsLoader, failLoading = true),
                        DummyLoader(LoaderType.YoutubeLinkTitlesLoader, failLoading = true),
                        DummyLoader(LoaderType.InlinedFileSizeLoader)
                )
        )

        loaderManager.listenPostContentUpdates().subscribe(testSubscriber)
        loaderManager.onPostBind(loadable, post)
        workerScheduler.advanceTimeBy(1200, TimeUnit.MILLISECONDS)

        testSubscriber.assertValueCount(1)
        testSubscriber.assertNoErrors()
        testSubscriber.assertNotComplete()

        val events = testSubscriber.values().first()
        assertEquals(4, events.results.size)

        val eventMap = hashMapOf<LoaderType, Boolean>().apply {
            put(LoaderType.PrefetchLoader, false)
            put(LoaderType.YoutubeLinkDurationsLoader, false)
            put(LoaderType.YoutubeLinkTitlesLoader, false)
            put(LoaderType.InlinedFileSizeLoader, false)
        }

        events.results.forEach { event ->
            eventMap[event.loaderType] = true

            when (event.loaderType) {
                LoaderType.PrefetchLoader -> assertTrue(event is LoaderResult.Success)
                LoaderType.YoutubeLinkDurationsLoader -> assertTrue(event is LoaderResult.Error)
                LoaderType.YoutubeLinkTitlesLoader -> assertTrue(event is LoaderResult.Error)
                LoaderType.InlinedFileSizeLoader -> assertTrue(event is LoaderResult.Success)
            }
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
                        DummyLoader(LoaderType.YoutubeLinkDurationsLoader, failLoading = true),
                        DummyLoader(LoaderType.YoutubeLinkTitlesLoader, failLoading = true),
                        DummyLoader(LoaderType.InlinedFileSizeLoader)
                )
        )

        loaderManager.listenPostContentUpdates().subscribe(testSubscriber)

        repeat(1000) { i ->
            val (loadable, post) = createTestData(i + 1000)
            loaderManager.onPostBind(loadable, post)
        }

        workerScheduler.advanceTimeBy(10000, TimeUnit.MILLISECONDS)

        testSubscriber.assertValueCount(1000)
        testSubscriber.assertNoErrors()
        testSubscriber.assertNotComplete()
    }

    @Test
    fun `test startLoading is never called when canceling before delay passed`() {
        val testSubscriber = TestSubscriber<LoaderBatchResult>()
        val loaders = setOf(
                spy(DummyLoader(LoaderType.PrefetchLoader, isCache = true)),
                spy(DummyLoader(LoaderType.YoutubeLinkDurationsLoader, isCache = true)),
                spy(DummyLoader(LoaderType.YoutubeLinkTitlesLoader)),
                spy(DummyLoader(LoaderType.InlinedFileSizeLoader))
        )
        val expectedToBeCalledLoaderTypes = listOf(
                LoaderType.PrefetchLoader,
                LoaderType.YoutubeLinkDurationsLoader,
                LoaderType.YoutubeLinkTitlesLoader
        )
        val loaderManager = OnDemandContentLoaderManager(
                workerScheduler,
                loaders
        )

        loaderManager.listenPostContentUpdates().subscribe(testSubscriber)

        repeat(10) { i ->
            val (loadable, post) = createTestData(i + 1000)
            loaderManager.onPostBind(loadable, post)
        }

        val (_, loadable) = createTestLoadable()
        loaderManager.cancelAllForLoadable(loadable)

        workerScheduler.advanceTimeBy(1500, TimeUnit.MILLISECONDS)

        loaders.forEach { loader ->
            if (loader.loaderType in expectedToBeCalledLoaderTypes) {
                verify(loader, times(10)).isAlreadyCached(any())
            } else {
                verify(loader, never()).isAlreadyCached(any())
            }

            verify(loader, never()).startLoading(any())
            verify(loader, times(10)).cancelLoading(any())
        }

        testSubscriber.assertNoValues()
        testSubscriber.assertNoErrors()
        testSubscriber.assertNotComplete()
    }

    @Test
    fun `test startLoading and cancelLoading are called when canceling after delay passed`() {
        val testSubscriber = TestSubscriber<LoaderBatchResult>()
        val loaders = setOf(
                spy(DummyLoader(LoaderType.PrefetchLoader)),
                spy(DummyLoader(LoaderType.YoutubeLinkDurationsLoader)),
                spy(DummyLoader(LoaderType.YoutubeLinkTitlesLoader)),
                spy(DummyLoader(LoaderType.InlinedFileSizeLoader))
        )
        val expectedToBeCalledLoaderTypes = listOf(LoaderType.PrefetchLoader)
        val loaderManager = OnDemandContentLoaderManager(
                workerScheduler,
                loaders
        )

        loaderManager.listenPostContentUpdates().subscribe(testSubscriber)

        repeat(10) { i ->
            val (loadable, post) = createTestData(i + 1000)
            loaderManager.onPostBind(loadable, post)
        }

        val (_, loadable) = createTestLoadable()

        workerScheduler.advanceTimeBy(1500, TimeUnit.MILLISECONDS)
        loaderManager.cancelAllForLoadable(loadable)

        loaders.forEach { loader ->
            if (loader.loaderType in expectedToBeCalledLoaderTypes) {
                verify(loader, times(10)).isAlreadyCached(any())
            } else {
                verify(loader, never()).isAlreadyCached(any())
            }

            verify(loader, times(10)).startLoading(any())
            verify(loader, times(10)).cancelLoading(any())
        }

        testSubscriber.assertValueCount(10)
        testSubscriber.assertNoErrors()
        testSubscriber.assertNotComplete()
    }

    private fun createTestData(postNo: Int = 1): TestData {
        val (board, loadable) = createTestLoadable()

        val post = Post.Builder()
                .board(board)
                .id(postNo)
                .opId(postNo)
                .setUnixTimestampSeconds(System.currentTimeMillis())
                .comment("Test comment")
                .build()

        return TestData(loadable, post)
    }

    private fun createTestLoadable(): Pair<Board, Loadable> {
        val site = Chan4()

        val board = Board.fromSiteNameCode(site, "4chan", "test")
        val loadable = Loadable.forThread(site, board, 1, "Test")

        return Pair(board, loadable)
    }

    data class TestData(val loadable: Loadable, val post: Post)

    open class DummyLoader(
            loaderType: LoaderType,
            private val isCache: Boolean = false,
            private val failLoading: Boolean = false
    ) : OnDemandContentLoader(loaderType) {

        override fun isAlreadyCached(postLoaderData: PostLoaderData): Boolean {
            return isCache
        }

        override fun startLoading(postLoaderData: PostLoaderData): Single<LoaderResult> {
            return if (failLoading) {
                Single.just(LoaderResult.Error(loaderType))
            } else {
                Single.just(LoaderResult.Success(loaderType))
            }
        }

        override fun cancelLoading(postLoaderData: PostLoaderData) {
        }
    }

}