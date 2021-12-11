package com.github.k1rakishou.chan.core.helper

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2OptionsController
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.HeaderFloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.filter.FilterType
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.persist_state.PersistableChanState
import dagger.Lazy

class ThumbnailLongtapOptionsHelper(
  private val globalWindowInsetsManager: GlobalWindowInsetsManager,
  private val imageSaverV2: Lazy<ImageSaverV2>
) {

  fun onThumbnailLongTapped(
    context: Context,
    chanDescriptor: ChanDescriptor,
    isCurrentlyInAlbum: Boolean,
    postImage: ChanPostImage,
    presentControllerFunc: (Controller) -> Unit,
    showFiltersControllerFunc: (ChanFilterMutable) -> Unit,
    openThreadFunc: (PostDescriptor) -> Unit,
    goToPostFunc: (PostDescriptor) -> Unit,
  ) {
    val fullImageName = buildString {
      append((postImage.filename ?: postImage.serverFilename))

      if (postImage.extension.isNotNullNorEmpty()) {
        append(".")
        append(postImage.extension!!)
      }
    }

    val items = mutableListOf<FloatingListMenuItem>()
    items += HeaderFloatingListMenuItem(THUMBNAIL_LONG_CLICK_MENU_HEADER, fullImageName)

    if (isCurrentlyInAlbum) {
      if (chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
        items += createMenuItem(context, OPEN_THREAD, R.string.action_open_thread)
      } else if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
        items += createMenuItem(context, GO_TO_POST, R.string.action_go_to_post)
      }
    }

    items += createMenuItem(context, IMAGE_COPY_FULL_URL, R.string.action_copy_image_full_url)
    items += createMenuItem(context, IMAGE_COPY_THUMBNAIL_URL, R.string.action_copy_image_thumbnail_url)

    if (postImage.formatFullOriginalFileName().isNotNullNorEmpty()) {
      items += createMenuItem(context, IMAGE_COPY_ORIGINAL_FILE_NAME, R.string.action_copy_image_original_name)
    }

    if (postImage.formatFullServerFileName().isNotNullNorEmpty()) {
      items += createMenuItem(context, IMAGE_COPY_SERVER_FILE_NAME, R.string.action_copy_image_server_name)
    }

    if (postImage.fileHash.isNotNullNorEmpty()) {
      items += createMenuItem(context, IMAGE_COPY_MD5_HASH_HEX, R.string.action_copy_image_file_hash_hex)

      if (!isCurrentlyInAlbum) {
        items += createMenuItem(context, FILTER_POSTS_WITH_THIS_IMAGE_HASH, R.string.action_filter_image_by_hash)
      }
    }

    items += createMenuItem(context, OPEN_IN_EXTERNAL_APP, R.string.action_open_in_browser)
    items += createMenuItem(context, SHARE_MEDIA_FILE_CONTENT, R.string.action_share_content)
    items += createMenuItem(context, DOWNLOAD_MEDIA_FILE_CONTENT, R.string.action_download_content)
    items += createMenuItem(context, DOWNLOAD_WITH_OPTIONS_MEDIA_FILE_CONTENT, R.string.action_download_content_with_options)

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = items,
      itemClickListener = { item ->
        onThumbnailOptionClicked(
          context = context,
          id = item.key as Int,
          postImage = postImage,
          presentControllerFunc = presentControllerFunc,
          showFiltersControllerFunc = showFiltersControllerFunc,
          openThreadFunc = openThreadFunc,
          goToPostFunc = goToPostFunc
        )
      }
    )

    presentControllerFunc(floatingListMenuController)
  }

  private fun onThumbnailOptionClicked(
    context: Context,
    id: Int,
    postImage: ChanPostImage,
    presentControllerFunc: (Controller) -> Unit,
    showFiltersControllerFunc: (ChanFilterMutable) -> Unit,
    openThreadFunc: (PostDescriptor) -> Unit,
    goToPostFunc: (PostDescriptor) -> Unit,
  ) {
    when (id) {
      OPEN_THREAD -> {
        openThreadFunc(postImage.ownerPostDescriptor)
      }
      GO_TO_POST -> {
        goToPostFunc(postImage.ownerPostDescriptor)
      }
      IMAGE_COPY_FULL_URL -> {
        if (postImage.imageUrl == null) {
          return
        }

        AndroidUtils.setClipboardContent("Image URL", postImage.imageUrl.toString())
        AppModuleAndroidUtils.showToast(context, R.string.image_url_copied_to_clipboard)
      }
      IMAGE_COPY_THUMBNAIL_URL -> {
        if (postImage.actualThumbnailUrl == null) {
          return
        }

        AndroidUtils.setClipboardContent("Thumbnail URL", postImage.actualThumbnailUrl.toString())
        AppModuleAndroidUtils.showToast(context, R.string.image_url_copied_to_clipboard)
      }
      IMAGE_COPY_ORIGINAL_FILE_NAME -> {
        AndroidUtils.setClipboardContent("Original file name", postImage.formatFullOriginalFileName())
        AppModuleAndroidUtils.showToast(context, R.string.image_file_name_copied_to_clipboard)
      }
      IMAGE_COPY_SERVER_FILE_NAME -> {
        AndroidUtils.setClipboardContent("Server file name", postImage.formatFullServerFileName())
        AppModuleAndroidUtils.showToast(context, R.string.image_file_name_copied_to_clipboard)
      }
      IMAGE_COPY_MD5_HASH_HEX -> {
        AndroidUtils.setClipboardContent("File hash", postImage.fileHash)
        AppModuleAndroidUtils.showToast(context, R.string.image_file_hash_copied_to_clipboard)
      }
      FILTER_POSTS_WITH_THIS_IMAGE_HASH -> {
        val filter = ChanFilterMutable()
        filter.type = FilterType.IMAGE.flag
        filter.pattern = postImage.fileHash

        showFiltersControllerFunc(filter)
      }
      OPEN_IN_EXTERNAL_APP -> {
        val imageUrl = postImage.imageUrl
          ?: return

        AppModuleAndroidUtils.openLink(imageUrl.toString())
      }
      SHARE_MEDIA_FILE_CONTENT -> {
        imageSaverV2.get().downloadMediaAndShare(postImage) { result ->
          if (result is ModularResult.Error) {
            AppModuleAndroidUtils.showToast(
              context,
              "Failed to share content, error=${result.error.errorMessageOrClassName()}",
              Toast.LENGTH_LONG
            )

            return@downloadMediaAndShare
          }
        }
      }
      DOWNLOAD_MEDIA_FILE_CONTENT -> {
        downloadMediaFile(context, false, postImage, presentControllerFunc)
      }
      DOWNLOAD_WITH_OPTIONS_MEDIA_FILE_CONTENT -> {
        downloadMediaFile(context, true, postImage, presentControllerFunc)
      }
    }
  }

  private fun downloadMediaFile(
    context: Context,
    showOptions: Boolean,
    postImage: ChanPostImage,
    presentControllerFunc: (Controller) -> Unit
  ) {
    val simpleSaveableMediaInfo = ImageSaverV2.SimpleSaveableMediaInfo.fromChanPostImage(postImage)
    if (simpleSaveableMediaInfo == null) {
      return
    }

    val imageSaverV2Options = PersistableChanState.imageSaverV2PersistedOptions.get()

    if (!showOptions && !imageSaverV2Options.shouldShowImageSaverOptionsController()) {
      imageSaverV2.get().save(imageSaverV2Options, simpleSaveableMediaInfo, null)
      return
    }

    val options = ImageSaverV2OptionsController.Options.SingleImage(
      simpleSaveableMediaInfo = simpleSaveableMediaInfo,
      onSaveClicked = { updatedImageSaverV2Options, newFileName ->
        imageSaverV2.get().save(updatedImageSaverV2Options, simpleSaveableMediaInfo, newFileName)
      },
      onCanceled = {}
    )

    val controller = ImageSaverV2OptionsController(context, options)
    presentControllerFunc(controller)
  }

  private fun createMenuItem(
    context: Context,
    menuItemId: Int,
    @StringRes stringId: Int,
    value: Any? = null
  ): FloatingListMenuItem {
    return FloatingListMenuItem(
      menuItemId,
      context.getString(stringId),
      value
    )
  }

  companion object {
    private const val IMAGE_COPY_FULL_URL = 1000
    private const val IMAGE_COPY_THUMBNAIL_URL = 1001
    private const val SHARE_MEDIA_FILE_CONTENT = 1002
    private const val DOWNLOAD_MEDIA_FILE_CONTENT = 1003
    private const val DOWNLOAD_WITH_OPTIONS_MEDIA_FILE_CONTENT = 1004
    private const val IMAGE_COPY_ORIGINAL_FILE_NAME = 1005
    private const val IMAGE_COPY_SERVER_FILE_NAME = 1006
    private const val IMAGE_COPY_MD5_HASH_HEX = 1007
    private const val FILTER_POSTS_WITH_THIS_IMAGE_HASH = 1008
    private const val OPEN_IN_EXTERNAL_APP = 1009
    private const val OPEN_THREAD = 1010
    private const val GO_TO_POST = 1011

    private const val THUMBNAIL_LONG_CLICK_MENU_HEADER = "thumbnail_copy_menu_header"
  }

}