package com.github.k1rakishou.chan.features.reply.data

import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.exifinterface.media.ExifInterface
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.image.InputFile
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.utils.HashingUtil
import com.github.k1rakishou.chan.utils.MediaUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.util.ChanPostUtils
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ReplyLayoutReplyFileHelper(
  private val replyManagerLazy: Lazy<ReplyManager>,
  private val siteManagerLazy: Lazy<SiteManager>,
  private val boardManagerLazy: Lazy<BoardManager>,
  private val postingLimitationsInfoManagerLazy: Lazy<PostingLimitationsInfoManager>,
  private val imageLoaderV2Lazy: Lazy<ImageLoaderV2>
) {
  private val replyManager: ReplyManager
    get() = replyManagerLazy.get()
  private val siteManager: SiteManager
    get() = siteManagerLazy.get()
  private val boardManager: BoardManager
    get() = boardManagerLazy.get()
  private val postingLimitationsInfoManager: PostingLimitationsInfoManager
    get() = postingLimitationsInfoManagerLazy.get()
  private val imageLoaderV2: ImageLoaderV2
    get() = imageLoaderV2Lazy.get()

  private val imageDimensionsCache = LruCache<FileKey, ReplyFileAttachable.ImageDimensions>(1024)
  private val fileExifInfoCache = LruCache<FileKey, Set<FileExifInfoStatus>>(1024)

  suspend fun enumerate(
    chanDescriptor: ChanDescriptor
  ): ModularResult<ReplyAttachables> {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return ModularResult.value(ReplyAttachables())
    }

    return withContext(Dispatchers.IO) {
      return@withContext ModularResult.Try {
        val newAttachFiles = mutableListOf<ReplyFileAttachable>()
        val maxAllowedFilesPerPost = getMaxAllowedFilesPerPost(chanDescriptor)
        val fileFileSizeMap = mutableMapOf<String, Long>()

        var attachableCounter = 0

        val replyFiles = replyManager.mapOrderedNotNull { _, replyFile ->
          val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
            Logger.e(TAG, "getReplyFileMeta() error", error)
            return@mapOrderedNotNull null
          }

          if (replyFileMeta.isTaken()) {
            return@mapOrderedNotNull null
          }

          ++attachableCounter

          if (attachableCounter > MAX_VISIBLE_ATTACHABLES_COUNT) {
            return@mapOrderedNotNull null
          }

          fileFileSizeMap[replyFile.fileOnDisk.absolutePath] = replyFile.fileOnDisk.length()
          return@mapOrderedNotNull replyFile
        }

        if (replyFiles.isNotEmpty()) {
          val maxAllowedTotalFilesSizePerPost = getTotalFileSizeSumPerPost(chanDescriptor) ?: -1
          val totalFileSizeSum = fileFileSizeMap.values.sum()
          val boardSupportsSpoilers = boardSupportsSpoilers(chanDescriptor)

          val replyFileAttachables = replyFiles.map { replyFile ->
            val replyFileMeta = replyFile.getReplyFileMeta().unwrap()

            val isSelected = replyManager.isSelected(replyFileMeta.fileUuid).unwrap()
            val selectedFilesCount = replyManager.selectedFilesCount().unwrap()
            val fileExifStatus = getFileExifInfoStatus(replyFile.fileOnDisk)
            val exceedsMaxFileSize = fileExceedsMaxFileSize(replyFile, replyFileMeta, chanDescriptor)
            val markedAsSpoilerOnNonSpoilerBoard = isSelected && replyFileMeta.spoiler && !boardSupportsSpoilers

            val totalFileSizeExceeded = if (maxAllowedTotalFilesSizePerPost <= 0) {
              false
            } else {
              totalFileSizeSum > maxAllowedTotalFilesSizePerPost
            }

            val spoilerInfo = if (!boardSupportsSpoilers && !replyFileMeta.spoiler) {
              null
            } else {
              ReplyFileAttachable.SpoilerInfo(
                replyFileMeta.spoiler,
                boardSupportsSpoilers
              )
            }

            val imageDimensions = getImageDimensions(replyFile)

            return@map ReplyFileAttachable(
              fileUuid = replyFileMeta.fileUuid,
              fileName = replyFileMeta.fileName,
              spoilerInfo = spoilerInfo,
              selected = isSelected,
              fileSize = fileFileSizeMap[replyFile.fileOnDisk.absolutePath]!!,
              imageDimensions = imageDimensions,
              attachAdditionalInfo = ReplyFileAttachable.AttachAdditionalInfo(
                fileExifStatus = fileExifStatus,
                totalFileSizeExceeded = totalFileSizeExceeded,
                fileMaxSizeExceeded = exceedsMaxFileSize,
                markedAsSpoilerOnNonSpoilerBoard = markedAsSpoilerOnNonSpoilerBoard
              ),
              maxAttachedFilesCountExceeded = when {
                maxAllowedFilesPerPost == null -> false
                selectedFilesCount < maxAllowedFilesPerPost -> false
                selectedFilesCount == maxAllowedFilesPerPost -> !isSelected
                else -> true
              }
            )
          }

          newAttachFiles.addAll(replyFileAttachables)
        }

        return@Try ReplyAttachables(
          maxAllowedAttachablesPerPost = maxAllowedFilesPerPost ?: 0,
          attachables = newAttachFiles
        )
      }
    }
  }

  suspend fun attachableFileStatus(
    chanDescriptor: ChanDescriptor,
    chanTheme: ChanTheme,
    clickedFile: ReplyFileAttachable
  ): AnnotatedString {
    val chanBoard = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())

    val maxBoardFileSizeRaw = chanBoard?.maxFileSize ?: -1
    val maxBoardWebmSizeRaw = chanBoard?.maxWebmSize ?: -1

    val totalFileSizeSum = getTotalFileSizeSumPerPost(chanDescriptor)
    val attachAdditionalInfo = clickedFile.attachAdditionalInfo

    val fileMd5Hash = withContext(Dispatchers.Default) {
      val replyFile = replyManager.getReplyFileByFileUuid(clickedFile.fileUuid).valueOrNull()
        ?: return@withContext null

      return@withContext HashingUtil.fileHash(replyFile.fileOnDisk)
    }

    fun infoText(text: String): AnnotatedString {
      return AnnotatedString(text, SpanStyle(color = chanTheme.textColorSecondaryCompose))
    }

    fun warningText(text: String): AnnotatedString {
      return AnnotatedString(text, SpanStyle(color = chanTheme.colorWarning))
    }

    fun errorText(text: String): AnnotatedString {
      return AnnotatedString(text, SpanStyle(color = chanTheme.colorError))
    }

    fun colorizedText(text: String): AnnotatedString {
      val bgColor = chanTheme.calculateTextColor(text)
      val textColor = if (ThemeEngine.isDarkColor(bgColor)) {
        Color.White
      } else {
        Color.Black
      }

      return AnnotatedString(text, SpanStyle(color = textColor, background = bgColor))
    }

    return buildAnnotatedString {
      pushStyle(SpanStyle(color = chanTheme.textColorPrimaryCompose))

      run {
        append("File name")
        appendLine()
        append(infoText(clickedFile.fileName))
        appendLine()
        appendLine()
      }

      if (fileMd5Hash != null) {
        append("File MD5 hash")
        appendLine()
        append(colorizedText(fileMd5Hash))
        appendLine()
        appendLine()
      }

      clickedFile.spoilerInfo?.let { spoilerInfo ->
        append("Marked as spoiler: ")
        append(infoText(spoilerInfo.markedAsSpoiler.toString()))
        appendLine()

        append("Board supports spoilers: ")
        if (spoilerInfo.boardSupportsSpoilers) {
          append(infoText(spoilerInfo.boardSupportsSpoilers.toString()))
        } else {
          append(errorText(spoilerInfo.boardSupportsSpoilers.toString()))
        }

        appendLine()
        appendLine()
      }

      run {
        append("File size: ")

        val exceedsFileSize = (maxBoardFileSizeRaw > 0 && clickedFile.fileSize > maxBoardFileSizeRaw) ||
          (maxBoardWebmSizeRaw > 0 && clickedFile.fileSize > maxBoardWebmSizeRaw)

        if (exceedsFileSize) {
          append(errorText(ChanPostUtils.getReadableFileSize(clickedFile.fileSize)))
        } else {
          append(infoText(ChanPostUtils.getReadableFileSize(clickedFile.fileSize)))
        }

        appendLine()
        appendLine()
      }


      clickedFile.imageDimensions?.let { imageDimensions ->
        append("Image dimensions: ")

        if (imageDimensions.width > 10000) {
          append(errorText(imageDimensions.width.toString()))
        } else {
          append(infoText(imageDimensions.width.toString()))
        }

        append("x")

        if (imageDimensions.height > 10000) {
          append(errorText(imageDimensions.height.toString()))
        } else {
          append(infoText(imageDimensions.height.toString()))
        }

        appendLine()
        appendLine()
      }

      if (totalFileSizeSum != null && totalFileSizeSum > 0) {
        val totalFileSizeSumFormatted = ChanPostUtils.getReadableFileSize(totalFileSizeSum)

        append("Total file size sum per post: ")
        if (attachAdditionalInfo.totalFileSizeExceeded) {
          append(errorText(totalFileSizeSumFormatted))
        } else {
          append(infoText(totalFileSizeSumFormatted))
        }

        appendLine()
        appendLine()
      }

      val gpsExifData = attachAdditionalInfo.getGspExifDataOrNull()
      if (gpsExifData != null) {
        append("GPS exif data: ")
        append(warningText("'${gpsExifData.value}'"))
        appendLine()
        appendLine()
      }

      val orientationExifData = attachAdditionalInfo.getOrientationExifData()
      if (orientationExifData != null) {
        append("Orientation exif data: ")
        append(warningText("'${orientationExifData.value}'"))
        appendLine()
        appendLine()
      }
    }
  }

  private fun getImageDimensions(replyFile: ReplyFile): ReplyFileAttachable.ImageDimensions? {
    val fileOnDisk = replyFile.fileOnDisk
    val key = fileOnDisk.asFileKey()

    val fromCache = imageDimensionsCache.get(key)
    if (fromCache != null) {
      return fromCache
    }

    return MediaUtils.getImageDims(replyFile.fileOnDisk)
      ?.let { dimensions ->
        val imageDimensions = ReplyFileAttachable.ImageDimensions(
          dimensions.first!!,
          dimensions.second!!
        )

        imageDimensionsCache.put(key, imageDimensions)
        return@let imageDimensions
      }
  }

  private suspend fun fileExceedsMaxFileSize(
    replyFile: ReplyFile,
    replyFileMeta: ReplyFileMeta,
    chanDescriptor: ChanDescriptor
  ): Boolean {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return false
    }

    val chanBoard = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
      ?: return false

    val isProbablyVideo = imageLoaderV2.fileIsProbablyVideoInterruptible(
      replyFileMeta.originalFileName,
      InputFile.JavaFile(replyFile.fileOnDisk)
    )

    val fileOnDiskSize = replyFile.fileOnDisk.length()

    if (chanBoard.maxWebmSize > 0 && isProbablyVideo) {
      return fileOnDiskSize > chanBoard.maxWebmSize
    }

    if (chanBoard.maxFileSize > 0 && !isProbablyVideo) {
      return fileOnDiskSize > chanBoard.maxFileSize
    }

    return false
  }

  private fun getFileExifInfoStatus(fileOnDisk: File): Set<FileExifInfoStatus> {
    try {
      val fromCache = fileExifInfoCache.get(fileOnDisk.asFileKey())
      if (fromCache != null) {
        return fromCache
      }

      val resultSet = hashSetOf<FileExifInfoStatus>()
      val exif = ExifInterface(fileOnDisk.absolutePath)
      val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

      if (orientation != ExifInterface.ORIENTATION_UNDEFINED) {
        val orientationString = when (orientation) {
          ExifInterface.ORIENTATION_NORMAL -> "orientation_normal"
          ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "orientation_flip_horizontal"
          ExifInterface.ORIENTATION_ROTATE_180 -> "orientation_rotate_180"
          ExifInterface.ORIENTATION_FLIP_VERTICAL -> "orientation_flip_vertical"
          ExifInterface.ORIENTATION_TRANSPOSE -> "orientation_transpose"
          ExifInterface.ORIENTATION_ROTATE_90 -> "orientation_rotate_90"
          ExifInterface.ORIENTATION_TRANSVERSE -> "orientation_transverse"
          ExifInterface.ORIENTATION_ROTATE_270 -> "orientation_rotate_270"
          else -> "orientation_undefined"
        }

        resultSet += FileExifInfoStatus.OrientationExifFound(orientationString)
      }

      if (exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) != null
        || exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null
      ) {
        val fullString = buildString {
          append("GPS_LONGITUDE='${exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)}', ")
          append("GPS_LATITUDE='${exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)}'")
        }

        resultSet += FileExifInfoStatus.GpsExifFound(fullString)
      }

      fileExifInfoCache.put(fileOnDisk.asFileKey(), resultSet)
      return resultSet
    } catch (ignored: Exception) {
      return emptySet()
    }
  }

  private fun boardSupportsSpoilers(chanDescriptor: ChanDescriptor): Boolean {
    return boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
      ?.spoilers
      ?: return false
  }

  suspend fun getTotalFileSizeSumPerPost(chanDescriptor: ChanDescriptor): Long? {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return null
    }

    // TODO: add caching
    return postingLimitationsInfoManager.getMaxAllowedTotalFilesSizePerPost(
      chanDescriptor.boardDescriptor()
    )
  }

  suspend fun getMaxAllowedFilesPerPost(chanDescriptor: ChanDescriptor): Int? {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return null
    }

    siteManager.awaitUntilInitialized()

    // TODO: add caching
    return postingLimitationsInfoManager.getMaxAllowedFilesPerPost(
      chanDescriptor.boardDescriptor()
    )
  }

  private fun File.asFileKey(): FileKey {
    return FileKey(
      filePath = absolutePath,
      fileSize = length()
    )
  }

  private data class FileKey(
    val filePath: String,
    val fileSize: Long
  )

  companion object {
    private const val TAG = "ReplyLayoutFileEnumerator"

    const val MAX_VISIBLE_ATTACHABLES_COUNT = 32
  }

}