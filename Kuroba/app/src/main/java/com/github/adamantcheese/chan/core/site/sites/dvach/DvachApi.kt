package com.github.adamantcheese.chan.core.site.sites.dvach

import android.util.JsonReader
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.site.SiteEndpoints
import com.github.adamantcheese.chan.core.site.common.CommonSite
import com.github.adamantcheese.chan.core.site.common.CommonSite.CommonApi
import com.github.adamantcheese.chan.core.site.parser.ChanReader
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessor
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.data.bookmark.StickyThread
import com.github.adamantcheese.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.adamantcheese.model.data.bookmark.ThreadBookmarkInfoPostObject
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import org.jsoup.parser.Parser
import java.io.IOException
import java.util.*
import kotlin.math.max

@Suppress("BlockingMethodInNonBlockingContext")
class DvachApi internal constructor(commonSite: CommonSite) : CommonApi(commonSite) {

  @Throws(Exception::class)
  override suspend fun loadThread(reader: JsonReader, chanReaderProcessor: ChanReaderProcessor) {
    iteratePostsInThread(reader) { _, reader -> readPostObject(reader, chanReaderProcessor) }
  }

  @Throws(Exception::class)
  override suspend fun loadCatalog(reader: JsonReader, chanReaderProcessor: ChanReaderProcessor) {
    iterateThreadsInCatalog(reader) { reader -> readPostObject(reader, chanReaderProcessor) }
  }

  @Throws(Exception::class)
  override suspend fun readPostObject(reader: JsonReader, chanReaderProcessor: ChanReaderProcessor) {
    val builder = Post.Builder()
    builder.board(chanReaderProcessor.loadable.board)
    val endpoints = chanReaderProcessor.loadable.getSite().endpoints()
    val files: MutableList<PostImage> = ArrayList()
    var parentPostId = 0

    reader.beginObject()

    while (reader.hasNext()) {
      val key = reader.nextName()
      when (key) {
        "name" -> builder.name(reader.nextString())
        "subject" -> builder.subject(reader.nextString())
        "comment" -> builder.comment(reader.nextString())
        "timestamp" -> builder.setUnixTimestampSeconds(reader.nextLong())
        "trip" -> builder.tripcode(reader.nextString())
        "parent" -> {
          parentPostId = reader.nextInt()
          builder.op(parentPostId == 0)
          if (parentPostId != 0) {
            builder.opId(parentPostId.toLong())
          }
        }
        "sticky" -> builder.sticky(reader.nextInt() == 1 && builder.op)
        "closed" -> builder.closed(reader.nextInt() == 1)
        "archived" -> builder.archived(reader.nextInt() == 1)
        "posts_count" -> builder.replies(reader.nextInt() - 1)
        "files_count" -> builder.threadImagesCount(reader.nextInt())
        "lasthit" -> builder.lastModified(reader.nextLong())
        "num" -> {
          val num = reader.nextString()
          builder.id(num.toInt().toLong())
        }
        "files" -> {
          reader.beginArray()
          while (reader.hasNext()) {
            val postImage = readPostImage(reader, builder, endpoints)
            if (postImage != null) {
              files.add(postImage)
            }
          }
          reader.endArray()
        }
        else -> {
          // Unknown/ignored key
          reader.skipValue()
        }
      }
    }

    reader.endObject()

    if (parentPostId == 0) {
      builder.opId(builder.id)
    }

    builder.postImages(files)
    if (builder.op) {
      // Update OP fields later on the main thread
      val op = Post.Builder()
      op.closed(builder.closed)
      op.archived(builder.archived)
      op.sticky(builder.sticky)
      op.replies(builder.totalRepliesCount)
      op.threadImagesCount(builder.threadImagesCount)
      op.uniqueIps(builder.uniqueIps)
      op.lastModified(builder.lastModified)
      chanReaderProcessor.op = op
    }

    chanReaderProcessor.addPost(builder)
  }

  @Throws(IOException::class)
  private fun readPostImage(reader: JsonReader, builder: Post.Builder, endpoints: SiteEndpoints): PostImage? {
    var path: String? = null
    var fileSize: Long = 0
    var fileExt: String? = null
    var fileWidth = 0
    var fileHeight = 0
    var fileName: String? = null
    var thumbnail: String? = null
    var fileHash: String? = null

    reader.beginObject()

    while (reader.hasNext()) {
      when (reader.nextName()) {
        "path" -> path = reader.nextString()
        "name" -> fileName = reader.nextString()
        "size" -> {
          // 2ch is in kB
          fileSize = reader.nextLong() * 1024
        }
        "width" -> fileWidth = reader.nextInt()
        "height" -> fileHeight = reader.nextInt()
        "thumbnail" -> thumbnail = reader.nextString()
        "md5" -> fileHash = reader.nextString()
        else -> reader.skipValue()
      }
    }

    reader.endObject()

    if (fileName != null) {
      fileExt = fileName.substring(fileName.lastIndexOf('.') + 1)
      fileName = fileName.substring(0, fileName.lastIndexOf('.'))
    }

    if (path != null && fileName != null) {
      val args = SiteEndpoints.makeArgument("path", path, "thumbnail", thumbnail)
      return PostImage.Builder().serverFilename(fileName)
        .thumbnailUrl(endpoints.thumbnailUrl(builder, false, args))
        .spoilerThumbnailUrl(endpoints.thumbnailUrl(builder, true, args))
        .imageUrl(endpoints.imageUrl(builder, args))
        .filename(Parser.unescapeEntities(fileName, false))
        .extension(fileExt)
        .imageWidth(fileWidth)
        .imageHeight(fileHeight)
        .size(fileSize)
        .fileHash(fileHash, false)
        .build()
    }

    return null
  }

  override suspend fun readThreadBookmarkInfoObject(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    expectedCapacity: Int,
    reader: JsonReader
  ): ModularResult<ThreadBookmarkInfoObject> {
    return ModularResult.Try {
      val postObjects = ArrayList<ThreadBookmarkInfoPostObject>(
        max(expectedCapacity, ChanReader.DEFAULT_POST_LIST_CAPACITY)
      )

      iteratePostsInThread(reader) { dvachExtraThreadInfo, reader ->
        val postObject = readThreadBookmarkInfoPostObject(dvachExtraThreadInfo, reader)
        if (postObject != null) {
          postObjects += postObject
        }
      }

      val originalPost = postObjects.firstOrNull { postObject ->
        postObject is ThreadBookmarkInfoPostObject.OriginalPost
      } as? ThreadBookmarkInfoPostObject.OriginalPost

      if (originalPost == null) {
        throw IllegalStateException("Thread $threadDescriptor has no OP")
      }

      check(threadDescriptor.threadNo == originalPost.postNo) {
        "Original post has incorrect postNo, " +
          "expected: ${threadDescriptor.threadNo}, actual: ${originalPost.postNo}"
      }

      return@Try ThreadBookmarkInfoObject(threadDescriptor, postObjects)
    }
  }

  @Throws(Exception::class)
  suspend fun readThreadBookmarkInfoPostObject(
    dvachExtraThreadInfo: DvachExtraThreadInfo,
    reader: JsonReader
  ): ThreadBookmarkInfoPostObject? {
    var isOp: Boolean = false
    var postNo: Long? = null
    var closed: Boolean = false
    var archived: Boolean = false
    var comment: String = ""
    var sticky: Boolean = false
    var rollingSticky: Boolean = false
    var bumpLimit = dvachExtraThreadInfo.postsCount >= dvachExtraThreadInfo.bumpLimit

    reader.beginObject()

    while (reader.hasNext()) {
      when (reader.nextName()) {
        "num" -> {
          val num = reader.nextString()
          postNo = num.toInt().toLong()
        }
        "closed" -> closed = reader.nextInt() == 1
        "archived" -> archived = reader.nextInt() == 1
        "comment" -> comment = reader.nextString()
        "parent" -> {
          val parentPostId = reader.nextInt()
          isOp = parentPostId == 0
        }
        "sticky" -> sticky = reader.nextInt() > 0
        "endless" -> rollingSticky = reader.nextInt() == 1
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

      val stickyPost = if (sticky && rollingSticky) {
        StickyThread.StickyWithCap(dvachExtraThreadInfo.bumpLimit)
      } else if (sticky) {
        StickyThread.StickyUnlimited
      } else {
        StickyThread.NotSticky
      }

      if (stickyPost !is StickyThread.NotSticky) {
        bumpLimit = false
      }

      return ThreadBookmarkInfoPostObject.OriginalPost(
        postNo,
        closed,
        archived,
        bumpLimit,
        false,
        stickyPost,
        comment
      )
    } else {
      if (postNo == null) {
        Logger.e(TAG, "Error reading RegularPost (postNo=$postNo)")
        return null
      }

      return ThreadBookmarkInfoPostObject.RegularPost(postNo, comment)
    }
  }

  private suspend fun iteratePostsInThread(
    reader: JsonReader,
    iterator: suspend (DvachExtraThreadInfo, JsonReader) -> Unit
  ) {
    reader.beginObject() // Main object

    val dvachExtraThreadInfo = DvachExtraThreadInfo()

    while (reader.hasNext()) {
      when (reader.nextName()) {
        "threads" -> {
          reader.beginArray() // Threads array

          while (reader.hasNext()) {
            reader.beginObject() // Posts object

            if (reader.nextName() == "posts") {
              reader.beginArray() // Posts array

              while (reader.hasNext()) {
                iterator(dvachExtraThreadInfo, reader)
              }

              reader.endArray()
            }

            reader.endObject()
          }

          reader.endArray()
        }
        "bump_limit" -> dvachExtraThreadInfo.bumpLimit = reader.nextInt()
        "posts_count" -> dvachExtraThreadInfo.postsCount = reader.nextInt()
        else -> reader.skipValue()
      }
    }

    reader.endObject()
  }

  private suspend fun iterateThreadsInCatalog(reader: JsonReader, iterator: suspend (JsonReader) -> Unit) {
    reader.beginObject() // Main object

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

  data class DvachExtraThreadInfo(
    var bumpLimit: Int = 500,
    var postsCount: Int = 0
  )

  companion object {
    private const val TAG = "DvachApi"
  }
}