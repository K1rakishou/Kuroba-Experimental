package com.github.adamantcheese.model.source.remote

import android.util.JsonReader
import android.util.JsonToken
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.util.ensureBackgroundThread
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class ArchivesRemoteSource(
        okHttpClient: OkHttpClient,
        loggerTag: String,
        logger: Logger
) : AbstractRemoteSource(okHttpClient, logger) {
    private val TAG = "$loggerTag ArchivesRemoteSource"

    open suspend fun fetchThreadFromNetwork(
            threadArchiveRequestLink: String,
            threadNo: Long,
            supportsFiles: Boolean
    ): List<ArchivePost> {
        logger.log(TAG, "fetchThreadFromNetwork($threadArchiveRequestLink)")
        ensureBackgroundThread()

        val httpRequest = Request.Builder()
                .url(threadArchiveRequestLink)
                .get()
                .build()

        val response = okHttpClient.suspendCall(httpRequest)
        if (!response.isSuccessful) {
            throw IOException("Bad response status: ${response.code}")
        }

        val body = response.body
                ?: throw IOException("Response has no body")

        return body.byteStream().use { inputStream ->
            return@use JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .use { jsonReader -> parsePosts(jsonReader, threadNo, supportsFiles) }
        }
    }

    private fun parsePosts(
            jsonReader: JsonReader,
            threadNo: Long,
            supportsFiles: Boolean
    ): List<ArchivePost> {
        val archivedPosts = mutableListOf<ArchivePost>()

        jsonReader.jsonObject {
            if (!hasNext()) {
                return@jsonObject
            }

            val parsedThreadNo = nextName().toLongOrNull()
            if (parsedThreadNo == null || parsedThreadNo != threadNo) {
                logger.logError(TAG, "Bad parsedThreadNo: ${parsedThreadNo}, expected ${threadNo}")
                return@jsonObject
            }

            jsonObject {
                while (hasNext()) {
                    when (nextName()) {
                        "op" -> {
                            val originalPost = readOriginalPost(supportsFiles)
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
                                archivedPosts.addAll(readRegularPosts(supportsFiles))
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

    private fun JsonReader.readRegularPosts(supportsFiles: Boolean): List<ArchivePost> {
        if (!hasNext()) {
            return emptyList()
        }

        return jsonObject {
            val archivedPosts = mutableListOf<ArchivePost>()

            while (hasNext()) {
                nextName()

                val post = jsonObject { readPost(supportsFiles) }
                if (!post.isValid()) {
                    continue
                }

                archivedPosts += post
            }

            return@jsonObject archivedPosts
        }
    }

    private fun JsonReader.readOriginalPost(supportsFiles: Boolean): ArchivePost? {
        return jsonObject {
            val archivePost = readPost(supportsFiles)
            if (!archivePost.isValid()) {
                logger.logError(TAG, "Invalid archive post: ${archivePost}")
                return@jsonObject null
            }

            return@jsonObject archivePost
        }
    }

    private fun JsonReader.readPost(supportsFiles: Boolean): ArchivePost {
        val archivePost = ArchivePost()

        while (hasNext()) {
            when (nextName()) {
                "num" -> archivePost.postNo = nextInt().toLong()
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
                    if (supportsFiles) {
                        while (hasNext()) {
                            jsonObject {
                                val archivePostMedia = readPostMedia()

                                if (!archivePostMedia.isValid()) {
                                    logger.logError(TAG,
                                            "Invalid archive post media: ${archivePostMedia}")
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

        while (hasNext()) {
            when (nextName()) {
                "spoiler" -> archivePostMedia.spoiler = nextInt() == 1
                "media" -> {
                    archivePostMedia.serverFilename = nextStringOrNull() ?: ""
                    archivePostMedia.extension =
                            extractFileNameExtension(archivePostMedia.serverFilename!!)
                }
                "media_filename_processed" -> archivePostMedia.filename = nextStringOrNull() ?: ""
                "media_w" -> archivePostMedia.imageWidth = nextInt()
                "media_h" -> archivePostMedia.imageHeight = nextInt()
                "media_size" -> archivePostMedia.size = nextInt().toLong()
                "media_hash" -> archivePostMedia.fileHashBase64 = nextStringOrNull() ?: ""
                "banned" -> archivePostMedia.deleted = nextInt() == 1
                "remote_media_link" -> archivePostMedia.imageUrl = nextStringOrNull() ?: ""
                "thumb_link" -> archivePostMedia.thumbnailUrl = nextStringOrNull() ?: ""
                else -> skipValue()
            }
        }

        return archivePostMedia
    }

    private fun extractFileNameExtension(filename: String): String? {
        val index = filename.lastIndexOf('.')
        return if (index == -1) {
            null
        } else filename.substring(index + 1)
    }

    private fun JsonReader.nextStringOrNull(): String? {
        if (peek() == JsonToken.NULL) {
            skipValue()
            return null
        }

        return nextString()
    }

    private fun <T : Any?> JsonReader.jsonObject(func: JsonReader.() -> T): T {
        beginObject()

        try {
            return func(this)
        } finally {
            endObject()
        }
    }

    class ArchivePost(
            var postNo: Long = -1L,
            var threadNo: Long = -1L,
            var isOP: Boolean = false,
            var unixTimestampSeconds: Long = -1L,
            var moderatorCapcode: String = "",
            var name: String = "",
            var subject: String = "",
            var comment: String = "",
            var sticky: Boolean = false,
            var closed: Boolean = false,
            var archived: Boolean = false,
            var tripcode: String = "",
            val archivePostMediaList: MutableList<ArchivePostMedia> = mutableListOf()
    ) {

        override fun toString(): String {
            return "ArchivePost(postNo=$postNo, threadNo=$threadNo, isOP=$isOP, " +
                    "unixTimestampSeconds=$unixTimestampSeconds, moderatorCapcode='$moderatorCapcode'," +
                    " name='$name', subject='$subject', comment='$comment', sticky=$sticky, " +
                    "closed=$closed, archived=$archived, tripcode='$tripcode', " +
                    "archivePostMediaListCount=${archivePostMediaList.size})"
        }

        fun isValid(): Boolean {
            return postNo > 0
                    && threadNo > 0
                    && unixTimestampSeconds > 0
        }
    }

    class ArchivePostMedia(
            var serverFilename: String? = null,
            var thumbnailUrl: String? = null,
            var imageUrl: String? = null,
            var filename: String? = null,
            var extension: String? = null,
            var imageWidth: Int = 0,
            var imageHeight: Int = 0,
            var spoiler: Boolean = false,
            var deleted: Boolean = false,
            var size: Long = 0L,
            var fileHashBase64: String? = null,
            // Need to be set in another place
            var spoilerThumbnailUrl: String? = null
    ) {

        fun isValid(): Boolean {
            return serverFilename != null
                    && thumbnailUrl != null
                    && imageUrl != null
                    && imageWidth > 0
                    && imageHeight > 0
                    && size > 0
        }

        override fun toString(): String {
            return "ArchivePostMedia(serverFilename=$serverFilename, thumbnailUrl=$thumbnailUrl, " +
                    "imageUrl=$imageUrl, filename=$filename, extension=$extension, " +
                    "imageWidth=$imageWidth, imageHeight=$imageHeight, spoiler=$spoiler," +
                    " deleted=$deleted, size=$size, fileHashBase64=$fileHashBase64, " +
                    "spoilerThumbnailUrl=$spoilerThumbnailUrl)"
        }


    }
}