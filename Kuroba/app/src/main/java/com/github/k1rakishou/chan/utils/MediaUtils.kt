package com.github.k1rakishou.chan.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.core.math.MathUtils
import androidx.core.util.Pair
import androidx.exifinterface.media.ExifInterface
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.presenter.ImageReencodingPresenter.ReencodeSettings
import com.github.k1rakishou.chan.core.presenter.ImageReencodingPresenter.ReencodeType
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.*

object MediaUtils {
  private const val TAG = "BitmapUtils"
  private const val MIN_QUALITY = 1
  private const val MAX_QUALITY = 100
  private const val MIN_REDUCE = 0
  private const val MAX_REDUCE = 100
  private const val PIXEL_DIFF = 5
  private const val TEMP_FILE_EXTENSION = ".tmp"
  private const val TEMP_FILE_NAME = "temp_file_name"
  private const val TEMP_FILE_NAME_WITH_CACHE_DIR = "cache/$TEMP_FILE_NAME"

  private val PNG_HEADER = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
  private val JPEG_HEADER = byteArrayOf(-1, -40)
  private val random = Random()

  @Throws(IOException::class)
  fun reencodeBitmapFile(
    inputBitmapFile: File,
    fixExif: Boolean,
    removeMetadata: Boolean,
    changeImageChecksum: Boolean,
    reencodeSettings: ReencodeSettings?
  ): File? {
    BackgroundUtils.ensureBackgroundThread()

    var quality = MAX_QUALITY
    var reduce = MIN_REDUCE
    var reencodeType = ReencodeType.AS_IS

    if (reencodeSettings != null) {
      quality = reencodeSettings.reencodeQuality
      reduce = reencodeSettings.reducePercent
      reencodeType = reencodeSettings.reencodeType
    }

    quality = MathUtils.clamp(quality, MIN_QUALITY, MAX_QUALITY)
    reduce = MathUtils.clamp(reduce, MIN_REDUCE, MAX_REDUCE)

    // all parameters are default - do nothing
    if (quality == MAX_QUALITY
      && reduce == MIN_REDUCE
      && reencodeType == ReencodeType.AS_IS
      && !fixExif
      && !removeMetadata
      && !changeImageChecksum
    ) {
      return inputBitmapFile
    }

    var bitmap: Bitmap? = null
    var compressFormat = getImageFormat(inputBitmapFile)

    if (reencodeType == ReencodeType.AS_JPEG) {
      compressFormat = CompressFormat.JPEG
    } else if (reencodeType == ReencodeType.AS_PNG) {
      compressFormat = CompressFormat.PNG
    }

    return try {
      val opt = BitmapFactory.Options()
      opt.inMutable = true

      bitmap = BitmapFactory.decodeFile(inputBitmapFile.absolutePath, opt)

      val matrix = Matrix()

      //slightly change one pixel of the image to change it's checksum
      if (changeImageChecksum) {
        changeBitmapChecksum(bitmap)
      }

      //scale the image down
      if (reduce != MIN_REDUCE) {
        val scale = (100f - reduce.toFloat()) / 100f
        matrix.setScale(scale, scale)
      }

      //fix exif
      if (compressFormat == CompressFormat.JPEG && fixExif) {
        val exif = ExifInterface(inputBitmapFile.absolutePath)

        val orientation = exif.getAttributeInt(
          ExifInterface.TAG_ORIENTATION,
          ExifInterface.ORIENTATION_UNDEFINED
        )

        when (orientation) {
          ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
          ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
          ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
          else -> matrix.postRotate(0f)
        }
      }

      val newBitmap = Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true
      )

      var tempFile: File? = null

      try {
        tempFile = tempFilename

        FileOutputStream(tempFile).use { output ->
          newBitmap!!.compress(
            compressFormat,
            quality,
            output
          )
        }

        tempFile
      } catch (error: Throwable) {
        if (tempFile != null) {
          if (!tempFile.delete()) {
            Logger.w(TAG, "Could not delete temp image file: " + tempFile.absolutePath)
          }
        }
        throw error
      } finally {
        if (newBitmap != null && !newBitmap.isRecycled) {
          newBitmap.recycle()
        }
      }
    } finally {
      if (bitmap != null && !bitmap.isRecycled) {
        bitmap.recycle()
      }
    }
  }

  @get:Throws(IOException::class)
  private val tempFilename: File
    private get() {
      val outputDir = AndroidUtils.getAppContext().cacheDir
      deleteOldTempFiles(outputDir.listFiles())
      return File.createTempFile(TEMP_FILE_NAME, TEMP_FILE_EXTENSION, outputDir)
    }

  private fun deleteOldTempFiles(files: Array<File>?) {
    if (files == null || files.isEmpty()) {
      return
    }

    for (file in files) {
      if (file.absolutePath.contains(TEMP_FILE_NAME_WITH_CACHE_DIR)) {
        if (!file.delete()) {
          Logger.w(TAG, "Could not delete old temp image file: " + file.absolutePath)
        }
      }
    }
  }

  private fun changeBitmapChecksum(bitmap: Bitmap) {
    val randomX = Math.abs(random.nextInt()) % bitmap.width
    val randomY = Math.abs(random.nextInt()) % bitmap.height

    // one pixel is enough to change the checksum of an image
    var pixel = bitmap.getPixel(randomX, randomY)

    // NOTE: apparently when re-encoding jpegs, changing a pixel by 1 is sometimes not enough
    // due to the jpeg's compression algorithm (it may even out this pixel with surrounding
    // pixels like it wasn't changed at all) so we have to increase the difference a little bit
    if (pixel - PIXEL_DIFF >= 0) {
      pixel -= PIXEL_DIFF
    } else {
      pixel += PIXEL_DIFF
    }

    bitmap.setPixel(randomX, randomY, pixel)
  }

  fun isFileSupportedForReencoding(file: File): Boolean {
    val imageFormat = getImageFormat(file)
    return imageFormat == CompressFormat.JPEG || imageFormat == CompressFormat.PNG
  }

  fun getImageFormat(file: File): CompressFormat? {
    try {
      RandomAccessFile(file, "r").use { raf ->
        val header = ByteArray(16)
        raf.read(header)

        run {
          var isPngHeader = true
          val size = Math.min(PNG_HEADER.size, header.size)
          for (i in 0 until size) {
            if (header[i] != PNG_HEADER[i]) {
              isPngHeader = false
              break
            }
          }
          if (isPngHeader) {
            return CompressFormat.PNG
          }
        }

        var isJpegHeader = true
        val size = Math.min(JPEG_HEADER.size, header.size)

        for (i in 0 until size) {
          if (header[i] != JPEG_HEADER[i]) {
            isJpegHeader = false
            break
          }
        }

        if (isJpegHeader) {
          return CompressFormat.JPEG
        } else {
          return null
        }
      }
    } catch (e: Exception) {
      return null
    }
  }

  /**
   * Gets the dimensions of the specified image file
   *
   * @param file image
   * @return a pair of dimensions, in WIDTH then HEIGHT order; -1, -1 if not determinable
   */
  fun getImageDims(file: File): Pair<Int, Int> {
    return try {
      val bitmap = BitmapFactory.decodeStream(FileInputStream(file))

      try {
        Pair(bitmap.width, bitmap.height)
      } finally {
        if (bitmap != null && !bitmap.isRecycled) {
          bitmap.recycle()
        }
      }
    } catch (e: Exception) {
      Pair(-1, -1)
    }
  }

  fun decodeFileMimeType(file: File): String? {
    BackgroundUtils.ensureBackgroundThread()

    try {
      val metadataRetriever = MediaMetadataRetriever()
      metadataRetriever.setDataSource(file.absolutePath)
      return metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
    } catch (ignored: Throwable) {
      return null
    }
  }

  fun decodeVideoFilePreviewImage(
    context: Context,
    file: File,
    maxWidth: Int,
    maxHeight: Int,
    addAudioIcon: Boolean
  ): BitmapDrawable? {
    BackgroundUtils.ensureBackgroundThread()
    var result: Bitmap? = null

    try {
      val metadataRetriever = MediaMetadataRetriever()
      metadataRetriever.setDataSource(file.absolutePath)
      val frameBitmap = metadataRetriever.frameAtTime

      val audioMetaResult =
        metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
      val hasAudio = "yes" == audioMetaResult

      if (hasAudio && frameBitmap != null && addAudioIcon) {
        val audioIconBitmap = AppCompatResources.getDrawable(context, R.drawable.ic_volume_up_white_24dp)
          ?.toBitmap(maxWidth, maxHeight)
          ?: return null

        val audioBitmap = Bitmap.createScaledBitmap(
          audioIconBitmap,
          audioIconBitmap.width / 3,
          audioIconBitmap.height / 3,
          true
        )

        val newWidth = Math.min(frameBitmap.width, maxWidth)
        val newHeight = Math.min(frameBitmap.height, maxHeight)

        try {
          result = Bitmap.createBitmap(
            newWidth,
            newHeight,
            frameBitmap.config
          )

          val canvas = Canvas(result)
          canvas.drawBitmap(frameBitmap, Matrix(), null)

          canvas.drawBitmap(
            audioBitmap,
            (newWidth - audioBitmap.width).toFloat() / 2f,
            (newHeight - audioBitmap.height).toFloat() / 2f,
            null
          )

          println()

        } finally {
          if (audioBitmap != null && !audioBitmap.isRecycled) {
            audioBitmap.recycle()
          }
          if (frameBitmap != null && !frameBitmap.isRecycled) {
            frameBitmap.recycle()
          }
        }
      } else {
        result = frameBitmap
      }
    } catch (error: Exception) {
      val errorMsg = error.errorMessageOrClassName()
      Logger.e(TAG, "decodeVideoFilePreviewImage() error: $errorMsg")
    }

    check(!(result != null && result.isRecycled)) { "Result bitmap is already recycled!" }

    return result?.let { BitmapDrawable(context.resources, it) }
  }

  fun bitmapToDrawable(bitmap: Bitmap?): BitmapDrawable {
    return BitmapDrawable(AppModuleAndroidUtils.getRes(), bitmap)
  }
}