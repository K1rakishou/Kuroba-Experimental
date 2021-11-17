package com.github.k1rakishou.chan.core.site.common.vichan

import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.bookmark.StickyThread
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoPostObject
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogThreadInfoObject
import com.google.gson.stream.JsonReader
import okhttp3.HttpUrl

@Suppress("BlockingMethodInNonBlockingContext")
class VichanReaderExtensions {

  suspend fun iteratePostsInThread(reader: JsonReader, iterator: suspend (JsonReader) -> Unit) {
    reader.beginObject()
    // Page object

    while (reader.hasNext()) {
      val key = reader.nextName()
      if (key == "posts") {
        reader.beginArray()

        // Thread array
        while (reader.hasNext()) {
          // Thread object
          iterator(reader)
        }

        reader.endArray()
      } else {
        reader.skipValue()
      }
    }

    reader.endObject()
  }

  suspend fun iterateThreadsInCatalog(reader: JsonReader, iterator: suspend (JsonReader) -> Unit) {
    reader.beginArray() // Array of pages

    while (reader.hasNext()) {
      reader.beginObject() // Page object

      while (reader.hasNext()) {
        if (reader.nextName() == "threads") {
          reader.beginArray() // Threads array

          while (reader.hasNext()) {
            iterator(reader)
          }

          reader.endArray()
        } else {
          reader.skipValue()
        }
      }

      reader.endObject()
    }

    reader.endArray()
  }

  suspend fun readThreadBookmarkInfoPostObject(reader: JsonReader): ThreadBookmarkInfoPostObject? {
    var isOp: Boolean = false
    var postNo: Long? = null
    var closed: Boolean = false
    var archived: Boolean = false
    var bumpLimit: Boolean = false
    var imageLimit: Boolean = false
    var comment: String = ""
    var sticky: Boolean = false
    var stickyCap: Int = -1

    reader.beginObject()

    while (reader.hasNext()) {
      when (reader.nextName()) {
        "no" -> postNo = reader.nextInt().toLong()
        "closed" -> closed = reader.nextInt() == 1
        "archived" -> archived = reader.nextInt() == 1
        "com" -> comment = reader.nextString()
        "resto" -> {
          val opId = reader.nextInt()
          isOp = opId == 0
        }
        "bumplimit" -> bumpLimit = reader.nextInt() == 1
        "imagelimit" -> imageLimit = reader.nextInt() == 1
        "sticky" -> sticky = reader.nextInt() == 1
        "sticky_cap" -> stickyCap = reader.nextInt()
        else -> {
          // Unknown/ignored key
          reader.skipValue()
        }
      }
    }

    reader.endObject()

    if (isOp) {
      if (postNo == null) {
        Logger.e(TAG, "Error reading OriginalPost (postNo=$postNo)")
        return null
      }

      val stickyPost = StickyThread.create(sticky, stickyCap)

      return ThreadBookmarkInfoPostObject.OriginalPost(
        postNo = postNo,
        closed = closed,
        archived = archived,
        isBumpLimit = bumpLimit,
        isImageLimit = imageLimit,
        stickyThread = stickyPost,
        comment = comment
      )
    } else {
      if (postNo == null) {
        Logger.e(TAG, "Error reading RegularPost (isOp=$isOp)")
        return null
      }

      return ThreadBookmarkInfoPostObject.RegularPost(postNo, comment)
    }
  }

  fun readFilterWatchCatalogThreadInfoObject(
    endpoints: SiteEndpoints,
    boardDescriptor: BoardDescriptor,
    reader: JsonReader
  ): FilterWatchCatalogThreadInfoObject? {
    var isOp: Boolean = false
    var threadNo: Long? = null
    var comment: String = ""
    var subject: String = ""
    var thumbnailUrl: HttpUrl? = null
    var fileId: String? = null
    var fileExt: String? = null

    reader.beginObject()

    while (reader.hasNext()) {
      when (reader.nextName()) {
        "no" -> threadNo = reader.nextInt().toLong()
        "com" -> comment = reader.nextString()
        "resto" -> {
          val opId = reader.nextInt()
          isOp = opId == 0
        }
        "sub" -> subject = reader.nextString()
        "tim" -> fileId = reader.nextString()
        "ext" -> fileExt = reader.nextString().replace(".", "")
        else -> {
          // Unknown/ignored key
          reader.skipValue()
        }
      }
    }

    reader.endObject()

    if (!isOp || threadNo == null) {
      return null
    }

    if (fileId != null && fileExt != null) {
      val args = SiteEndpoints.makeArgument("tim", fileId, "ext", fileExt)
      thumbnailUrl = endpoints.thumbnailUrl(boardDescriptor, false, 0, args)
    }

    return FilterWatchCatalogThreadInfoObject(
      threadDescriptor = ChanDescriptor.ThreadDescriptor.Companion.create(boardDescriptor, threadNo),
      commentRaw = comment,
      subjectRaw = subject,
      thumbnailUrl = thumbnailUrl
    )
  }

  companion object {
    private const val TAG = "VichanReaderExtensions"
  }
}