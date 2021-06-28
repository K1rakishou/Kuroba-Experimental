package com.github.k1rakishou.chan.features.media_viewer

import android.os.Parcelable
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.extractFileName
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.*

sealed class ViewableMediaParcelableHolder {

  @Parcelize
  data class TransitionInfo(
    val transitionThumbnailUrl: String,
    val lastTouchPosX: Int,
    val lastTouchPosY: Int
  ) : Parcelable

  @Parcelize
  data class CatalogMediaParcelableHolder(
    val catalogDescriptorParcelable: DescriptorParcelable,
    val initialImageUrl: String?,
    val transitionInfo: TransitionInfo?,
    val mediaViewerOptions: MediaViewerOptions
  ) : ViewableMediaParcelableHolder(), Parcelable {
    @IgnoredOnParcel
    val catalogDescriptor by lazy { catalogDescriptorParcelable.toChanDescriptor() as ChanDescriptor.CatalogDescriptor }

    companion object {
      fun fromCatalogDescriptor(
        catalogDescriptor: ChanDescriptor.CatalogDescriptor,
        initialImageUrl: String?,
        transitionInfo: TransitionInfo?,
        mediaViewerOptions: MediaViewerOptions
      ) : CatalogMediaParcelableHolder {
        return CatalogMediaParcelableHolder(
          catalogDescriptorParcelable = DescriptorParcelable.fromDescriptor(catalogDescriptor),
          initialImageUrl = initialImageUrl,
          transitionInfo = transitionInfo,
          mediaViewerOptions = mediaViewerOptions
        )
      }
    }
  }

  @Parcelize
  data class ThreadMediaParcelableHolder(
    val threadDescriptorParcelable: DescriptorParcelable,
    val initialImageUrl: String?,
    val transitionInfo: TransitionInfo?,
    val mediaViewerOptions: MediaViewerOptions
  ) : ViewableMediaParcelableHolder(), Parcelable {
    @IgnoredOnParcel
    val threadDescriptor by lazy { threadDescriptorParcelable.toChanDescriptor() as ChanDescriptor.ThreadDescriptor }

    companion object {
      fun fromThreadDescriptor(
        threadDescriptor: ChanDescriptor.ThreadDescriptor,
        initialImageUrl: String?,
        transitionInfo: TransitionInfo?,
        mediaViewerOptions: MediaViewerOptions
      ) : ThreadMediaParcelableHolder {
        return ThreadMediaParcelableHolder(
          threadDescriptorParcelable = DescriptorParcelable.fromDescriptor(threadDescriptor),
          initialImageUrl = initialImageUrl,
          transitionInfo = transitionInfo,
          mediaViewerOptions = mediaViewerOptions
        )
      }
    }
  }

  @Parcelize
  data class MixedMediaParcelableHolder(
    val mixedMedia: List<MediaLocation>
  ) : ViewableMediaParcelableHolder(), Parcelable

  @Parcelize
  data class ReplyAttachMediaParcelableHolder(
    val replyUuidList: List<UUID>
  ) : ViewableMediaParcelableHolder(), Parcelable

}

@Parcelize
data class MediaViewerOptions(val mediaViewerOpenedFromAlbum: Boolean = false) : Parcelable

sealed class ViewableMedia(
  open val mediaLocation: MediaLocation,
  open val previewLocation: MediaLocation?,
  open val spoilerLocation: MediaLocation?,
  open val viewableMediaMeta: ViewableMediaMeta
) {

  fun formatFullOriginalFileName(): String? {
    if (viewableMediaMeta.originalMediaName.isNullOrEmpty()) {
      return null
    }

    return buildString {
      append(viewableMediaMeta.originalMediaName!!)

      if (viewableMediaMeta.extension.isNotNullNorEmpty()) {
        append(".")
        append(viewableMediaMeta.extension!!)
      }
    }
  }

  fun formatFullServerFileName(): String? {
    if (viewableMediaMeta.serverMediaName.isNullOrEmpty()) {
      return null
    }

    return buildString {
      append(viewableMediaMeta.serverMediaName!!)

      if (viewableMediaMeta.extension.isNotNullNorEmpty()) {
        append(".")
        append(viewableMediaMeta.extension!!)
      }
    }
  }

  fun canReloadMedia(): Boolean {
    return mediaLocation !is MediaLocation.Local
  }

  fun canMediaBeDownloaded(): Boolean {
    return viewableMediaMeta.ownerPostDescriptor != null && mediaLocation is MediaLocation.Remote
  }

  fun canGoToMediaPost(): Boolean {
    return viewableMediaMeta.ownerPostDescriptor != null
  }

  fun getMediaNameForMenuHeader(): String? {
    val mediaName = when {
      viewableMediaMeta.originalMediaName.isNotNullNorEmpty() -> viewableMediaMeta.originalMediaName!!
      viewableMediaMeta.serverMediaName.isNotNullNorEmpty() -> viewableMediaMeta.serverMediaName!!
      else -> {
        when (val location = mediaLocation) {
          is MediaLocation.Local -> {
            if (!location.path.contains("/")) {
              null
            } else {
              location.path.substringAfterLast("/")
            }
          }
          is MediaLocation.Remote -> {
            location.url.extractFileName()
          }
        }
      }
    }

    if (viewableMediaMeta.extension.isNullOrEmpty()) {
      return mediaName
    }


    return "${mediaName}.${viewableMediaMeta.extension!!}"
  }

  fun toSimpleImageInfoOrNull(): ImageSaverV2.SimpleSaveableMediaInfo? {
    val postDescriptor = viewableMediaMeta.ownerPostDescriptor
      ?: return null

    val mediaUrl = when (val location = mediaLocation) {
      is MediaLocation.Local -> null
      is MediaLocation.Remote -> location.url
    }

    if (mediaUrl == null) {
      return null
    }

    var serverFileName = viewableMediaMeta.serverMediaName
    if (serverFileName.isNullOrEmpty()) {
      serverFileName = when (val location = mediaLocation) {
        is MediaLocation.Local -> {
          if (!location.path.contains("/")) {
            null
          } else {
            location.path.substringAfterLast("/")
          }
        }
        is MediaLocation.Remote -> {
          location.url.extractFileName()
        }
      }
    }

    if (serverFileName.isNullOrEmpty()) {
      return null
    }

    var extension = viewableMediaMeta.extension
    if (extension.isNullOrEmpty()) {
      extension = when (val location = mediaLocation) {
        is MediaLocation.Local -> {
          StringUtils.extractFileNameExtension(location.path)
        }
        is MediaLocation.Remote -> {
          location.url.extractFileName()?.let { segment ->
            StringUtils.extractFileNameExtension(segment)
          }
        }
      }
    }

    if (extension.isNullOrEmpty()) {
      return null
    }

    return ImageSaverV2.SimpleSaveableMediaInfo(
      mediaUrl = mediaUrl,
      ownerPostDescriptor = postDescriptor,
      serverFilename = serverFileName,
      originalFileName = viewableMediaMeta.originalMediaName,
      extension = extension
    )
  }

  data class Image(
    override val mediaLocation: MediaLocation,
    override val previewLocation: MediaLocation?,
    override val spoilerLocation: MediaLocation?,
    override val viewableMediaMeta: ViewableMediaMeta
  ) : ViewableMedia(mediaLocation, previewLocation, spoilerLocation, viewableMediaMeta)

  data class Gif(
    override val mediaLocation: MediaLocation,
    override val previewLocation: MediaLocation?,
    override val spoilerLocation: MediaLocation?,
    override val viewableMediaMeta: ViewableMediaMeta
  ) : ViewableMedia(mediaLocation, previewLocation, spoilerLocation, viewableMediaMeta)

  data class Video(
    override val mediaLocation: MediaLocation,
    override val previewLocation: MediaLocation?,
    override val spoilerLocation: MediaLocation?,
    override val viewableMediaMeta: ViewableMediaMeta
  ) : ViewableMedia(mediaLocation, previewLocation, spoilerLocation, viewableMediaMeta)

  data class Unsupported(
    override val mediaLocation: MediaLocation,
    override val previewLocation: MediaLocation?,
    override val spoilerLocation: MediaLocation?,
    override val viewableMediaMeta: ViewableMediaMeta
  ) : ViewableMedia(mediaLocation, previewLocation, spoilerLocation, viewableMediaMeta)

}

data class ViewableMediaMeta(
  val ownerPostDescriptor: PostDescriptor?,
  val serverMediaName: String?,
  val originalMediaName: String?,
  val extension: String?,
  val mediaWidth: Int?,
  val mediaHeight: Int?,
  val mediaSize: Long?,
  val mediaHash: String?,
  val isSpoiler: Boolean
) {

  fun formatFullServerMediaName(): String? {
    if (serverMediaName.isNullOrEmpty()) {
      return null
    }

    if (extension.isNullOrEmpty()) {
      return serverMediaName
    }

    return "${serverMediaName}.${extension}"
  }

}

sealed class MediaLocation : Parcelable{

  @Parcelize
  data class Remote(val urlRaw: String) : MediaLocation() {
    @IgnoredOnParcel
    val url by lazy { urlRaw.toHttpUrl() }
  }

  @Parcelize
  data class Local(val path: String, val isUri: Boolean) : MediaLocation()

}