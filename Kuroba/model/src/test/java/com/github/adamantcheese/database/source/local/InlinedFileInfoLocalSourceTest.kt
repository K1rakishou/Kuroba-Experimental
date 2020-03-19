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

@RunWith(RobolectricTestRunner::class)
class InlinedFileInfoLocalSourceTest {
    lateinit var inlinedFileInfoLocalSource: InlinedFileInfoLocalSource
    lateinit var inlinedFileDao: InlinedFileInfoDao

    @Before
    fun setUp() {
        val testDatabaseModuleComponent = TestDatabaseModuleComponent()

        inlinedFileDao = testDatabaseModuleComponent.provideKurobaDatabase().inlinedFileDao()
        inlinedFileInfoLocalSource = testDatabaseModuleComponent.provideInlinedFileInfoLocalSource()
    }

    @Test
    fun `test shouldn't update old entity with the new one if they are the same`() {
        runBlocking {
            val inlinedFileInfo = InlinedFileInfo("test.com/123", 1000L)

            inlinedFileInfoLocalSource.insert(inlinedFileInfo).unwrap()
            inlinedFileInfoLocalSource.insert(inlinedFileInfo).unwrap()
            inlinedFileInfoLocalSource.insert(inlinedFileInfo).unwrap()

            val allEntities = inlinedFileDao.testGetAll()
            assertEquals(1, allEntities.size)
        }
    }

    @Test
    fun `test delete old entries`() {
        runBlocking {
            val oneSecondAgo = DateTime.now().minus(Period.seconds(1))
            val oneMinuteAgo = DateTime.now().minus(Period.minutes(1))

            inlinedFileDao.insert(InlinedFileInfoEntity("test.com/123", null, oneSecondAgo))
            inlinedFileDao.insert(InlinedFileInfoEntity("test.com/124", 234234, oneMinuteAgo))
            inlinedFileDao.insert(InlinedFileInfoEntity("test.com/125", 6677, oneSecondAgo))
            inlinedFileDao.insert(InlinedFileInfoEntity("test.com/126", 90000, oneMinuteAgo))
            inlinedFileInfoLocalSource.deleteOlderThan(oneSecondAgo).unwrap()

            val allEntities = inlinedFileDao.testGetAll()
            assertEquals(2, allEntities.size)

            assertEquals("test.com/123", allEntities.first().fileUrl)
            assertEquals("test.com/125", allEntities.last().fileUrl)
        }

    }
}