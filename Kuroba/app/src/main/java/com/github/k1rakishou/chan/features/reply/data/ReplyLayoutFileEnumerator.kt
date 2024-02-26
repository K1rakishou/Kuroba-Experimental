package com.github.k1rakishou.chan.features.reply.data

import androidx.exifinterface.media.ExifInterface
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.image.InputFile
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.utils.MediaUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ReplyLayoutFileEnumerator(
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

  suspend fun enumerate(
    chanDescriptor: ChanDescriptor
  ): ModularResult<ReplyAttachables> {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return ModularResult.value(ReplyAttachables())
    }

    return withContext(Dispatchers.IO) {
      return@withContext ModularResult.Try {
        val newAttachFiles = mutableListOf<ReplyAttachable>()
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
          val maxAllowedTotalFilesSizePerPost = getTotalFileSizeSumPerPost(chanDescriptor)
            ?: -1
          val totalFileSizeSum = fileFileSizeMap.values.sum()
          val boardSupportsSpoilers = boardSupportsSpoilers(chanDescriptor)

          val replyFileAttachables = replyFiles.map { replyFile ->
            val replyFileMeta = replyFile.getReplyFileMeta().unwrap()

            val isSelected = replyManager.isSelected(replyFileMeta.fileUuid).unwrap()
            val selectedFilesCount = replyManager.selectedFilesCount().unwrap()
            val fileExifStatus = getFileExifInfoStatus(replyFile.fileOnDisk)
            val exceedsMaxFileSize = fileExceedsMaxFileSize(replyFile, replyFileMeta, chanDescriptor)
            val markedAsSpoilerOnNonSpoilerBoard =
              isSelected && replyFileMeta.spoiler && !boardSupportsSpoilers

            val totalFileSizeExceeded = if (maxAllowedTotalFilesSizePerPost <= 0) {
              false
            } else {
              totalFileSizeSum > maxAllowedTotalFilesSizePerPost
            }

            val spoilerInfo = if (!boardSupportsSpoilers && !replyFileMeta.spoiler) {
              null
            } else {
              ReplyAttachable.ReplyFileAttachable.SpoilerInfo(
                replyFileMeta.spoiler,
                boardSupportsSpoilers
              )
            }

            val imageDimensions = MediaUtils.getImageDims(replyFile.fileOnDisk)
              ?.let { dimensions ->
                ReplyAttachable.ReplyFileAttachable.ImageDimensions(
                  dimensions.first!!,
                  dimensions.second!!
                )
              }

            return@map ReplyAttachable.ReplyFileAttachable(
              fileUuid = replyFileMeta.fileUuid,
              fileName = replyFileMeta.fileName,
              spoilerInfo = spoilerInfo,
              selected = isSelected,
              fileSize = fileFileSizeMap[replyFile.fileOnDisk.absolutePath]!!,
              imageDimensions = imageDimensions,
              attachAdditionalInfo = ReplyAttachable.ReplyFileAttachable.AttachAdditionalInfo(
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

        if (attachableCounter > MAX_VISIBLE_ATTACHABLES_COUNT) {
          newAttachFiles.add(ReplyAttachable.ReplyTooManyAttachables(attachableCounter))
        }

        return@Try ReplyAttachables(
          maxAllowedAttachablesPerPost = maxAllowedFilesPerPost ?: 0,
          attachables = newAttachFiles
        )
      }
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

      val orientation =
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

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

  private suspend fun getTotalFileSizeSumPerPost(chanDescriptor: ChanDescriptor): Long? {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return null
    }

    return postingLimitationsInfoManager.getMaxAllowedTotalFilesSizePerPost(
      chanDescriptor.boardDescriptor()
    )
  }

  private suspend fun getMaxAllowedFilesPerPost(chanDescriptor: ChanDescriptor): Int? {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return null
    }

    siteManager.awaitUntilInitialized()

    return postingLimitationsInfoManager.getMaxAllowedFilesPerPost(
      chanDescriptor.boardDescriptor()
    )
  }

  companion object {
    private const val TAG = "ReplyLayoutFileEnumerator"

    const val MAX_VISIBLE_ATTACHABLES_COUNT = 32
  }

}