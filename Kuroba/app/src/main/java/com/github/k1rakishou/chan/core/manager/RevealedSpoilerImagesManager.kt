package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.features.album.AlbumItemData
import com.github.k1rakishou.chan.features.view.media.MediaLocation
import com.github.k1rakishou.chan.features.view.media.ViewableMedia
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class RevealedSpoilerImagesManager {
  private val _revealedSpoilerImages = ConcurrentHashMap<ChanDescriptor.ThreadDescriptor, RevealedImagesInThread>(32)

  private val _spoilerImageRevealEventsFlow = MutableSharedFlow<RevealedSpoilerImage>(extraBufferCapacity = Channel.UNLIMITED)
  val spoilerImageRevealEventsFlow: SharedFlow<RevealedSpoilerImage>
    get() = _spoilerImageRevealEventsFlow.asSharedFlow()

  suspend fun isImageSpoilerImageRevealed(chanPostImage: ChanPostImage): Boolean {
    return _revealedSpoilerImages[chanPostImage.ownerPostDescriptor.threadDescriptor()]
      ?.isImageSpoilerImageRevealed(chanPostImage) ?: false
  }

  suspend fun isImageSpoilerImageRevealed(albumItemData: AlbumItemData): Boolean {
    return _revealedSpoilerImages[albumItemData.postDescriptor.threadDescriptor()]
      ?.isImageSpoilerImageRevealed(albumItemData) ?: false
  }

  suspend fun onImageClicked(viewableMedia: ViewableMedia) {
    if (viewableMedia.spoilerLocation == null) {
      return
    }

    val postDescriptor = viewableMedia.postDescriptor
      ?: return

    val revealedImagesInThread = _revealedSpoilerImages.getOrPut(
      key = postDescriptor.threadDescriptor(),
      defaultValue = { RevealedImagesInThread() }
    )

    val revealedSpoilerImage = revealedImagesInThread.add(viewableMedia)
    if (revealedSpoilerImage != null) {
      _spoilerImageRevealEventsFlow.emit(revealedSpoilerImage)
    }
  }

  suspend fun onImageClicked(albumItemData: AlbumItemData) {
    if (albumItemData.spoilerThumbnailImageUrl == null) {
      return
    }

    val revealedImagesInThread = _revealedSpoilerImages.getOrPut(
      key = albumItemData.postDescriptor.threadDescriptor(),
      defaultValue = { RevealedImagesInThread() }
    )

    val revealedSpoilerImage = revealedImagesInThread.add(albumItemData)
    if (revealedSpoilerImage != null) {
      _spoilerImageRevealEventsFlow.emit(revealedSpoilerImage)
    }
  }

  suspend fun onImageClicked(chanPostImage: ChanPostImage) {
    if (!chanPostImage.spoiler) {
      return
    }

    val revealedImagesInThread = _revealedSpoilerImages.getOrPut(
      key = chanPostImage.ownerPostDescriptor.threadDescriptor(),
      defaultValue = { RevealedImagesInThread() }
    )

    val revealedSpoilerImage = revealedImagesInThread.add(chanPostImage)
    if (revealedSpoilerImage != null) {
      _spoilerImageRevealEventsFlow.emit(revealedSpoilerImage)
    }
  }

  class RevealedImagesInThread {
    private val mutex = Mutex()

    @GuardedBy("mutex")
    private val _revealedImages = hashSetWithCap<RevealedSpoilerImage>(16)

    suspend fun add(albumItemData: AlbumItemData): RevealedSpoilerImage? {
      val revealedSpoilerImage = albumItemData.toRevealedSpoilerImage()

      mutex.withLock { _revealedImages.add(revealedSpoilerImage) }
      return revealedSpoilerImage
    }

    suspend fun add(chanPostImage: ChanPostImage): RevealedSpoilerImage? {
      val revealedSpoilerImage = chanPostImage.toRevealedSpoilerImage()
        ?: return null

      mutex.withLock { _revealedImages.add(revealedSpoilerImage) }
      return revealedSpoilerImage
    }

    suspend fun add(viewableMedia: ViewableMedia): RevealedSpoilerImage? {
      val revealedSpoilerImage = viewableMedia.toRevealedSpoilerImage()
        ?: return null

      mutex.withLock { _revealedImages.add(revealedSpoilerImage) }
      return revealedSpoilerImage
    }

    suspend fun isImageSpoilerImageRevealed(chanPostImage: ChanPostImage): Boolean {
      val revealedSpoilerImage = chanPostImage.toRevealedSpoilerImage()
        ?: return false

      return mutex.withLock { _revealedImages.contains(revealedSpoilerImage) }
    }

    suspend fun isImageSpoilerImageRevealed(albumItemData: AlbumItemData): Boolean {
      val revealedSpoilerImage = albumItemData.toRevealedSpoilerImage()
      return mutex.withLock { _revealedImages.contains(revealedSpoilerImage) }
    }

    private fun ChanPostImage.toRevealedSpoilerImage(): RevealedSpoilerImage? {
      val thumbnailUrl = actualThumbnailUrl
      if (thumbnailUrl == null) {
        return null
      }

      return RevealedSpoilerImage(
        postDescriptor = ownerPostDescriptor,
        thumbnailUrl = thumbnailUrl,
        fullImageUrl = imageUrl
      )
    }

    private fun AlbumItemData.toRevealedSpoilerImage(): RevealedSpoilerImage {
      return RevealedSpoilerImage(
        postDescriptor = postDescriptor,
        thumbnailUrl = thumbnailImageUrl,
        fullImageUrl = fullImageUrl
      )
    }

    private fun ViewableMedia.toRevealedSpoilerImage(): RevealedSpoilerImage? {
      val postDescriptor = postDescriptor
        ?: return null
      val previewLocation = previewLocation
        ?: return null
      val mediaLocation = mediaLocation

      val thumbnailUrl = if (previewLocation is MediaLocation.Remote) {
        previewLocation.url
      } else {
        return null
      }

      val fullImageUrl = if (mediaLocation is MediaLocation.Remote) {
        mediaLocation.url
      } else {
        null
      }

      return RevealedSpoilerImage(
        postDescriptor = postDescriptor,
        thumbnailUrl = thumbnailUrl,
        fullImageUrl = fullImageUrl
      )
    }

  }

  data class RevealedSpoilerImage(
    val postDescriptor: PostDescriptor,
    val thumbnailUrl: HttpUrl,
    val fullImageUrl: HttpUrl?
  )
}