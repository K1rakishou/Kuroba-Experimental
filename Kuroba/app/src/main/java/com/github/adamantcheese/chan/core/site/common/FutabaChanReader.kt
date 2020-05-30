package com.github.adamantcheese.chan.core.site.common

import android.util.JsonReader
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostHttpIcon
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.site.SiteEndpoints
import com.github.adamantcheese.chan.core.site.parser.ChanReader
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessor
import com.github.adamantcheese.chan.core.site.parser.CommentParser
import com.github.adamantcheese.chan.core.site.parser.PostParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.parser.Parser
import java.io.IOException
import java.util.*

@Suppress("BlockingMethodInNonBlockingContext")
class FutabaChanReader(
  private val archivesManager: ArchivesManager
) : ChanReader {
    private val mutex = Mutex()
    private var parser: PostParser? = null

    override suspend fun getParser(): PostParser {
        return mutex.withLock {
            if (parser == null) {
                val commentParser = CommentParser().addDefaultRules()
                val foolFuukaCommentParser = FoolFuukaCommentParser()
                val defaultPostParser = DefaultPostParser(commentParser)

                for (archiveDescriptor in archivesManager.getAllArchivesDescriptors()) {
                    defaultPostParser.addArchiveCommentParser(archiveDescriptor, foolFuukaCommentParser)
                }

                parser = defaultPostParser
            }

            return@withLock parser!!
        }
    }

    @Throws(Exception::class)
    override suspend fun loadThread(reader: JsonReader, chanReaderProcessor: ChanReaderProcessor) {
        reader.beginObject()

        // Page object
        while (reader.hasNext()) {
            val key = reader.nextName()
            if (key == "posts") {
                reader.beginArray()

                // Thread array
                while (reader.hasNext()) {
                    // Thread object
                    readPostObject(reader, chanReaderProcessor)
                }

                reader.endArray()
            } else {
                reader.skipValue()
            }
        }

        reader.endObject()
    }

    @Throws(Exception::class)
    override suspend fun loadCatalog(reader: JsonReader, chanReaderProcessor: ChanReaderProcessor) {
        reader.beginArray() // Array of pages

        while (reader.hasNext()) {
            reader.beginObject() // Page object

            while (reader.hasNext()) {
                if (reader.nextName() == "threads") {
                    reader.beginArray() // Threads array

                    while (reader.hasNext()) {
                        readPostObject(reader, chanReaderProcessor)
                    }

                    reader.endArray()
                } else {
                    reader.skipValue()
                }
            }

            reader.endObject()
        }

        reader.endArray()
    }

    @Throws(Exception::class)
    override suspend fun readPostObject(reader: JsonReader, chanReaderProcessor: ChanReaderProcessor) {
        val builder = Post.Builder()
        builder.board(chanReaderProcessor.loadable.board)
        val endpoints = chanReaderProcessor.loadable.getSite().endpoints()

        // File
        var fileId: String? = null
        var fileExt: String? = null
        var fileWidth = 0
        var fileHeight = 0
        var fileSize: Long = 0
        var fileSpoiler = false
        var fileName: String? = null
        var fileHash: String? = null
        var fileDeleted = false
        val files: MutableList<PostImage> = ArrayList()

        // Country flag
        var countryCode: String? = null
        var trollCountryCode: String? = null
        var countryName: String? = null

        // 4chan pass leaf
        var since4pass = 0

        // A sticky thread may as well be a rolling sticky thread. The difference is that in rolling
        // sticky thread old posts will be deleted right away once they exceed the "sticky_cap" value.
        var stickyCap = -1

        reader.beginObject()

        while (reader.hasNext()) {
            val key = reader.nextName()
            when (key) {
                "no" -> builder.id(reader.nextInt().toLong())
                "sub" -> builder.subject(reader.nextString())
                "name" -> builder.name(reader.nextString())
                "com" -> builder.comment(reader.nextString())
                "tim" -> fileId = reader.nextString()
                "time" -> builder.setUnixTimestampSeconds(reader.nextLong())
                "ext" -> fileExt = reader.nextString().replace(".", "")
                "w" -> fileWidth = reader.nextInt()
                "h" -> fileHeight = reader.nextInt()
                "fsize" -> fileSize = reader.nextLong()
                "filename" -> fileName = reader.nextString()
                "trip" -> builder.tripcode(reader.nextString())
                "country" -> countryCode = reader.nextString()
                "troll_country" -> trollCountryCode = reader.nextString()
                "country_name" -> countryName = reader.nextString()
                "spoiler" -> fileSpoiler = reader.nextInt() == 1
                "resto" -> {
                    val opId = reader.nextInt()
                    builder.op(opId == 0)
                    builder.opId(opId.toLong())
                }
                "filedeleted" -> fileDeleted = reader.nextInt() == 1
                "sticky" -> builder.sticky(reader.nextInt() == 1)
                "closed" -> builder.closed(reader.nextInt() == 1)
                "archived" -> builder.archived(reader.nextInt() == 1)
                "replies" -> builder.replies(reader.nextInt())
                "images" -> builder.threadImagesCount(reader.nextInt())
                "unique_ips" -> builder.uniqueIps(reader.nextInt())
                "last_modified" -> builder.lastModified(reader.nextLong())
                "id" -> builder.posterId(reader.nextString())
                "capcode" -> builder.moderatorCapcode(reader.nextString())
                "since4pass" -> since4pass = reader.nextInt()
                "extra_files" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        val postImage = readPostImage(reader, builder, endpoints)
                        if (postImage != null) {
                            files.add(postImage)
                        }
                    }
                    reader.endArray()
                }
                "md5" -> fileHash = reader.nextString()
                "sticky_cap" -> stickyCap = reader.nextInt()
                else -> {
                    // Unknown/ignored key
                    reader.skipValue()
                }
            }
        }

        reader.endObject()

        // The file from between the other values.
        if (fileId != null && fileName != null && fileExt != null && !fileDeleted) {
            val args = SiteEndpoints.makeArgument("tim", fileId, "ext", fileExt)
            val image = PostImage.Builder()
                    .serverFilename(fileId)
                    .thumbnailUrl(endpoints.thumbnailUrl(builder, false, args))
                    .spoilerThumbnailUrl(endpoints.thumbnailUrl(builder, true, args))
                    .imageUrl(endpoints.imageUrl(builder, args))
                    .filename(Parser.unescapeEntities(fileName, false))
                    .extension(fileExt)
                    .imageWidth(fileWidth)
                    .imageHeight(fileHeight)
                    .spoiler(fileSpoiler)
                    .size(fileSize)
                    .fileHash(fileHash, true)
                    .build()
            // Insert it at the beginning.
            files.add(0, image)
        }

        builder.postImages(files)

        if (builder.op) {
            // Update OP fields later on the main thread
            val op = Post.Builder()
            op.closed(builder.closed)
            op.archived(builder.archived)
            op.sticky(builder.sticky)
            op.replies(builder.totalRepliesCount)
            op.threadImagesCount(builder.threadImagesCount)
            op.uniqueIps(builder.uniqueIps)
            op.lastModified(builder.lastModified)
            if (builder.sticky) {
                op.stickyCap(stickyCap)
            }
            chanReaderProcessor.op = op
        }

        if (countryCode != null && countryName != null) {
            val countryUrl = endpoints.icon("country", SiteEndpoints.makeArgument("country_code", countryCode))
            builder.addHttpIcon(PostHttpIcon(countryUrl, "$countryName/$countryCode"))
        }

        if (trollCountryCode != null && countryName != null) {
            val countryUrl = endpoints.icon("troll_country", SiteEndpoints.makeArgument("troll_country_code", trollCountryCode))
            builder.addHttpIcon(PostHttpIcon(countryUrl, "$countryName/t_$trollCountryCode"))
        }

        if (since4pass != 0) {
            val iconUrl = endpoints.icon("since4pass", null)
            builder.addHttpIcon(PostHttpIcon(iconUrl, since4pass.toString()))
        }

        chanReaderProcessor.addPost(builder)
    }

    @Throws(IOException::class)
    private fun readPostImage(reader: JsonReader, builder: Post.Builder, endpoints: SiteEndpoints): PostImage? {
        reader.beginObject()

        var fileId: String? = null
        var fileSize: Long = 0
        var fileExt: String? = null
        var fileWidth = 0
        var fileHeight = 0
        var fileSpoiler = false
        var fileName: String? = null
        var fileHash: String? = null

        while (reader.hasNext()) {
            when (reader.nextName()) {
                "tim" -> fileId = reader.nextString()
                "fsize" -> fileSize = reader.nextLong()
                "w" -> fileWidth = reader.nextInt()
                "h" -> fileHeight = reader.nextInt()
                "spoiler" -> fileSpoiler = reader.nextInt() == 1
                "ext" -> fileExt = reader.nextString().replace(".", "")
                "filename" -> fileName = reader.nextString()
                "md5" -> fileHash = reader.nextString()
                else -> reader.skipValue()
            }
        }

        reader.endObject()

        if (fileId != null && fileName != null && fileExt != null) {
            val args = SiteEndpoints.makeArgument("tim", fileId, "ext", fileExt)
            return PostImage.Builder().serverFilename(fileId)
                    .thumbnailUrl(endpoints.thumbnailUrl(builder, false, args))
                    .spoilerThumbnailUrl(endpoints.thumbnailUrl(builder, true, args))
                    .imageUrl(endpoints.imageUrl(builder, args))
                    .filename(Parser.unescapeEntities(fileName, false))
                    .extension(fileExt)
                    .imageWidth(fileWidth)
                    .imageHeight(fileHeight)
                    .spoiler(fileSpoiler)
                    .size(fileSize)
                    .fileHash(fileHash, true)
                    .build()
        }

        return null
    }

}