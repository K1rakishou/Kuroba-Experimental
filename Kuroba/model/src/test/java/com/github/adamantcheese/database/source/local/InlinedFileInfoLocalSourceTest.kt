package com.github.adamantcheese.database.source.local

import com.github.adamantcheese.database.TestDatabaseModuleComponent
import com.github.adamantcheese.database.dao.InlinedFileInfoDao
import com.github.adamantcheese.database.data.InlinedFileInfo
import com.github.adamantcheese.database.entity.InlinedFileInfoEntity
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
class InlinedFileInfoLocalSourceTest {
    lateinit var localSource: InlinedFileInfoLocalSource
    lateinit var dao: InlinedFileInfoDao

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        val testDatabaseModuleComponent = TestDatabaseModuleComponent()

        dao = testDatabaseModuleComponent.provideKurobaDatabase().inlinedFileDao()
        localSource = testDatabaseModuleComponent.provideInlinedFileInfoLocalSource()
    }

    @Test
    fun `test shouldn't update old entity with the new one if they are the same`() {
        runBlocking {
            val inlinedFileInfo = InlinedFileInfo("test.com/123", 1000L)

            localSource.insert(inlinedFileInfo).unwrap()
            localSource.insert(inlinedFileInfo).unwrap()
            localSource.insert(inlinedFileInfo).unwrap()

            val allEntities = dao.testGetAll()
            assertEquals(1, allEntities.size)
        }
    }

    @Test
    fun `test delete old entries`() {
        runBlocking {
            val oneSecondAgo = DateTime.now().minus(Period.seconds(1))
            val oneMinuteAgo = DateTime.now().minus(Period.minutes(1))

            dao.insert(InlinedFileInfoEntity("test.com/123", null, oneSecondAgo))
            dao.insert(InlinedFileInfoEntity("test.com/124", 234234, oneMinuteAgo))
            dao.insert(InlinedFileInfoEntity("test.com/125", 6677, oneSecondAgo))
            dao.insert(InlinedFileInfoEntity("test.com/126", 90000, oneMinuteAgo))
            localSource.deleteOlderThan(oneSecondAgo).unwrap()

            val allEntities = dao.testGetAll()
            assertEquals(2, allEntities.size)

            assertEquals("test.com/123", allEntities.first().fileUrl)
            assertEquals("test.com/125", allEntities.last().fileUrl)
        }
    }
}