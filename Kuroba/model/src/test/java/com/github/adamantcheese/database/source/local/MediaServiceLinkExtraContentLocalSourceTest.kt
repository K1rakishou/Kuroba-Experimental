package com.github.adamantcheese.database.source.local

import com.github.adamantcheese.database.TestDatabaseModuleComponent
import com.github.adamantcheese.database.dao.MediaServiceLinkExtraContentDao
import com.github.adamantcheese.database.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.database.data.video_service.MediaServiceType
import com.github.adamantcheese.database.entity.MediaServiceLinkExtraContentEntity
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.joda.time.Period
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class MediaServiceLinkExtraContentLocalSourceTest {
    lateinit var localSource: MediaServiceLinkExtraContentLocalSource
    lateinit var dao: MediaServiceLinkExtraContentDao

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        val testDatabaseModuleComponent = TestDatabaseModuleComponent()

        dao = testDatabaseModuleComponent.provideKurobaDatabase().mediaServiceLinkExtraContentDao()
        localSource = testDatabaseModuleComponent.provideMediaServiceLinkExtraContentLocalSource()
    }

    @Test
    fun `test shouldn't update old entity with the new one if they are the same`() {
        runBlocking {
            val linkExtraContent = MediaServiceLinkExtraContent(
                    "test.com/123",
                    MediaServiceType.Youtube,
                    "tet title",
                    Period.seconds(90)
            )

            localSource.insert(linkExtraContent).unwrap()
            localSource.insert(linkExtraContent).unwrap()
            localSource.insert(linkExtraContent).unwrap()

            val allEntities = dao.testGetAll()
            assertEquals(1, allEntities.size)
        }
    }

    @Test
    fun `test delete old entries`() {
        runBlocking {
            val oneSecondAgo = DateTime.now().minus(Period.seconds(1))
            val oneMinuteAgo = DateTime.now().minus(Period.minutes(1))

            dao.insert(
                    MediaServiceLinkExtraContentEntity(
                            "id123",
                            MediaServiceType.Youtube,
                            null,
                            Period.seconds(90),
                            oneSecondAgo
                    )
            )
            dao.insert(
                    MediaServiceLinkExtraContentEntity(
                            "id124",
                            MediaServiceType.Youtube,
                            "title1",
                            Period.seconds(33),
                            oneMinuteAgo)
            )
            dao.insert(
                    MediaServiceLinkExtraContentEntity(
                            "id125",
                            MediaServiceType.Youtube,
                            "title2",
                            Period.seconds(44),
                            oneSecondAgo
                    )
            )
            dao.insert(
                    MediaServiceLinkExtraContentEntity(
                            "id126",
                            MediaServiceType.Youtube,
                            "title3",
                            Period.seconds(52),
                            oneMinuteAgo)
            )
            localSource.deleteOlderThan(oneSecondAgo).unwrap()

            val allEntities = dao.testGetAll()
            assertEquals(2, allEntities.size)

            assertEquals("id123", allEntities.first().videoId)
            assertEquals(null, allEntities.first().videoTitle)
            assertEquals("id125", allEntities.last().videoId)
            assertEquals("title2", allEntities.last().videoTitle)
        }
    }
}