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
import coil.size.Dimension
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.image.InputFile
import com.github.k1rakishou.chan.features.reencoding.ImageReencodingPresenter.ReencodeSettings
import com.github.k1rakishou.chan.features.reencoding.ImageReencodingPresenter.ReencodeType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isCoroutineCancellationException
import com.github.k1rakishou.common.rethrowCancellationException
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.*
import kotlin.math.abs
import kotlin.math.min

object MediaUtils {
  private const val TAG = "MediaUtils"
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
  private val WEBP_HEADER = arrayOf(
    byteArrayOf(0x52, 0x49, 0x46, 0x46),
    byteArrayOf(0x57, 0x45, 0x42, 0x50)
  )

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
      return null
    }

    var bitmap: Bitmap? = null

    try {
      var compressFormat = getImageFormat(inputBitmapFile)
        ?: return null

      if (reencodeType == ReencodeType.AS_JPEG) {
        compressFormat = CompressFormat.JPEG
      } else if (reencodeType == ReencodeType.AS_PNG) {
        compressFormat = CompressFormat.PNG
      }

      val opt = BitmapFactory.Options()
      opt.inMutable = true

      bitmap = BitmapFactory.decodeFile(inputBitmapFile.absolutePath, opt)
      if (bitmap == null) {
        return null
      }

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
        tempFile = this.tempFile

        FileOutputStream(tempFile).use { output ->
          newBitmap.compress(
            compressFormat,
            quality,
            output
          )
        }

        return tempFile
      } catch (error: Throwable) {
        if (tempFile != null) {
          if (!tempFile.delete()) {
            Logger.w(TAG, "Could not delete temp image file: " + tempFile.absolutePath)
          }
        }

        throw error
      } finally {
        if (!newBitmap.isRecycled) {
          newBitmap.recycle()
        }
      }
    } catch (error: Throwable) {
      Logger.error(TAG, error) { "reencodeBitmapFile() unhandled error" }
      return null
    } finally {
      if (bitmap != null && !bitmap.isRecycled) {
        bitmap.recycle()
      }
    }
  }

  @get:Throws(IOException::class)
  private val tempFile: File
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
    val randomX = abs(random.nextInt()) % bitmap.width
    val randomY = abs(random.nextInt()) % bitmap.height

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

    return imageFormat == CompressFormat.JPEG
      || imageFormat == CompressFormat.PNG
      || imageFormat == CompressFormat.WEBP
  }

  fun getImageFormat(file: File): CompressFormat? {
    try {
      RandomAccessFile(file, "r").use { raf ->
        val header = ByteArray(16)
        raf.read(header)

        if (isPngHeader(header)) {
          return CompressFormat.PNG
        }

        if (isJpegHeader(header)) {
          return CompressFormat.JPEG
        }

        if (isWebpHeader(header)) {
          return CompressFormat.WEBP
        }

        return null
      }
    } catch (e: Exception) {
      return null
    }
  }

  private fun isWebpHeader(header: ByteArray): Boolean {
    if (!header.sliceArray(0..3).contentEquals(WEBP_HEADER[0])) {
      return false
    }

    if (!header.sliceArray(8..11).contentEquals(WEBP_HEADER[1])) {
      return false
    }

    return true
  }

  private fun isJpegHeader(header: ByteArray): Boolean {
    var isJpegHeader = true
    val size = min(JPEG_HEADER.size, header.size)

    for (i in 0 until size) {
      if (header[i] != JPEG_HEADER[i]) {
        isJpegHeader = false
        break
      }
    }

    if (isJpegHeader) {
      return true
    }

    return false
  }

  private fun isPngHeader(header: ByteArray): Boolean {
    var isPngHeader = true
    val size = min(PNG_HEADER.size, header.size)

    for (i in 0 until size) {
      if (header[i] != PNG_HEADER[i]) {
        isPngHeader = false
        break
      }
    }

    if (isPngHeader) {
      return true
    }

    return false
  }

  /**
   * Gets the dimensions of the specified image file
   *
   * @param file image
   * @return a pair of dimensions, in WIDTH then HEIGHT order; -1, -1 if not determinable
   */
  fun getImageDims(file: File): Pair<Int, Int>? {
    try {
      val bitmap = FileInputStream(file)
        .use { fis -> BitmapFactory.decodeStream(fis) }

      try {
        return Pair(bitmap.width, bitmap.height)
      } finally {
        if (bitmap != null && !bitmap.isRecycled) {
          bitmap.recycle()
        }
      }
    } catch (e: Exception) {
      return null
    }
  }

  suspend fun decodeFileMimeTypeInterruptible(inputFile: InputFile): String? {
    BackgroundUtils.ensureBackgroundThread()

    try {
      return runInterruptible {
        MediaMetadataRetriever().use { metadataRetriever ->
          when (inputFile) {
            is InputFile.FileUri -> metadataRetriever.setDataSource(
              inputFile.applicationContext,
              inputFile.uri
            )
            is InputFile.JavaFile -> metadataRetriever.setDataSource(
              inputFile.file.absolutePath
            )
          }

          return@runInterruptible metadataRetriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_MIMETYPE
          )
        }
      }
    } catch (exception: Throwable) {
      if (exception.isCoroutineCancellationException()) {
        throw exception
      }

      return null
    }
  }

  suspend fun decodeVideoFilePreviewImageInterruptible(
    context: Context,
    inputFile: InputFile,
    maxWidth: Dimension,
    maxHeight: Dimension,
    addAudioIcon: Boolean
  ): BitmapDrawable? {
    return runInterruptible {
      return@runInterruptible decodeVideoFilePreviewImage(
        context = context,
        inputFile = inputFile,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        addAudioIcon = addAudioIcon
      )
    }
  }

  private fun decodeVideoFilePreviewImage(
    context: Context,
    inputFile: InputFile,
    maxWidth: Dimension,
    maxHeight: Dimension,
    addAudioIcon: Boolean
  ): BitmapDrawable? {
    BackgroundUtils.ensureBackgroundThread()
    var result: Bitmap? = null
    val metadataRetriever = MediaMetadataRetriever()

    try {
      when (inputFile) {
        is InputFile.FileUri -> metadataRetriever.setDataSource(inputFile.applicationContext, inputFile.uri)
        is InputFile.JavaFile -> metadataRetriever.setDataSource(inputFile.file.absolutePath)
      }

      val oneHundredMs = 100 * 1000L

      // MediaMetadataRetriever is an absolute trash and apparently uses global locks so all
      // getFrameAtTime() are processed sequentially (doesn't matter how many threads is used).
      // This is especially bad for WEBMs since the average execution time of getFrameAtTime()
      // for one WEBM is 5-10 seconds.
      val frameBitmap = metadataRetriever.getFrameAtTime(
        oneHundredMs,
        MediaMetadataRetriever.OPTION_CLOSEST
      )

      val audioMetaResult =
        metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
      val hasAudio = "yes" == audioMetaResult

      if (hasAudio && frameBitmap != null && addAudioIcon) {
        var newWidth = min(frameBitmap.width, maxWidth.asPixelsOr(frameBitmap.width))
        if (newWidth <= 0) {
          newWidth = frameBitmap.width
        }

        var newHeight = min(frameBitmap.height, maxHeight.asPixelsOr(frameBitmap.height))
        if (newHeight <= 0) {
          newHeight = frameBitmap.height
        }

        val audioIconBitmapSize = ((Math.min(newWidth, newHeight)) / 4).coerceAtLeast(dp(32f))

        val audioIconBitmap = AppCompatResources.getDrawable(
          context,
          R.drawable.ic_volume_up_white_24dp
        )
          ?.toBitmap(audioIconBitmapSize, audioIconBitmapSize)
          ?: return null

        result = Bitmap.createBitmap(newWidth, newHeight, frameBitmap.config)

        val canvas = Canvas(result)
        canvas.drawBitmap(frameBitmap, Matrix(), null)

        canvas.drawBitmap(
          audioIconBitmap,
          (newWidth - audioIconBitmapSize).toFloat() / 2f,
          (newHeight - audioIconBitmapSize).toFloat() / 2f,
          null
        )
      } else {
        result = frameBitmap
      }
    } catch (error: Exception) {
      error.rethrowCancellationException()

      val errorMsg = error.errorMessageOrClassName()
      Logger.e(TAG, "decodeVideoFilePreviewImage() error: $errorMsg")
    } finally {
      metadataRetriever.release()
    }

    return result?.let { BitmapDrawable(context.resources, it) }
  }

  fun bitmapToDrawable(bitmap: Bitmap?): BitmapDrawable {
    return BitmapDrawable(AppModuleAndroidUtils.getRes(), bitmap)
  }

  // For testing
  fun Canvas.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    this.drawBitmap(bitmap, 0f, 0f, null)

    return bitmap
  }

}