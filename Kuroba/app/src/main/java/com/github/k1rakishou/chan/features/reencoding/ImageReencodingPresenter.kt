package com.github.k1rakishou.chan.features.reencoding

import android.content.Context
import android.graphics.Bitmap
import androidx.core.util.Pair
import coil.size.Scale
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.MediaUtils
import com.github.k1rakishou.chan.utils.MediaUtils.getImageDims
import com.github.k1rakishou.chan.utils.MediaUtils.getImageFormat
import com.github.k1rakishou.common.AndroidUtils.getDisplaySize
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.v2.KurobaSettings
import com.google.gson.Gson
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

class ImageReencodingPresenter(
  context: Context,
  callback: ImageReencodingPresenterCallback,
  fileUuid: UUID,
  chanDescriptor: ChanDescriptor,
  lastOptions: ImageOptions?
) {
  private val context: Context

  @Inject
  lateinit var kurobaSettings: KurobaSettings
  @Inject
  lateinit var replyManager: ReplyManager
  @Inject
  lateinit var gson: Gson
  @Inject
  lateinit var imageLoaderDeprecated: ImageLoaderDeprecated

  private val callback: ImageReencodingPresenterCallback
  private val chanDescriptor: ChanDescriptor
  private var imageOptions: ImageOptions

  private var scope = KurobaCoroutineScope()
  private var bitmapReencodeJob: Job? = null

  val fileUuid: UUID

  val imageFormat: Bitmap.CompressFormat?
    get() {
      val replyFile = replyManager.getReplyFileByFileUuid(fileUuid).valueOrNull()
        ?: return null

      return getImageFormat(replyFile.fileOnDisk)
    }

  val imageDims: Pair<Int, Int>?
    get() {
      val replyFile = replyManager.getReplyFileByFileUuid(fileUuid).valueOrNull()
        ?: return null

      return getImageDims(replyFile.fileOnDisk)
    }

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    this.context = context
    this.chanDescriptor = chanDescriptor
    this.fileUuid = fileUuid
    this.callback = callback

    imageOptions = lastOptions ?: ImageOptions()
  }

  fun onDestroy() {
    scope.cancelChildren()
    bitmapReencodeJob?.cancel()
    bitmapReencodeJob = null
  }

  fun loadImagePreview() {
    val displaySize = getDisplaySize(context)

    val imageSize = ImageLoaderDeprecated.ImageSize.FixedImageSize(
      width = displaySize.x,
      height = displaySize.y,
    )

    imageLoaderDeprecated.loadRelyFilePreviewFromDisk(
      context = context,
      fileUuid = fileUuid,
      imageSize = imageSize,
      scale = Scale.FIT,
      transformations = emptyList()
    ) { bitmapDrawable -> callback.showImagePreview(bitmapDrawable.bitmap) }
  }

  fun getCurrentFileName(): String {
    return replyManager.getReplyFileByFileUuid(fileUuid)
      .valueOrNull()
      ?.getReplyFileMeta()
      ?.valueOrNull()
      ?.fileName
      ?: ""
  }

  fun getGenerateNewFileName(): String {
    val oldFileName = getCurrentFileName()
    return replyManager.getNewImageName(oldFileName)
  }

  fun hasAttachedFile(): Boolean {
    return replyManager.getReplyFileByFileUuid(fileUuid).valueOrNull() != null
  }

  fun setReencode(reencodeSettings: ReencodeSettings?) {
    if (reencodeSettings != null) {
      imageOptions = imageOptions.copy(reencodeSettings = reencodeSettings)
    } else {
      imageOptions = imageOptions.copy(reencodeSettings = null)
    }
  }

  fun fixExif(isChecked: Boolean) {
    imageOptions = imageOptions.copy(fixExif = isChecked)
  }

  fun removeMetadata(isChecked: Boolean) {
    imageOptions = imageOptions.copy(removeMetadata = isChecked)
  }

  fun changeImageChecksum(isChecked: Boolean) {
    imageOptions = imageOptions.copy(changeImageChecksum = isChecked)
  }

  fun applyImageOptions(fileName: String?) {
    val alreadyRunning = synchronized(this) { bitmapReencodeJob != null }
    if (alreadyRunning) {
      return
    }

    val replyFile = replyManager.getReplyFileByFileUuid(fileUuid).valueOrNull()
    if (replyFile == null) {
      callback.onImageOptionsApplied(fileUuid)
      return
    }

    imageOptions = imageOptions.copy(
      newFileName = if (fileName.isNullOrEmpty()) {
        null
      } else {
        fileName
      }
    )

    kurobaSettings.application.lastImageOptions.writeAsync(gson.toJson(imageOptions))
    Logger.d(TAG, "imageOptions: [$imageOptions]")

    // all options are default - do nothing
    if (optionsDefault()) {
      callback.onImageOptionsApplied(fileUuid)
      return
    }

    // only the "remove filename" option is selected
    if (onlyRemoveFileNameSelected()) {
      updateFileName(imageOptions.newFileName)
      callback.onImageOptionsApplied(fileUuid)
      return
    }

    // one of the options that affects the image is selected (reencode/remove metadata/change checksum)
    val newJob = scope.launch(Dispatchers.IO) {
      try {
        callback.disableOrEnableButtons(false)

        if (imageOptions.newFileName != null) {
          updateFileName(imageOptions.newFileName)
        }

        val reencodedFile = MediaUtils.reencodeBitmapFile(
          inputBitmapFile = replyFile.fileOnDisk,
          fixExif = imageOptions.fixExif,
          removeMetadata = imageOptions.removeMetadata,
          changeImageChecksum = imageOptions.changeImageChecksum,
          reencodeSettings = imageOptions.reencodeSettings
        )

        if (reencodedFile == null) {
          AppModuleAndroidUtils.showToast(
            context,
            AppModuleAndroidUtils.getString(R.string.could_not_reencode_image)
          )

          callback.onImageOptionsApplied(fileUuid)
          return@launch
        }

        replyFile.overwriteFileOnDisk(reencodedFile)
          .unwrap()

        imageLoaderDeprecated.calculateFilePreviewAndStoreOnDisk(
          context.applicationContext,
          fileUuid
        )

        callback.onImageOptionsApplied(fileUuid)
      } catch (error: Throwable) {
        Logger.e(TAG, "Error while trying to re-encode bitmap file", error)
        callback.disableOrEnableButtons(true)

        AppModuleAndroidUtils.showToast(
          context,
          AppModuleAndroidUtils.getString(R.string.could_not_apply_image_options, error.message)
        )

        callback.onImageOptionsApplied(fileUuid)
      } finally {
        callback.disableOrEnableButtons(true)
        synchronized(this) { bitmapReencodeJob = null }
      }
    }

    synchronized(this) { bitmapReencodeJob = newJob }
  }

  private fun updateFileName(newFileName: String? = null) {
    val replyFile = replyManager.getReplyFileByFileUuid(fileUuid).valueOrNull()
      ?: return

    val oldFileName = replyFile.getReplyFileMeta().valueOrNull()?.fileName
      ?: return

    val fileName = newFileName
      ?: replyManager.getNewImageName(oldFileName, ReencodeType.AS_IS)

    replyManager.updateFileName(fileUuid, fileName, false)
      .onError { error -> Logger.e(TAG, "updateFileName() old='$oldFileName', new='$newFileName' error", error) }
      .ignore()
  }

  private fun onlyRemoveFileNameSelected(): Boolean {
    return imageOptions.newFileName != null
      && !imageOptions.fixExif
      && !imageOptions.removeMetadata
      && !imageOptions.changeImageChecksum
      && imageOptions.reencodeSettings == null
  }

  private fun optionsDefault(): Boolean {
    return imageOptions.newFileName == null
      && !imageOptions.fixExif
      && !imageOptions.removeMetadata
      && !imageOptions.changeImageChecksum
      && imageOptions.reencodeSettings == null
  }

  @JsonClass(generateAdapter = true)
  data class ImageOptions(
    val fixExif: Boolean = false,
    val removeMetadata: Boolean = false,
    val newFileName: String? = null,
    val changeImageChecksum: Boolean = false,
    val reencodeSettings: ReencodeSettings? = null
  )

  @JsonClass(generateAdapter = true)
  data class ReencodeSettings(
    val reencodeType: ReencodeType,
    val reencodeQuality: Int,
    val reducePercent: Int
  ) {
    val isDefault: Boolean
      get() = reencodeType == ReencodeType.AS_IS
        && reencodeQuality == 100
        && reducePercent == 0

    fun prettyPrint(currentFormat: Bitmap.CompressFormat?): String {
      var type = "Unknown"

      if (currentFormat == null) {
        Logger.e(TAG, "currentFormat == null")
        return type
      }

      type = when (reencodeType) {
        ReencodeType.AS_IS -> "As-is"
        ReencodeType.AS_PNG -> "PNG"
        ReencodeType.AS_JPEG -> "JPEG"
      }

      val isJpeg = reencodeType == ReencodeType.AS_JPEG
        || reencodeType == ReencodeType.AS_IS && currentFormat == Bitmap.CompressFormat.JPEG

      val quality = if (isJpeg) {
        "$reencodeQuality, "
      } else {
        ""
      }

      return "($type, " + quality + (100 - reducePercent) + "%)"
    }
  }

  enum class ReencodeType {
    AS_IS,
    AS_JPEG,
    AS_PNG;

    companion object {
      @JvmStatic
      fun fromInt(value: Int): ReencodeType {
        return when (value) {
          AS_IS.ordinal -> AS_IS
          AS_PNG.ordinal -> AS_PNG
          AS_JPEG.ordinal -> AS_JPEG
          else -> error("Cannot get ReencodeType from int value: $value")
        }
      }
    }
  }

  interface ImageReencodingPresenterCallback {
    fun showImagePreview(bitmap: Bitmap)
    fun disableOrEnableButtons(enabled: Boolean)
    fun onImageOptionsApplied(fileUuid: UUID)
  }

  companion object {
    private const val TAG = "ImageReencodingPresenter"
  }

}