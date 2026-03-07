package com.github.k1rakishou.chan.core.site.sites.vichan.leftypol

import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.vichan.VichanApi
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageBuilder
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.jsoup.parser.Parser

class LeftypolApi(
  site: CommonSite
) : VichanApi(site) {

  override fun otherPostKey(
    name: String,
    reader: JsonReader,
    builder: ChanPostBuilder,
    board: ChanBoard?,
    endpoints: SiteEndpoints
  ) {
    when (name) {
      "files" -> {
        reader.beginArray()
        while (reader.hasNext()) {
          val postImage = readPostImage(reader, builder, board, endpoints)
          if (postImage != null) {
            builder.postImages.add(postImage)
          }
        }
        reader.endArray()
      }

      "locked" -> builder.closed(reader.nextInt() != 0)
      "cyclical" -> builder.endless(reader.nextInt() != 0)
      else -> super.otherPostKey(name, reader, builder, board, endpoints)
    }
  }

  override fun readPostImage(
    reader: JsonReader,
    builder: ChanPostBuilder,
    board: ChanBoard?,
    endpoints: SiteEndpoints
  ): ChanPostImage? {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull()
      return null
    }

    var filePath: String? = null
    var thumbPath: String? = null
    var fileId: String? = null
    var fileExt: String? = null
    var fileName: String? = null
    val imageBuilder = ChanPostImageBuilder()

    reader.beginObject()

    while (reader.hasNext()) {
      when (reader.nextName()) {
        "file_path" -> filePath = reader.nextString()
        "thumb_path" -> thumbPath = reader.nextString()
        "tim" -> fileId = reader.nextString()
        "fsize" -> imageBuilder.imageSize(reader.nextLong())
        "w" -> imageBuilder.imageWidth(reader.nextInt())
        "h" -> imageBuilder.imageHeight(reader.nextInt())
        "spoiler" -> imageBuilder.spoiler(reader.nextBoolean())
        "ext" -> fileExt = reader.nextString().replace(".", "")
        "filename" -> fileName = reader.nextString()
        "md5" -> imageBuilder.fileHash(reader.nextString(), true)
        else -> reader.skipValue()
      }
    }

    reader.endObject()

    if (
      fileId.isNotNullNorEmpty() &&
      fileName.isNotNullNorEmpty() &&
      fileExt.isNotNullNorEmpty() &&
      filePath.isNotNullNorEmpty() &&
      thumbPath.isNotNullNorEmpty()
    ) {
      val args = SiteEndpoints.makeArgument("file_path", filePath, "thumb_path", thumbPath)
      val customSpoilers = board?.customSpoilers ?: -1

      return imageBuilder
        .serverFilename(fileId)
        .thumbnailUrl(endpoints.thumbnailUrl(builder.requireBoardDescriptor(), false, customSpoilers, args))
        .spoilerThumbnailUrl(endpoints.thumbnailUrl(builder.requireBoardDescriptor(), true, customSpoilers, args))
        .imageUrl(endpoints.imageUrl(builder.requireBoardDescriptor(), args))
        .filename(Parser.unescapeEntities(fileName, false))
        .extension(fileExt)
        .build()
    }

    return null
  }
}