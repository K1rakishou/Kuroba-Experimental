package com.github.k1rakishou.chan.core.site.sites.fuuka

import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.parser.processor.AbstractChanReaderProcessor
import com.github.k1rakishou.chan.core.site.parser.processor.ChanReaderProcessor
import com.github.k1rakishou.chan.utils.extractFileNameExtension
import com.github.k1rakishou.chan.utils.removeExtensionIfPresent
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ParsingException
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.archive.ArchivePost
import com.github.k1rakishou.model.data.archive.ArchivePostMedia
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogInfoObject
import com.github.k1rakishou.model.mapper.ArchiveThreadMapper
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.InputStream
import java.util.regex.Pattern

class FuukaApi(
  site: CommonSite
) : CommonSite.CommonApi(site) {

  override suspend fun loadThreadFresh(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    readBodyHtml(requestUrl, responseBodyStream) { document ->
      require(chanReaderProcessor.chanDescriptor is ChanDescriptor.ThreadDescriptor) {
        "Cannot load catalogs here!"
      }

      val threadDescriptor = chanReaderProcessor.chanDescriptor

      val archivePosts = try {
        parsePosts(
          requestUrl = requestUrl,
          document = document,
          threadDescriptor = threadDescriptor
        )
      } catch (error: Throwable) {
        Logger.e(TAG, "parserCommandExecutor.executeCommands() error", error)
        return@readBodyHtml
      }

      val postBuilders = archivePosts.mapNotNull { archivePost ->
        if (!archivePost.isValid()) {
          return@mapNotNull null
        }

        return@mapNotNull ArchiveThreadMapper.fromPost(threadDescriptor.boardDescriptor, archivePost)
      }

      val originalPost = postBuilders.firstOrNull()
      if (originalPost == null || !originalPost.op) {
        Logger.e(TAG, "Failed to parse original post or first post is not original post for some reason")
        return@readBodyHtml
      }

      chanReaderProcessor.setOp(originalPost)
      postBuilders.forEach { chanPostBuilder -> chanReaderProcessor.addPost(chanPostBuilder) }
    }

    chanReaderProcessor.applyChanReadOptions()
  }

  override suspend fun loadCatalog(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: AbstractChanReaderProcessor
  ) {
    throw CommonClientException("Catalog is not supported for site ${site.name}")
  }

  override suspend fun readThreadBookmarkInfoObject(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    expectedCapacity: Int,
    requestUrl: String,
    responseBodyStream: InputStream,
  ): ModularResult<ThreadBookmarkInfoObject> {
    val error = CommonClientException("Bookmarks are not supported for site ${site.name}")

    return ModularResult.error(error)
  }

  override suspend fun readFilterWatchCatalogInfoObject(
    boardDescriptor: BoardDescriptor,
    requestUrl: String,
    responseBodyStream: InputStream,
  ): ModularResult<FilterWatchCatalogInfoObject> {
    val error = CommonClientException("Filter watching is not supported for site ${site.name}")

    return ModularResult.error(error)
  }

  private fun parsePosts(
    requestUrl: String,
    document: Document,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): List<ArchivePost> {
    val postForm = document.selectFirst("form[id=postForm]")
      ?: throw ParsingException("Failed to find postForm")

    val originalPostElement = postForm.selectFirst("div[class=comment]")
      ?: throw ParsingException("Failed to find original post")

    val threadPosts = postForm.select("td[class=comment reply]")

    return buildList<ArchivePost>(capacity = threadPosts.size + 1) {
      var originalPost = parsePost(
        requestUrl = requestUrl,
        threadDescriptor = threadDescriptor,
        postElement = originalPostElement,
        isOP = true
      )
      if (originalPost == null) {
        originalPost = ArchivePost(
          boardDescriptor = threadDescriptor.boardDescriptor,
          threadNo = threadDescriptor.threadNo,
          postNo = threadDescriptor.threadNo,
          isOP = true,
          unixTimestampSeconds = System.currentTimeMillis() / 1000L,
          comment = "Failed to find Original Post (most likely warosu.org bug)"
        )
      }

      add(originalPost)

      threadPosts.forEach { threadPost ->
        parsePost(
          requestUrl = requestUrl,
          threadDescriptor = threadDescriptor,
          postElement = threadPost,
          isOP = false
        )?.let { post -> add(post) }
      }
    }
  }

  private fun parsePost(
    requestUrl: String,
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    postElement: Element,
    isOP: Boolean
  ): ArchivePost? {
    val idElement = if (isOP) {
      postElement.selectFirst("div[id^=p]")
    } else {
      postElement.selectFirst("td[id^=p]")
    }

    if (idElement == null) {
      Logger.error(TAG) { "parsePost() failed to parse post id" }
      return null
    }

    val idParts = idElement.attr("id").removePrefix("p").split("_")

    val postNo = idParts.getOrNull(0)?.toLongOrNull()
    if (postNo == null) {
      Logger.error(TAG) { "parsePost() failed to parse postNo, idParts: ${idParts}" }
      return null
    }

    val postSubNo = idParts.getOrNull(1)?.toLongOrNull() ?: 0L

    val unixTimestampSeconds = postElement.selectFirst("span[class=posttime]")
      ?.attr("title")
      ?.toLongOrNull()
      ?.div(1000) ?: -1L

    val name = postElement.selectFirst("span[class=postername]")?.text() ?: ""
    val subject = postElement.selectFirst("span[class=filetitle]")?.text() ?: ""
    val tripcode = postElement.selectFirst("span[class=postertrip]")?.text() ?: ""
    val comment = postElement.selectFirst("blockquote")?.html() ?: ""

    val archivePostMediaList = parseMedia(requestUrl, postElement, threadDescriptor)
      ?.let { archivePostMedia -> mutableListOf(archivePostMedia) }
      ?: mutableListOf()

    return ArchivePost(
      boardDescriptor = threadDescriptor.boardDescriptor,
      postNo = postNo,
      postSubNo = postSubNo,
      threadNo = threadDescriptor.threadNo,
      isOP = isOP,
      unixTimestampSeconds = unixTimestampSeconds,
      name = name,
      subject = subject,
      comment = comment,
      sticky = false,
      closed = false,
      archived = false,
      tripcode = tripcode,
      posterId = "",
      archivePostMediaList = archivePostMediaList
    )
  }

  private fun parseMedia(
    requestUrl: String,
    postElement: Element,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): ArchivePostMedia? {
    val rawText = postElement.selectFirst("span[class=fileinfo]")?.text()
    if (rawText == null) {
      Logger.error(TAG) { "parseMedia() failed to find fileinfo" }
      return null
    }

    val matcher = FILE_INFO_PATTERN.matcher(rawText)
    if (!matcher.find()) {
      return null
    }

    val fileSizeValue = matcher.groupOrNull(1)?.toFloatOrNull()
    val fileSizeType = matcher.groupOrNull(2)
    val fileWidthAndHeight = matcher.groupOrNull(3)
    val originalFileName = matcher.groupOrNull(4)

    if (fileSizeValue == null
      || fileSizeType == null
      || fileWidthAndHeight == null
      || originalFileName == null
    ) {
      Logger.error(TAG) {
        "Failed to parse file, tagText: '$rawText', fileSizeValue: $fileSizeValue, " +
          "fileSizeType: $fileSizeType, fileWidthAndHeight: $fileWidthAndHeight, " +
          "originalFileName: $originalFileName"
      }

      return null
    }

    val (width: Int?, height: Int?) = fileWidthAndHeight
      .split("x")
      .map { size -> size.toIntOrNull() }

    if (width == null || height == null) {
      Logger.error(TAG) {
        "Failed to extract file width and height, " +
          "fileWidthAndHeight: '$fileWidthAndHeight', width: $width, height: $height"
      }

      return null
    }

    val fileSizeMultiplier = when (fileSizeType) {
      "MB" -> 1024 * 1024
      "KB" -> 1024
      "B" -> 1
      else -> 1
    }

    val actualFileSize = (fileSizeValue * fileSizeMultiplier.toFloat()).toLong()

    val fullImageUrl = postElement.select("a[href]")
      .firstOrNull { it.attr("href").startsWith("https://i.warosu.org/data/") }
      ?.attr("href")

    if (fullImageUrl == null) {
      Logger.e(TAG, "Failed to parse full image url, fullImageUrl: '$fullImageUrl'")
      return null
    }

    val thumbnailUrl = postElement.select("a[href]")
      .firstOrNull { it.attr("href").startsWith("https://i.warosu.org/data/") }
      ?.selectFirst("img")
      ?.attr("src")

    val serverFilename = removeExtensionIfPresent(fullImageUrl.substringAfterLast('/'))
    val extension = extractFileNameExtension(fullImageUrl)

    return ArchivePostMedia(
      filename = originalFileName,
      imageWidth = width,
      imageHeight = height,
      size = actualFileSize,
      thumbnailUrl = thumbnailUrl,
      imageUrl = fullImageUrl,
      serverFilename = serverFilename,
      extension = extension
    )
  }

  companion object {
    private const val TAG = "FuukaApi"

    // File: 1.32 MB, 2688x4096,   EsGgXsxUcAQV0_h.jpg
    // File:   694 KB, 2894x4093, EscP39vUUAEE9ol.jpg
    // File: 694 B,   2894x4093,   EscP39vUUAEE9ol.png
    // File: 694 B,   2894x4093,   EscP39vUUAEE9ol.webm
    // File: 3.29 MB, 1920x1080, 【龍が如く極2】関西と関東の戦い！！？？止めるにぇ、桐生ちゃん！【ホロライブ_さくらみこ】※ネタバレあり 48-45 screenshot.png
    private val FILE_INFO_PATTERN = Pattern.compile("File:\\s+(\\d+(?:\\.\\d+)?)\\s+((?:[MK])?B),\\s+(\\d+x\\d+),\\s+(.*)\$")

    // https://warosu.org/jp/thread/32638291
    // https://warosu.org/jp/thread/32638291#p32638297
    // https://warosu.org/jp/thread/32638291#p32638297_123
    // https://warosu.org/jp/thread/S32638291#p32638297_123
    private val POST_LINK_PATTERN = Pattern.compile("\\/(\\w+)\\/thread\\/S?(\\d+)(?:#p(\\d+))?(?:_(\\d+))?")
  }
}
