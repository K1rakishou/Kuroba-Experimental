package com.github.k1rakishou.chan.features.reply.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import androidx.exifinterface.media.ExifInterface
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.image.InputFile
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.PostFormatterButton
import com.github.k1rakishou.chan.utils.MediaUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Stable
class ReplyLayoutState(
    val chanDescriptor: ChanDescriptor,
    private val coroutineScope: CoroutineScope,
    private val appConstantsLazy: Lazy<AppConstants>,
    private val siteManagerLazy: Lazy<SiteManager>,
    private val boardManagerLazy: Lazy<BoardManager>,
    private val replyManagerLazy: Lazy<ReplyManager>,
    private val postingLimitationsInfoManagerLazy: Lazy<PostingLimitationsInfoManager>,
    private val imageLoaderV2Lazy: Lazy<ImageLoaderV2>
) {
    private val _replyText = mutableStateOf<TextFieldValue>(TextFieldValue())
    val replyText: State<TextFieldValue>
        get() = _replyText

    private val _subject = mutableStateOf<TextFieldValue>(TextFieldValue())
    val subject: State<TextFieldValue>
        get() = _subject

    private val _name = mutableStateOf<TextFieldValue>(TextFieldValue())
    val name: State<TextFieldValue>
        get() = _name

    private val _options = mutableStateOf<TextFieldValue>(TextFieldValue())
    val options: State<TextFieldValue>
        get() = _options

    private val _postFormatterButtons = mutableStateOf<List<PostFormatterButton>>(emptyList())
    val postFormatterButtons: State<List<PostFormatterButton>>
        get() = _postFormatterButtons

    // TODO: New reply layout
    private val _maxCommentLength = mutableIntStateOf(-1)
    val maxCommentLength: State<Int>
        get() = _maxCommentLength

    private val _attachables = mutableStateListOf<ReplyAttachable>()
    val attachables: List<ReplyAttachable>
        get() = _attachables

    private val _replyLayoutVisibility = mutableStateOf<ReplyLayoutVisibility>(ReplyLayoutVisibility.Collapsed)
    val replyLayoutVisibility: State<ReplyLayoutVisibility>
        get() = _replyLayoutVisibility

    private val _sendReplyState = mutableStateOf<SendReplyState>(SendReplyState.Finished)
    val sendReplyState: State<SendReplyState>
        get() = _sendReplyState

    private val _replySendProgressState = mutableStateOf<Float?>(null)
    val replySendProgressState: State<Float?>
        get() = _replySendProgressState

    private val appConstants: AppConstants
        get() = appConstantsLazy.get()
    private val siteManager: SiteManager
        get() = siteManagerLazy.get()
    private val boardManager: BoardManager
        get() = boardManagerLazy.get()
    private val replyManager: ReplyManager
        get() = replyManagerLazy.get()
    private val postingLimitationsInfoManager: PostingLimitationsInfoManager
        get() = postingLimitationsInfoManagerLazy.get()
    private val imageLoaderV2: ImageLoaderV2
        get() = imageLoaderV2Lazy.get()

    val isCatalogMode: Boolean
        get() = chanDescriptor is ChanDescriptor.ICatalogDescriptor

    suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
        replyManager.readReply(chanDescriptor) { reply ->
            // TODO: New reply layout. Read all the stuff into the state
        }
    }

    suspend fun unbindChanDescriptor(chanDescriptor: ChanDescriptor) {

    }

    fun collapseReplyLayout() {
        if (_replyLayoutVisibility.value != ReplyLayoutVisibility.Collapsed) {
            _replyLayoutVisibility.value = ReplyLayoutVisibility.Collapsed
        }
    }

    fun openReplyLayout() {
        if (_replyLayoutVisibility.value != ReplyLayoutVisibility.Opened) {
            _replyLayoutVisibility.value = ReplyLayoutVisibility.Opened
        }
    }

    fun expandReplyLayout() {
        if (_replyLayoutVisibility.value != ReplyLayoutVisibility.Expanded) {
            _replyLayoutVisibility.value = ReplyLayoutVisibility.Expanded
        }
    }

    fun onReplyTextChanged(replyText: TextFieldValue) {
        TODO("Not yet implemented")
    }

    fun onSubjectChanged(subject: TextFieldValue) {
        TODO("Not yet implemented")
    }

    fun onNameChanged(name: TextFieldValue) {
        TODO("Not yet implemented")
    }

    fun onOptionsChanged(options: TextFieldValue) {
        TODO("Not yet implemented")
    }

    fun insertTags(postFormatterButton: PostFormatterButton) {
        TODO("Not yet implemented")
    }

    private suspend fun enumerateReplyAttachables(
        chanDescriptor: ChanDescriptor
    ): ModularResult<List<ReplyAttachable>> {
        if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
            return ModularResult.value(listOf())
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
                    val boardSupportsSpoilers = boardSupportsSpoilers()

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

                if (attachableCounter != MAX_VISIBLE_ATTACHABLES_COUNT) {
                    if (attachableCounter > MAX_VISIBLE_ATTACHABLES_COUNT) {
                        newAttachFiles.add(ReplyAttachable.ReplyTooManyAttachables(attachableCounter))
                    } else {
                        newAttachFiles.add(0, ReplyAttachable.ReplyNewAttachable)
                    }
                }

                return@Try newAttachFiles
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
                || exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null) {
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

    private fun boardSupportsSpoilers(): Boolean {
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
        private const val TAG = "ReplyLayoutState"

        const val MAX_VISIBLE_ATTACHABLES_COUNT = 32
    }

}