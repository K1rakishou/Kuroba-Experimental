package com.github.k1rakishou.persist_state

import com.google.gson.annotations.SerializedName

data class ImageSaverV2Options(
  @SerializedName("root_dir_uri")
  var rootDirectoryUri: String? = null,
  @SerializedName("sub_dirs")
  var subDirs: String? = null,
  @SerializedName("append_site_name")
  var appendSiteName: Boolean = false,
  @SerializedName("append_board_code")
  var appendBoardCode: Boolean = false,
  @SerializedName("append_thread_id")
  var appendThreadId: Boolean = false,
  @SerializedName("append_thread_subject")
  var appendThreadSubject: Boolean = false,
  @SerializedName("image_name_options")
  var imageNameOptions: Int = ImageNameOptions.UseServerFileName.rawValue,
  @SerializedName("duplicates_resolution")
  var duplicatesResolution: Int = DuplicatesResolution.AskWhatToDo.rawValue,
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
        return values().firstOrNull { value -> value.rawValue == rawValue }
          ?: AskWhatToDo
      }
    }
  }

  enum class ImageNameOptions(val rawValue: Int) {
    UseServerFileName(0),
    UseOriginalFileName(1);

    companion object {
      fun fromRawValue(rawValue: Int): ImageNameOptions {
        return values().firstOrNull { value -> value.rawValue == rawValue }
          ?: UseServerFileName
      }
    }
  }

}