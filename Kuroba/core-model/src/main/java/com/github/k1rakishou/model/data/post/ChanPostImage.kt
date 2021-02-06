package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

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
    get() = hiddenByFilter || ChanSettings.hideImages.get()

  val size: Long = fileSize
    get() = loadedFileSize ?: field

  @get:Synchronized
  @set:Synchronized
  lateinit var ownerPostDescriptor: PostDescriptor
    private set

  private var loadedFileSize: Long? = null

  @get:Synchronized
  @set:Synchronized
  var isPrefetched = false

  @get:Synchronized
  @set:Synchronized
  var hiddenByFilter: Boolean = false

  @Synchronized
  fun setSize(newSize: Long) {
    loadedFileSize = newSize
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

  fun canBeUsedForCloudflarePreloading(): Boolean {
    if (isInlined) {
      return false
    }

    if (isPrefetched) {
       return false
    }

    return imageUrl != null
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
    if (!spoiler) {
      return actualThumbnailUrl
    }

    if (!hidden) {
      return spoilerThumbnailUrl
    }

    return (AppConstants.RESOURCES_ENDPOINT + "hide_thumb.png").toHttpUrl()
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
    // 10 MB
    const val MAX_PREFETCH_FILE_SIZE = 10 * (1024 * 1024).toLong()
  }
}