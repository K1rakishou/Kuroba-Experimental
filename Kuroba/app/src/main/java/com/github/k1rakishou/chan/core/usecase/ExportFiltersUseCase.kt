package com.github.k1rakishou.chan.core.usecase

import android.net.Uri
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.flatMapNotNull
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.FileDescriptorMode
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.FileOutputStream

class ExportFiltersUseCase(
  private val fileManager: FileManager,
  private val chanFilterManager: ChanFilterManager,
  private val moshi: Moshi
) : IUseCase<ExportFiltersUseCase.Params, ModularResult<Unit>> {

  override fun execute(parameter: Params): ModularResult<Unit> {
    return ModularResult.Try { exportFiltersInternal(parameter.outputFileUri) }
  }

  private fun exportFiltersInternal(outputFileUri: Uri) {
    val filtersToExport = chanFilterManager.getAllFilters()
      .map { chanFilter -> ExportedChanFilter.fromChanFilter(chanFilter) }

    if (filtersToExport.isEmpty()) {
      throw ExportFiltersError("Nothing to export")
    }

    val json = moshi.adapter(ExportedChanFilters::class.java)
      .toJson(ExportedChanFilters(BuildConfig.VERSION_CODE, filtersToExport))

    val externalFile = fileManager.fromUri(outputFileUri)
      ?: throw ExportFiltersError("Failed to open output file '${outputFileUri}'")

    fileManager.withFileDescriptor(externalFile, FileDescriptorMode.WriteTruncate) { fileDescriptor ->
      FileOutputStream(fileDescriptor).use { fos ->
        json.byteInputStream().copyTo(fos)
      }
    }
  }

  @JsonClass(generateAdapter = true)
  data class ExportedChanFilters(
    @Json(name = "application_version_code")
    val applicationVersionCode: Int,
    @Json(name = "exported_filters")
    val exportedChanFilters: List<ExportedChanFilter>
  )

  @JsonClass(generateAdapter = true)
  data class ExportedChanFilter(
    @Json(name = "enabled")
    val enabled: Boolean,
    @Json(name = "type")
    val type: Int,
    @Json(name = "pattern")
    val pattern: String?,
    @Json(name = "boards")
    val boards: String,
    @Json(name = "action")
    val action: Int,
    @Json(name = "color")
    val color: Int,
    @Json(name = "note")
    val note: String? = null,
    @Json(name = "apply_to_replies")
    val applyToReplies: Boolean,
    @Json(name = "only_on_op")
    val onlyOnOP: Boolean,
    @Json(name = "apply_to_saved")
    val applyToSaved: Boolean
  ) {

    fun toChanFilter(): ChanFilter? {
      val parsedBoards = if (boards.isNotEmpty()) {
        boards.split(";")
          .flatMapNotNull { siteWithBoardsUnsplit ->
            val siteWithBoardsSplit = siteWithBoardsUnsplit.split(":")

            val siteName = siteWithBoardsSplit.getOrNull(0)
            if (siteName.isNullOrEmpty()) {
              Logger.e(TAG, "toChanFilter() Failed to parse site name: siteWithBoardsUnsplit=$siteWithBoardsUnsplit")
              return@flatMapNotNull null
            }

            val boardCodes = siteWithBoardsSplit.getOrNull(1)
            if (boardCodes == null) {
              Logger.e(TAG, "toChanFilter() Failed to parse board code: siteWithBoardsUnsplit=$siteWithBoardsUnsplit")
              return@flatMapNotNull null
            }

            if (boardCodes.isEmpty()) {
              return@flatMapNotNull null
            }

            return@flatMapNotNull boardCodes
              .split(",")
              .map { boardCode -> BoardDescriptor.create(siteName, boardCode) }
          }
      } else {
        // A filter that matches all active boards
        emptyList()
      }

      if (parsedBoards.isEmpty() && boards.isNotEmpty()) {
        Logger.e(TAG, "Failed to parse boards: '$boards'")
        return null
      }

      return ChanFilter(
        enabled = enabled,
        type = type,
        pattern = pattern,
        boards = parsedBoards.toSet(),
        action = action,
        color = color,
        note = note,
        applyToReplies = applyToReplies,
        onlyOnOP = onlyOnOP,
        applyToSaved = applyToSaved
      )
    }

    companion object {
      private const val TAG = "ExportedChanFilter"

      fun fromChanFilter(chanFilter: ChanFilter): ExportedChanFilter {
        val boardsMappedToString = chanFilter.boards
          .groupBy { boardDescriptor -> boardDescriptor.siteDescriptor }
          .entries
          .joinToString(
            separator = ";",
            transform = { entry ->
              val siteName = entry.key.siteName
              val boardDescriptors = entry.value

              if (boardDescriptors.isEmpty()) {
                return@joinToString ""
              }

              val boardDescriptorsString = boardDescriptors.joinToString(
                separator = ",",
                transform = { boardDescriptor -> boardDescriptor.boardCode }
              )

              return@joinToString "${siteName}:${boardDescriptorsString}"
            }
          )

        return ExportedChanFilter(
          enabled = chanFilter.enabled,
          type = chanFilter.type,
          pattern = chanFilter.pattern,
          boards = boardsMappedToString,
          action = chanFilter.action,
          color = chanFilter.color,
          note = chanFilter.note,
          applyToReplies = chanFilter.applyToReplies,
          onlyOnOP = chanFilter.onlyOnOP,
          applyToSaved = chanFilter.applyToSaved
        )
      }
    }
  }

  data class Params(val outputFileUri: Uri)

  class ExportFiltersError(message: String) : Exception(message)
}