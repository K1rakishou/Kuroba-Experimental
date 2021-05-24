package com.github.k1rakishou.chan.features.media_viewer

import android.os.Parcelable
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import okhttp3.HttpUrl

sealed class ViewableMediaParcelableHolder {

  @Parcelize
  data class CatalogMediaParcelableHolder(
    val catalogDescriptorParcelable: DescriptorParcelable,
    val scrollToImageWithUrl: String?
  ) : ViewableMediaParcelableHolder(), Parcelable {
    @IgnoredOnParcel
    val catalogDescriptor by lazy { catalogDescriptorParcelable.toChanDescriptor() as ChanDescriptor.CatalogDescriptor }

    companion object {
      fun fromCatalogDescriptor(
        catalogDescriptor: ChanDescriptor.CatalogDescriptor,
        scrollToImageWithUrl: String?
      ) : CatalogMediaParcelableHolder {
        return CatalogMediaParcelableHolder(
          DescriptorParcelable.fromDescriptor(catalogDescriptor),
          scrollToImageWithUrl
        )
      }
    }
  }

  @Parcelize
  data class ThreadMediaParcelableHolder(
    val threadDescriptorParcelable: DescriptorParcelable,
    val scrollToImageWithUrl: String?
  ) : ViewableMediaParcelableHolder(), Parcelable {
    @IgnoredOnParcel
    val threadDescriptor by lazy { threadDescriptorParcelable.toChanDescriptor() as ChanDescriptor.ThreadDescriptor }

    companion object {
      fun fromThreadDescriptor(
        threadDescriptor: ChanDescriptor.ThreadDescriptor,
        scrollToImageWithUrl: String?
      ) : ThreadMediaParcelableHolder {
        return ThreadMediaParcelableHolder(
          DescriptorParcelable.fromDescriptor(threadDescriptor),
          scrollToImageWithUrl
        )
      }
    }
  }

  @Parcelize
  data class LocalMediaParcelableHolder(val mediaList: List<Media>) : ViewableMediaParcelableHolder(), Parcelable {

    @Parcelize
    data class Media(
      val path: String,
      val isUri: Boolean
    ) : Parcelable

  }

  @Parcelize
  data class RemoteMediaParcelableHolder(val urlList: List<String>) : ViewableMediaParcelableHolder(), Parcelable

}

data class ViewableMediaList(val viewableMediaList: List<ViewableMedia>)

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
  val mediaName: String?,
  val mediaWidth: Int?,
  val mediaHeight: Int?,
  val mediaSize: Long?,
  val mediaHash: String?,
  val isSpoiler: Boolean,
  val inlined: Boolean
)

sealed class MediaLocation {

  data class Remote(val url: HttpUrl) : MediaLocation()

  data class Local(val path: String, val isUri: Boolean) : MediaLocation()

}