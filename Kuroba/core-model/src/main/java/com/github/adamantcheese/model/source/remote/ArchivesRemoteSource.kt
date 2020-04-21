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
            supportsMediaThumbnails: Boolean,
            supportsMedia: Boolean
    ): ArchiveThread {
        logger.log(TAG, "fetchThreadFromNetwork($threadArchiveRequestLink, $threadNo, " +
                "$supportsMediaThumbnails, $supportsMedia)")
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
                    .use { jsonReader ->
                        val parsedArchivePosts = parsePosts(
                                jsonReader,
                                threadNo,
                                supportsMediaThumbnails,
                                supportsMedia
                        )

                        return@use ArchiveThread(parsedArchivePosts)
                    }
        }
    }

    private fun parsePosts(
            jsonReader: JsonReader,
            threadNo: Long,
            supportsMediaThumbnails: Boolean,
            supportsMedia: Boolean
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
                            val originalPost = readOriginalPost(
                                    supportsMediaThumbnails,
                                    supportsMedia
                            )

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
                                archivedPosts.addAll(
                                        readRegularPosts(supportsMediaThumbnails, supportsMedia)
                                )
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

    private fun JsonReader.readRegularPosts(
            supportsMediaThumbnails: Boolean,
            supportsFiles: Boolean
    ): List<ArchivePost> {
        if (!hasNext()) {
            return emptyList()
        }

        return jsonObject {
            val archivedPosts = mutableListOf<ArchivePost>()

            while (hasNext()) {
                nextName()

                val post = jsonObject { readPost(supportsMediaThumbnails, supportsFiles) }
                if (!post.isValid()) {
                    continue
                }

                archivedPosts += post
            }

            return@jsonObject archivedPosts
        }
    }

    private fun JsonReader.readOriginalPost(
            supportsMediaThumbnails: Boolean,
            supportsMedia: Boolean
    ): ArchivePost? {
        return jsonObject {
            val archivePost = readPost(supportsMediaThumbnails, supportsMedia)
            if (!archivePost.isValid()) {
                logger.logError(TAG, "Invalid archive post: ${archivePost}")
                return@jsonObject null
            }

            return@jsonObject archivePost
        }
    }

    private fun JsonReader.readPost(
            supportsMediaThumbnails: Boolean,
            supportsMedia: Boolean
    ): ArchivePost {
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
                    if ((supportsMediaThumbnails || supportsMedia) && hasNext()) {
                        if (peek() == JsonToken.NULL) {
                            skipValue()
                        } else {
                            jsonObject {
                                val archivePostMedia = readPostMedia(supportsMedia)

                                if (!archivePostMedia.isValid()) {
                                    logger.logError(TAG, "Invalid archive post media: ${archivePostMedia}")
                                    return@jsonObject
                                }

                                archivePost.archivePostMediaList += archivePostMedia
                            }
                        }
                    }
                }
                else -> skipValue()
            }
        }

        return archivePost
    }

    private fun JsonReader.readPostMedia(supportsMedia: Boolean): ArchivePostMedia {
        val archivePostMedia = ArchivePostMedia()

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
                "remote_media_link" -> {
                    if (supportsMedia) {
                        archivePostMedia.imageUrl = nextStringOrNull()
                    } else {
                        skipValue()
                    }
                }
                "thumb_link" -> archivePostMedia.thumbnailUrl = nextStringOrNull()
                else -> skipValue()
            }
        }

        return archivePostMedia
    }

    private fun removeExtensionIfPresent(filename: String): String {
        val index = filename.lastIndexOf('.')
        if (index < 0) {
            return filename
        }

        return filename.substring(0, index)
    }

    private fun extractFileNameExtension(filename: String): String? {
        val index = filename.lastIndexOf('.')
        return if (index == -1) {
            null
        } else {
            filename.substring(index + 1)
        }
    }

    private fun JsonReader.nextStringOrNull(): String? {
        if (peek() == JsonToken.NULL) {
            skipValue()
            return null
        }

        val value = nextString()
        if (value.isNullOrEmpty()) {
            return null
        }

        return value
    }

    private fun <T : Any?> JsonReader.jsonObject(func: JsonReader.() -> T): T {
        beginObject()

        try {
            return func(this)
        } finally {
            endObject()
        }
    }

    class ArchiveThread(
            val posts: List<ArchivePost>
    )

    class ArchivePost(
            var postNo: Long = -1L,
            var threadNo: Long = -1L,
            var isOP: Boolean = false,
            var unixTimestampSeconds: Long = -1L,
            var name: String = "",
            var subject: String = "",
            var comment: String = "",
            var sticky: Boolean = false,
            var closed: Boolean = false,
            var archived: Boolean = false,
            var tripcode: String = "",
            val archivePostMediaList: MutableList<ArchivePostMedia> = mutableListOf()
    ) {

        var moderatorCapcode: String = ""
            set(value) {
                if (shouldFilterCapcode(value)) {
                    field = ""
                } else {
                    field = value
                }
            }

        private fun shouldFilterCapcode(value: String): Boolean {
            return when (value) {
                // Archived.moe returns capcode field with "N" symbols for every single post. I have
                // no idea what this means but I suppose it's the same as no capcode.
                "N" -> true
                else -> false
            }
        }

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
            var fileHashBase64: String? = null
    ) {

        fun isValid(): Boolean {
            return serverFilename != null
                    && extension != null
                    && thumbnailUrl != null
                    && imageWidth > 0
                    && imageHeight > 0
                    && size > 0
        }

        override fun toString(): String {
            return "ArchivePostMedia(serverFilename=$serverFilename, thumbnailUrl=$thumbnailUrl, " +
                    "imageUrl=$imageUrl, filename=$filename, extension=$extension, " +
                    "imageWidth=$imageWidth, imageHeight=$imageHeight, spoiler=$spoiler," +
                    " deleted=$deleted, size=$size, fileHashBase64=$fileHashBase64)"
        }


    }
}