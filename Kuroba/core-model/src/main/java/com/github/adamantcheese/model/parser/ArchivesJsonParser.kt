package com.github.adamantcheese.model.parser

import android.util.JsonReader
import android.util.JsonToken
import com.github.adamantcheese.common.mutableListWithCap
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.archive.ArchivePost
import com.github.adamantcheese.model.data.archive.ArchivePostMedia
import com.github.adamantcheese.model.source.remote.ArchivesRemoteSource
import com.github.adamantcheese.model.util.extractFileNameExtension
import com.github.adamantcheese.model.util.jsonObject
import com.github.adamantcheese.model.util.nextStringOrNull
import com.github.adamantcheese.model.util.removeExtensionIfPresent

class ArchivesJsonParser(
  loggerTag: String,
  private val logger: Logger
) {
  private val TAG = "$loggerTag ArchivesJsonParser"

  fun parsePosts(jsonReader: JsonReader, threadNo: Long): List<ArchivePost> {
    val archivedPosts = mutableListWithCap<ArchivePost>(64)

    jsonReader.jsonObject {
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
      if (parsedThreadNo == null || parsedThreadNo != threadNo) {
        logger.logError(TAG, "Bad parsedThreadNo: ${parsedThreadNo}, expected ${threadNo}")
        return@jsonObject
      }

      jsonObject {
        while (hasNext()) {
          when (nextName()) {
            "op" -> {
              val originalPost = readOriginalPost()

              if (originalPost != null) {
                archivedPosts += originalPost
              }
            }
            "posts" -> {
              if (archivedPosts.isEmpty() || !archivedPosts.first().isOP) {
                // Original Post must be the first post of the list of posts
                // we got from an archive. If it's not present then something is
                // wrong and we should abort everything.
                skipValue()
              } else {
                archivedPosts.addAll(readRegularPosts())
              }
            }
            else -> skipValue()
          }
        }
      }
    }

    if (archivedPosts.size > 0 && !archivedPosts.first().isOP) {
      logger.logError(TAG, "Parsed posts has no OP!")
      return emptyList()
    }

    return archivedPosts
  }

  private fun JsonReader.readRegularPosts(): List<ArchivePost> {
    if (!hasNext()) {
      return emptyList()
    }

    return jsonObject {
      val archivedPosts = mutableListWithCap<ArchivePost>(64)

      while (hasNext()) {
        // skip the json key
        nextName()

        val post = jsonObject { readPost() }
        if (!post.isValid()) {
          continue
        }

        archivedPosts += post
      }

      return@jsonObject archivedPosts
    }
  }

  private fun JsonReader.readOriginalPost(): ArchivePost? {
    return jsonObject {
      val archivePost = readPost()
      if (!archivePost.isValid()) {
        logger.logError(TAG, "Invalid archive post: ${archivePost}")
        return@jsonObject null
      }

      return@jsonObject archivePost
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
                  logger.logError(TAG, "Invalid archive post media: ${archivePostMedia}")
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

    if (imageUrl.startsWith("https://") || imageUrl.startsWith("http://")) {
      return imageUrl
    }

    if (imageUrl.startsWith("//")) {
      return "https:$imageUrl"
    }

    logger.logError(TAG, "Unknown kind of broken image url: \"$imageUrl\". " +
      "If you see this report it to devs!")
    return null
  }

}