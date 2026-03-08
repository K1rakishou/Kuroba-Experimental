package com.github.k1rakishou.chan.features.album

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.download.media.ImageSaverV2
import com.github.k1rakishou.chan.features.download.media.ImageSaverV2OptionsController
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarManager
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import com.github.k1rakishou.persist_state.PersistableChanState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class DownloadImagesHelper(
  private val viewModelScope: CoroutineScope,
  private val snackbarManager: SnackbarManager,
  private val imageSaverV2: ImageSaverV2
) {
  private var _enqueueAlbumItemDownloadJob: Job? = null
  private var _enqueueAlbumItemsDownloadJob: Job? = null

  fun downloadImage(
    albumSelection: AlbumSelection,
    albumItems: SnapshotStateList<AlbumItemData>,
    downloadingAlbumItems: SnapshotStateMap<Long, DownloadingAlbumItem>,
    chanPostImage: ChanPostImage,
    showOptions: Boolean,
    presentImageSaverOptionsController: (ImageSaverV2OptionsController.Options.SingleImage) -> Boolean,
    exitSelectionMode: () -> Unit
  ) {
    _enqueueAlbumItemDownloadJob?.cancel()
    _enqueueAlbumItemDownloadJob = viewModelScope.launch {
      Logger.debug(TAG) { "downloadImage() fullImageUrl: ${chanPostImage.imageUrl}, showOptions: ${showOptions}" }

      if (chanPostImage.isInlined || chanPostImage.hidden) {
        // Do not download inlined files via the Album downloads (because they often
        // fail with SSL exceptions) and we can't really trust those files.
        // Also don't download filter hidden items
        Logger.error(TAG) { "downloadImage() skipping " }
        return@launch
      }

      val simpleSaveableMediaInfo = ImageSaverV2.SimpleSaveableMediaInfo.fromChanPostImage(chanPostImage)
      if (simpleSaveableMediaInfo == null) {
        Logger.debug(TAG) { "downloadImage() simpleSaveableMediaInfoList is null" }
        snackbarManager.errorToast(R.string.album_download_no_suitable_images)
        return@launch
      }

      var imageSaverV2Options = PersistableChanState.imageSaverV2PersistedOptions.get()
      var newFilename: String? = null

      if (showOptions || imageSaverV2Options.shouldShowImageSaverOptionsController()) {
        val result = suspendCancellableCoroutine<Pair<ImageSaverV2Options, String?>?> { continuation ->
          val options = ImageSaverV2OptionsController.Options.SingleImage(
            simpleSaveableMediaInfo = simpleSaveableMediaInfo,
            onSaveClicked = { updatedImageSaverV2Options, newFilename ->
              continuation.resumeValueSafe(Pair(updatedImageSaverV2Options, newFilename))
            },
            onCanceled = {
              continuation.resumeValueSafe(null)
            }
          )

          if (!presentImageSaverOptionsController(options)) {
            continuation.cancel()
          }
        }

        if (result == null) {
          Logger.debug(TAG) { "downloadImage() updatedImageSaverV2Options is null" }
          snackbarManager.errorToast(R.string.album_download_canceled_by_user)
          return@launch
        }

        val (updatedImageSaverV2Options, updatedNewFilename) = result

        imageSaverV2Options = updatedImageSaverV2Options
        newFilename = updatedNewFilename
      }

      val downloadUniqueId = imageSaverV2.saveSuspend(
        imageSaverV2Options = imageSaverV2Options,
        simpleSaveableMediaInfo = simpleSaveableMediaInfo,
        newFilename = newFilename
      )

      if (downloadUniqueId == null) {
        Logger.debug(TAG) { "downloadImage() downloadUniqueId is null" }
        snackbarManager.errorToast(R.string.album_download_failed_to_enqueue_download)
        return@launch
      }

      updateAlbumItems(
        albumSelection = albumSelection,
        albumItems = albumItems,
        downloadingAlbumItems = downloadingAlbumItems,
        downloadUniqueId = downloadUniqueId
      )

      Logger.debug(TAG) {
        "downloadImage() Successfully enqueued a download with id downloadUniqueId: '${downloadUniqueId}'"
      }

      exitSelectionMode()
    }
  }

  fun downloadSelectedItems(
    albumSelection: AlbumSelection,
    albumItems: SnapshotStateList<AlbumItemData>,
    downloadingAlbumItems: SnapshotStateMap<Long, DownloadingAlbumItem>,
    findChanPostImage: (Long) -> ChanPostImage?,
    presentImageSaverOptionsController: (ImageSaverV2OptionsController.Options.MultipleImages) -> Boolean,
    exitSelectionMode: () -> Unit
  ) {
    _enqueueAlbumItemsDownloadJob?.cancel()
    _enqueueAlbumItemsDownloadJob = viewModelScope.launch {
      val selectedCount = albumSelection.size
      if (selectedCount <= 0) {
        Logger.debug(TAG) { "downloadSelectedItems() album selection is empty" }
        snackbarManager.errorToast(R.string.album_download_none_checked)
        return@launch
      }

      Logger.debug(TAG) { "downloadSelectedItems() selectedItemsCount: ${albumSelection.size}" }

      val simpleSaveableMediaInfoList = ArrayList<ImageSaverV2.SimpleSaveableMediaInfo>(selectedCount)

      for (albumItemId in albumSelection.selectedItems) {
        val chanPostImage = findChanPostImage(albumItemId)
        if (chanPostImage == null) {
          continue
        }

        if (chanPostImage.isInlined || chanPostImage.hidden) {
          // Do not download inlined files via the Album downloads (because they often
          // fail with SSL exceptions) and we can't really trust those files.
          // Also don't download filter hidden items
          continue
        }

        val simpleSaveableMediaInfo = ImageSaverV2.SimpleSaveableMediaInfo.fromChanPostImage(chanPostImage)
        if (simpleSaveableMediaInfo != null) {
          simpleSaveableMediaInfoList.add(simpleSaveableMediaInfo)
        }
      }

      if (simpleSaveableMediaInfoList.isEmpty()) {
        Logger.debug(TAG) { "downloadSelectedItems() simpleSaveableMediaInfoList is empty" }
        snackbarManager.errorToast(R.string.album_download_no_suitable_images)
        return@launch
      }

      Logger.debug(TAG) { "downloadSelectedItems() simpleSaveableMediaInfoList: ${simpleSaveableMediaInfoList.size}" }

      val updatedImageSaverV2Options = suspendCancellableCoroutine<ImageSaverV2Options?> { continuation ->
        val options = ImageSaverV2OptionsController.Options.MultipleImages(
          onSaveClicked = { updatedImageSaverV2Options ->
            continuation.resumeValueSafe(updatedImageSaverV2Options)
          },
          onCanceled = {
            continuation.resumeValueSafe(null)
          }
        )

        if (!presentImageSaverOptionsController(options)) {
          continuation.cancel()
        }
      }

      if (updatedImageSaverV2Options == null) {
        Logger.debug(TAG) { "downloadSelectedItems() updatedImageSaverV2Options is null" }
        snackbarManager.errorToast(R.string.album_download_canceled_by_user)
        return@launch
      }

      val downloadUniqueId = imageSaverV2.saveManySuspend(
        imageSaverV2Options = updatedImageSaverV2Options,
        simpleSaveableMediaInfoList = simpleSaveableMediaInfoList
      )

      if (downloadUniqueId == null) {
        Logger.debug(TAG) { "downloadSelectedItems() downloadUniqueId is null" }
        snackbarManager.errorToast(R.string.album_download_failed_to_enqueue_download)
        return@launch
      }

      updateAlbumItems(
        albumSelection = albumSelection,
        albumItems = albumItems,
        downloadingAlbumItems = downloadingAlbumItems,
        downloadUniqueId = downloadUniqueId
      )

      Logger.debug(TAG) {
        "downloadSelectedItems() Successfully enqueued a download with id downloadUniqueId: '${downloadUniqueId}'"
      }

      exitSelectionMode()
    }
  }

  private fun updateAlbumItems(
    albumSelection: AlbumSelection,
    albumItems: SnapshotStateList<AlbumItemData>,
    downloadingAlbumItems: SnapshotStateMap<Long, DownloadingAlbumItem>,
    downloadUniqueId: String
  ) {
    albumSelection.selectedItems.forEach { albumItemDataId ->
      val albumItemDataIndex = albumItems.indexOfFirst { albumItemData -> albumItemData.id == albumItemDataId }
      if (albumItemDataIndex < 0) {
        return@forEach
      }

      val albumItemData = albumItems[albumItemDataIndex]

      val fullImageUrl = albumItemData.fullImageUrl
      if (fullImageUrl == null) {
        return@forEach
      }

      downloadingAlbumItems.put(
        albumItemDataId,
        DownloadingAlbumItem(
          albumItemDataId = albumItemDataId,
          downloadUniqueId = downloadUniqueId,
          fullImageUrl = fullImageUrl,
          state = DownloadingAlbumItem.State.Enqueued
        )
      )

      albumItems[albumItemDataIndex] = albumItemData.copy(downloadUniqueId = downloadUniqueId)
    }
  }

  companion object {
    private const val TAG = "DownloadImagesHelper"
  }

}