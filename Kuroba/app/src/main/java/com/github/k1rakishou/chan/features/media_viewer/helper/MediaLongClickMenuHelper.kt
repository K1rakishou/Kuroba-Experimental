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
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.HeaderFloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.isNotNullNorEmpty
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
    viewableMedia: ViewableMedia
  ) {
    val mediaLongClickOptions = buildMediaLongClickOptions(viewableMedia)
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

  private fun buildMediaLongClickOptions(viewableMedia: ViewableMedia): List<FloatingListMenuItem> {
    val mediaName = viewableMedia.getMediaNameForMenuHeader()
    if (mediaName.isNullOrEmpty() || viewableMedia.mediaLocation !is MediaLocation.Remote) {
      return emptyList()
    }

    val options = mutableListOf<FloatingListMenuItem>()

    options += HeaderFloatingListMenuItem(MEDIA_LONG_CLICK_MENU_HEADER, mediaName)
    options += FloatingListMenuItem(ACTION_IMAGE_COPY_FULL_URL, getString(R.string.action_copy_image_full_url))

    if (viewableMedia.previewLocation is MediaLocation.Remote) {
      options += FloatingListMenuItem(ACTION_IMAGE_COPY_THUMBNAIL_URL, getString(R.string.action_copy_image_thumbnail_url))
    }

    if (viewableMedia.formatFullOriginalFileName().isNotNullNorEmpty()) {
      options += FloatingListMenuItem(ACTION_IMAGE_COPY_ORIGINAL_FILE_NAME, getString(R.string.action_copy_image_original_name))
    }

    if (viewableMedia.formatFullServerFileName().isNotNullNorEmpty()) {
      options += FloatingListMenuItem(ACTION_IMAGE_COPY_SERVER_FILE_NAME, getString(R.string.action_copy_image_server_name))
    }

    if (viewableMedia.viewableMediaMeta.mediaHash.isNotNullNorEmpty()) {
      options += FloatingListMenuItem(ACTION_IMAGE_COPY_MD5_HASH_HEX, getString(R.string.action_copy_image_file_hash_hex))
    }

    options += FloatingListMenuItem(ACTION_OPEN_IN_BROWSER, getString(R.string.action_open_in_browser))
    options += FloatingListMenuItem(ACTION_MEDIA_SEARCH, getString(R.string.action_media_search))

    options += FloatingListMenuItem(ACTION_SHARE_MEDIA_URL, getString(R.string.action_share_media_url))
    options += FloatingListMenuItem(ACTION_SHARE_MEDIA_CONTENT, getString(R.string.action_share_media_content))

    if (viewableMedia.canReloadMedia()) {
      options += FloatingListMenuItem(ACTION_RELOAD_MEDIA, getString(R.string.action_reload))
    }

    if (viewableMedia.canMediaBeDownloaded()) {
      options += FloatingListMenuItem(ACTION_DOWNLOAD_MEDIA_FILE_CONTENT, getString(R.string.action_download_content))
      options += FloatingListMenuItem(ACTION_DOWNLOAD_WITH_OPTIONS_MEDIA_FILE_CONTENT, getString(R.string.action_download_content_with_options))
    }

    return options
  }

  private suspend fun handleMenuItemClick(context: Context, clickedItem: FloatingListMenuItem, viewableMedia: ViewableMedia) {
    when (clickedItem.key as Int) {
      ACTION_IMAGE_COPY_FULL_URL -> {
        val remoteLocation = viewableMedia.mediaLocation as? MediaLocation.Remote
          ?: return

        AndroidUtils.setClipboardContent("Image URL", remoteLocation.url.toString())
        AppModuleAndroidUtils.showToast(context, R.string.image_url_copied_to_clipboard)
      }
      ACTION_IMAGE_COPY_THUMBNAIL_URL -> {
        val previewLocationUrl = (viewableMedia.previewLocation as? MediaLocation.Remote)?.url
          ?: return

        AndroidUtils.setClipboardContent("Thumbnail URL", previewLocationUrl.toString())
        AppModuleAndroidUtils.showToast(context, R.string.image_url_copied_to_clipboard)
      }
      ACTION_IMAGE_COPY_ORIGINAL_FILE_NAME -> {
        AndroidUtils.setClipboardContent("Original file name", viewableMedia.formatFullOriginalFileName())
        AppModuleAndroidUtils.showToast(context, R.string.image_file_name_copied_to_clipboard)
      }
      ACTION_IMAGE_COPY_SERVER_FILE_NAME -> {
        AndroidUtils.setClipboardContent("Server file name", viewableMedia.formatFullServerFileName())
        AppModuleAndroidUtils.showToast(context, R.string.image_file_name_copied_to_clipboard)
      }
      ACTION_IMAGE_COPY_MD5_HASH_HEX -> {
        AndroidUtils.setClipboardContent("File hash", viewableMedia.viewableMediaMeta.mediaHash)
        AppModuleAndroidUtils.showToast(context, R.string.image_file_hash_copied_to_clipboard)
      }
      ACTION_OPEN_IN_BROWSER -> {
        val mediaUrl = (viewableMedia.mediaLocation as? MediaLocation.Remote)?.url
          ?: return

        AppModuleAndroidUtils.openLink(mediaUrl.toString())
      }
      ACTION_MEDIA_SEARCH -> {
        showImageSearchOptions(context, viewableMedia)
      }
      ACTION_SHARE_MEDIA_URL -> {
        val mediaUrl = (viewableMedia.mediaLocation as? MediaLocation.Remote)?.url
          ?: return

        AppModuleAndroidUtils.shareLink(mediaUrl.toString())
      }
      ACTION_SHARE_MEDIA_CONTENT -> {
        shareMediaContent(viewableMedia)
      }
      ACTION_RELOAD_MEDIA -> {
        getMediaViewerAdapterFunc()?.reloadMedia(viewableMedia)
      }
      ACTION_DOWNLOAD_MEDIA_FILE_CONTENT -> {
        downloadMediaFile(context, false, viewableMedia)
      }
      ACTION_DOWNLOAD_WITH_OPTIONS_MEDIA_FILE_CONTENT -> {
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

  private suspend fun shareMediaContent(viewableMedia: ViewableMedia) {
    val remoteMediaLocation = viewableMedia.mediaLocation as? MediaLocation.Remote
      ?: return
    val downloadFileName = viewableMedia.viewableMediaMeta.formatFullServerMediaName()
      ?: return
    val mediaUrl = remoteMediaLocation.url
    val threadDescriptor = viewableMedia.viewableMediaMeta.ownerPostDescriptor?.threadDescriptor()

    imageSaverV2.downloadMediaAndShare(mediaUrl, downloadFileName, threadDescriptor)
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

    const val MEDIA_LONG_CLICK_MENU_HEADER = "media_copy_menu_header"
    const val ACTION_IMAGE_COPY_FULL_URL = 1
    const val ACTION_IMAGE_COPY_THUMBNAIL_URL = 2
    const val ACTION_IMAGE_COPY_ORIGINAL_FILE_NAME = 3
    const val ACTION_IMAGE_COPY_SERVER_FILE_NAME = 4
    const val ACTION_IMAGE_COPY_MD5_HASH_HEX = 5
    const val ACTION_OPEN_IN_BROWSER = 6
    const val ACTION_MEDIA_SEARCH = 7
    const val ACTION_SHARE_MEDIA_URL = 8
    const val ACTION_SHARE_MEDIA_CONTENT = 9
    const val ACTION_RELOAD_MEDIA = 10
    const val ACTION_DOWNLOAD_MEDIA_FILE_CONTENT = 11
    const val ACTION_DOWNLOAD_WITH_OPTIONS_MEDIA_FILE_CONTENT = 12
  }

}