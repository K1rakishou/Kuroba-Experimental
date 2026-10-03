package com.github.k1rakishou.chan.core.presenter

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadPresenterBookmarkOrchestrationTest {
  private val catalogDescriptor = ChanDescriptor.CatalogDescriptor.create("test", "test")
  private val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(catalogDescriptor, 1L)
  private val postDescriptor = PostDescriptor.create(threadDescriptor, 2L)

  @Test
  fun successfulSaveCreatesBookmarkForPostThread() = runTest {
    val harness = Harness(postDescriptor)

    harness.execute(isSaving = true)

    assertEquals(listOf(postDescriptor), harness.saveCalls)
    assertTrue(harness.unsaveCalls.isEmpty())
    assertEquals(listOf(threadDescriptor), harness.createdBookmarks)
  }

  @Test
  fun failedSaveDoesNotCreateBookmark() = runTest {
    val harness = Harness(postDescriptor, saveSucceeds = false)

    harness.execute(isSaving = true)

    assertEquals(listOf(postDescriptor), harness.saveCalls)
    assertTrue(harness.bookmarkRequests.isEmpty())
  }

  @Test
  fun unsaveDoesNotTouchExistingBookmark() = runTest {
    val harness = Harness(postDescriptor, initialBookmarks = setOf(threadDescriptor))

    harness.execute(isSaving = false)

    assertEquals(listOf(postDescriptor), harness.unsaveCalls)
    assertTrue(harness.saveCalls.isEmpty())
    assertTrue(harness.bookmarkRequests.isEmpty())
    assertEquals(setOf(threadDescriptor), harness.bookmarks)
  }

  @Test
  fun disabledSettingDoesNotCreateBookmark() = runTest {
    val harness = Harness(postDescriptor, postPinThreadEnabled = false)

    harness.execute(isSaving = true)

    assertEquals(listOf(postDescriptor), harness.saveCalls)
    assertTrue(harness.bookmarkRequests.isEmpty())
  }

  @Test
  fun existingBookmarkIsPreserved() = runTest {
    val harness = Harness(postDescriptor, initialBookmarks = setOf(threadDescriptor))

    harness.execute(isSaving = true)

    assertEquals(listOf(threadDescriptor), harness.bookmarkRequests)
    assertTrue(harness.createdBookmarks.isEmpty())
    assertEquals(setOf(threadDescriptor), harness.bookmarks)
  }

  private class Harness(
    private val postDescriptor: PostDescriptor,
    private val saveSucceeds: Boolean = true,
    private val postPinThreadEnabled: Boolean = true,
    initialBookmarks: Set<ChanDescriptor.ThreadDescriptor> = emptySet()
  ) {
    val saveCalls = mutableListOf<PostDescriptor>()
    val unsaveCalls = mutableListOf<PostDescriptor>()
    val bookmarkRequests = mutableListOf<ChanDescriptor.ThreadDescriptor>()
    val createdBookmarks = mutableListOf<ChanDescriptor.ThreadDescriptor>()
    val bookmarks = initialBookmarks.toMutableSet()

    suspend fun execute(isSaving: Boolean) {
      saveUnsavePostAndBookmarkThread(
        postDescriptor = postDescriptor,
        isSaving = isSaving,
        savePost = { descriptor ->
          saveCalls += descriptor
          saveSucceeds
        },
        unsavePost = { descriptor -> unsaveCalls += descriptor },
        shouldCreateBookmark = { postPinThreadEnabled },
        createBookmarkIfNotExists = { descriptor ->
          bookmarkRequests += descriptor

          if (bookmarks.add(descriptor)) {
            createdBookmarks += descriptor
          }

          true
        }
      )
    }
  }
}
