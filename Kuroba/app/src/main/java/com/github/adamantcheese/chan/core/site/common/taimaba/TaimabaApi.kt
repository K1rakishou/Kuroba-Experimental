package com.github.adamantcheese.chan.core.site.common.taimaba

import android.util.JsonReader
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostHttpIcon
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.site.SiteEndpoints
import com.github.adamantcheese.chan.core.site.common.CommonSite
import com.github.adamantcheese.chan.core.site.common.CommonSite.CommonApi
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessor
import org.jsoup.parser.Parser
import java.io.IOException
import java.util.*

@Suppress("BlockingMethodInNonBlockingContext")
class TaimabaApi(commonSite: CommonSite) : CommonApi(commonSite) {

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
    var fileExt: String? = null
    var fileWidth = 0
    var fileHeight = 0
    var fileSize: Long = 0
    var fileSpoiler = false
    var fileName: String? = null

    /* prevent API parse error
       resto is not available on opening board overview the first time
       so, we manually set the opId to 0, builder.op to true and builder.opId to 0 */
    var opId: Int
    builder.op(true)
    builder.opId(0)
    var postcom: String? = null
    val files: MutableList<PostImage> = ArrayList()

    // Country flag
    var countryCode: String? = null
    var trollCountryCode: String? = null
    var countryName: String? = null

    reader.beginObject()

    while (reader.hasNext()) {
      val key = reader.nextName()
      when (key) {
        "no" -> builder.id(reader.nextInt().toLong())
        "resto" -> {
          opId = reader.nextInt()
          builder.op(opId == 0)
          builder.opId(opId.toLong())
        }
        "sticky" -> builder.sticky(reader.nextInt() == 1)
        "closed" -> builder.closed(reader.nextInt() == 1)
        "time" -> builder.setUnixTimestampSeconds(reader.nextLong())
        "name" -> builder.name(reader.nextString())
        "trip" -> builder.tripcode(reader.nextString())
        "id" -> builder.posterId(reader.nextString())
        "sub" -> builder.subject(reader.nextString())
        "com" -> builder.comment(readComment(reader))
        "filename" -> fileName = reader.nextString()
        "ext" -> fileExt = reader.nextString().replace(".", "")
        "fsize" -> fileSize = reader.nextLong()
        "w" -> fileWidth = reader.nextInt()
        "h" -> fileHeight = reader.nextInt()
        "country" -> countryCode = reader.nextString()
        "troll_country" -> trollCountryCode = reader.nextString()
        "country_name" -> countryName = reader.nextString()
        "spoiler" -> fileSpoiler = reader.nextInt() == 1
        "archived" -> builder.archived(reader.nextInt() == 1)
        "replies" -> builder.replies(reader.nextInt())
        "images" -> builder.threadImagesCount(reader.nextInt())
        "unique_ips" -> builder.uniqueIps(reader.nextInt())
        "last_modified" -> builder.lastModified(reader.nextLong())
        "capcode" -> builder.moderatorCapcode(reader.nextString())
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
        else -> {
          // Unknown/ignored key
          reader.skipValue()
        }
      }
    }
    reader.endObject()

    // The file from between the other values.
    if (fileName != null && fileExt != null) {
      val args = SiteEndpoints.makeArgument("tim", fileName, "ext", fileExt)
      val image = PostImage.Builder().thumbnailUrl(endpoints.thumbnailUrl(builder, false, args))
        .spoilerThumbnailUrl(endpoints.thumbnailUrl(builder, true, args))
        .imageUrl(endpoints.imageUrl(builder, args))
        .filename(Parser.unescapeEntities(fileName, false))
        .extension(fileExt)
        .imageWidth(fileWidth)
        .imageHeight(fileHeight)
        .spoiler(fileSpoiler)
        .size(fileSize)
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

    chanReaderProcessor.addPost(builder)
  }

  private fun readComment(reader: JsonReader): String {
    var postcom = reader.nextString()
    postcom = postcom.replace(">(.*+)".toRegex(), "<blockquote class=\"unkfunc\">&gt;$1</blockquote>")
    postcom = postcom.replace("<blockquote class=\"unkfunc\">&gt;>(\\d+)</blockquote>".toRegex(),
      "<a href=\"#$1\">&gt;&gt;$1</a>"
    )
    postcom = postcom.replace("\n".toRegex(), "<br/>")
    postcom = postcom.replace("(?i)\\[b](.*?)\\[/b]".toRegex(), "<b>$1</b>")
    postcom = postcom.replace("(?i)\\[\\*\\*](.*?)\\[/\\*\\*]".toRegex(), "<b>$1</b>")
    postcom = postcom.replace("(?i)\\[i](.*?)\\[/i]".toRegex(), "<i>$1</i>")
    postcom = postcom.replace("(?i)\\[\\*](.*?)\\[/\\*]".toRegex(), "<i>$1</i>")
    postcom = postcom.replace("(?i)\\[spoiler](.*?)\\[/spoiler]".toRegex(), "<span class=\"spoiler\">$1</span>")
    postcom = postcom.replace("(?i)\\[%](.*?)\\[/%]".toRegex(), "<span class=\"spoiler\">$1</span>")
    postcom = postcom.replace("(?i)\\[s](.*?)\\[/s]".toRegex(), "<strike>$1</strike>")
    postcom = postcom.replace("(?i)\\[pre](.*?)\\[/pre]".toRegex(), "<pre>$1</pre>")
    postcom = postcom.replace("(?i)\\[sub](.*?)\\[/sub]".toRegex(), "<pre>$1</pre>")

    return postcom
  }

  @Throws(IOException::class)
  private fun readPostImage(
    reader: JsonReader, builder: Post.Builder, endpoints: SiteEndpoints
  ): PostImage? {
    var fileSize: Long = 0
    var fileExt: String? = null
    var fileWidth = 0
    var fileHeight = 0
    var fileSpoiler = false
    var fileName: String? = null

    reader.beginObject()

    while (reader.hasNext()) {
      when (reader.nextName()) {
        "fsize" -> fileSize = reader.nextLong()
        "w" -> fileWidth = reader.nextInt()
        "h" -> fileHeight = reader.nextInt()
        "spoiler" -> fileSpoiler = reader.nextInt() == 1
        "ext" -> fileExt = reader.nextString().replace(".", "")
        "filename" -> fileName = reader.nextString()
        else -> reader.skipValue()
      }
    }

    reader.endObject()

    if (fileName != null && fileExt != null) {
      val args = SiteEndpoints.makeArgument("tim", fileName, "ext", fileExt)
      return PostImage.Builder().thumbnailUrl(endpoints.thumbnailUrl(builder, false, args))
        .spoilerThumbnailUrl(endpoints.thumbnailUrl(builder, true, args))
        .imageUrl(endpoints.imageUrl(builder, args))
        .filename(Parser.unescapeEntities(fileName, false))
        .extension(fileExt)
        .imageWidth(fileWidth)
        .imageHeight(fileHeight)
        .spoiler(fileSpoiler)
        .size(fileSize)
        .build()
    }

    return null
  }
}