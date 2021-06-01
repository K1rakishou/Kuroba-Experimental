package com.github.k1rakishou.chan.features.media_viewer.helper

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.site.ImageSearch
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2OptionsController
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerAdapter
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaView
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.persist_state.PersistableChanState
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl

class MediaLongClickMenuHelper(
  private val scope: CoroutineScope,
  private val globalWindowInsetsManager: GlobalWindowInsetsManager,
  private val imageSaverV2: ImageSaverV2,
  private val getMediaViewerAdapterFunc: () -> MediaViewerAdapter?,
  private val presentControllerFunc: (Controller) -> Unit
) {
  private val mediaOptionsHandlerExecutor = RendezvousCoroutineExecutor(scope)

  fun onDestroy() {
    mediaOptionsHandlerExecutor.stop()
  }

  fun onMediaLongClick(
    view: View,
    viewableMedia: ViewableMedia,
    mediaLongClickOptions: List<FloatingListMenuItem>
  ) {
    if (mediaLongClickOptions.isEmpty()) {
      return
    }

    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

    val floatingListMenuController = FloatingListMenuController(
      view.context,
      globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      mediaLongClickOptions,
      itemClickListener = { clickedItem ->
        mediaOptionsHandlerExecutor.post { handleMenuItemClick(view.context, clickedItem, viewableMedia) }
      }
    )

    presentControllerFunc(floatingListMenuController)
  }

  private suspend fun handleMenuItemClick(context: Context, clickedItem: FloatingListMenuItem, viewableMedia: ViewableMedia) {
    when (clickedItem.key as Int) {
      MediaView.ACTION_IMAGE_COPY_FULL_URL -> {
        val remoteLocation = viewableMedia.mediaLocation as? MediaLocation.Remote
          ?: return

        AndroidUtils.setClipboardContent("Image URL", remoteLocation.url.toString())
        AppModuleAndroidUtils.showToast(context, R.string.image_url_copied_to_clipboard)
      }
      MediaView.ACTION_IMAGE_COPY_THUMBNAIL_URL -> {
        val previewLocationUrl = (viewableMedia.previewLocation as? MediaLocation.Remote)?.url
          ?: return

        AndroidUtils.setClipboardContent("Thumbnail URL", previewLocationUrl.toString())
        AppModuleAndroidUtils.showToast(context, R.string.image_url_copied_to_clipboard)
      }
      MediaView.ACTION_IMAGE_COPY_ORIGINAL_FILE_NAME -> {
        AndroidUtils.setClipboardContent("Original file name", viewableMedia.formatFullOriginalFileName())
        AppModuleAndroidUtils.showToast(context, R.string.image_file_name_copied_to_clipboard)
      }
      MediaView.ACTION_IMAGE_COPY_SERVER_FILE_NAME -> {
        AndroidUtils.setClipboardContent("Server file name", viewableMedia.formatFullServerFileName())
        AppModuleAndroidUtils.showToast(context, R.string.image_file_name_copied_to_clipboard)
      }
      MediaView.ACTION_IMAGE_COPY_MD5_HASH_HEX -> {
        AndroidUtils.setClipboardContent("File hash HEX", viewableMedia.viewableMediaMeta.mediaHash)
        AppModuleAndroidUtils.showToast(context, R.string.image_file_hash_copied_to_clipboard)
      }
      MediaView.ACTION_OPEN_IN_BROWSER -> {
        val mediaUrl = (viewableMedia.mediaLocation as? MediaLocation.Remote)?.url
          ?: return

        AppModuleAndroidUtils.openLink(mediaUrl.toString())
      }
      MediaView.ACTION_MEDIA_SEARCH -> {
        showImageSearchOptions(context, viewableMedia)
      }
      MediaView.ACTION_SHARE_MEDIA_URL -> {
        val mediaUrl = (viewableMedia.mediaLocation as? MediaLocation.Remote)?.url
          ?: return

        AppModuleAndroidUtils.shareLink(mediaUrl.toString())
      }
      MediaView.ACTION_SHARE_MEDIA_CONTENT -> {
        shareMediaContent(context, viewableMedia)
      }
      MediaView.ACTION_DOWNLOAD_MEDIA_FILE_CONTENT -> {
        downloadMediaFile(context, false, viewableMedia)
      }
      MediaView.ACTION_DOWNLOAD_WITH_OPTIONS_MEDIA_FILE_CONTENT -> {
        downloadMediaFile(context, true, viewableMedia)
      }
    }
  }

  private fun downloadMediaFile(
    context: Context,
    showOptions: Boolean,
    viewableMedia: ViewableMedia
  ) {
    val simpleSaveableMediaInfo = ImageSaverV2.SimpleSaveableMediaInfo.fromViewableMedia(viewableMedia)
    if (simpleSaveableMediaInfo == null) {
      return
    }

    val imageSaverV2Options = PersistableChanState.imageSaverV2PersistedOptions.get()

    if (!showOptions && !imageSaverV2Options.shouldShowImageSaverOptionsController()) {
      imageSaverV2.save(imageSaverV2Options, simpleSaveableMediaInfo, null)
      getMediaViewerAdapterFunc()?.markMediaAsDownloaded(viewableMedia)
      return
    }

    val options = ImageSaverV2OptionsController.Options.SingleImage(
      simpleSaveableMediaInfo = simpleSaveableMediaInfo,
      onSaveClicked = { updatedImageSaverV2Options, newFileName ->
        imageSaverV2.save(updatedImageSaverV2Options, simpleSaveableMediaInfo, newFileName)
        getMediaViewerAdapterFunc()?.markMediaAsDownloaded(viewableMedia)
      },
      onCanceled = {}
    )

    presentControllerFunc(ImageSaverV2OptionsController(context, options))
  }

  private suspend fun shareMediaContent(context: Context, viewableMedia: ViewableMedia) {
    val remoteMediaLocation = viewableMedia.mediaLocation as MediaLocation.Remote
      ?: return
    val downloadFileName = viewableMedia.viewableMediaMeta.serverMediaName
      ?: return
    val mediaUrl = remoteMediaLocation.url

    val loadingViewController = LoadingViewController(context, true)
    presentControllerFunc(loadingViewController)

    try {
      imageSaverV2.downloadMediaAndShare(mediaUrl, downloadFileName)
    } finally {
      loadingViewController.stopPresenting()
    }
  }

  private fun showImageSearchOptions(context: Context, viewableMedia: ViewableMedia) {
    val items = ImageSearch.engines
      .map { imageSearch -> FloatingListMenuItem(imageSearch.id, imageSearch.name) }

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = items,
      itemClickListener = { item ->
        for (imageSearch in ImageSearch.engines) {
          val id = item.key as Int
          if (id == imageSearch.id) {
            val searchImageUrl = getSearchImageUrl(viewableMedia)
            if (searchImageUrl == null) {
              Logger.e(TAG, "showImageSearchOptions() searchImageUrl == null")
              break
            }

            AppModuleAndroidUtils.openLink(imageSearch.getUrl(searchImageUrl.toString()))
            break
          }
        }
      }
    )

    presentControllerFunc(floatingListMenuController)
  }

  private fun getSearchImageUrl(viewableMedia: ViewableMedia): HttpUrl? {
    if (viewableMedia is ViewableMedia.Video) {
      return (viewableMedia.previewLocation as? MediaLocation.Remote)?.url
    }

    return (viewableMedia.mediaLocation as? MediaLocation.Remote)?.url
  }

  companion object {
    private const val TAG = "MediaLongClickMenuHelper"
  }

}