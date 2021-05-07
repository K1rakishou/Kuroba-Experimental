package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.CommonSite.CommonApi
import com.github.k1rakishou.chan.core.site.parser.ChanReader
import com.github.k1rakishou.chan.core.site.parser.processor.ChanReaderProcessor
import com.github.k1rakishou.chan.core.site.parser.processor.IChanReaderProcessor
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.jsonArray
import com.github.k1rakishou.common.jsonObject
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.bookmark.StickyThread
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoPostObject
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogInfoObject
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogThreadInfoObject
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageBuilder
import com.google.gson.stream.JsonReader
import okhttp3.HttpUrl
import org.jsoup.parser.Parser
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*
import kotlin.math.max

@Suppress("BlockingMethodInNonBlockingContext")
class DvachApi internal constructor(
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  commonSite: CommonSite
) : CommonApi(commonSite) {

  @Throws(Exception::class)
  override suspend fun loadThread(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    readBodyJson(responseBodyStream) { jsonReader ->
      iteratePostsInThread(jsonReader) { dvachExtraThreadInfo, reader ->
        readPostObject(reader, dvachExtraThreadInfo, chanReaderProcessor)
      }
    }

    chanReaderProcessor.applyChanReadOptions()
  }

  @Throws(Exception::class)
  override suspend fun loadCatalog(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: IChanReaderProcessor
  ) {
    readBodyJson(responseBodyStream) { jsonReader ->
      iterateThreadsInCatalog(jsonReader) { reader ->
        readPostObject(reader, null, chanReaderProcessor)
      }
    }
  }

  @Throws(Exception::class)
  private suspend fun readPostObject(
    reader: JsonReader,
    dvachExtraThreadInfo: DvachExtraThreadInfo?,
    chanReaderProcessor: IChanReaderProcessor
  ) {
    val builder = ChanPostBuilder()
    builder.boardDescriptor(chanReaderProcessor.chanDescriptor.boardDescriptor())

    val site = siteManager.bySiteDescriptor(chanReaderProcessor.chanDescriptor.siteDescriptor())
      ?: return
    val board = boardManager.byBoardDescriptor(chanReaderProcessor.chanDescriptor.boardDescriptor())
      ?: return

    val endpoints = site.endpoints()

    val files: MutableList<ChanPostImage> = ArrayList()
    var parentPostId = 0
    var rollingSticky = false

    reader.beginObject()

    while (reader.hasNext()) {
      when (reader.nextName()) {
        "name" -> builder.name(reader.nextStringWithoutBOM())
        "subject" -> builder.subject(reader.nextStringWithoutBOM())
        "comment" -> builder.comment(reader.nextStringWithoutBOM())
        "timestamp" -> builder.setUnixTimestampSeconds(reader.nextLong())
        "trip" -> builder.tripcode(reader.nextStringWithoutBOM())
        "parent" -> {
          parentPostId = reader.nextInt()
          builder.op(parentPostId == 0)
          if (parentPostId != 0) {
            builder.opId(parentPostId.toLong())
          }
        }
        "sticky" -> builder.sticky(reader.nextInt() == 1 && builder.op)
        "endless" -> rollingSticky = reader.nextInt() == 1
        "closed" -> builder.closed(reader.nextInt() == 1)
        "archived" -> builder.archived(reader.nextInt() == 1)
        "posts_count" -> builder.replies(reader.nextInt() - 1)
        "files_count" -> builder.threadImagesCount(reader.nextInt())
        "lasthit" -> builder.lastModified(reader.nextLong())
        "num" -> {
          val num = reader.nextStringWithoutBOM()
          builder.id(num.toInt().toLong())
        }
        "files" -> {
          reader.beginArray()
          while (reader.hasNext()) {
            val postImage = readPostImage(reader, builder, board, endpoints)
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

    builder.postImages(files, builder.postDescriptor)

    if (builder.op) {
      // Update OP fields later on the main thread
      val op = ChanPostBuilder()
      op.closed(builder.closed)
      op.archived(builder.archived)
      op.sticky(builder.sticky)
      op.replies(builder.totalRepliesCount)
      op.threadImagesCount(builder.threadImagesCount)
      op.uniqueIps(builder.uniqueIps)
      op.lastModified(builder.lastModified)

      if (rollingSticky && builder.sticky && dvachExtraThreadInfo != null) {
        op.stickyCap(dvachExtraThreadInfo.bumpLimit)
      }

      chanReaderProcessor.setOp(op)
    }

    chanReaderProcessor.addPost(builder)
  }

  @Throws(IOException::class)
  private fun readPostImage(
    reader: JsonReader,
    builder: ChanPostBuilder,
    board: ChanBoard,
    endpoints: SiteEndpoints
  ): ChanPostImage? {
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
        "path" -> path = reader.nextStringWithoutBOM()
        "name" -> fileName = reader.nextStringWithoutBOM()
        "size" -> {
          // 2ch is in kB
          fileSize = reader.nextLong() * 1024
        }
        "width" -> fileWidth = reader.nextInt()
        "height" -> fileHeight = reader.nextInt()
        "thumbnail" -> thumbnail = reader.nextStringWithoutBOM()
        "md5" -> fileHash = reader.nextStringWithoutBOM()
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
      return ChanPostImageBuilder()
        .serverFilename(fileName)
        .thumbnailUrl(endpoints.thumbnailUrl(builder.boardDescriptor, false, board.customSpoilers, args))
        .spoilerThumbnailUrl(endpoints.thumbnailUrl(builder.boardDescriptor, true, board.customSpoilers, args))
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
    requestUrl: String,
    responseBodyStream: InputStream,
  ): ModularResult<ThreadBookmarkInfoObject> {
    return ModularResult.Try {
      val postObjects = ArrayList<ThreadBookmarkInfoPostObject>(
        max(expectedCapacity, ChanReader.DEFAULT_POST_LIST_CAPACITY)
      )

      JsonReader(InputStreamReader(responseBodyStream)).use { jsonReader ->
        iteratePostsInThread(jsonReader) { dvachExtraThreadInfo, reader ->
          val postObject = readThreadBookmarkInfoPostObject(dvachExtraThreadInfo, reader)
          if (postObject != null) {
            postObjects += postObject
          }
        }
      }

      val originalPost = postObjects.firstOrNull { postObject ->
        postObject is ThreadBookmarkInfoPostObject.OriginalPost
      } as? ThreadBookmarkInfoPostObject.OriginalPost
        ?: throw IllegalStateException("Thread $threadDescriptor has no OP")

      check(threadDescriptor.threadNo == originalPost.postNo) {
        "Original post has incorrect postNo, " +
          "expected: ${threadDescriptor.threadNo}, actual: ${originalPost.postNo}"
      }

      return@Try ThreadBookmarkInfoObject(threadDescriptor, postObjects)
    }
  }

  @Throws(Exception::class)
  private suspend fun readThreadBookmarkInfoPostObject(
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
          val num = reader.nextStringWithoutBOM()
          postNo = num.toInt().toLong()
        }
        "closed" -> closed = reader.nextInt() == 1
        "archived" -> archived = reader.nextInt() == 1
        "comment" -> comment = reader.nextStringWithoutBOM()
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

  override suspend fun readFilterWatchCatalogInfoObject(
    boardDescriptor: BoardDescriptor,
    requestUrl: String,
    responseBodyStream: InputStream,
  ): ModularResult<FilterWatchCatalogInfoObject> {
    val endpoints = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
      ?.endpoints()
      ?: return ModularResult.error(SiteManager.SiteNotFoundException(boardDescriptor.siteDescriptor))

    return ModularResult.Try {
      val threadObjects = mutableListWithCap<FilterWatchCatalogThreadInfoObject>(100)

      readBodyJson(responseBodyStream) { jsonReader ->
        iterateThreadsInCatalog(jsonReader) { reader ->
          val threadObject = readFilterWatchCatalogThreadInfoObject(boardDescriptor, reader, endpoints)
          if (threadObject != null) {
            threadObjects += threadObject
          }
        }
      }

      return@Try FilterWatchCatalogInfoObject(
        boardDescriptor,
        threadObjects
      )
    }
  }

  private fun readFilterWatchCatalogThreadInfoObject(
    boardDescriptor: BoardDescriptor,
    reader: JsonReader,
    endpoints: SiteEndpoints
  ): FilterWatchCatalogThreadInfoObject? {
    var threadNo: Long? = null
    var isOp = false
    var comment = ""
    var subject = ""
    var path: String? = null
    var thumbnail: String? = null
    var fullThumbnailUrl: HttpUrl? = null

    reader.beginObject()

    while (reader.hasNext()) {
      when (reader.nextName()) {
        "num" -> {
          val num = reader.nextStringWithoutBOM()
          threadNo = num.toInt().toLong()
        }
        "comment" -> comment = reader.nextStringWithoutBOM()
        "parent" -> {
          val parentPostId = reader.nextInt()
          isOp = parentPostId == 0
        }
        "subject" -> subject = reader.nextStringWithoutBOM()
        "files" -> {
          reader.jsonArray {
            if (hasNext()) {
              jsonObject {
                while (hasNext()) {
                  when (nextName()) {
                    "path" -> path = reader.nextStringWithoutBOM()
                    "thumbnail" -> thumbnail = nextStringWithoutBOM()
                    else -> skipValue()
                  }
                }
              }
            }
          }
        }
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

    if (path != null && thumbnail != null) {
      val args = SiteEndpoints.makeArgument("path", path, "thumbnail", thumbnail)
      fullThumbnailUrl = endpoints.thumbnailUrl(boardDescriptor, false, 0, args)
    }

    return FilterWatchCatalogThreadInfoObject(
      threadDescriptor = ChanDescriptor.ThreadDescriptor.Companion.create(boardDescriptor, threadNo),
      commentRaw = comment,
      subjectRaw = subject,
      thumbnailUrl = fullThumbnailUrl
    )
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

  private suspend fun iterateThreadsInCatalog(
    reader: JsonReader,
    iterator: suspend (JsonReader) -> Unit
  ) {
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

  // 2ch.hk sometimes sends strings with BOM character which crashes the app after extracting
  // the posts subject from the database (for some reason Room/SQLite filters out this character)
  // because we are trying to restore the spans but we end up with a string that has spans which
  // bounds exceed string length.
  private fun JsonReader.nextStringWithoutBOM(): String {
    return StringUtils.removeUTF8BOM(nextString())
  }

  data class DvachExtraThreadInfo(
    var bumpLimit: Int = 500,
    var postsCount: Int = 0
  )

  companion object {
    private const val TAG = "DvachApi"
  }
}