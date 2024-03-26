package com.github.k1rakishou.chan.features.reply_attach_sound

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.BaseComposeController
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.KurobaComposeImage
import com.github.k1rakishou.chan.ui.compose.components.IconTint
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.fsaf.FileChooser
import java.io.File
import javax.inject.Inject

class CreateSoundMediaController(
  context: Context
) : BaseComposeController<CreateSoundMediaControllerViewModel>(
  context = context,
  titleStringId = R.string.create_sound_media_controller_title
) {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2

  @Inject
  lateinit var fileChooser: FileChooser

  @Inject
  lateinit var dialogFactory: DialogFactory

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun controllerVM(): CreateSoundMediaControllerViewModel {
    return requireComponentActivity().viewModelByKey<CreateSoundMediaControllerViewModel>()
  }

  @Composable
  override fun BuildContent() {
    val attachments = controllerViewModel.attachments
    val processingAttachments = controllerViewModel.processingAttachments
    val selectedFiles = controllerViewModel.selectedFiles

    val paddingValues by controllerPaddingsState

    LazyVerticalGrid(
      modifier = Modifier
          .fillMaxSize()
          .padding(4.dp),
      contentPadding = paddingValues,
      verticalArrangement = Arrangement.spacedBy(4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      columns = GridCells.Adaptive(minSize = 192.dp)
    ) {
      items(
        count = attachments.size,
        itemContent = { index ->
          val attachment = attachments[index]
          val processingAttachment = processingAttachments[attachment.fileUUID] ?: AsyncData.NotInitialized

          Attachment(
            attachment = attachment,
            canRetryOnError = selectedFiles.containsKey(attachment.fileUUID),
            processingAttachment = processingAttachment,
            createSoundMedia = { clickedAttachment, checkMediaHasSound ->
              tryToCreateSoundMedia(
                clickedAttachment = clickedAttachment,
                checkMediaHasSound = checkMediaHasSound
              )
            }
          )
        }
      )
    }
  }

  @Composable
  private fun Attachment(
    attachment: CreateSoundMediaControllerViewModel.Attachment,
    canRetryOnError: Boolean,
    processingAttachment: AsyncData<Unit>,
    createSoundMedia: (CreateSoundMediaControllerViewModel.Attachment, checkMediaHasSound: Boolean) -> Unit
  ) {
    val request = remember(attachment.imagePath) {
      ImageLoaderRequest(
        data = ImageLoaderRequestData.File(
          file = File(attachment.imagePath)
        )
      )
    }

    Box(
      modifier = Modifier
          .fillMaxWidth()
          .height(256.dp)
          .kurobaClickable(
              enabled = processingAttachment !is AsyncData.Loading,
              bounded = true,
              onClick = { createSoundMedia(attachment, true) }
          )
    ) {
      KurobaComposeImage(
        request = request,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        imageLoaderV2 = imageLoaderV2
      )

      KurobaComposeText(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .kurobaClickable(
                bounded = true,
                onClick = { showFullAttachmentName(attachment) }
            )
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .align(Alignment.BottomCenter),
        text = attachment.attachmentName,
        maxLines = 5,
        overflow = TextOverflow.Ellipsis,
        color = Color.White,
        fontSize = 12.ktu
      )

      AttachmentOverlay(
        processingAttachment = processingAttachment,
        canRetryOnError = canRetryOnError,
        retryCreatingSoundMedia = { createSoundMedia(attachment, false) },
        cancelCreatingSoundMedia = {
          controllerViewModel.cancelCreatingSoundMedia(attachment)
          showToast(R.string.create_sound_media_controller_creation_canceled)
        }
      )
    }
  }

  @Composable
  private fun AttachmentOverlay(
    processingAttachment: AsyncData<Unit>,
    canRetryOnError: Boolean,
    retryCreatingSoundMedia: () -> Unit,
    cancelCreatingSoundMedia: () -> Unit,
  ) {
    val bgColor = remember { Color.Black.copy(alpha = 0.7f) }

    Box(
      modifier = Modifier
          .customClickModifier(
              processingAttachment = processingAttachment,
              retryCreatingSoundMedia = retryCreatingSoundMedia,
              cancelCreatingSoundMedia = cancelCreatingSoundMedia
          )
          .fillMaxSize()
          .drawBehind {
              val drawDimmedBackground = processingAttachment is AsyncData.Loading
                || (processingAttachment is AsyncData.Error && canRetryOnError)

              if (drawDimmedBackground) {
                  drawRect(color = bgColor)
              }
          }
    ) {
      when (processingAttachment) {
        AsyncData.NotInitialized -> {
          // no-op
        }

        AsyncData.Loading -> {
          Box(
            modifier = Modifier
                .size(42.dp)
                .align(Alignment.Center)
          ) {
            KurobaComposeProgressIndicator(
              modifier = Modifier.fillMaxSize(),
              overrideColor = Color.White
            )

            KurobaComposeIcon(
              modifier = Modifier
                  .fillMaxSize()
                  .padding(8.dp),
              drawableId = R.drawable.ic_clear_white_24dp
            )
          }
        }

        is AsyncData.Error -> {
          if (canRetryOnError) {
            KurobaComposeIcon(
              modifier = Modifier
                  .size(42.dp)
                  .align(Alignment.Center),
              drawableId = R.drawable.ic_refresh_white_24dp,
              iconTint = IconTint.TintWithColor(bgColor)
            )
          }
        }

        is AsyncData.Data -> {
          // no-op
        }
      }
    }
  }

  private fun Modifier.customClickModifier(
    processingAttachment: AsyncData<Unit>,
    retryCreatingSoundMedia: () -> Unit,
    cancelCreatingSoundMedia: () -> Unit,
  ): Modifier {
    return when (processingAttachment) {
      is AsyncData.Loading -> {
        this.then(
          Modifier.kurobaClickable(
            bounded = true,
            onClick = { cancelCreatingSoundMedia() }
          )
        )
      }

      is AsyncData.Error -> {
        this.then(
          Modifier.kurobaClickable(
            bounded = true,
            onClick = { retryCreatingSoundMedia() }
          )
        )
      }

      else -> this
    }
  }

  private fun tryToCreateSoundMedia(
    clickedAttachment: CreateSoundMediaControllerViewModel.Attachment,
    checkMediaHasSound: Boolean = true
  ) {
    if (checkMediaHasSound && controllerViewModel.checkMediaAlreadyHasSoundAttached(clickedAttachment)) {
      dialogFactory.createSimpleConfirmationDialog(
        context = context,
        titleTextId = R.string.create_sound_media_controller_media_already_has_sound_dialog_title,
        descriptionTextId = R.string.create_sound_media_controller_media_already_has_sound_dialog_description,
        onPositiveButtonClickListener = { tryToCreateSoundMedia(clickedAttachment, false) }
      )

      return
    }

    controllerViewModel.tryToCreateSoundMedia(
      fileChooser = fileChooser,
      clickedAttachment = clickedAttachment,
      showErrorToast = { error ->
        error.toastOnError(
          message = { error ->
            getString(R.string.create_sound_media_controller_creation_error, error.errorMessageOrClassName())
          }
        )
      },
      showSuccessToast = { attachmentName ->
        showToast(getString(R.string.create_sound_media_controller_creation_success, attachmentName))
      }
    )
  }


  private fun showFullAttachmentName(attachment: CreateSoundMediaControllerViewModel.Attachment) {
    dialogFactory.createSimpleInformationDialog(
      context = context,
      titleText = getString(R.string.create_sound_media_controller_attachment_full_file_name_dialog_title),
      descriptionText = attachment.attachmentName
    )
  }

  companion object {
    private const val TAG = "CreateSoundMediaController"
  }
}