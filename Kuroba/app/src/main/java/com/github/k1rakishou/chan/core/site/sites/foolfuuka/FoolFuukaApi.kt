package com.github.k1rakishou.chan.core.site.sites.foolfuuka

import com.github.k1rakishou.chan.core.mapper.ArchiveThreadMapper
import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.parser.ChanReaderProcessor
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.jsonObject
import com.github.k1rakishou.common.nextStringOrNull
import com.github.k1rakishou.model.data.archive.ArchivePost
import com.github.k1rakishou.model.data.archive.ArchivePostMedia
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.source.remote.ArchivesRemoteSource
import com.github.k1rakishou.model.util.extractFileNameExtension
import com.github.k1rakishou.model.util.removeExtensionIfPresent
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken

class FoolFuukaApi(
  site: CommonSite
) : CommonSite.CommonApi(site) {

  override suspend fun loadThread(reader: JsonReader, chanReaderProcessor: ChanReaderProcessor) {
    val chanDescriptor = chanReaderProcessor.chanDescriptor

    val threadDescriptor = chanDescriptor.threadDescriptorOrNull()
      ?: throw CommonClientException("chanDescriptor is not thread descriptor: ${chanDescriptor}")

    reader.jsonObject {
      if (!hasNext()) {
        return@jsonObject
      }

      val jsonKey = nextName()
      if (jsonKey == "error") {
        val errorMessage = nextStringOrNull()
          ?: "No error message"
        throw ArchivesRemoteSource.ArchivesApiException(errorMessage)
      }

      val parsedThreadNo = jsonKey.toLongOrNull()
      if (parsedThreadNo == null || parsedThreadNo != threadDescriptor.threadNo) {
        Logger.e(TAG, "Bad parsedThreadNo: ${parsedThreadNo}, expected ${threadDescriptor.threadNo}")
        return@jsonObject
      }

      jsonObject {
        while (hasNext()) {
          when (nextName()) {
            "op" -> readOriginalPost(this, chanReaderProcessor)
            "posts" -> readRegularPosts(this, chanReaderProcessor)
            else -> skipValue()
          }
        }
      }
    }

    println()
  }

  private suspend fun readOriginalPost(
    reader: JsonReader,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    reader.jsonObject { readPostObject(reader, chanReaderProcessor, true) }
  }

  private suspend fun readRegularPosts(
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

        reader.jsonObject { readPostObject(reader, chanReaderProcessor, false) }
      }
    }
  }

  private suspend fun readPostObject(
    reader: JsonReader,
    chanReaderProcessor: ChanReaderProcessor,
    expectedOp: Boolean
  ) {
    val chanDescriptor = chanReaderProcessor.chanDescriptor
    val boardDescriptor = chanDescriptor.boardDescriptor()

    val archivePost = reader.readPost()
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

  private fun JsonReader.readPost(): ArchivePost {
    val archivePost = ArchivePost()

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
        "media" -> {
          if (hasNext()) {
            if (peek() == JsonToken.NULL) {
              skipValue()
            } else {
              jsonObject {
                val archivePostMedia = readPostMedia()

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

  private fun JsonReader.readPostMedia(): ArchivePostMedia {
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

    archivePostMedia.imageUrl = fixImageUrlIfNecessary(archivePostMedia.imageUrl)

    return archivePostMedia
  }

  private fun fixImageUrlIfNecessary(imageUrl: String?): String? {
    if (imageUrl == null) {
      return imageUrl
    }

    // arch.b4k.co was caught red-handed sending broken links (without http/https schema but
    // with both forward slashes, e.g. "//arch.b4k.co/..."  instead of "https://arch.b4k.co/...".
    // We gotta fix this by ourselves for now.
    // https://arch.b4k.co/meta/thread/357/
    //
    // UPD: it was fixed, but let's still leave this hack in case it happens again
    if (imageUrl.startsWith("https://") || imageUrl.startsWith("http://")) {
      return imageUrl
    }

    if (imageUrl.startsWith("//")) {
      return "https:$imageUrl"
    }

    Logger.e(TAG, "Unknown kind of broken image url: \"$imageUrl\". If you see this report it to devs!")
    return null
  }

  override suspend fun loadCatalog(reader: JsonReader, chanReaderProcessor: ChanReaderProcessor) {
    throw CommonClientException("Catalog is not supported for site ${site.name()}")
  }

  override suspend fun readThreadBookmarkInfoObject(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    expectedCapacity: Int,
    reader: JsonReader
  ): ModularResult<ThreadBookmarkInfoObject> {
    val error = CommonClientException("Bookmarks are not supported for site ${site.name()}")

    return ModularResult.error(error)
  }

  companion object {
    private const val TAG = "FoolFuukaApi"
  }

}