package com.github.k1rakishou.chan.core.site.sites.foolfuuka

import com.github.k1rakishou.chan.core.site.parser.processor.AbstractChanReaderProcessor
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageBuilder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.regex.Pattern

class FoolFuukaReadCatalogThreadsHelper {

  suspend fun readCatalogThreads(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: AbstractChanReaderProcessor
  ) {
    val document = Jsoup.parse(responseBodyStream, StandardCharsets.UTF_8.name(), requestUrl)

    val originalPostElements = document
      .getElementById("main")
      .getElementsByTag("article")
      .filter { element -> element.attributes().any { attribute -> attribute.value.contains("post_is_op", ignoreCase = true) } }

    originalPostElements.forEach { originalPostElement ->
      processOriginalPostElement(originalPostElement, chanReaderProcessor)
    }

    if (originalPostElements.isEmpty()) {
      chanReaderProcessor.endOfUnlimitedCatalogReached = true
    }
  }

  private suspend fun processOriginalPostElement(
    originalPostElement: Element,
    chanReaderProcessor: AbstractChanReaderProcessor
  ) {
    val chanDescriptor = chanReaderProcessor.chanDescriptor

    val threadNo = originalPostElement.attr("id").toLongOrNull()
    if (threadNo == null) {
      Logger.e(TAG, "processOriginalPostElement() failed to find threadNo amount " +
        "attributes '${originalPostElement.concatAttributesToString()}'")
      return
    }

    val boardCode = originalPostElement.attr("data-board")
    if (boardCode != chanDescriptor.boardCode()) {
      Logger.e(TAG, "processOriginalPostElement() Unexpected board code: '${boardCode}', expected: '${chanDescriptor.boardCode()}'")
      return
    }

    val chanPostBuilder = ChanPostBuilder()
    chanPostBuilder.boardDescriptor(chanDescriptor.boardDescriptor())
    chanPostBuilder.id(threadNo)
    chanPostBuilder.opId(threadNo)
    chanPostBuilder.op(true)

    val postSubject = originalPostElement.getElementsByAttributeValue("class", "post_title")
      .textNodes()
      .joinToString(separator = " ")

    chanPostBuilder.subject(postSubject)

    val posterDataNode = originalPostElement.getElementsByAttributeValue("class", "post_poster_data")
      .firstOrNull()

    if (posterDataNode != null) {
      val posterName = posterDataNode.getElementsByAttributeValue("class", "post_author")
        .firstOrNull()
        ?.textNodes()
        ?.joinToString(separator = " ")

      if (posterName.isNotNullNorBlank()) {
        chanPostBuilder.name(posterName)
      }

      val posterTripcode = posterDataNode.getElementsByAttributeValue("class", "post_tripcode")
        .firstOrNull()
        ?.textNodes()
        ?.joinToString(separator = " ")

      if (posterTripcode.isNotNullNorBlank()) {
        chanPostBuilder.tripcode(posterTripcode)
      }
    }

    val dateTime = originalPostElement.getElementsByTag("time")
      .firstOrNull()
      ?.attr("datetime")
      ?.let { dateTimeRaw -> DateTime.parse(dateTimeRaw) }

    if (dateTime != null) {
      chanPostBuilder.setUnixTimestampSeconds(dateTime.millis / 1000L)
    }

    val chanPostImages = originalPostElement.getElementsByAttributeValue("class", "thread_image_box")
      .firstOrNull()
      ?.let { threadImageBoxElement -> convertToChanPostImages(threadImageBoxElement) }

    if (chanPostImages != null && chanPostImages.isNotEmpty()) {
      chanPostBuilder.postImages(chanPostImages, chanPostBuilder.postDescriptor)
    }

    val postComment = originalPostElement.getElementsByAttributeValue("class", "text")
      .firstOrNull()
      ?.text()
      ?: ""

    chanPostBuilder.postCommentBuilder.setUnparsedComment(postComment)

    if (chanPostBuilder.op) {
      chanReaderProcessor.setOp(chanPostBuilder)
    }

    chanReaderProcessor.addPost(chanPostBuilder)
  }

  private fun convertToChanPostImages(threadImageBoxElement: Element): List<ChanPostImage> {
    val fullImageLink = threadImageBoxElement.getElementsByAttributeValue("class", "thread_image_link")
      .firstOrNull()
      ?.attr("href")
      ?.toHttpUrlOrNull()
      ?: return emptyList()

    val postImageElement = threadImageBoxElement.getElementsByAttributeValue("class", "post_image")
      .firstOrNull()
      ?: return emptyList()

    val thumbnailImageLink = postImageElement.attr("src")?.toHttpUrlOrNull()
    val md5Base64 = postImageElement.attr("data-md5")

    val postFileInfo = threadImageBoxElement.getElementsByAttributeValue("class", "post_file")
      .firstOrNull()
      ?.textNodes()
      ?.firstOrNull()

    if (postFileInfo == null) {
      return emptyList()
    }

    val actualFileName = threadImageBoxElement.getElementsByAttributeValue("class", "post_file_filename")
      .firstOrNull()
      ?.textNodes()
      ?.firstOrNull()
      ?.text()

    if (actualFileName == null) {
      return emptyList()
    }

    val matcher = FILE_INFO_PATTERN.matcher(postFileInfo.text())
    if (!matcher.find()) {
      return emptyList()
    }

    val fileSize = matcher.groupOrNull(1)?.toIntOrNull() ?: 0
    val sizeType = matcher.groupOrNull(2) ?: "KB"
    val width = matcher.groupOrNull(3)?.toIntOrNull() ?: 0
    val height = matcher.groupOrNull(4)?.toIntOrNull() ?: 0

    val multiplier = when (sizeType.toUpperCase(Locale.ENGLISH)) {
      "KB" -> 1000
      "KIB" -> 1024
      "MB" -> 1000 * 1000
      "MIB" -> 1024 * 1024
      "GB" -> 1000 * 1000 * 1000
      "GIB" -> 1024 * 1024 * 1024
      "B" -> 1
      else -> 1024
    }

    val actualFileSize = (fileSize * multiplier).toLong()
    val serverFileName = fullImageLink.toString().substringAfterLast("/")
    val extension = StringUtils.extractFileNameExtension(actualFileName)

    val postImage = ChanPostImageBuilder()
      .serverFilename(serverFileName)
      .thumbnailUrl(thumbnailImageLink)
      .spoiler(false)
      .filename(actualFileName)
      .imageUrl(fullImageLink)
      .extension(extension)
      .imageWidth(width)
      .imageHeight(height)
      .size(actualFileSize)
      .fileHash(md5Base64, true)
      .build()

    return listOf(postImage)
  }

  private fun Element.concatAttributesToString(): String {
    return this.attributes().joinToString(separator = ";") { attribute ->
      return@joinToString "key=${attribute.key}, value=${attribute.value}"
    }
  }

  companion object {
    private const val TAG = "FoolFuukaReadCatalogThreadsHelper"
    private val FILE_INFO_PATTERN = Pattern.compile("(\\d+)\\s*([G?|M?|K?i?B]+),\\s*(\\d+)x(\\d+)")
  }

}