package com.github.k1rakishou.chan.features.reply.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
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
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.util.ChanPostUtils
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import java.util.regex.Pattern

class ReplyLayoutHelper(
  private val appContext: Context,
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

  private val cachedReplyFileVersions = mutableMapOf<UUID, Int>()

  suspend fun enumerateReplyFiles(
    chanDescriptor: ChanDescriptor,
    forceUpdateFiles: Collection<UUID>
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
            val dimensionsExceeded = dimensionsExceeded(imageDimensions, chanDescriptor)

            val fileOnDisk = replyFile.fileOnDisk.absolutePath
            val fileMetaOnDisk = replyFile.fileMetaOnDisk.absolutePath
            val previewFileOnDiskPath = replyFile.previewFileOnDisk?.absolutePath
            val version = getFileVersion(replyFileMeta, forceUpdateFiles)

            return@map ReplyFileAttachable(
              version = version,
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
                markedAsSpoilerOnNonSpoilerBoard = markedAsSpoilerOnNonSpoilerBoard,
                maxAttachedFilesCountExceeded = when {
                  maxAllowedFilesPerPost == null -> false
                  selectedFilesCount < maxAllowedFilesPerPost -> false
                  selectedFilesCount == maxAllowedFilesPerPost -> !isSelected
                  else -> true
                },
                dimensionsExceeded = dimensionsExceeded
              ),
              fileOnDisk = fileOnDisk,
              fileMetaOnDisk = fileMetaOnDisk,
              previewFileOnDiskPath = previewFileOnDiskPath
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
    val maxMediaWidth = chanBoard?.maxMediaWidth ?: -1
    val maxMediaHeight = chanBoard?.maxMediaHeight ?: -1

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
      }

      if (fileMd5Hash != null) {
        appendLine()
        appendLine()

        append("File MD5 hash")
        appendLine()
        append(colorizedText(fileMd5Hash))
      }

      clickedFile.spoilerInfo?.let { spoilerInfo ->
        appendLine()
        appendLine()

        append("Marked as spoiler: ")
        append(infoText(spoilerInfo.markedAsSpoiler.toString()))
        appendLine()
        appendLine()

        append("Board supports spoilers: ")
        if (spoilerInfo.boardSupportsSpoilers) {
          append(infoText(spoilerInfo.boardSupportsSpoilers.toString()))
        } else {
          append(errorText(spoilerInfo.boardSupportsSpoilers.toString()))
        }
      }

      run {
        appendLine()
        appendLine()

        append("File size: ")

        val exceedsFileSize = (maxBoardFileSizeRaw > 0 && clickedFile.fileSize > maxBoardFileSizeRaw) ||
          (maxBoardWebmSizeRaw > 0 && clickedFile.fileSize > maxBoardWebmSizeRaw)

        if (exceedsFileSize) {
          append(errorText(ChanPostUtils.getReadableFileSize(clickedFile.fileSize)))
        } else {
          append(infoText(ChanPostUtils.getReadableFileSize(clickedFile.fileSize)))
        }

        appendLine()

        val maxFileSizeFormatted = maxBoardFileSizeRaw
          .takeIf { it > 0 }
          ?.let { fileSize -> ChanPostUtils.getReadableFileSize(fileSize.toLong()) }
          ?: "INF"

        val maxWebmSizeFormatted = maxBoardWebmSizeRaw
          .takeIf { it > 0 }
          ?.let { webmSize -> ChanPostUtils.getReadableFileSize(webmSize.toLong()) }
          ?: "INF"

        append("Max file size: ")
        append(infoText(maxFileSizeFormatted))
        appendLine()
        append("Max webm size: ")
        append(infoText(maxWebmSizeFormatted))
      }

      clickedFile.imageDimensions?.let { imageDimensions ->
        appendLine()
        appendLine()

        append("Image dimensions: ")

        if (maxMediaWidth > 0 && imageDimensions.width > maxMediaWidth) {
          append(errorText(imageDimensions.width.toString()))
        } else {
          append(infoText(imageDimensions.width.toString()))
        }

        append(infoText("x"))

        if (maxMediaHeight > 0 && imageDimensions.height > maxMediaHeight) {
          append(errorText(imageDimensions.height.toString()))
        } else {
          append(infoText(imageDimensions.height.toString()))
        }

        appendLine()

        val maxMediaWidthFormatted = maxMediaWidth
          .takeIf { it > 0 }
          ?.toString()
          ?: "INF"

        val maxMediaHeightFormatted = maxMediaHeight
          .takeIf { it > 0 }
          ?.toString()
          ?: "INF"

        append("Max width: ")
        append(infoText(maxMediaWidthFormatted))
        appendLine()
        append("Max height: ")
        append(infoText(maxMediaHeightFormatted))
      }

      if (totalFileSizeSum != null && totalFileSizeSum > 0) {
        appendLine()
        appendLine()

        val totalFileSizeSumFormatted = ChanPostUtils.getReadableFileSize(totalFileSizeSum)

        append("Total file size sum per post: ")
        if (attachAdditionalInfo.totalFileSizeExceeded) {
          append(errorText(totalFileSizeSumFormatted))
        } else {
          append(infoText(totalFileSizeSumFormatted))
        }
      }

      val gpsExifData = attachAdditionalInfo.getGspExifDataOrNull()
      if (gpsExifData.isNotEmpty()) {
        appendLine()
        appendLine()

        append("GPS exif data: ")
        append(warningText(gpsExifData.joinToString(separator = ", ", transform = { it.value })))
      }

      val orientationExifData = attachAdditionalInfo.getOrientationExifData()
      if (orientationExifData.isNotEmpty()) {
        appendLine()
        appendLine()

        append("Orientation exif data: ")
        append(warningText(orientationExifData.joinToString(separator = ", ", transform = { it.value })))
      }
    }
  }

  suspend fun removeSelectedFilesMetadata(): List<UUID> {
    val fileUuids = mutableListOf<UUID>()
    val selectedReplyFiles = replyManager.getSelectedFilesOrdered()

    selectedReplyFiles.forEach { replyFile ->
      val replyFileMeta = replyFile.getReplyFileMeta().valueOrNull()
        ?: return@forEach

      val reencodedFile = MediaUtils.reencodeBitmapFile(
        inputBitmapFile = replyFile.fileOnDisk,
        fixExif = false,
        removeMetadata = true,
        changeImageChecksum = false,
        reencodeSettings = null
      )

      if (reencodedFile == null) {
        Logger.error(TAG) {
          "removeSelectedFilesMetadata() Failed to remove metadata for " +
            "file '${replyFile.fileOnDisk.absolutePath}'"
        }

        return@forEach
      }

      val isSuccess = replyFile.overwriteFileOnDisk(reencodedFile)
        .onError { error ->
          Logger.error(TAG, error) {
            "removeSelectedFilesMetadata() Failed to overwrite " +
              "file '${replyFile.fileOnDisk.absolutePath}' " +
              "with '${reencodedFile.absolutePath}'"
          }
        }
        .isValue()

      if (!isSuccess) {
        return@forEach
      }

      val success = imageLoaderV2.calculateFilePreviewAndStoreOnDisk(
        appContext,
        replyFileMeta.fileUuid
      )

      if (!success) {
        return@forEach
      }

      fileUuids += replyFileMeta.fileUuid
    }

    return fileUuids
  }

  suspend fun changeSelectedFilesChecksum(): List<UUID> {
    val fileUuids = mutableListOf<UUID>()
    val selectedReplyFiles = replyManager.getSelectedFilesOrdered()

    selectedReplyFiles.forEach { replyFile ->
      val replyFileMeta = replyFile.getReplyFileMeta().valueOrNull()
        ?: return@forEach

      val reencodedFile = MediaUtils.reencodeBitmapFile(
        inputBitmapFile = replyFile.fileOnDisk,
        fixExif = false,
        removeMetadata = false,
        changeImageChecksum = true,
        reencodeSettings = null
      )

      if (reencodedFile == null) {
        Logger.error(TAG) {
          "changeSelectedFilesChecksum() Failed to change checksum for " +
            "file '${replyFile.fileOnDisk.absolutePath}'"
        }

        return@forEach
      }

      val isSuccess = replyFile.overwriteFileOnDisk(reencodedFile)
        .onError { error ->
          Logger.error(TAG, error) {
            "changeSelectedFilesChecksum() Failed to overwrite " +
              "file '${replyFile.fileOnDisk.absolutePath}' " +
              "with '${reencodedFile.absolutePath}'"
          }
        }
        .isValue()

      if (!isSuccess) {
        return@forEach
      }

      val success = imageLoaderV2.calculateFilePreviewAndStoreOnDisk(
        appContext,
        replyFileMeta.fileUuid
      )

      if (!success) {
        return@forEach
      }

      fileUuids += replyFileMeta.fileUuid
    }

    return fileUuids
  }

  suspend fun getTotalFileSizeSumPerPost(chanDescriptor: ChanDescriptor): Long? {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return null
    }

    return postingLimitationsInfoManager.getMaxAllowedTotalFilesSizePerPost(
      chanDescriptor.boardDescriptor()
    )
  }

  suspend fun getMaxAllowedFilesPerPost(chanDescriptor: ChanDescriptor): Int? {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return null
    }

    siteManager.awaitUntilInitialized()

    return postingLimitationsInfoManager.getMaxAllowedFilesPerPost(
      chanDescriptor.boardDescriptor()
    )
  }

  private fun getFileVersion(
    replyFileMeta: ReplyFileMeta,
    forceUpdateFiles: Collection<UUID>
  ): Int {
    if (replyFileMeta.fileUuid in forceUpdateFiles) {
      val prevVersion = cachedReplyFileVersions[replyFileMeta.fileUuid]
      val newVersion = if (prevVersion != null) {
        prevVersion + 1
      } else {
        0
      }

      cachedReplyFileVersions[replyFileMeta.fileUuid] = newVersion
      return newVersion
    }

    return -1
  }

  private fun dimensionsExceeded(
    imageDimensions: ReplyFileAttachable.ImageDimensions?,
    chanDescriptor: ChanDescriptor
  ): Boolean {
    if (imageDimensions == null) {
      return false
    }

    val chanBoard = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
    if (chanBoard == null) {
      return false
    }

    val maxMediaWidth = chanBoard.maxMediaWidth
    val maxMediaHeight = chanBoard.maxMediaHeight

    if (maxMediaWidth > 0 && imageDimensions.width > maxMediaWidth) {
      return true
    }

    if (maxMediaHeight > 0 && imageDimensions.height > maxMediaHeight) {
      return true
    }

    return false
  }

  private fun getImageDimensions(replyFile: ReplyFile): ReplyFileAttachable.ImageDimensions? {
    return MediaUtils.getImageDims(replyFile.fileOnDisk)
      ?.let { dimensions ->
        return@let ReplyFileAttachable.ImageDimensions(
          dimensions.first!!,
          dimensions.second!!
        )
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

  fun handleQuote(replyTextState: TextFieldValue, postNo: Long, textQuote: String?): TextFieldValue {
    val stringBuilder = StringBuilder()
    val comment = replyTextState.text
    val selectionStart = replyTextState.selection.start.coerceAtLeast(0)

    if (selectionStart - 1 >= 0
      && comment.isNotEmpty()
      && selectionStart - 1 < comment.length
      && comment[selectionStart - 1] != '\n'
    ) {
      stringBuilder
        .append('\n')
    }

    if (!comment.contains(">>${postNo}")) {
      stringBuilder
        .append(">>")
        .append(postNo)
        .append("\n")
    }

    if (textQuote.isNotNullNorEmpty()) {
      val lines = textQuote.split("\n").toTypedArray()
      for (line in lines) {
        // do not include post no from quoted post
        if (QUOTE_PATTERN_COMPLEX.matcher(line).matches()) {
          continue
        }

        if (!line.startsWith(">>") && !line.startsWith(">")) {
          stringBuilder
            .append(">")
        }

        stringBuilder
          .append(line)
          .append("\n")
      }
    }

    val resultComment = StringBuilder(comment)
      .insert(selectionStart, stringBuilder)
      .toString()

    return replyTextState.copy(
      text = resultComment,
      selection = TextRange(selectionStart + stringBuilder.length)
    )
  }

  companion object {
    private const val TAG = "ReplyLayoutFileEnumerator"
    private val QUOTE_PATTERN_COMPLEX = Pattern.compile("^>>(>/[a-z0-9]+/)?\\d+.*$")

    const val MAX_VISIBLE_ATTACHABLES_COUNT = 32
  }

}