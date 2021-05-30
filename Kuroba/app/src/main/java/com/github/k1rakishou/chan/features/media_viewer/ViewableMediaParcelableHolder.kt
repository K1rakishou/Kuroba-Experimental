package com.github.k1rakishou.chan.features.media_viewer

import android.os.Parcelable
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import okhttp3.HttpUrl

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
    val transitionInfo: TransitionInfo?
  ) : ViewableMediaParcelableHolder(), Parcelable {
    @IgnoredOnParcel
    val catalogDescriptor by lazy { catalogDescriptorParcelable.toChanDescriptor() as ChanDescriptor.CatalogDescriptor }

    companion object {
      fun fromCatalogDescriptor(
        catalogDescriptor: ChanDescriptor.CatalogDescriptor,
        initialImageUrl: String?,
        transitionInfo: TransitionInfo?
      ) : CatalogMediaParcelableHolder {
        return CatalogMediaParcelableHolder(
          catalogDescriptorParcelable = DescriptorParcelable.fromDescriptor(catalogDescriptor),
          initialImageUrl = initialImageUrl,
          transitionInfo = transitionInfo
        )
      }
    }
  }

  @Parcelize
  data class ThreadMediaParcelableHolder(
    val threadDescriptorParcelable: DescriptorParcelable,
    val initialImageUrl: String?,
    val transitionInfo: TransitionInfo?
  ) : ViewableMediaParcelableHolder(), Parcelable {
    @IgnoredOnParcel
    val threadDescriptor by lazy { threadDescriptorParcelable.toChanDescriptor() as ChanDescriptor.ThreadDescriptor }

    companion object {
      fun fromThreadDescriptor(
        threadDescriptor: ChanDescriptor.ThreadDescriptor,
        initialImageUrl: String?,
        transitionInfo: TransitionInfo?
      ) : ThreadMediaParcelableHolder {
        return ThreadMediaParcelableHolder(
          threadDescriptorParcelable = DescriptorParcelable.fromDescriptor(threadDescriptor),
          initialImageUrl = initialImageUrl,
          transitionInfo = transitionInfo
        )
      }
    }
  }

  @Parcelize
  data class LocalMediaParcelableHolder(val filePathList: List<String>) : ViewableMediaParcelableHolder(), Parcelable {

  }

  @Parcelize
  data class RemoteMediaParcelableHolder(val urlList: List<String>) : ViewableMediaParcelableHolder(), Parcelable

}

sealed class ViewableMedia(
  open val mediaLocation: MediaLocation,
  open val previewLocation: MediaLocation?,
  open val spoilerLocation: MediaLocation?,
  open val viewableMediaMeta: ViewableMediaMeta
) {

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
  val mediaName: String?,
  val mediaWidth: Int?,
  val mediaHeight: Int?,
  val mediaSize: Long?,
  val mediaHash: String?,
  val isSpoiler: Boolean
)

sealed class MediaLocation {

  data class Remote(val url: HttpUrl) : MediaLocation()

  data class Local(val path: String, val isUri: Boolean) : MediaLocation()

}