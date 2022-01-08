package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.SiteSpecificError
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.parser.processor.AbstractChanReaderProcessor
import com.github.k1rakishou.chan.core.site.parser.processor.ChanReaderProcessor
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.useBufferedSource
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
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.Lazy
import org.jsoup.parser.Parser
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

class DvachApiV2(
  private val moshi: Lazy<Moshi>,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  commonSite: CommonSite
) : CommonSite.CommonApi(commonSite) {
  private val extraThreadInfoMap = ConcurrentHashMap<ChanDescriptor.ThreadDescriptor, ExtraThreadInfo>(16)

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

    val dvachThreadsFreshAdapter = moshi.get().adapter(DvachThreadsFresh::class.java)
    val dvachThreadsFresh = responseBodyStream
      .useBufferedSource { bufferedSource -> dvachThreadsFreshAdapter.fromJson(bufferedSource) }

    val error = dvachThreadsFresh?.error
    if (error != null && error.isActuallyError()) {
      chanReaderProcessor.error = SiteSpecificError.DvachError(error.errorCode, error.message())

      if (error.isThreadDeleted()) {
        chanReaderProcessor.deleted = true
      }

      if (error.isThreadClosed()) {
        chanReaderProcessor.closed = true
      }

      return
    }

    val bumpLimit = dvachThreadsFresh?.bumpLimit
    val posters = dvachThreadsFresh?.posters
    val threadPosts = dvachThreadsFresh?.threads?.firstOrNull()?.posts

    if (threadPosts == null) {
      throw IllegalStateException("No posts parsed for '$requestUrl'")
    }

    if (threadPosts.isEmpty()) {
      return
    }

    val threadDescriptor = threadPosts.first().threadDescriptor(board.boardDescriptor)
    val extraThreadInfo = extraThreadInfoMap.getOrPut(threadDescriptor, { ExtraThreadInfo() })

    extraThreadInfo.bumpLimit = bumpLimit
    extraThreadInfo.posters = posters

    processPostsInternal(threadPosts, chanReaderProcessor, board, endpoints)

    if (requestUrl.contains("/arch/res/")) {
      chanReaderProcessor.archived = true
    }
  }

  override suspend fun loadThreadIncremental(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    Logger.d(TAG, "loadThreadIncremental($requestUrl)")

    val site = siteManager.bySiteDescriptor(chanReaderProcessor.chanDescriptor.siteDescriptor())
      ?: return
    val board = boardManager.byBoardDescriptor(chanReaderProcessor.chanDescriptor.boardDescriptor())
      ?: return

    val endpoints = site.endpoints()
    val dvachThreadIncrementalAdapter = moshi.get().adapter(DvachThreadIncremental::class.java)

    val dvachThreadIncremental = responseBodyStream
      .useBufferedSource { bufferedSource -> dvachThreadIncrementalAdapter.fromJson(bufferedSource) }

    val error = dvachThreadIncremental?.error
    if (error != null && error.isActuallyError()) {
      chanReaderProcessor.error = SiteSpecificError.DvachError(error.errorCode, error.message())

      if (error.isThreadDeleted()) {
        chanReaderProcessor.deleted = true
      }

      if (error.isThreadClosed()) {
        chanReaderProcessor.closed = true
      }

      return
    }

    val threadPosts = dvachThreadIncremental?.posts
    val posters = dvachThreadIncremental?.posters

    if (threadPosts == null) {
      throw IllegalStateException("No posts parsed for '$requestUrl'")
    }

    if (threadPosts.isEmpty()) {
      return
    }

    val threadDescriptor = threadPosts.first().threadDescriptor(board.boardDescriptor)
    val extraThreadInfo = extraThreadInfoMap.getOrPut(threadDescriptor, { ExtraThreadInfo() })

    extraThreadInfo.posters = posters

    processPostsInternal(threadPosts, chanReaderProcessor, board, endpoints)
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

    val dvachCatalogAdapter = moshi.get().adapter(DvachCatalog::class.java)
    val dvachCatalog = responseBodyStream
      .useBufferedSource { bufferedSource -> dvachCatalogAdapter.fromJson(bufferedSource) }

    val error = dvachCatalog?.error
    if (error != null && error.isActuallyError()) {
      chanReaderProcessor.error = SiteSpecificError.DvachError(error.errorCode, error.message())

      if (error.cantAccessCatalog()) {
        chanReaderProcessor.closed = true
      }

      return
    }

    val catalogThreadPosts = dvachCatalog?.threads

    if (catalogThreadPosts == null) {
      throw IllegalStateException("No posts parsed for '$requestUrl'")
    }

    if (catalogThreadPosts.isEmpty()) {
      return
    }

    processPostsInternal(catalogThreadPosts, chanReaderProcessor, board, endpoints)
  }

  private suspend fun processPostsInternal(
    posts: List<DvachPost>,
    chanReaderProcessor: AbstractChanReaderProcessor,
    board: ChanBoard,
    endpoints: SiteEndpoints
  ) {
    val postsCount = posts.size

    // TODO(KurobaEx): this seems to be incorrect when we are reading a catalog
    val threadDescriptor = posts.first().threadDescriptor(board.boardDescriptor)
    val posters = extraThreadInfoMap[threadDescriptor]?.posters

    val postBuilders = posts.map { threadPost ->
      val builder = ChanPostBuilder()
      builder.boardDescriptor(chanReaderProcessor.chanDescriptor.boardDescriptor())

      builder.op(threadPost.parent == 0L)
      builder.lastModified(threadPost.lasthit)
      builder.id(threadPost.num)

      if (threadPost.parent != 0L) {
        builder.opId(threadPost.parent)
      } else {
        builder.opId(threadPost.num)
      }

      if (builder.op) {
        builder.sticky(threadPost.sticky > 0L)
        builder.closed(threadPost.closed == 1L)
        builder.endless(threadPost.endless == 1L)

        if (posters != null) {
          builder.uniqueIps(posters)
        }

        if (threadPost.postsCount != null && postsCount > 0) {
          builder.replies(threadPost.postsCount)
        }

        if (threadPost.filesCount != null && threadPost.filesCount > 0) {
          builder.threadImagesCount(threadPost.filesCount)
        }

        chanReaderProcessor.setOp(builder)
      }

      if (threadPost.trip.startsWith("!!%")) {
        val trip = threadPost.trip
          .removePrefix("!!%")
          .removeSuffix("%!!")

        builder.moderatorCapcode(trip)
      } else {
        builder.tripcode(threadPost.trip)
      }

      if (threadPost.name != DvachPostParser.DVACH_DEFAULT_POSTER_NAME) {
        builder.name(threadPost.name)
      } else {
        builder.name("")
      }

      builder.subject(threadPost.subject)
      builder.comment(threadPost.comment)
      builder.setUnixTimestampSeconds(threadPost.timestamp)
      builder.sage(threadPost.isSage())

      val postImages = threadPost.files
        ?.mapNotNull { postFile -> postFile.toChanPostImage(board, endpoints) }
        ?: emptyList()

      builder.postImages(postImages, builder.postDescriptor)

      return@map builder
    }

    chanReaderProcessor.addManyPosts(postBuilders)
  }

  override suspend fun readThreadBookmarkInfoObject(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    expectedCapacity: Int,
    requestUrl: String,
    responseBodyStream: InputStream
  ): ModularResult<ThreadBookmarkInfoObject> {
    return ModularResult.Try {
      val dvachBookmarkCatalogInfoAdapter = moshi.get().adapter(DvachBookmarkCatalogInfo::class.java)
      val dvachThreadsFresh = responseBodyStream
        .useBufferedSource { bufferedSource -> dvachBookmarkCatalogInfoAdapter.fromJson(bufferedSource) }

      val bumpLimitCount = dvachThreadsFresh?.bumpLimit
      val threadPosts = dvachThreadsFresh?.threads?.firstOrNull()?.posts

      if (threadPosts == null) {
        throw IllegalStateException("No posts parsed for '$requestUrl'")
      }

      if (threadPosts.isEmpty()) {
        return@Try ThreadBookmarkInfoObject(threadDescriptor, emptyList())
      }

      if (bumpLimitCount != null && bumpLimitCount > 0) {
        val extraThreadInfo = extraThreadInfoMap.getOrPut(threadDescriptor, { ExtraThreadInfo() })
        extraThreadInfo.bumpLimit = bumpLimitCount
      }

      val postObjects = threadPosts.map { threadPost ->
        val postNo = threadPost.num

        if (threadPost.isOp) {
          val sticky = threadPost.sticky > 0
          val rollingSticky = threadPost.endless == 1L
          val closed = threadPost.closed == 1L
          var isBumpLimit = bumpLimitCount != null && bumpLimitCount > 0

          val stickyPost = if (sticky && rollingSticky && bumpLimitCount != null) {
            StickyThread.StickyWithCap
          } else if (sticky) {
            StickyThread.StickyUnlimited
          } else {
            StickyThread.NotSticky
          }

          if (stickyPost !is StickyThread.NotSticky) {
            isBumpLimit = false
          }

          return@map ThreadBookmarkInfoPostObject.OriginalPost(
            postNo,
            closed,
            false,
            isBumpLimit,
            false,
            stickyPost,
            threadPost.comment
          )
        }

        return@map ThreadBookmarkInfoPostObject.RegularPost(postNo, threadPost.comment)
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

      val dvachFilterWatchCatalogInfoAdapter = moshi.get().adapter(DvachFilterWatchCatalogInfo::class.java)
      val catalogThreadPosts = responseBodyStream
        .useBufferedSource { bufferedSource -> dvachFilterWatchCatalogInfoAdapter.fromJson(bufferedSource) }
        ?.threads

      if (catalogThreadPosts == null) {
        throw IllegalStateException("No posts parsed for '$requestUrl'")
      }

      if (catalogThreadPosts.isEmpty()) {
        return@Try FilterWatchCatalogInfoObject(boardDescriptor, emptyList())
      }

      val threadObjects = catalogThreadPosts.mapNotNull { catalogThreadPost ->
        val threadNo = catalogThreadPost.num
        val comment = catalogThreadPost.comment
        val isOp = catalogThreadPost.parent == 0L
        val subject = catalogThreadPost.subject

        if (!isOp) {
          return@mapNotNull null
        }

        val fullThumbnailUrl = catalogThreadPost.files?.firstOrNull()?.let { file ->
          if (file.isSticker()) {
            return@let null
          }

          val args = SiteEndpoints.makeArgument("path", file.path, "thumbnail", file.thumbnail)
          return@let endpoints.thumbnailUrl(boardDescriptor, false, 0, args)
        }

        return@mapNotNull FilterWatchCatalogThreadInfoObject(
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

  @JsonClass(generateAdapter = true)
  data class DvachError(
    @Json(name = "code")
    val errorCode: Int
  ) {

    fun message(): String {
      return when (errorCode) {
        NO_ERROR -> "No error"
        BOARD_DOES_NOT_EXIST -> "Board does not exist"
        THREAD_DOES_NOT_EXIST -> "Thread does not exist"
        NO_ACCESS -> "No access"
        THREAD_IS_CLOSED -> "Thread is closed"
        BOARD_IS_CLOSED -> "Board is closed"
        BOARD_IS_VIP_ONLY -> "Board is VIP (passcode) only"
        else -> "Unsupported error code: $errorCode"
      }
    }

    fun isActuallyError(): Boolean = errorCode != 0

    fun isThreadDeleted(): Boolean {
      when (errorCode) {
        BOARD_IS_CLOSED,
        BOARD_DOES_NOT_EXIST,
        BOARD_IS_VIP_ONLY,
        THREAD_DOES_NOT_EXIST -> return true
        else -> return false
      }
    }

    fun isThreadClosed(): Boolean {
      when (errorCode) {
        THREAD_IS_CLOSED -> return true
        else -> return false
      }
    }

    fun cantAccessCatalog(): Boolean {
      when (errorCode) {
        BOARD_DOES_NOT_EXIST,
        BOARD_IS_VIP_ONLY,
        BOARD_IS_CLOSED,
        NO_ACCESS -> return true
        else -> return false
      }
    }

    companion object {
      private const val NO_ERROR = 0
      private const val BOARD_DOES_NOT_EXIST = -2
      private const val THREAD_DOES_NOT_EXIST = -3
      private const val NO_ACCESS = -4
      private const val THREAD_IS_CLOSED = -7
      private const val BOARD_IS_CLOSED = -41
      private const val BOARD_IS_VIP_ONLY = -42

      fun isNotFoundError(errorCode: Int): Boolean {
        return errorCode == BOARD_DOES_NOT_EXIST || errorCode == THREAD_DOES_NOT_EXIST
      }
    }

  }

  @JsonClass(generateAdapter = true)
  data class DvachThreadsFresh(
    @Json(name = "bump_limit")
    val bumpLimit: Int?,
    @Json(name = "threads")
    val threads: List<DvachThreadFresh>?,
    @Json(name = "unique_posters")
    val posters: Int?,
    @Json(name = "error")
    val error: DvachError?
  )

  @JsonClass(generateAdapter = true)
  data class DvachThreadFresh(
    @Json(name = "posts")
    val posts: List<DvachPost>?,
    @Json(name = "error")
    val error: DvachError?
  )

  @JsonClass(generateAdapter = true)
  data class DvachThreadIncremental(
    @Json(name = "posts")
    val posts: List<DvachPost>?,
    @Json(name = "unique_posters")
    val posters: Int?,
    @Json(name = "error")
    val error: DvachError?
  )

  @JsonClass(generateAdapter = true)
  data class DvachCatalog(
    @Json(name = "threads")
    val threads: List<DvachPost>?,
    @Json(name = "error")
    val error: DvachError?
  )

  @JsonClass(generateAdapter = true)
  data class DvachPost(
    val num: Long,
    val op: Long,
    val parent: Long,
    val banned: Long,
    val closed: Long,
    val comment: String,
    val subject: String,
    val date: String,
    val email: String,
    val name: String,
    val sticky: Long,
    val endless: Long,
    val timestamp: Long,
    val trip: String,
    val lasthit: Long,
    @Json(name = "posts_count")
    val postsCount: Int?,
    @Json(name = "files_count")
    val filesCount: Int?,
    val files: List<DvachFile>?
  ) {
    val isOp: Boolean
      get() = parent == 0L

    val originalPostNo: Long
      get() {
        if (parent != 0L) {
          return parent
        } else {
          return num
        }
      }

    fun threadDescriptor(boardDescriptor: BoardDescriptor): ChanDescriptor.ThreadDescriptor {
      return ChanDescriptor.ThreadDescriptor.Companion.create(
        Dvach.SITE_DESCRIPTOR.siteName,
        boardDescriptor.boardCode,
        originalPostNo
      )
    }

    fun isSage(): Boolean {
      return email.contains("mailto:sage")
    }
  }

  @JsonClass(generateAdapter = true)
  data class DvachFile(
    val fullname: String?,
    val md5: String?,
    val name: String?,
    val path: String?,
    val size: Long,
    val thumbnail: String,
    @Json(name = "tn_height")
    val tnHeight: Long,
    @Json(name = "tn_width")
    val tnWidth: Long,
    val type: Long,
    val width: Int,
    val height: Int
  ) {

    fun toChanPostImage(
      board: ChanBoard,
      endpoints: SiteEndpoints
    ): ChanPostImage? {
      if (path?.contains("/stickers/", ignoreCase = true) == true) {
        return null
      }

      var fileExt: String? = null
      var serverFileName: String? = null

      if (name != null) {
        fileExt = StringUtils.extractFileNameExtension(name)
        serverFileName = StringUtils.removeExtensionFromFileName(name)
      }

      val originalFileName = if (fullname.isNullOrEmpty()) {
        serverFileName
      } else {
        StringUtils.removeExtensionFromFileName(fullname)
      }

      if (path != null && serverFileName != null) {
        val args = SiteEndpoints.makeArgument(
          "path", path,
          "thumbnail", thumbnail
        )

        return ChanPostImageBuilder()
          .serverFilename(serverFileName)
          .thumbnailUrl(
            endpoints.thumbnailUrl(board.boardDescriptor, false, board.customSpoilers, args)
          )
          .spoilerThumbnailUrl(
            endpoints.thumbnailUrl(board.boardDescriptor, true, board.customSpoilers, args)
          )
          .imageUrl(
            endpoints.imageUrl(board.boardDescriptor, args)
          )
          .filename(Parser.unescapeEntities(originalFileName, false))
          .extension(fileExt)
          .imageWidth(width)
          .imageHeight(height)
          // 2ch file size is in kB
          .imageSize(size * 1024)
          .fileHash(md5, false)
          .build()
      }

      return null
    }

  }

  @JsonClass(generateAdapter = true)
  data class DvachBookmarkCatalogInfo(
    @Json(name = "bump_limit")
    val bumpLimit: Int,
    @Json(name = "threads")
    val threads: List<DvachThreadPostInfo>,
  )

  @JsonClass(generateAdapter = true)
  data class DvachThreadPostInfo(
    @Json(name = "posts")
    val posts: List<DvachBookmarkPostInfo>
  )

  @JsonClass(generateAdapter = true)
  data class DvachBookmarkPostInfo(
    val num: Long,
    val parent: Long,
    val closed: Long,
    val comment: String,
    val sticky: Long,
    val endless: Long,
  ) {
    val isOp: Boolean
      get() = parent == 0L
  }

  @JsonClass(generateAdapter = true)
  data class DvachFilterWatchCatalogInfo(
    @Json(name = "threads")
    val threads: List<DvachFilterWatchPostInfo>
  )

  @JsonClass(generateAdapter = true)
  data class DvachFilterWatchPostInfo(
    val num: Long,
    val parent: Long,
    val comment: String,
    val subject: String,
    val files: List<DvachFilterWatchFileInfo>?
  )

  @JsonClass(generateAdapter = true)
  data class DvachFilterWatchFileInfo(
    val path: String?,
    val thumbnail: String,
  ) {
    fun isSticker(): Boolean {
      return path?.contains("/stickers/", ignoreCase = true) == true
    }
  }

  data class ExtraThreadInfo(
    @get:Synchronized
    @set:Synchronized
    var bumpLimit: Int? = null,
    @get:Synchronized
    @set:Synchronized
    var posters: Int? = null
  )

  companion object {
    private const val TAG = "DvachApiV2"
  }
}