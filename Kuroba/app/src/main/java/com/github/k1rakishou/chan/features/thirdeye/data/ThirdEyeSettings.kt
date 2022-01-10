package com.github.k1rakishou.chan.features.thirdeye.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.*
import java.util.regex.Pattern

@JsonClass(generateAdapter = true)
data class ThirdEyeSettings(
  @Json(name = "enabled") val enabled: Boolean = false,
  @Json(name = "added_boorus") val addedBoorus: List<BooruSetting> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BooruSetting(
  @Json(name = "image_file_name_regex") val imageFileNameRegex: String = defaultImageFileNameRegex,
  @Json(name = "image_by_key_endpoint") val imageByKeyEndpoint: String = "",
  @Json(name = "full_url_json_key") val fullUrlJsonKey: String = "",
  @Json(name = "preview_url_json_key") val previewUrlJsonKey: String = "",
  @Json(name = "file_size_json_key") val fileSizeJsonKey: String = "",
  @Json(name = "width_json_key") val widthJsonKey: String = "",
  @Json(name = "height_json_key") val heightJsonKey: String = "",
  @Json(name = "tags_json_key") val tagsJsonKey: String = "",
  @Json(name = "banned_tags") val bannedTags: List<String> = emptyList()
) {
  // This is used to differentiate two boorus apart from each other.
  // It's impossible to have to separate boorus with the same key.
  val booruUniqueKey: String
    get() = imageByKeyEndpoint

  val bannedTagsAsSet by lazy {
    bannedTags
      .map { bannedTag -> bannedTag.lowercase(Locale.ENGLISH) }
      .toSet()
  }

  val bannedTagsAsString by lazy { bannedTags.joinToString(separator = " ") }

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

  fun formatFullImageByMd5EndpointUrl(imageHash: String): HttpUrl? {
    val index = imageByKeyEndpoint.indexOf(string = KEY_MARKER)
    if (index < 0) {
      // If KEY_MARKER not found then append the hash to the end of the imageByKeyEndpoint
      return (imageByKeyEndpoint + imageHash).toHttpUrlOrNull()
    }

    // If KEY_MARKER found then replace it with the hash
    return imageByKeyEndpoint.replace(KEY_MARKER, imageHash).toHttpUrlOrNull()
  }

  companion object {
    const val KEY_MARKER = "{key}"
    const val defaultImageFileNameRegex = "^([a-f0-9]{32})\$"
  }

}