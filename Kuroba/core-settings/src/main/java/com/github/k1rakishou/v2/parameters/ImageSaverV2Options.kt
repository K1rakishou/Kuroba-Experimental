package com.github.k1rakishou.v2.parameters

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ImageSaverV2Options(
  @field:Json("root_dir_uri")
  val rootDirectoryUri: String? = null,
  @field:Json("sub_dirs")
  val subDirs: String? = null,
  @field:Json("append_site_name")
  val appendSiteName: Boolean = false,
  @field:Json("append_board_code")
  val appendBoardCode: Boolean = false,
  @field:Json("append_thread_id")
  val appendThreadId: Boolean = false,
  @field:Json("append_thread_subject")
  val appendThreadSubject: Boolean = false,
  @field:Json("image_name_options")
  val imageNameOptions: Int = ImageNameOptions.UseServerFileName.rawValue,
  @field:Json("duplicates_resolution")
  val duplicatesResolution: Int = DuplicatesResolution.AskWhatToDo.rawValue,
) {

  fun shouldShowImageSaverOptionsController(): Boolean {
    if (rootDirectoryUri.isNullOrBlank()) {
      return true
    }

    return false
  }

  enum class DuplicatesResolution(val rawValue: Int) {
    AskWhatToDo(0),
    Overwrite(1),
    Skip(2),
    SaveAsDuplicate(3);

    companion object {
      fun fromRawValue(rawValue: Int): DuplicatesResolution {
        return entries
          .firstOrNull { value -> value.rawValue == rawValue }
          ?: AskWhatToDo
      }
    }
  }

  enum class ImageNameOptions(val rawValue: Int) {
    UseServerFileName(0),
    UseOriginalFileName(1);

    companion object {
      fun fromRawValue(rawValue: Int): ImageNameOptions {
        return entries
          .firstOrNull { value -> value.rawValue == rawValue }
          ?: UseServerFileName
      }
    }
  }
}
