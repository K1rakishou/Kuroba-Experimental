package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.util.ChanPostUtils
import okhttp3.HttpUrl
import java.util.*

class ChanPostImage(
  val serverFilename: String,
  val actualThumbnailUrl: HttpUrl? = null,
  val spoilerThumbnailUrl: HttpUrl? = null,
  val imageUrl: HttpUrl? = null,
  val filename: String? = null,
  val extension: String? = null,
  val imageWidth: Int = 0,
  val imageHeight: Int = 0,
  val spoiler: Boolean = false,
  val isInlined: Boolean = false,
  fileSize: Long = 0L,
  val fileHash: String? = null,
  val type: ChanPostImageType? = null
) {
  val hidden: Boolean
    get() = ChanSettings.hideImages.get()

  val size: Long = fileSize
    get() = _loadedFileSize ?: field

  val imageSpoilered: Boolean
    get() {
      if (ChanSettings.postThumbnailRemoveImageSpoilers.get()) {
        return false
      }

      return spoiler
    }

  @get:Synchronized
  @set:Synchronized
  lateinit var ownerPostDescriptor: PostDescriptor
    private set

  private var _loadedFileSize: Long? = null

  val loadedFileSize: Long?
    get() = _loadedFileSize

  @get:Synchronized
  @set:Synchronized
  var isPrefetched = false

  @Synchronized
  fun setSize(newSize: Long) {
    _loadedFileSize = newSize
  }

  @Synchronized
  fun setPostDescriptor(postDescriptor: PostDescriptor) {
    if (::ownerPostDescriptor.isInitialized) {
      check(ownerPostDescriptor == postDescriptor) {
        "Attempt to replace post descriptor to a different one. " +
          "prevPostDescriptor=$ownerPostDescriptor, newPostDescriptor=$postDescriptor"
      }

      return
    }

    ownerPostDescriptor = postDescriptor
  }

  @Synchronized
  fun copy(): ChanPostImage {
    return ChanPostImage(
      serverFilename = serverFilename,
      actualThumbnailUrl = actualThumbnailUrl,
      spoilerThumbnailUrl = spoilerThumbnailUrl,
      imageUrl = imageUrl,
      filename = filename,
      extension = extension,
      imageWidth = imageWidth,
      imageHeight = imageHeight,
      spoiler = spoiler,
      isInlined = isInlined,
      fileSize = size,
      fileHash = fileHash,
      type = type
    ).also { newPostImage ->
      if (::ownerPostDescriptor.isInitialized) {
        newPostImage.ownerPostDescriptor = ownerPostDescriptor
      }

      newPostImage.isPrefetched = isPrefetched
    }
  }

  fun isPlayableType(): Boolean {
    return type === ChanPostImageType.MOVIE || type === ChanPostImageType.GIF
  }

  fun canBeUsedForCloudflarePreloading(): Boolean {
    if (isInlined) {
      return false
    }

    if (isPrefetched) {
       return false
    }

    return imageUrl != null
  }

  fun canBeUsedAsHighResolutionThumbnail(): Boolean {
    if (isInlined) {
      return false
    }

    if (imageSpoilered) {
      return false
    }

    if (size > MAX_PREFETCH_FILE_SIZE) {
      return false
    }

    if (imageWidth > MAX_IMAGE_SIZE || imageHeight > MAX_IMAGE_SIZE) {
      return false
    }

    return true
  }

  fun equalUrl(other: ChanPostImage?): Boolean {
    if (other == null) {
      return false
    }

    if (imageUrl == null || other.imageUrl == null) {
      return serverFilename == other.serverFilename
    }

    return imageUrl == other.imageUrl
  }

  fun getThumbnailUrl(): HttpUrl? {
    if (hidden) {
      return AppConstants.HIDDEN_IMAGE_THUMBNAIL_URL
    }

    if (imageSpoilered) {
      return spoilerThumbnailUrl
    }

    return actualThumbnailUrl
  }

  fun formatFullAvailableFileName(appendExtension: Boolean = true): String {
    var name = filename
    if (name.isNullOrBlank()) {
      name = serverFilename
    }

    return buildString {
      append(name)

      if (appendExtension && extension.isNotNullNorBlank()) {
        append('.')
        append(extension)
      }
    }
  }

  fun formatImageInfo(): String {
    return buildString {
      if (extension.isNotNullNorBlank()) {
        append(' ')
        append(extension.toUpperCase(Locale.ENGLISH))
      }

      if (imageWidth > 0 || imageHeight > 0) {
        append(StringUtils.UNBREAKABLE_SPACE_SYMBOL)
        append("${imageWidth}x${imageHeight}")
      }

      append(StringUtils.UNBREAKABLE_SPACE_SYMBOL)
      append(ChanPostUtils.getReadableFileSize(size).replace(' ', StringUtils.UNBREAKABLE_SPACE_SYMBOL))
    }
  }

  fun formatFullOriginalFileName(): String? {
    if (filename.isNullOrEmpty()) {
      return null
    }

    return buildString {
      append(filename)

      if (extension.isNotNullNorEmpty()) {
        append(".")
        append(extension)
      }
    }
  }

  fun formatFullServerFileName(): String? {
    if (serverFilename.isNullOrEmpty()) {
      return null
    }

    return buildString {
      append(serverFilename)

      if (extension.isNotNullNorEmpty()) {
        append(".")
        append(extension)
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChanPostImage) return false

    if (type != other.type) {
      return false
    }
    if (serverFilename != other.serverFilename) {
      return false
    }
    if (imageUrl != other.imageUrl) {
      return false
    }
    if (actualThumbnailUrl != other.actualThumbnailUrl) {
      return false
    }

    return true
  }

  override fun hashCode(): Int {
    var result = imageUrl.hashCode()
    result = 31 * result + actualThumbnailUrl.hashCode()
    result = 31 * result + serverFilename.hashCode()
    result = 31 * result + type.hashCode()
    return result
  }

  override fun toString(): String {
    return "ChanPostImage(" +
      "serverFilename='$serverFilename', " +
      "imageUrl=$imageUrl, " +
      "actualThumbnailUrl=$actualThumbnailUrl, " +
      "imageWidth=$imageWidth, " +
      "imageHeight=$imageHeight, " +
      "size=$size, " +
      "fileHash=$fileHash)"
  }

  companion object {
    // 5 MB
    const val MAX_PREFETCH_FILE_SIZE = 5 * (1024 * 1024).toLong()

    const val MAX_IMAGE_SIZE = 4096
  }
}