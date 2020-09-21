package com.github.k1rakishou.model.source.local

import androidx.room.withTransaction
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.TestDatabaseModuleComponent
import com.github.k1rakishou.model.dao.*
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostHttpIcon
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageType
import com.github.k1rakishou.model.data.serializable.spans.SerializableSpannableString
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChanPostLocalSourceTest {
  lateinit var database: KurobaDatabase
  lateinit var localSource: ChanPostLocalSource
  lateinit var chanPostDao: ChanPostDao
  lateinit var chanPostImageDao: ChanPostImageDao
  lateinit var chanPostHttpIconDao: ChanPostHttpIconDao
  lateinit var chanPostReplyDao: ChanPostReplyDao
  lateinit var chanTextSpanDao: ChanTextSpanDao

  private val testSiteName = "test.com"
  private val testBoardCode = "test"
  private val testThreadNo = 1234567890L
  private val archiveId0 = 0L
  private val archiveId1 = 1L
  private val archiveId2 = 2L

  private val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
    testSiteName,
    testBoardCode,
    testThreadNo
  )

  private val random = Random(System.currentTimeMillis())

  @Before
  fun setUp() {
    ShadowLog.stream = System.out
    val testDatabaseModuleComponent = TestDatabaseModuleComponent()

    database = testDatabaseModuleComponent.provideInMemoryKurobaDatabase()
    chanPostDao = database.chanPostDao()
    chanPostImageDao = database.chanPostImageDao()
    chanPostHttpIconDao = database.chanPostHttpIconDao()
    chanPostReplyDao = database.chanPostReplyDao()
    chanTextSpanDao = database.chanTextSpanDao()
    localSource = testDatabaseModuleComponent.provideChanPostLocalSource(database)
  }

  @Test
  fun `test insert the same post twice ensure no tables have duplicates`() {
    withTransaction {
      repeat(9) { index ->
        insertOriginalPost(
          createChanPost(
            123L,
            123L,
            archiveId0,
            true,
            createPostImages(2, archiveId0),
            createChanPostIcons(3),
            createReplies(4),
            (100500 + index).toLong()
          )
        )

        assertEquals(1, chanPostDao.testGetAll().size)
        assertEquals(2, chanPostImageDao.testGetAll().size)
        assertEquals(3, chanPostHttpIconDao.testGetAll().size)
        assertEquals(4, chanPostReplyDao.testGetAll().size)
        assertEquals(3, chanTextSpanDao.testGetAll().size)

        kotlin.run {
          val allSpans = chanTextSpanDao.testGetAll().map { it.originalText }.toSet()

          assertTrue("test text 10050$index" in allSpans)
          assertTrue("test subject 10050$index" in allSpans)
          assertTrue("test tripcode 10050$index" in allSpans)
        }
      }
    }
  }

  @Test
  fun `test posts with the same no, sub no and archiveId should be the same post`() {
    withTransaction {
      val newComment = "new comment 123"

      val newOriginalPost = createChanPost(testThreadNo, testThreadNo, archiveId0, true, emptyList())
      val chanThreadId = localSource.insertOriginalPost(newOriginalPost)
      require(chanThreadId > 0L) { "insertOriginalPost failed, chanThreadId = $chanThreadId" }

      val newOriginalPostFromDB = localSource.getThreadPosts(threadDescriptor, setOf(archiveId0), listOf(testThreadNo)).let { posts ->
        assertEquals(1, posts.size)
        return@let posts.first()
      }

      assertEquals("test text $testThreadNo", newOriginalPostFromDB.postComment.text)

      val updatedOriginalPost = newOriginalPost.copy(
        postComment = SerializableSpannableString(emptyList(), newComment)
      )

      assertEquals(chanThreadId, localSource.insertOriginalPost(updatedOriginalPost))

      val updatedOriginalPostFromDB = localSource.getThreadPosts(threadDescriptor, setOf(archiveId0), listOf(testThreadNo)).let { posts ->
        assertEquals(1, posts.size)
        return@let posts.first()
      }

      assertEquals(newComment, updatedOriginalPostFromDB.postComment.text)

      assertEquals(1, chanPostDao.testGetAll().size)
    }
  }

  @Test
  fun `test posts and images from different archives should be stored separately`() {
    withTransaction {
      val newOriginalPost = createChanPost(testThreadNo, testThreadNo, archiveId0, true, emptyList())
      val chanThreadId = localSource.insertOriginalPost(newOriginalPost)
      require(chanThreadId > 0L) { "insertOriginalPost failed, chanThreadId = $chanThreadId" }

      val newArchivedOriginalPost = createChanPost(testThreadNo, testThreadNo, archiveId1, true, createPostImages(1, archiveId1))
      assertEquals(chanThreadId, localSource.insertOriginalPost(newArchivedOriginalPost))

      val newOriginalPostFromDB = localSource.getThreadPosts(threadDescriptor, setOf(archiveId0), listOf(testThreadNo)).let { posts ->
        assertEquals(1, posts.size)
        return@let posts.first()
      }

      assertEquals(archiveId0, newOriginalPostFromDB.archiveId)
      assertEquals(0, newOriginalPostFromDB.postImages.size)

      val archivedOriginalPost = localSource.getThreadPosts(threadDescriptor, setOf(archiveId1), listOf(testThreadNo)).let { posts ->
        assertEquals(1, posts.size)
        return@let posts.first()
      }

      assertEquals(archiveId1, archivedOriginalPost.archiveId)
      assertEquals(1, archivedOriginalPost.postImages.size)
      assertEquals("0", archivedOriginalPost.postImages.first().filename)

      assertEquals(2, chanPostDao.testGetAll().size)
      assertEquals(1, chanPostImageDao.testGetAll().size)
    }
  }

  @Test
  fun `test among archived and regular posts with the same ids post with the most amount of images must be preferred`() {
    withTransaction {
      kotlin.run {
        val postNo = testThreadNo

        val originalPost = createChanPost(testThreadNo, postNo, archiveId0, true, createPostImages(1, archiveId0))
        val chanThreadId = localSource.insertOriginalPost(originalPost)
        require(chanThreadId > 0L) { "insertOriginalPost failed, chanThreadId = $chanThreadId" }

        assertEquals(1, chanPostDao.testGetAll().size)
        assertEquals(1, chanPostImageDao.testGetAll().size)

        val archivedPost = createChanPost(testThreadNo, postNo, archiveId1, true, createPostImages(1, archiveId1))
        assertEquals(chanThreadId, localSource.insertOriginalPost(archivedPost))

        assertEquals(2, chanPostDao.testGetAll().size)
        assertEquals(2, chanPostImageDao.testGetAll().size)

        val archivePost2 = createChanPost(testThreadNo, postNo, archiveId2, true, createPostImages(2, archiveId2))
        assertEquals(chanThreadId, localSource.insertOriginalPost(archivePost2))
        assertEquals(3, chanPostDao.testGetAll().size)
        assertEquals(4, chanPostImageDao.testGetAll().size)
      }

      kotlin.run {
        val postNo = testThreadNo + 1

        val originalPost = createChanPost(testThreadNo, postNo, archiveId0, false, createPostImages(1, archiveId0))
        val chanThreadId = localSource.insertOriginalPost(originalPost)
        require(chanThreadId > 0L) { "insertOriginalPost failed, chanThreadId = $chanThreadId" }

        assertEquals(4, chanPostDao.testGetAll().size)
        assertEquals(5, chanPostImageDao.testGetAll().size)

        val archivedPost = createChanPost(testThreadNo, postNo, archiveId1, false, createPostImages(4, archiveId1))
        assertEquals(chanThreadId, localSource.insertOriginalPost(archivedPost))

        assertEquals(5, chanPostDao.testGetAll().size)
        assertEquals(9, chanPostImageDao.testGetAll().size)

        val archivePost2 = createChanPost(testThreadNo, postNo, archiveId2, false, createPostImages(2, archiveId2))
        assertEquals(chanThreadId, localSource.insertOriginalPost(archivePost2))
        assertEquals(6, chanPostDao.testGetAll().size)
        assertEquals(11, chanPostImageDao.testGetAll().size)
      }

      val mostValuablePosts = localSource.getThreadPosts(
        threadDescriptor,
        setOf(archiveId0, archiveId1, archiveId2),
        listOf(testThreadNo, testThreadNo + 1)
      )

      assertEquals(2, mostValuablePosts.size)

      val post1 = mostValuablePosts.first { it.chanPostId == 3L }
      assertEquals(2, post1.postImages.size)

      val post2 = mostValuablePosts.first { it.chanPostId == 5L }
      assertEquals(4, post2.postImages.size)
    }
  }

  @Test
  fun `test should return posts from different archives separately`() {
    withTransaction {
      // postId 1
      val chanThreadId = insertOriginalPost(createChanPost(testThreadNo, testThreadNo, archiveId0, true, emptyList()))

      // postId 2
      insertOriginalPost(createChanPost(testThreadNo, testThreadNo, archiveId1, true, createPostImages(1, archiveId1)))

      // postId 3
      insertOriginalPost(createChanPost(testThreadNo, testThreadNo, archiveId2, true, emptyList()))

      // postId 4
      insertPost(chanThreadId, createChanPost(testThreadNo, testThreadNo + 1, archiveId0, false, createPostImages(3, archiveId0)))

      // postId 5
      insertPost(chanThreadId, createChanPost(testThreadNo, testThreadNo + 2, archiveId1, false, emptyList()))

      // postId 6
      insertPost(chanThreadId, createChanPost(testThreadNo, testThreadNo + 2, archiveId0, false, emptyList()))

      // postId 7
      insertPost(chanThreadId, createChanPost(testThreadNo, testThreadNo + 3, archiveId2, false, createPostImages(2, archiveId2)))

      // postId 8
      insertPost(chanThreadId, createChanPost(testThreadNo, testThreadNo + 3, archiveId1, false, createPostImages(2, archiveId1)))

      assertEquals(8, chanPostImageDao.testGetAll().size)
      assertEquals(8, chanPostDao.testGetAll().size)

      kotlin.run {
        val regularPosts = localSource.getThreadPosts(threadDescriptor, setOf(archiveId0), emptySet(), Int.MAX_VALUE)
        assertEquals(3, regularPosts.size)
        assertEquals(3, regularPosts.sumBy { it.postImages.size })

        regularPosts.forEach { post ->
          assertEquals(archiveId0, post.archiveId)

          post.postImages.forEach { image ->
            assertEquals(archiveId0, image.archiveId)
          }
        }
      }

      kotlin.run {
        val archiveIdPosts = localSource.getThreadPosts(threadDescriptor, setOf(archiveId1), emptySet(), Int.MAX_VALUE)
        assertEquals(3, archiveIdPosts.size)
        assertEquals(3, archiveIdPosts.sumBy { it.postImages.size })

        archiveIdPosts.forEach { post ->
          assertEquals(archiveId1, post.archiveId)

          post.postImages.forEach { image ->
            assertEquals(archiveId1, image.archiveId)
          }
        }

      }

      kotlin.run {
        val archiveId2Posts = localSource.getThreadPosts(threadDescriptor, setOf(archiveId2), emptySet(), Int.MAX_VALUE)
        assertEquals(2, archiveId2Posts.size)
        assertEquals(2, archiveId2Posts.sumBy { it.postImages.size })

        archiveId2Posts.forEach { post ->
          assertEquals(archiveId2, post.archiveId)

          post.postImages.forEach { image ->
            assertEquals(archiveId2, image.archiveId)
          }
        }
      }

      kotlin.run {
        val archives = setOf(archiveId0, archiveId1, archiveId2)
        val mostValuablePosts = localSource.getThreadPosts(threadDescriptor, archives, emptySet(), Int.MAX_VALUE)

        assertEquals(2, mostValuablePosts[0].chanPostId)
        assertEquals(1, mostValuablePosts[0].archiveId)
        assertEquals(testThreadNo, mostValuablePosts[0].postDescriptor.postNo)
        assertEquals(1, mostValuablePosts[0].postImages.size)

        assertEquals(4, mostValuablePosts[1].chanPostId)
        assertEquals(0, mostValuablePosts[1].archiveId)
        assertEquals(testThreadNo + 1, mostValuablePosts[1].postDescriptor.postNo)
        assertEquals(3, mostValuablePosts[1].postImages.size)

        assertEquals(6, mostValuablePosts[2].chanPostId)
        assertEquals(0, mostValuablePosts[2].archiveId)
        assertEquals(testThreadNo + 2, mostValuablePosts[2].postDescriptor.postNo)
        assertEquals(0, mostValuablePosts[2].postImages.size)

        assertEquals(8, mostValuablePosts[3].chanPostId)
        assertEquals(1, mostValuablePosts[3].archiveId)
        assertEquals(testThreadNo + 3, mostValuablePosts[3].postDescriptor.postNo)
        assertEquals(2, mostValuablePosts[3].postImages.size)
      }
    }
  }

  @Test
  fun `test delete posts should delete regular posts and archive posts but not OPs`() {
    val threadsCount = 10
    val postsPerThread = 10
    val threadStartNo = 1_000_000L

    withTransaction {
      (0L until threadsCount).map { threadIndex ->
        val threadNo = threadStartNo + threadIndex

        val chanThreadId = insertOriginalPost(
          createChanPost(threadNo, threadNo, archiveId0, true, createPostImages(1, archiveId0))
        )
        assertEquals((threadIndex + 1), chanThreadId)

        insertOriginalPost(
          createChanPost(threadNo, threadNo, archiveId1, true, createPostImages(2, archiveId1))
        )
        insertOriginalPost(
          createChanPost(threadNo, threadNo, archiveId2, true, createPostImages(4, archiveId2))
        )

        val posts = (0 until postsPerThread).map { postIndex ->
          createChanPost(threadNo, threadNo + postIndex + 1, archiveId0, false,
            createPostImages(postIndex, archiveId0)
          )
        }

        localSource.insertPosts(chanThreadId, posts)
      }
    }

    runBlocking {
      repeat(threadsCount) { threadIndex ->
        withTransaction {
          val actualDeletedCount = chanPostDao.deletePostsByThreadId(threadIndex + 1L)
          assertEquals(postsPerThread, actualDeletedCount)
        }
      }
    }

    withTransaction {
      val originalPosts = chanPostDao.testGetAll()

      assertEquals(30, originalPosts.size)
      assertTrue(originalPosts.all { it.chanPostEntity.isOp })
    }
  }

  @Test
  fun `test update image with new info`() {
    val serverName = "123"
    val thumbnailUrl = "http://${testSiteName}/${123}s.jpg".toHttpUrl()
    val imageUrl = "http://${testSiteName}/${123}.jpg".toHttpUrl()

    fun createImage(_serverName: String, _thumbnailUrl: HttpUrl? = null, _imageUrl: HttpUrl? = null): List<ChanPostImage> {
      return listOf(
        ChanPostImage(_serverName, archiveId0, _thumbnailUrl, null, _imageUrl, "1234-image", "jpg", 111,
          222, false, false, 12345L, null, ChanPostImageType.STATIC)
      )
    }

    withTransaction {
      val chanThreadId = insertOriginalPost(
        createChanPost(testThreadNo, testThreadNo, archiveId0, true, emptyList())
      )
      assertEquals(1, chanThreadId)
      assertEquals(0, chanPostImageDao.testGetAll().size)

      insertOriginalPost(
        createChanPost(testThreadNo, testThreadNo, archiveId0, true, createImage(serverName))
      )
      assertEquals(1, chanThreadId)

      var images = chanPostImageDao.testGetAll()
      assertEquals(1, images.size)
      assertEquals(serverName, images.first().serverFilename)
      assertEquals(null, images.first().thumbnailUrl)
      assertEquals(null, images.first().imageUrl)

      insertOriginalPost(
        createChanPost(testThreadNo, testThreadNo, archiveId0, true,
          createImage(serverName, thumbnailUrl))
      )

      images = chanPostImageDao.testGetAll()
      assertEquals(1, images.size)
      assertEquals(serverName, images.first().serverFilename)
      assertEquals(thumbnailUrl, images.first().thumbnailUrl)
      assertEquals(null, images.first().imageUrl)

      insertOriginalPost(
        createChanPost(testThreadNo, testThreadNo, archiveId0, true,
          createImage(serverName, thumbnailUrl, imageUrl))
      )

      images = chanPostImageDao.testGetAll()
      assertEquals(1, images.size)
      assertEquals(serverName, images.first().serverFilename)
      assertEquals(thumbnailUrl, images.first().thumbnailUrl)
      assertEquals(imageUrl, images.first().imageUrl)

      insertOriginalPost(
        createChanPost(testThreadNo, testThreadNo, archiveId1, true,
          createImage(serverName, thumbnailUrl, imageUrl))
      )

      images = chanPostImageDao.testGetAll()
      assertEquals(2, images.size)
    }
  }

  private suspend fun insertPost(chanThreadId: Long, chanPost: ChanPost) {
    localSource.insertPosts(chanThreadId, listOf(chanPost))
  }

  private suspend fun insertOriginalPost(chanPost: ChanPost): Long {
    val chanThreadId = localSource.insertOriginalPost(chanPost)
    require(chanThreadId > 0L) { "insertOriginalPost failed, chanThreadId = $chanThreadId" }

    return chanThreadId
  }

  private fun withTransaction(func: suspend () -> Unit) {
    runBlocking {
      database.withTransaction {
        func()
      }
    }
  }

  private fun createChanPost(
    threadNo: Long,
    postNo: Long,
    archiveId: Long,
    isOp: Boolean,
    images: List<ChanPostImage>,
    icons: List<ChanPostHttpIcon> = createChanPostIcons(),
    replies: Set<Long> = createReplies(),
    textId: Long = postNo
  ): ChanPost {
    return ChanPost(
      0L,
      PostDescriptor.create(
        threadDescriptor.siteName(),
        threadDescriptor.boardCode(),
        threadNo,
        postNo
      ),
      images.toMutableList(),
      icons.toMutableList(),
      replies.toMutableSet(),
      0,
      0,
      0,
      0,
      false,
      false,
      false,
      false,
      archiveId,
      System.currentTimeMillis(),
      SerializableSpannableString(emptyList(), "test text $textId"),
      SerializableSpannableString(emptyList(), "test subject $textId"),
      SerializableSpannableString(emptyList(), "test tripcode $textId"),
      "Anonymous",
      null,
      null,
      isOp,
      false,
      false
    )
  }

  private fun createReplies(count: Int = random.nextInt(0, 5)): Set<Long> {
    return (0 until count).map { random.nextLong() }.toSet()
  }

  private fun createChanPostIcons(count: Int = 2): List<ChanPostHttpIcon> {
    return (0 until count).map {
      return@map ChanPostHttpIcon(
        "http://${testSiteName}/${System.nanoTime()}_icon.ico".toHttpUrl(),
        "${System.nanoTime()}_icon"
      )
    }
  }

  private fun createPostImages(count: Int, archiveId: Long): List<ChanPostImage> {
    return (0 until count).map { index ->
      val time = System.nanoTime()

      return@map ChanPostImage(
        time.toString(),
        archiveId,
        "http://${testSiteName}/${time}s.jpg".toHttpUrl(),
        null,
        "http://${testSiteName}/${time}.jpg".toHttpUrl(),
        index.toString(),
        "jpg",
        111,
        222,
        false,
        false,
        12345L,
        null,
        ChanPostImageType.STATIC
      )
    }
  }
}