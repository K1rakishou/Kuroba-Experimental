package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
class LynxchanPostingContractTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun `board mapping enables image spoilers`() {
    val boardData = LynxchanGetBoardsUseCase.LynxchanBoardsData(
      boardUri = "test",
      boardName = "Test board",
      boardDescription = null,
      tags = listOf("sfw"),
      specialSettings = null
    )

    assertTrue(boardData.supportsImageSpoilers)
  }

  @Test
  fun `multipart metadata preserves mixed per-file spoiler flags`() {
    val formBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
    addFile(formBuilder, "spoilered.png", "image/png", "sha256-a", spoiler = true)
    addFile(formBuilder, "plain.jpg", "image/jpeg", "sha256-b", spoiler = false)

    val buffer = Buffer()
    formBuilder.build().writeTo(buffer)
    val multipartBody = buffer.readUtf8()

    assertEquals(listOf("spoilered.png", "plain.jpg"), fieldValues(multipartBody, "fileName"))
    assertEquals(listOf("image/png", "image/jpeg"), fieldValues(multipartBody, "fileMime"))
    assertEquals(listOf("sha256-a", "sha256-b"), fieldValues(multipartBody, "fileSha256"))
    assertEquals(listOf("true", ""), fieldValues(multipartBody, "fileSpoiler"))
    assertTrue(multipartBody.indexOf("name=\"fileSpoiler\"") < multipartBody.indexOf("name=\"files\""))
  }

  @Test
  fun `metadata uses final PNG bytes and calculates SHA-256 off caller dispatcher`() = runTest {
    val pngBytes = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3)
    val file = temporaryFolder.newFile("reencoded-file").apply { writeBytes(pngBytes) }
    val executor = Executors.newSingleThreadExecutor()
    val delegate = executor.asCoroutineDispatcher()
    val dispatcher = CountingDispatcher(delegate)

    try {
      val (mime, sha256) = LynxchanReplyHttpCall.deriveFileMetadata(file, "original.JPG", dispatcher)

      assertEquals("image/png", mime)
      assertEquals("7f47b756761a46e6d4a4d96f0d8a4448f8449235009d1f3ad1493f5c773c19e8", sha256)
      assertTrue(dispatcher.dispatchCount.get() > 0)
    } finally {
      delegate.close()
      executor.shutdownNow()
    }
  }

  @Test
  fun `metadata normalizes uppercase extension and falls back for unknown content`() = runTest {
    val file = temporaryFolder.newFile("unknown-content").apply { writeText("not an image") }

    assertEquals("image/jpeg", LynxchanReplyHttpCall.deriveFileMetadata(file, "final.JPEG").first)
    assertEquals("application/octet-stream", LynxchanReplyHttpCall.deriveFileMetadata(file, "final.unknown").first)
  }

  @Test
  fun `SHA-256 checks cancellation between chunks`() = runTest {
    val hashing = async(Dispatchers.Default) {
      val job = coroutineContext.job
      val input = object : ByteArrayInputStream(ByteArray(DEFAULT_BUFFER_SIZE * 2)) {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
          return super.read(buffer, offset, length).also { job.cancel() }
        }
      }

      LynxchanReplyHttpCall.calculateSha256(input)
    }

    try {
      hashing.await()
      fail("Expected SHA-256 calculation to be cancelled")
    } catch (_: CancellationException) {
      // Expected: the next chunk boundary observes cancellation.
    }
  }

  private fun addFile(
    formBuilder: MultipartBody.Builder,
    fileName: String,
    fileMime: String,
    fileSha256: String,
    spoiler: Boolean
  ) {
    LynxchanReplyHttpCall.addFileMetadata(formBuilder, fileName, fileMime, fileSha256, spoiler)
    formBuilder.addFormDataPart(
      "files",
      fileName,
      fileName.toRequestBody("application/octet-stream".toMediaType())
    )
  }

  private fun fieldValues(multipartBody: String, fieldName: String): List<String> {
    val pattern = Regex("name=\\\"${fieldName}\\\"\\r\\n(?:Content-[^\\r]+\\r\\n)*\\r\\n(.*?)\\r\\n")
    return pattern.findAll(multipartBody).map { match -> match.groupValues[1] }.toList()
  }

  private class CountingDispatcher(
    private val delegate: CoroutineDispatcher
  ) : CoroutineDispatcher() {
    val dispatchCount = AtomicInteger()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      dispatchCount.incrementAndGet()
      delegate.dispatch(context, block)
    }
  }
}
