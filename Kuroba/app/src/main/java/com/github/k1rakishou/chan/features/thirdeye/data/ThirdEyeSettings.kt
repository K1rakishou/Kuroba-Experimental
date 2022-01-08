package com.github.k1rakishou.chan.features.thirdeye.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.*
import java.util.regex.Pattern

private val testBoorus = listOf<BooruSetting>()

@JsonClass(generateAdapter = true)
data class ThirdEyeSettings(
  @Json(name = "image_file_name_regex") val imageFileNameRegex: String = "^([a-f0-9]{32})\$",
  @Json(name = "added_boorus") val addedBoorus: List<BooruSetting> = testBoorus
) {
  @Transient
  private var _imageFileNamePattern: Pattern? = null

  fun imageFileNamePattern(): Pattern {
    return synchronized(this) {
      if (_imageFileNamePattern == null || _imageFileNamePattern?.pattern() != imageFileNameRegex) {
        _imageFileNamePattern = Pattern.compile(imageFileNameRegex)
      }

      return@synchronized _imageFileNamePattern!!
    }
  }

}

@JsonClass(generateAdapter = true)
data class BooruSetting(
  @Json(name = "image_by_md5_endpoint") val imageByMd5Endpoint: String,
  @Json(name = "preview_url_json_key") val previewUrlJsonKey: String,
  @Json(name = "full_url_json_key") val fullUrlJsonKey: String,
  @Json(name = "width_json_key") val widthJsonKey: String? = null,
  @Json(name = "height_json_key") val heightJsonKey: String? = null,
  @Json(name = "tags_json_key") val tagsJsonKey: String? = null,
  @Json(name = "file_size_json_key") val fileSizeJsonKey: String? = null,
  @Json(name = "banned_tags") val bannedTags: List<String>
) {
  val bannedTagsAsSet by lazy {
    bannedTags
      .map { bannedTag -> bannedTag.lowercase(Locale.ENGLISH) }
      .toSet()
  }

  fun formatFullImageByMd5EndpointUrl(imageHash: String): HttpUrl? {
    val index = imageByMd5Endpoint.indexOf(string = MD5_MARKER)
    if (index < 0) {
      return null
    }

    return imageByMd5Endpoint.replace(MD5_MARKER, imageHash).toHttpUrlOrNull()
  }

  companion object {
    const val MD5_MARKER = "{md5}"
  }

}