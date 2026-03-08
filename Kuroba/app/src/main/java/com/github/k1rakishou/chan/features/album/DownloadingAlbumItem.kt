package com.github.k1rakishou.chan.features.album

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.chan.features.download.media.ImageSaverV2ServiceDelegate
import okhttp3.HttpUrl

@Immutable
data class DownloadingAlbumItem(
  val albumItemDataId: Long,
  val downloadUniqueId: String,
  val fullImageUrl: HttpUrl,
  val state: State
) {
  enum class State {
    Enqueued,
    Downloading,
    Downloaded,
    FailedToDownload,
    Canceled,
    Deleted;

    companion object {
      fun from(state: ImageSaverV2ServiceDelegate.DownloadingImageState.State): State {
        return when (state) {
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.Downloading -> Downloading
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.Downloaded -> Downloaded
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.FailedToDownload -> FailedToDownload
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.Canceled -> Canceled
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.Deleted -> Deleted
        }
      }
    }
  }
}