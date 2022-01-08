package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import android.webkit.MimeTypeMap
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.parser.processor.AbstractChanReaderProcessor
import com.github.k1rakishou.chan.core.site.parser.processor.ChanReaderProcessor
import com.github.k1rakishou.chan.utils.ConversionUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.useBufferedSource
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.board.LynxchanBoardMeta
import com.github.k1rakishou.model.data.bookmark.StickyThread
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
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.Lazy
import org.joda.time.format.ISODateTimeFormat
import org.jsoup.parser.Parser
import java.io.InputStream
import java.util.regex.Pattern

open class LynxchanApi(
  private val _moshi: Lazy<Moshi>,
  private val _siteManager: Lazy<SiteManager>,
  private val _boardManager: Lazy<BoardManager>,
  site: LynxchanSite
) : CommonSite.CommonApi(site) {
  private val lynxchanCatalogList = Types.newParameterizedType(List::class.java, LynxchanCatalogThread::class.java)

  private val moshi: Moshi
    get() = _moshi.get()
  private val siteManager: SiteManager
    get() = _siteManager.get()
  private val boardManager: BoardManager
    get() = _boardManager.get()

  override suspend fun loadThreadFresh(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    Logger.d(TAG, "loadThreadFresh($requestUrl)")

    val site = siteManager.bySiteDescriptor(chanReaderProcessor.chanDescriptor.siteDescriptor())
      ?: return
    val board = boardManager.byBoardDescriptor(chanReaderProcessor.chanDescriptor.boardDescriptor())
      ?: return

    val endpoints = site.endpoints()

    val lynxchanPostAdapter = moshi.adapter(LynxchanPost::class.java)
    val lynxchanThread = responseBodyStream
      .useBufferedSource { bufferedSource -> lynxchanPostAdapter.fromJson(bufferedSource) }

    if (lynxchanThread == null) {
      return
    }

    val postsCount = lynxchanThread.morePosts?.size ?: 1
    val lynxchanPosts = mutableListWithCap<LynxchanPost>(postsCount)
    lynxchanPosts += lynxchanThread

    if (lynxchanThread.morePosts != null && lynxchanThread.morePosts.isNotEmpty()) {
      lynxchanPosts.addAll(lynxchanThread.morePosts)
    }

    processPostsInternal(
      isReadingCatalog = false,
      posts = lynxchanPosts,
      chanReaderProcessor = chanReaderProcessor,
      board = board,
      endpoints = endpoints
    )
  }

  override suspend fun loadCatalog(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: AbstractChanReaderProcessor
  ) {
    Logger.d(TAG, "loadCatalog($requestUrl)")

    val site = siteManager.bySiteDescriptor(chanReaderProcessor.chanDescriptor.siteDescriptor())
      ?: return
    val board = boardManager.byBoardDescriptor(chanReaderProcessor.chanDescriptor.boardDescriptor())
      ?: return

    val endpoints = site.endpoints()

    val lynxchanCatalogAdapter = moshi.adapter(LynxchanCatalogPage::class.java)
    val lynxchanCatalog = responseBodyStream
      .useBufferedSource { bufferedSource -> lynxchanCatalogAdapter.fromJson(bufferedSource) }

    val catalogThreadPosts = lynxchanCatalog?.threads
    if (catalogThreadPosts == null) {
      throw IllegalStateException("No posts parsed for '$requestUrl'")
    }

    if (catalogThreadPosts.isEmpty()) {
      return
    }

    processPostsInternal(
      isReadingCatalog = true,
      posts = catalogThreadPosts,
      chanReaderProcessor = chanReaderProcessor,
      board = board,
      endpoints = endpoints
    )

    if (chanReaderProcessor.page != null && chanReaderProcessor.page!! >= lynxchanCatalog.pageCount) {
      chanReaderProcessor.endOfUnlimitedCatalogReached = true
    }

    val maxAttachmentSize = ConversionUtils.fileSizeRawToFileSizeInBytes(lynxchanCatalog.maxFileSize)
      ?.toInt()
      ?: -1

    val updatedChanBoard = board.copy(
      maxCommentChars = lynxchanCatalog.maxMessageLength,
      maxFileSize = maxAttachmentSize,
      maxWebmSize = maxAttachmentSize,
      pages = lynxchanCatalog.pageCount
    )

    updatedChanBoard.updateChanBoardMeta<LynxchanBoardMeta> { lynxchanBoardMeta ->
      val captchaType = LynxchanBoardMeta.CaptchaType.fromValue(lynxchanCatalog.captchaMode)
      val maxFileCount = lynxchanCatalog.maxFileCount

      return@updateChanBoardMeta lynxchanBoardMeta
        ?.copy(boardCaptchaType = captchaType, maxFileCount = maxFileCount)
        ?: LynxchanBoardMeta(boardCaptchaType = captchaType, maxFileCount = maxFileCount)
    }

    boardManager.createOrUpdateBoards(boards = listOf(updatedChanBoard))
  }

  override suspend fun readThreadBookmarkInfoObject(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    expectedCapacity: Int,
    requestUrl: String,
    responseBodyStream: InputStream
  ): ModularResult<ThreadBookmarkInfoObject> {
    return ModularResult.Try {
      val lynxchanBookmarkThreadInfoAdapter = moshi.adapter<LynxchanBookmarkThreadInfo>(LynxchanBookmarkThreadInfo::class.java)
      val lynxchanBookmarkThreadInfo = responseBodyStream
        .useBufferedSource { bufferedSource -> lynxchanBookmarkThreadInfoAdapter.fromJson(bufferedSource) }

      if (lynxchanBookmarkThreadInfo == null) {
        throw IllegalStateException("No posts parsed for '$requestUrl'")
      }

      val postObjects = mutableListWithCap<ThreadBookmarkInfoPostObject>(lynxchanBookmarkThreadInfo.postsCount)

      lynxchanBookmarkThreadInfo.iterate { lynxchanCatalogThread ->
        if (lynxchanCatalogThread.isOp) {
          val threadId = lynxchanCatalogThread.threadId!!
          val comment = lynxchanCatalogThread.message ?: ""
          val sticky = lynxchanCatalogThread.pinned == true
          val rollingSticky = lynxchanCatalogThread.cyclic == true
          val closed = lynxchanCatalogThread.locked == true
          var isBumpLimit = lynxchanCatalogThread.autoSage == true

          val stickyPost = if (sticky && rollingSticky) {
            StickyThread.StickyWithCap
          } else if (sticky) {
            StickyThread.StickyUnlimited
          } else {
            StickyThread.NotSticky
          }

          if (stickyPost !is StickyThread.NotSticky) {
            isBumpLimit = false
          }

          postObjects += ThreadBookmarkInfoPostObject.OriginalPost(
            postNo = threadId,
            closed = closed,
            archived = false,
            isBumpLimit = isBumpLimit,
            isImageLimit = false,
            stickyThread = stickyPost,
            comment = comment
          )
        } else {
          val postId = lynxchanCatalogThread.postId!!
          val comment = lynxchanCatalogThread.message ?: ""

          postObjects += ThreadBookmarkInfoPostObject.RegularPost(postId, comment)
        }
      }

      return@Try ThreadBookmarkInfoObject(threadDescriptor, postObjects)
    }
  }

  override suspend fun readFilterWatchCatalogInfoObject(
    boardDescriptor: BoardDescriptor,
    requestUrl: String,
    responseBodyStream: InputStream
  ): ModularResult<FilterWatchCatalogInfoObject> {

    return ModularResult.Try {
      val endpoints = site.endpoints()

      val lynxchanCatalogAdapter = moshi.adapter<List<LynxchanCatalogThread>>(lynxchanCatalogList)
      val lynxchanCatalogThreads = responseBodyStream
        .useBufferedSource { bufferedSource -> lynxchanCatalogAdapter.fromJson(bufferedSource) }

      if (lynxchanCatalogThreads == null) {
        throw IllegalStateException("No posts parsed for '$requestUrl'")
      }

      if (lynxchanCatalogThreads.isEmpty()) {
        return@Try FilterWatchCatalogInfoObject(boardDescriptor, emptyList())
      }

      val threadObjects = lynxchanCatalogThreads.map { lynxchanCatalogThread ->
        val threadNo = lynxchanCatalogThread.threadId
        val comment = lynxchanCatalogThread.message ?: ""
        val subject = lynxchanCatalogThread.subject ?: ""

        val fullThumbnailUrl = lynxchanCatalogThread.thumb?.let { thumb ->
          val args = SiteEndpoints.makeArgument(
            LynxchanEndpoints.THUMB_ARGUMENT_KEY, thumb.removePrefix("/")
          )

          return@let endpoints.thumbnailUrl(boardDescriptor, false, 0, args)
        }

        return@map FilterWatchCatalogThreadInfoObject(
          threadDescriptor = ChanDescriptor.ThreadDescriptor.create(boardDescriptor, threadNo),
          commentRaw = comment,
          subjectRaw = subject,
          thumbnailUrl = fullThumbnailUrl
        )
      }

      return@Try FilterWatchCatalogInfoObject(
        boardDescriptor,
        threadObjects
      )
    }
  }

  private suspend fun processPostsInternal(
    isReadingCatalog: Boolean,
    posts: List<LynxchanPost>,
    chanReaderProcessor: AbstractChanReaderProcessor,
    board: ChanBoard,
    endpoints: SiteEndpoints
  ) {
    var originalPostId: Long? = null

    val postBuilders = posts.map { post ->
      val builder = ChanPostBuilder()
      builder.boardDescriptor(chanReaderProcessor.chanDescriptor.boardDescriptor())

      val isOp = post.threadId != null
      builder.op(isOp)

      if (originalPostId == null && post.threadId != null) {
        originalPostId = post.threadId
      }

      val lastModified = if (post.lastModified > 0L) {
        post.lastModified
      } else {
        if (post.creation != null) {
          LYNXCHAN_DATE_PARSER.parseMillis(post.creation)
        } else {
          0L
        }
      }

      builder.lastModified(lastModified)

      val postId = post.threadId
        ?: post.postId
        ?: error("Post has neither threadId nor postId")

      builder.id(postId)

      if (isReadingCatalog) {
        builder.opId(post.threadId!!)
      } else {
        builder.opId(originalPostId!!)
      }

      if (isOp) {
        if (post.pinned != null) {
          builder.sticky(post.pinned)
        }

        if (post.locked != null) {
          builder.closed(post.locked)
        }

        if (post.cyclic != null) {
          builder.endless(post.cyclic)
        }

        if (isReadingCatalog) {
          val visiblePostsCount = post.morePosts?.size ?: 0
          val omittedPostsCount = post.omittedPostsCount ?: 0

          builder.replies(visiblePostsCount + omittedPostsCount)
        }

        if (post.omittedFiles != null && post.omittedFiles > 0) {
          builder.threadImagesCount(post.omittedFiles)
        }

        chanReaderProcessor.setOp(builder)
      }

      if (post.signedRole != null) {
        builder.moderatorCapcode(post.signedRole)
      }

      builder.name(post.name)
      builder.subject(post.subject)
      builder.comment(post.markdown)
      builder.posterId(post.posterId)

      if (post.flag.isNotNullNorEmpty() && post.flagCode.isNotNullNorEmpty() && post.flagName.isNotNullNorEmpty()) {
        val flag = post.flag.removePrefix("/")
        val flagCode = post.flagCode.removePrefix("-")
        val flagName = post.flagName

        val countryUrl = endpoints.icon(
          LynxchanEndpoints.COUNTRY_FLAG_ICON_KEY,
          SiteEndpoints.makeArgument(LynxchanEndpoints.COUNTRY_FLAG_PATH_KEY, flag)
        )
        builder.addHttpIcon(ChanPostHttpIcon(countryUrl, "$flagName/$flagCode"))
      }

      val timestampSeconds = if (post.creation != null) {
        LYNXCHAN_DATE_PARSER.parseMillis(post.creation) / 1000L
      } else {
        0L
      }

      builder.setUnixTimestampSeconds(timestampSeconds)

      val postImages = post.files
        ?.mapNotNull { postFile -> postFile.toChanPostImage(board, endpoints) }
        ?: emptyList()

      builder.postImages(postImages, builder.postDescriptor)

      return@map builder
    }

    chanReaderProcessor.addManyPosts(postBuilders)
  }

  @JsonClass(generateAdapter = true)
  data class LynxchanBookmarkThreadInfo(
    @Json(name = "threadId") val threadId: Long?,
    @Json(name = "postId") val postId: Long?,
    @Json(name = "message") val message: String?,
    @Json(name = "subject") val subject: String?,
    @Json(name = "locked") val locked: Boolean?,
    @Json(name = "pinned") val pinned: Boolean?,
    @Json(name = "cyclic") val cyclic: Boolean?,
    @Json(name = "autoSage") val autoSage: Boolean?,
    @Json(name = "lastBump") val lastBump: String?,
    @Json(name = "posts") val morePosts: List<LynxchanBookmarkThreadInfo>?
  ) {
    val isOp: Boolean = threadId != null

    val postsCount: Int
      get() = 1 + (morePosts?.size ?: 0)

    fun iterate(func: (LynxchanBookmarkThreadInfo) -> Unit) {
      func(this)

      morePosts?.forEach { lynxchanBookmarkThreadInfo -> func(lynxchanBookmarkThreadInfo) }
    }
  }

  @JsonClass(generateAdapter = true)
  data class LynxchanCatalogThread(
    @Json(name = "threadId") val threadId: Long,
    @Json(name = "page") val page: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "subject") val subject: String?,
    @Json(name = "locked") val locked: Boolean?,
    @Json(name = "pinned") val pinned: Boolean?,
    @Json(name = "cyclic") val cyclic: Boolean?,
    @Json(name = "autoSage") val autoSage: Boolean?,
    @Json(name = "lastBump") val lastBump: String?,
    @Json(name = "thumb") val thumb: String?
  )

  @JsonClass(generateAdapter = true)
  data class LynxchanCatalogPage(
    @Json(name = "pageCount") val pageCount: Int,
    @Json(name = "maxMessageLength") val maxMessageLength: Int,
    @Json(name = "captchaMode") val captchaMode: Int,
    @Json(name = "maxFileCount") val maxFileCount: Int,
    @Json(name = "maxFileSize") val maxFileSize: String,
    @Json(name = "threads") val threads: List<LynxchanPost>
  )

  @JsonClass(generateAdapter = true)
  data class LynxchanPost(
    @Json(name = "id") val posterId: String?,
    @Json(name = "signedRole") val signedRole: String?,
    @Json(name = "name") val name: String,
    @Json(name = "threadId") val threadId: Long?,
    @Json(name = "postId") val postId: Long?,
    @Json(name = "subject") val subject: String?,
    @Json(name = "markdown") val markdown: String?,
    @Json(name = "locked") val locked: Boolean?,
    @Json(name = "pinned") val pinned: Boolean?,
    @Json(name = "cyclic") val cyclic: Boolean?,
    @Json(name = "files") val files: List<LynxchanPostFile>?,
    @Json(name = "omittedFiles") val omittedFiles: Int?,
    @Json(name = "creation") val creation: String?,
    @Json(name = "flag") val flag: String?,
    @Json(name = "flagCode") val flagCode: String?,
    @Json(name = "flagName") val flagName: String?,
    // Before Lynxchan 2.7.0
    @Json(name = "ommitedPosts") val ommitedPosts: Int?,
    // After Lynxchan 2.7.0
    @Json(name = "omittedPosts") val omittedPosts: Int?,
    @Json(name = "posts") val morePosts: List<LynxchanPost>?
  ) {
    val omittedPostsCount: Int?
      get() = omittedPosts ?: ommitedPosts

    val lastModified: Long
      get() = -1L
  }

  @JsonClass(generateAdapter = true)
  data class LynxchanPostFile(
    @Json(name = "originalName") val originalName: String,
    @Json(name = "path") val path: String,
    @Json(name = "thumb") val thumb: String,
    @Json(name = "mime") val mime: String,
    @Json(name = "size") val size: Long,
    @Json(name = "width") val width: Int?,
    @Json(name = "height") val height: Int?,
  ) {

    fun toChanPostImage(board: ChanBoard, endpoints: SiteEndpoints): ChanPostImage? {
      val matcher = POST_IMAGE_HASH_PATTERN.matcher(path)
      if (!matcher.find()) {
        Logger.e(TAG, "toChanPostImage() failed to extract hash out of path (1): \'$path\'")
        return null
      }

      val fileHash = matcher.groupOrNull(1)
      if (fileHash.isNullOrEmpty()) {
        Logger.e(TAG, "toChanPostImage() failed to extract hash out of path (2): \'$path\'")
        return null
      }

      var extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
      if (extension.isNullOrEmpty()) {
        extension = MimeTypeMap.getFileExtensionFromUrl(path)
      }

      if (extension.isNullOrEmpty()) {
        Logger.e(TAG, "toChanPostImage() failed to extract file extension: \'$path\'")
        return null
      }

      val args = SiteEndpoints.makeArgument(
        LynxchanEndpoints.PATH_ARGUMENT_KEY, path.removePrefix("/"),
        LynxchanEndpoints.THUMB_ARGUMENT_KEY, thumb.removePrefix("/")
      )

      var originalNameUnescaped = Parser.unescapeEntities(originalName, false)
      if (extension.isNotNullNorEmpty() && originalNameUnescaped.endsWith(".$extension")) {
        originalNameUnescaped = originalNameUnescaped.removeSuffix(".$extension")
      }

      return ChanPostImageBuilder()
        .serverFilename(fileHash)
        .thumbnailUrl(endpoints.thumbnailUrl(board.boardDescriptor, false, board.customSpoilers, args))
        .imageUrl(endpoints.imageUrl(board.boardDescriptor, args))
        .filename(originalNameUnescaped)
        .extension(extension)
        .imageWidth(width ?: 0)
        .imageHeight(height ?: 0)
        .imageSize(size)
        .fileHash(fileHash, false)
        .build()
    }

    companion object {
      private const val TAG = "LynxchanPostFile"
    }

  }

  companion object {
    private const val TAG = "LynxchanApi"

    // 2021-11-11T13:28:46.312Z
    private val LYNXCHAN_DATE_PARSER = ISODateTimeFormat.dateTimeParser()

    private val POST_IMAGE_HASH_PATTERN = Pattern.compile("\\W([0-9a-fA-F]+)\\W")
  }

}