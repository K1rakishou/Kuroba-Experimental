package com.github.k1rakishou.chan.core.site.sites.foolfuuka

import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.parser.processor.AbstractChanReaderProcessor
import com.github.k1rakishou.chan.core.site.parser.processor.ChanReaderProcessor
import com.github.k1rakishou.chan.utils.extractFileNameExtension
import com.github.k1rakishou.chan.utils.fixImageUrlIfNecessary
import com.github.k1rakishou.chan.utils.removeExtensionIfPresent
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.jsonObject
import com.github.k1rakishou.common.nextStringOrNull
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.archive.ArchivePost
import com.github.k1rakishou.model.data.archive.ArchivePostMedia
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogInfoObject
import com.github.k1rakishou.model.mapper.ArchiveThreadMapper
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStream

class FoolFuukaApi(
  site: CommonSite
) : CommonSite.CommonApi(site) {
  private val foolFuukaReadCatalogThreadsHelper by lazy { FoolFuukaReadCatalogThreadsHelper() }

  override suspend fun loadThreadFresh(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    readBodyJson(responseBodyStream) { jsonReader ->
      val chanDescriptor = chanReaderProcessor.chanDescriptor

      val threadDescriptor = chanDescriptor.threadDescriptorOrNull()
        ?: throw CommonClientException("chanDescriptor is not thread descriptor: ${chanDescriptor}")

      jsonReader.jsonObject {
        if (!hasNext()) {
          return@jsonObject
        }

        val jsonKey = nextName()
        if (jsonKey == "error") {
          val errorMessage = nextStringOrNull()
            ?: "No error message"
          throw ArchivesApiException(errorMessage)
        }

        val parsedThreadNo = jsonKey.toLongOrNull()
        if (parsedThreadNo == null || parsedThreadNo != threadDescriptor.threadNo) {
          Logger.e(TAG, "Bad parsedThreadNo: ${parsedThreadNo}, expected ${threadDescriptor.threadNo}")
          return@jsonObject
        }

        jsonObject {
          while (hasNext()) {
            when (nextName()) {
              "op" -> readOriginalPost(requestUrl, this, chanReaderProcessor)
              "posts" -> readRegularPosts(requestUrl, this, chanReaderProcessor)
              else -> skipValue()
            }
          }
        }
      }

      chanReaderProcessor.applyChanReadOptions()
    }
  }

  private suspend fun readOriginalPost(
    requestUrl: String,
    reader: JsonReader,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    reader.jsonObject { readPostObject(requestUrl, reader, chanReaderProcessor, true) }
  }

  private suspend fun readRegularPosts(
    requestUrl: String,
    reader: JsonReader,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    if (!reader.hasNext()) {
      return
    }

    reader.jsonObject {
      while (hasNext()) {
        // skip the json key
        nextName()

        reader.jsonObject { readPostObject(requestUrl, reader, chanReaderProcessor, false) }
      }
    }
  }

  private suspend fun readPostObject(
    requestUrl: String,
    reader: JsonReader,
    chanReaderProcessor: ChanReaderProcessor,
    expectedOp: Boolean
  ) {
    val chanDescriptor = chanReaderProcessor.chanDescriptor
    val boardDescriptor = chanDescriptor.boardDescriptor()

    val archivePost = reader.readPost(requestUrl, boardDescriptor)
    if (expectedOp != archivePost.isOP) {
      Logger.e(TAG, "Invalid archive post OP flag (expected: ${expectedOp}, actual: ${archivePost.isOP})")
      return
    }

    if (!archivePost.isValid()) {
      Logger.e(TAG, "Invalid archive post: ${archivePost}")
      return
    }

    val postBuilder = ArchiveThreadMapper.fromPost(
      boardDescriptor,
      archivePost
    )

    chanReaderProcessor.addPost(postBuilder)

    if (postBuilder.op) {
      chanReaderProcessor.setOp(postBuilder)
    }
  }

  private fun JsonReader.readPost(
    requestUrl: String,
    boardDescriptor: BoardDescriptor
  ): ArchivePost {
    val archivePost = ArchivePost(boardDescriptor)

    while (hasNext()) {
      when (nextName()) {
        "num" -> archivePost.postNo = nextInt().toLong()
        "subnum" -> archivePost.postSubNo = nextInt().toLong()
        "thread_num" -> archivePost.threadNo = nextInt().toLong()
        "op" -> archivePost.isOP = nextInt() == 1
        "timestamp" -> archivePost.unixTimestampSeconds = nextInt().toLong()
        "capcode" -> archivePost.moderatorCapcode = nextStringOrNull() ?: ""
        "name_processed" -> archivePost.name = nextStringOrNull() ?: ""
        "title_processed" -> archivePost.subject = nextStringOrNull() ?: ""
        "comment_processed" -> archivePost.comment = nextStringOrNull() ?: ""
        "sticky" -> archivePost.sticky = nextInt() == 1
        "locked" -> archivePost.closed = nextInt() == 1
        "deleted" -> archivePost.archived = nextInt() == 1
        "trip_processed" -> archivePost.tripcode = nextStringOrNull() ?: ""
        "poster_hash" -> archivePost.posterId = nextStringOrNull() ?: ""
        "media" -> {
          if (hasNext()) {
            if (peek() == JsonToken.NULL) {
              skipValue()
            } else {
              jsonObject {
                val archivePostMedia = readPostMedia(requestUrl)

                if (!archivePostMedia.isValid()) {
                  Logger.e(TAG, "Invalid archive post media: ${archivePostMedia}")
                  return@jsonObject
                }

                archivePost.archivePostMediaList += archivePostMedia
              }
            }
          } else {
            skipValue()
          }
        }
        else -> skipValue()
      }
    }

    return archivePost
  }

  private fun JsonReader.readPostMedia(
    requestUrl: String
  ): ArchivePostMedia {
    val archivePostMedia = ArchivePostMedia()

    var mediaLink: String? = null
    var remoteMediaLink: String? = null

    while (hasNext()) {
      when (nextName()) {
        "spoiler" -> archivePostMedia.spoiler = nextInt() == 1
        "media_orig" -> {
          val serverFileName = nextStringOrNull()

          if (!serverFileName.isNullOrEmpty()) {
            archivePostMedia.serverFilename = removeExtensionIfPresent(serverFileName)
            archivePostMedia.extension = extractFileNameExtension(serverFileName)
          }
        }
        "media_filename_processed" -> {
          val filename = nextStringOrNull()
          if (filename == null) {
            archivePostMedia.filename = ""
          } else {
            archivePostMedia.filename = removeExtensionIfPresent(filename)
          }
        }
        "media_w" -> archivePostMedia.imageWidth = nextInt()
        "media_h" -> archivePostMedia.imageHeight = nextInt()
        "media_size" -> archivePostMedia.size = nextInt().toLong()
        "media_hash" -> archivePostMedia.fileHashBase64 = nextStringOrNull() ?: ""
        "banned" -> archivePostMedia.deleted = nextInt() == 1
        "media_link" -> mediaLink = nextStringOrNull()
        "remote_media_link" -> remoteMediaLink = nextStringOrNull()
        "thumb_link" -> archivePostMedia.thumbnailUrl = nextStringOrNull()
        else -> skipValue()
      }
    }

    if (mediaLink != null) {
      archivePostMedia.imageUrl = mediaLink
    } else {
      // archived.moe doesn't store original media on it's server but it sends links to original
      // media that is store on other archives' servers.
      archivePostMedia.imageUrl = remoteMediaLink
    }

    archivePostMedia.imageUrl = fixImageUrlIfNecessary(requestUrl, archivePostMedia.imageUrl)
    archivePostMedia.thumbnailUrl = fixImageUrlIfNecessary(requestUrl, archivePostMedia.thumbnailUrl)

    return archivePostMedia
  }

  override suspend fun loadCatalog(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: AbstractChanReaderProcessor
  ) {
    foolFuukaReadCatalogThreadsHelper.readCatalogThreads(
      requestUrl = requestUrl,
      responseBodyStream = responseBodyStream,
      chanReaderProcessor = chanReaderProcessor
    )
  }

  override suspend fun readThreadBookmarkInfoObject(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    expectedCapacity: Int,
    requestUrl: String,
    responseBodyStream: InputStream,
  ): ModularResult<ThreadBookmarkInfoObject> {
    val error = CommonClientException("Bookmarks are not supported for site ${site.name()}")

    return ModularResult.error(error)
  }

  override suspend fun readFilterWatchCatalogInfoObject(
    boardDescriptor: BoardDescriptor,
    requestUrl: String,
    responseBodyStream: InputStream,
  ): ModularResult<FilterWatchCatalogInfoObject> {
    val error = CommonClientException("Filter watching is not supported for site ${site.name()}")

    return ModularResult.error(error)
  }

  class ArchivesApiException(message: String) : Exception(message)

  companion object {
    private const val TAG = "FoolFuukaApi"
  }

}