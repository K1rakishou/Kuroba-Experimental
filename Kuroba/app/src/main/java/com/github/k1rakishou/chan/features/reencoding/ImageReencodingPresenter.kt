/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.features.reencoding

import android.content.Context
import android.graphics.Bitmap
import androidx.core.util.Pair
import coil.size.Scale
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.MediaUtils
import com.github.k1rakishou.chan.utils.MediaUtils.getImageDims
import com.github.k1rakishou.chan.utils.MediaUtils.getImageFormat
import com.github.k1rakishou.common.AndroidUtils.getDisplaySize
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.*
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
  lateinit var replyManager: ReplyManager
  @Inject
  lateinit var gson: Gson
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2

  private val callback: ImageReencodingPresenterCallback
  private val chanDescriptor: ChanDescriptor
  private val imageOptions: ImageOptions

  private var scope = KurobaCoroutineScope()
  private var bitmapReencodeJob: Job? = null

  val fileUuid: UUID

  val imageFormat: Bitmap.CompressFormat?
    get() {
      val replyFile = replyManager.getReplyFileByFileUuid(fileUuid).valueOrNull()

      if (replyFile == null) {
        return null
      }

      return getImageFormat(replyFile.fileOnDisk)
    }

  val imageDims: Pair<Int, Int>?
    get() {
      val replyFile = replyManager.getReplyFileByFileUuid(fileUuid).valueOrNull()

      if (replyFile == null) {
        return null
      }

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

    val imageSize = ImageLoaderV2.ImageSize.FixedImageSize(
      width = displaySize.x,
      height = displaySize.y,
    )

    imageLoaderV2.loadRelyFilePreviewFromDisk(
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
      imageOptions.reencodeSettings = reencodeSettings
    } else {
      imageOptions.reencodeSettings = null
    }
  }

  fun fixExif(isChecked: Boolean) {
    imageOptions.fixExif = isChecked
  }

  fun removeMetadata(isChecked: Boolean) {
    imageOptions.removeMetadata = isChecked
  }

  fun changeImageChecksum(isChecked: Boolean) {
    imageOptions.changeImageChecksum = isChecked
  }

  fun applyImageOptions(fileName: String) {
    val alreadyRunning = synchronized(this) { bitmapReencodeJob != null }
    if (alreadyRunning) {
      return
    }

    val replyFile = replyManager.getReplyFileByFileUuid(fileUuid).valueOrNull()
    if (replyFile == null) {
      callback.onImageOptionsApplied()
      return
    }

    imageOptions.newFileName = if (fileName.isEmpty()) {
      null
    } else {
      fileName
    }

    ChanSettings.lastImageOptions.set(gson.toJson(imageOptions))
    Logger.d(TAG, "imageOptions: [$imageOptions]")

    // all options are default - do nothing
    if (optionsDefault()) {
      callback.onImageOptionsApplied()
      return
    }

    // only the "remove filename" option is selected
    if (onlyRemoveFileNameSelected()) {
      updateFileName(imageOptions.newFileName)
      callback.onImageOptionsApplied()
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
          replyFile.fileOnDisk,
          imageOptions.fixExif,
          imageOptions.removeMetadata,
          imageOptions.changeImageChecksum,
          imageOptions.reencodeSettings
        )

        if (reencodedFile == null) {
          AppModuleAndroidUtils.showToast(
            context,
            AppModuleAndroidUtils.getString(R.string.could_not_reencode_image)
          )

          callback.onImageOptionsApplied()
          return@launch
        }

        replyFile.overwriteFileOnDisk(reencodedFile)
          .unwrap()

        imageLoaderV2.calculateFilePreviewAndStoreOnDisk(
          context.applicationContext,
          fileUuid
        )

        callback.onImageOptionsApplied()
      } catch (error: Throwable) {
        Logger.e(TAG, "Error while trying to re-encode bitmap file", error)
        callback.disableOrEnableButtons(true)

        AppModuleAndroidUtils.showToast(
          context,
          AppModuleAndroidUtils.getString(R.string.could_not_apply_image_options, error.message)
        )

        callback.onImageOptionsApplied()
      } finally {
        callback.disableOrEnableButtons(true)
        synchronized(this) { bitmapReencodeJob = null }
      }
    }

    synchronized(this) { bitmapReencodeJob = newJob }
  }

  private fun updateFileName(newFileName: String? = null) {
    val replyFile = replyManager.getReplyFileByFileUuid(fileUuid).valueOrNull()
    if (replyFile != null) {
      val oldFileName = replyFile.getReplyFileMeta().valueOrNull()?.fileName
      if (oldFileName != null) {
        val fileName = newFileName
          ?: replyManager.getNewImageName(oldFileName, ReencodeType.AS_IS)

        replyManager.updateFileName(fileUuid, fileName, false)
          .onError { error ->
            Logger.e(TAG, "updateFileName() old='$oldFileName', new='$newFileName' error", error)
          }.ignore()
      }
    }
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

  @DoNotStrip
  class ImageOptions {
    var fixExif = false
    var removeMetadata = false
    var newFileName: String? = null
    var changeImageChecksum = false
    var reencodeSettings: ReencodeSettings? = null

    override fun toString(): String {
      val reencodeSettingsString = if (reencodeSettings != null) {
        reencodeSettings.toString()
      } else {
        "null"
      }

      return "fixExif='$fixExif', removeMetadata='$removeMetadata', " +
        "newFileName='$newFileName', changeImageChecksum='$changeImageChecksum', " +
        "reencodeSettings='$reencodeSettingsString'"
    }
  }

  class ReencodeSettings(
    var reencodeType: ReencodeType,
    var reencodeQuality: Int,
    var reducePercent: Int
  ) {

    val isDefault: Boolean
      get() = reencodeType == ReencodeType.AS_IS
        && reencodeQuality == 100
        && reducePercent == 0

    override fun toString(): String {
      return "reencodeType='$reencodeType', reencodeQuality='$reencodeQuality', " +
        "reducePercent='$reducePercent'"
    }

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
          else -> throw RuntimeException("Cannot get ReencodeType from int value: $value")
        }
      }
    }
  }

  interface ImageReencodingPresenterCallback {
    fun showImagePreview(bitmap: Bitmap)
    fun disableOrEnableButtons(enabled: Boolean)
    fun onImageOptionsApplied()
  }

  companion object {
    private const val TAG = "ImageReencodingPresenter"
  }

}