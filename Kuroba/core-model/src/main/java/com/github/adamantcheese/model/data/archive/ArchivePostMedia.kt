package com.github.adamantcheese.model.data.archive

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