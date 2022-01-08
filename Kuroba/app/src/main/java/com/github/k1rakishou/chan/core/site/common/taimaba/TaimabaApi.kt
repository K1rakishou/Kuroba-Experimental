package com.github.k1rakishou.chan.core.site.common.taimaba

import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.CommonSite.CommonApi
import com.github.k1rakishou.chan.core.site.parser.processor.AbstractChanReaderProcessor
import com.github.k1rakishou.chan.core.site.parser.processor.ChanReaderProcessor
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoPostObject
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogInfoObject
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogThreadInfoObject
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.data.post.ChanPostHttpIcon
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageBuilder
import com.google.gson.stream.JsonReader
import org.jsoup.parser.Parser
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*
import kotlin.math.max

@Suppress("BlockingMethodInNonBlockingContext")
class TaimabaApi(
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  commonSite: CommonSite
) : CommonApi(commonSite) {

  @Throws(Exception::class)
  override suspend fun loadThreadFresh(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    readBodyJson(responseBodyStream) { jsonReader ->
      vichanReaderExtensions.iteratePostsInThread(jsonReader) { reader ->
        readPostObject(reader, chanReaderProcessor)
      }

      chanReaderProcessor.applyChanReadOptions()
    }
  }

  @Throws(Exception::class)
  override suspend fun loadCatalog(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: AbstractChanReaderProcessor
  ) {
    readBodyJson(responseBodyStream) { jsonReader ->
      vichanReaderExtensions.iterateThreadsInCatalog(jsonReader) { reader ->
        readPostObject(reader, chanReaderProcessor)
      }
    }
  }

  @Throws(Exception::class)
  private suspend fun readPostObject(reader: JsonReader, chanReaderProcessor: AbstractChanReaderProcessor) {
    val builder = ChanPostBuilder()
    builder.boardDescriptor(chanReaderProcessor.chanDescriptor.boardDescriptor())

    val site = siteManager.bySiteDescriptor(chanReaderProcessor.chanDescriptor.siteDescriptor())
      ?: return
    val board = boardManager.byBoardDescriptor(chanReaderProcessor.chanDescriptor.boardDescriptor())
      ?: return

    val endpoints = site.endpoints()

    // File
    var fileExt: String? = null
    var fileWidth = 0
    var fileHeight = 0
    var fileSize: Long = 0
    var fileSpoiler = false
    var fileName: String? = null

    /* prevent API parse error
       resto is not available on opening board overview the first time
       so, we manually set the opId to 0, builder.op to true and builder.opId to 0 */
    var opId: Int
    builder.op(true)
    builder.opId(0)
    var postcom: String? = null
    val files: MutableList<ChanPostImage> = ArrayList()

    // Country flag
    var countryCode: String? = null
    var trollCountryCode: String? = null
    var countryName: String? = null

    reader.beginObject()

    while (reader.hasNext()) {
      when (reader.nextName()) {
        "no" -> builder.id(reader.nextInt().toLong())
        "resto" -> {
          opId = reader.nextInt()
          builder.op(opId == 0)
          builder.opId(opId.toLong())
        }
        "sticky" -> builder.sticky(reader.nextInt() == 1)
        "closed" -> builder.closed(reader.nextInt() == 1)
        "time" -> builder.setUnixTimestampSeconds(reader.nextLong())
        "name" -> builder.name(reader.nextString())
        "trip" -> builder.tripcode(reader.nextString())
        "id" -> builder.posterId(reader.nextString())
        "sub" -> builder.subject(reader.nextString())
        "com" -> builder.comment(readComment(reader))
        "filename" -> fileName = reader.nextString()
        "ext" -> fileExt = reader.nextString().replace(".", "")
        "fsize" -> fileSize = reader.nextLong()
        "w" -> fileWidth = reader.nextInt()
        "h" -> fileHeight = reader.nextInt()
        "country" -> countryCode = reader.nextString()
        "troll_country" -> trollCountryCode = reader.nextString()
        "country_name" -> countryName = reader.nextString()
        "spoiler" -> fileSpoiler = reader.nextInt() == 1
        "archived" -> builder.archived(reader.nextInt() == 1)
        "replies" -> builder.replies(reader.nextInt())
        "images" -> builder.threadImagesCount(reader.nextInt())
        "unique_ips" -> builder.uniqueIps(reader.nextInt())
        "last_modified" -> builder.lastModified(reader.nextLong())
        "capcode" -> builder.moderatorCapcode(reader.nextString())
        "extra_files" -> {
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

    if (!builder.hasPostDescriptor()) {
      Logger.e(TAG, "readPostObject() Post has no PostDescriptor!")
      return
    }

    // The file from between the other values.
    if (fileName != null && fileExt != null) {
      val args = SiteEndpoints.makeArgument("tim", fileName, "ext", fileExt)
      val image = ChanPostImageBuilder()
        .thumbnailUrl(endpoints.thumbnailUrl(builder.boardDescriptor, false, board.customSpoilers, args))
        .spoilerThumbnailUrl(endpoints.thumbnailUrl(builder.boardDescriptor, true, board.customSpoilers, args))
        .imageUrl(endpoints.imageUrl(builder.boardDescriptor, args))
        .filename(Parser.unescapeEntities(fileName, false))
        .serverFilename(fileName)
        .extension(fileExt)
        .imageWidth(fileWidth)
        .imageHeight(fileHeight)
        .spoiler(fileSpoiler)
        .imageSize(fileSize)
        .build()

      // Insert it at the beginning.
      files.add(0, image)
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
      chanReaderProcessor.setOp(op)
    }

    if (countryCode != null && countryName != null) {
      val countryUrl = endpoints.icon("country", SiteEndpoints.makeArgument("country_code", countryCode))
      builder.addHttpIcon(ChanPostHttpIcon(countryUrl, "$countryName/$countryCode"))
    }

    if (trollCountryCode != null && countryName != null) {
      val countryUrl = endpoints.icon("troll_country", SiteEndpoints.makeArgument("troll_country_code", trollCountryCode))
      builder.addHttpIcon(ChanPostHttpIcon(countryUrl, "$countryName/t_$trollCountryCode"))
    }

    chanReaderProcessor.addPost(builder)
  }

  private fun readComment(reader: JsonReader): String {
    var postcom = reader.nextString()
    postcom = postcom.replace(GREEN_TEXT_REPLACE_REGEX, "<blockquote class=\"unkfunc\">&gt;$1</blockquote>")
    postcom = postcom.replace(QUOTE_REPLACE_REGEX, "<a href=\"#$1\">&gt;&gt;$1</a>")
    postcom = postcom.replace(NEWLINE_REPLACE_REGEX, "<br/>")
    postcom = postcom.replace(BOLD_TAG_REPLACE_REGEX1, "<b>$1</b>")
    postcom = postcom.replace(BOLD_TAG_REPLACE_REGEX2, "<b>$1</b>")
    postcom = postcom.replace(ITALIC_TAG_REPLACE_REGEX1, "<i>$1</i>")
    postcom = postcom.replace(ITALIC_TAG_REPLACE_REGEX2, "<i>$1</i>")
    postcom = postcom.replace(SPOILER_TAG_REPLACE_REGEX1, "<span class=\"spoiler\">$1</span>")
    postcom = postcom.replace(SPOILER_TAG_REPLACE_REGEX2, "<span class=\"spoiler\">$1</span>")
    postcom = postcom.replace(STRIKE_TROUGH_TAG_REPLACE_REGEX, "<strike>$1</strike>")
    postcom = postcom.replace(PRE_TAG_REPLACE_REGEX1, "<pre>$1</pre>")
    postcom = postcom.replace(PRE_TAG_REPLACE_REGEX2, "<pre>$1</pre>")

    return postcom
  }

  @Throws(IOException::class)
  private fun readPostImage(
    reader: JsonReader,
    builder: ChanPostBuilder,
    board: ChanBoard,
    endpoints: SiteEndpoints
  ): ChanPostImage? {
    var fileSize: Long = 0
    var fileExt: String? = null
    var fileWidth = 0
    var fileHeight = 0
    var fileSpoiler = false
    var fileName: String? = null

    reader.beginObject()

    while (reader.hasNext()) {
      when (reader.nextName()) {
        "fsize" -> fileSize = reader.nextLong()
        "w" -> fileWidth = reader.nextInt()
        "h" -> fileHeight = reader.nextInt()
        "spoiler" -> fileSpoiler = reader.nextInt() == 1
        "ext" -> fileExt = reader.nextString().replace(".", "")
        "filename" -> fileName = reader.nextString()
        else -> reader.skipValue()
      }
    }

    reader.endObject()

    if (fileName != null && fileExt != null) {
      val args = SiteEndpoints.makeArgument("tim", fileName, "ext", fileExt)
      return ChanPostImageBuilder()
        .thumbnailUrl(endpoints.thumbnailUrl(builder.boardDescriptor, false, board.customSpoilers, args))
        .spoilerThumbnailUrl(endpoints.thumbnailUrl(builder.boardDescriptor, true, board.customSpoilers, args))
        .imageUrl(endpoints.imageUrl(builder.boardDescriptor, args))
        .filename(Parser.unescapeEntities(fileName, false))
        .extension(fileExt)
        .imageWidth(fileWidth)
        .imageHeight(fileHeight)
        .spoiler(fileSpoiler)
        .imageSize(fileSize)
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
        max(expectedCapacity, DEFAULT_POST_LIST_CAPACITY)
      )

      JsonReader(InputStreamReader(responseBodyStream)).use { jsonReader ->
        vichanReaderExtensions.iteratePostsInThread(jsonReader) { reader ->
          val postObject = vichanReaderExtensions.readThreadBookmarkInfoPostObject(reader)
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
        vichanReaderExtensions.iterateThreadsInCatalog(jsonReader) { reader ->
          val threadObject = vichanReaderExtensions.readFilterWatchCatalogThreadInfoObject(
            endpoints,
            boardDescriptor,
            reader
          )

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

  companion object {
    private const val TAG = "TaimabaApi"

    private val GREEN_TEXT_REPLACE_REGEX = ">(.*+)".toRegex()
    private val QUOTE_REPLACE_REGEX = "<blockquote class=\"unkfunc\">&gt;>(\\d+)</blockquote>".toRegex()
    private val NEWLINE_REPLACE_REGEX = "\n".toRegex()
    private val BOLD_TAG_REPLACE_REGEX1 = "(?i)\\[b](.*?)\\[/b]".toRegex()
    private val BOLD_TAG_REPLACE_REGEX2 = "(?i)\\[\\*\\*](.*?)\\[/\\*\\*]".toRegex()
    private val ITALIC_TAG_REPLACE_REGEX1 = "(?i)\\[i](.*?)\\[/i]".toRegex()
    private val ITALIC_TAG_REPLACE_REGEX2 = "(?i)\\[\\*](.*?)\\[/\\*]".toRegex()
    private val SPOILER_TAG_REPLACE_REGEX1 = "(?i)\\[spoiler](.*?)\\[/spoiler]".toRegex()
    private val SPOILER_TAG_REPLACE_REGEX2 = "(?i)\\[%](.*?)\\[/%]".toRegex()
    private val STRIKE_TROUGH_TAG_REPLACE_REGEX = "(?i)\\[s](.*?)\\[/s]".toRegex()
    private val PRE_TAG_REPLACE_REGEX1 = "(?i)\\[pre](.*?)\\[/pre]".toRegex()
    private val PRE_TAG_REPLACE_REGEX2 = "(?i)\\[sub](.*?)\\[/sub]".toRegex()
  }
}