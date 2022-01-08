package com.github.k1rakishou.chan.core.site.sites.foolfuuka

import com.github.k1rakishou.chan.core.site.parser.processor.AbstractChanReaderProcessor
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.getFirstElementByClassWithAnyValue
import com.github.k1rakishou.common.getFirstElementByClassWithValue
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
      ?.getElementsByTag("article")
      ?.filter { element ->
        element.attributes().any { attribute -> attribute.value.contains("clearfix thread doc_id", ignoreCase = true) }
      }

    if (originalPostElements == null) {
      return
    }

    originalPostElements.forEach { originalPostElement ->
      try {
        processOriginalPostElement(originalPostElement, chanReaderProcessor)
      } catch (error: Throwable) {
        Logger.e(TAG, "processOriginalPostElement() error", error)
      }
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

    val chanPostBuilder = ChanPostBuilder()
    chanPostBuilder.boardDescriptor(chanDescriptor.boardDescriptor())
    chanPostBuilder.id(threadNo)
    chanPostBuilder.opId(threadNo)
    chanPostBuilder.op(true)

    val sticky = originalPostElement.getFirstElementByClassWithValue("icon-pushpin") != null
    val closed = originalPostElement.getFirstElementByClassWithValue("icon-lock") != null
    val deleted = originalPostElement.getFirstElementByClassWithValue("icon-trash") != null

    chanPostBuilder.sticky(sticky)
    chanPostBuilder.closed(closed)
    chanPostBuilder.deleted(deleted)

    val postSubject = originalPostElement.getFirstElementByClassWithValue("post_title")
      ?.textNodes()
      ?.joinToString(separator = " ")
      ?: ""

    chanPostBuilder.subject(postSubject)

    val posterDataNode = originalPostElement.getFirstElementByClassWithValue("post_poster_data")
    if (posterDataNode != null) {
      val posterName = posterDataNode.getFirstElementByClassWithValue("post_author")
        ?.textNodes()
        ?.joinToString(separator = " ")

      if (posterName.isNotNullNorBlank()) {
        chanPostBuilder.name(posterName)
      }

      val posterTripcode = posterDataNode.getFirstElementByClassWithValue("post_tripcode")
        ?.textNodes()
        ?.joinToString(separator = " ")

      if (posterTripcode.isNotNullNorBlank()) {
        chanPostBuilder.tripcode(posterTripcode)
      }

      val postedId = posterDataNode.getFirstElementByClassWithValue("poster_hash")
        ?.textNodes()
        ?.joinToString(separator = " ")
        ?.removePrefix("ID:")

      if (postedId.isNotNullNorBlank()) {
        chanPostBuilder.posterId(postedId)
      }
    }

    val dateTime = originalPostElement.getElementsByTag("time")
      .firstOrNull()
      ?.attr("datetime")
      ?.let { dateTimeRaw -> DateTime.parse(dateTimeRaw) }

    if (dateTime != null) {
      chanPostBuilder.setUnixTimestampSeconds(dateTime.millis / 1000L)
    }

    val chanPostImages = originalPostElement.getFirstElementByClassWithValue("thread_image_box")
      ?.let { threadImageBoxElement -> convertToChanPostImages(threadImageBoxElement) }

    if (chanPostImages != null && chanPostImages.isNotEmpty()) {
      chanPostBuilder.postImages(chanPostImages, chanPostBuilder.postDescriptor)
    }

    val postComment = originalPostElement.getFirstElementByClassWithValue("text")
      ?.html()
      ?: ""

    chanPostBuilder.comment(postComment)

    originalPostElement.getFirstElementByClassWithValue("omitted_text")
      ?.let { omittedTextNode ->
        val omittedPosts = omittedTextNode.getFirstElementByClassWithValue("omitted_posts")
          ?.textNodes()
          ?.firstOrNull()
          ?.text()
          ?.toIntOrNull()

        if (omittedPosts != null) {
          chanPostBuilder.replies(omittedPosts)
        }

        val omittedImages = omittedTextNode.getFirstElementByClassWithValue("omitted_images")
          ?.textNodes()
          ?.firstOrNull()
          ?.text()
          ?.toIntOrNull()

        if (omittedImages != null) {
          chanPostBuilder.threadImagesCount(omittedImages)
        }
      }

    if (chanPostBuilder.op) {
      chanReaderProcessor.setOp(chanPostBuilder)
    }

    chanReaderProcessor.addPost(chanPostBuilder)
  }

  private fun convertToChanPostImages(threadImageBoxElement: Element): List<ChanPostImage> {
    val fullImageLink = threadImageBoxElement.getFirstElementByClassWithValue("thread_image_link")
      ?.attr("href")
      ?.toHttpUrlOrNull()
      ?: return emptyList()

    val postImageElement = threadImageBoxElement.getFirstElementByClassWithAnyValue("post_image", "thread_image")
      ?: return emptyList()

    val thumbnailImageLink = postImageElement.attr("src")?.toHttpUrlOrNull()
    val md5Base64 = postImageElement.attr("data-md5")

    val postFileInfo = threadImageBoxElement.getFirstElementByClassWithValue("post_file")
      ?.textNodes()
      ?.firstOrNull()

    if (postFileInfo == null) {
      return emptyList()
    }

    val actualFileName = threadImageBoxElement.getFirstElementByClassWithValue("post_file_filename")
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
      .imageSize(actualFileSize)
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