package com.github.k1rakishou.chan.features.media_viewer

import android.os.Parcelable
import android.webkit.MimeTypeMap
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.extractFileName
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.parser.Parser
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.regex.Pattern

sealed class ViewableMediaParcelableHolder {

  @Parcelize
  data class TransitionInfo(
    val transitionThumbnailUrl: String,
    val lastTouchPosX: Int,
    val lastTouchPosY: Int
  ) : Parcelable

  @Parcelize
  data class CompositeCatalogMediaParcelableHolder(
    val catalogDescriptorParcelable: DescriptorParcelable,
    val initialImageUrl: String?,
    val transitionInfo: TransitionInfo?,
    val mediaViewerOptions: MediaViewerOptions
  ) : ViewableMediaParcelableHolder(), Parcelable {
    @IgnoredOnParcel
    val compositeCatalogDescriptor by lazy {
      catalogDescriptorParcelable.toChanDescriptor() as ChanDescriptor.CompositeCatalogDescriptor
    }

    companion object {
      fun fromCompositeCatalogDescriptor(
        compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor,
        initialImageUrl: String?,
        transitionInfo: TransitionInfo?,
        mediaViewerOptions: MediaViewerOptions
      ) : CompositeCatalogMediaParcelableHolder {
        return CompositeCatalogMediaParcelableHolder(
          catalogDescriptorParcelable = DescriptorParcelable.fromDescriptor(compositeCatalogDescriptor),
          initialImageUrl = initialImageUrl,
          transitionInfo = transitionInfo,
          mediaViewerOptions = mediaViewerOptions
        )
      }
    }
  }

  @Parcelize
  data class CatalogMediaParcelableHolder(
    val catalogDescriptorParcelable: DescriptorParcelable,
    val initialImageUrl: String?,
    val transitionInfo: TransitionInfo?,
    val mediaViewerOptions: MediaViewerOptions
  ) : ViewableMediaParcelableHolder(), Parcelable {
    @IgnoredOnParcel
    val catalogDescriptor by lazy {
      catalogDescriptorParcelable.toChanDescriptor() as ChanDescriptor.CatalogDescriptor
    }

    companion object {
      fun fromCatalogDescriptor(
        catalogDescriptor: ChanDescriptor.CatalogDescriptor,
        initialImageUrl: String?,
        transitionInfo: TransitionInfo?,
        mediaViewerOptions: MediaViewerOptions
      ) : CatalogMediaParcelableHolder {
        return CatalogMediaParcelableHolder(
          catalogDescriptorParcelable = DescriptorParcelable.fromDescriptor(catalogDescriptor as ChanDescriptor),
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
    val postNoSubNoList: List<PostNoSubNo>?,
    val initialImageUrl: String?,
    val transitionInfo: TransitionInfo?,
    val mediaViewerOptions: MediaViewerOptions
  ) : ViewableMediaParcelableHolder(), Parcelable {
    @IgnoredOnParcel
    val threadDescriptor by lazy {
      threadDescriptorParcelable.toChanDescriptor() as ChanDescriptor.ThreadDescriptor
    }

    companion object {
      private const val TAG = "ThreadMediaParcelableHolder"
      private const val MAX_POST_DESCRIPTORS = 250

      fun fromThreadDescriptor(
        threadDescriptor: ChanDescriptor.ThreadDescriptor,
        postDescriptorList: List<PostDescriptor>,
        initialImageUrl: String?,
        transitionInfo: TransitionInfo?,
        mediaViewerOptions: MediaViewerOptions
      ) : ThreadMediaParcelableHolder {
        // To avoid TransactionTooLargeException
        val postNoSubNoList = if (postDescriptorList.size > MAX_POST_DESCRIPTORS) {
          Logger.d(TAG, "fromThreadDescriptor() ${postDescriptorList.size} > $MAX_POST_DESCRIPTORS")
          null
        } else {
          Logger.d(TAG, "fromThreadDescriptor() ${postDescriptorList.size} <= $MAX_POST_DESCRIPTORS")
          postDescriptorList.map { postDescriptor -> PostNoSubNo(postDescriptor.postNo, postDescriptor.postSubNo) }
        }

        return ThreadMediaParcelableHolder(
          threadDescriptorParcelable = DescriptorParcelable.fromDescriptor(threadDescriptor),
          postNoSubNoList = postNoSubNoList,
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
data class PostNoSubNo(val postNo: Long, val postSubNo: Long) : Parcelable

@Parcelize
data class MediaViewerOptions(
  val mediaViewerOpenedFromAlbum: Boolean = false
) : Parcelable

sealed class ViewableMedia(
  open val mediaLocation: MediaLocation,
  open val previewLocation: MediaLocation?,
  open val spoilerLocation: MediaLocation?,
  open val viewableMediaMeta: ViewableMediaMeta
) {

  val postDescriptor: PostDescriptor?
    get() = viewableMediaMeta.ownerPostDescriptor

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
    return mediaLocation is MediaLocation.Remote
  }

  fun hasPostDescriptor(): Boolean {
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

  data class Audio(
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

private val SOUND_POST_PATTERN by lazy { Pattern.compile("\\[sound=(.*?)\\]") }

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
  @get:Synchronized
  @set:Synchronized
  var mediaOnDiskSize: Long? = null

  val fullServerMediaName by lazy {
    if (serverMediaName.isNullOrEmpty()) {
      return@lazy null
    }

    if (extension.isNullOrEmpty()) {
      return@lazy serverMediaName
    }

    return@lazy "${serverMediaName}.${extension}"
  }

  val isGif by lazy {
    return@lazy extension?.equals("gif", ignoreCase = true) ?: false
  }

  val soundPostActualSoundMedia by lazy {
    if (!ChanSettings.mediaViewerSoundPostsEnabled.get()) {
      return@lazy null
    }

    if (originalMediaName.isNullOrEmpty()) {
      return@lazy null
    }

    try {
      val matcher = SOUND_POST_PATTERN.matcher(originalMediaName)
      if (!matcher.find()) {
        return@lazy null
      }

      val url = matcher.groupOrNull(1)
        ?: return@lazy null

      var unescapedUrl = URLDecoder.decode(Parser.unescapeEntities(url, false), StandardCharsets.UTF_8.name())

      if (unescapedUrl.startsWith("https://")) {
        unescapedUrl = unescapedUrl.removePrefix("https://")
      }
      if (unescapedUrl.startsWith("http://")) {
        unescapedUrl = unescapedUrl.removePrefix("http://")
      }
      if (unescapedUrl.startsWith("www.")) {
        unescapedUrl = unescapedUrl.removePrefix("www.")
      }

      val extension = MimeTypeMap.getFileExtensionFromUrl(unescapedUrl)
      if (extension.isNullOrEmpty()) {
        return@lazy null
      }

      val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
      if (mimeType.isNullOrEmpty()) {
        return@lazy null
      }

      if (!mimeType.startsWith("audio/")) {
        return@lazy null
      }

      val actualUrl = "https://$unescapedUrl"
      if (actualUrl.toHttpUrlOrNull() == null) {
        Logger.e("ViewableMediaMeta", "soundPostActualSoundMedia incorrect url: '$actualUrl'")
        return@lazy null
      }

      return@lazy ViewableMedia.Audio(
        mediaLocation = MediaLocation.Remote(actualUrl),
        previewLocation = null,
        spoilerLocation = null,
        viewableMediaMeta = ViewableMediaMeta(
          ownerPostDescriptor = null,
          serverMediaName = null,
          originalMediaName = null,
          extension = null,
          mediaWidth = null,
          mediaHeight = null,
          mediaSize = null,
          mediaHash = null,
          isSpoiler = false
        )
      )
    } catch (error: Throwable) {
      Logger.e("ViewableMediaMeta", "soundPostActualSoundMedia error", error)
      return@lazy null
    }
  }

}

sealed class MediaLocation : Parcelable {

  val value: String
    get() {
      return when (this) {
        is Local -> path
        is Remote -> urlRaw
      }
    }

  @Parcelize
  data class Remote(val urlRaw: String) : MediaLocation() {
    @IgnoredOnParcel
    val url by lazy { urlRaw.toHttpUrl() }
  }

  @Parcelize
  data class Local(val path: String, val isUri: Boolean) : MediaLocation()

}