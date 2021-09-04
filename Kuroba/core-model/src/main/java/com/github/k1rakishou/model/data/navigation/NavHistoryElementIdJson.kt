package com.github.k1rakishou.model.data.navigation

import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NavHistoryElementData(
  val chanDescriptorsData: List<ChanDescriptorData>
) {

  companion object {
    private const val TAG = "NavHistoryElementDataJson"

    fun fromChanDescriptor(chanDescriptor: ChanDescriptor): NavHistoryElementData? {
      when (chanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          val chanDescriptorJson = ChanDescriptorData(
            siteName = chanDescriptor.siteName(),
            boardCode = chanDescriptor.boardCode(),
            threadNo = null
          )

          return NavHistoryElementData(listOf(chanDescriptorJson))
        }
        is ChanDescriptor.ThreadDescriptor -> {
          val chanDescriptorJson = ChanDescriptorData(
            siteName = chanDescriptor.siteName(),
            boardCode = chanDescriptor.boardCode(),
            threadNo = chanDescriptor.threadNo
          )

          return NavHistoryElementData(listOf(chanDescriptorJson))
        }
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          val catalogDescriptors = chanDescriptor.catalogDescriptors

          if (catalogDescriptors.size < ChanDescriptor.CompositeCatalogDescriptor.MIN_CATALOGS_COUNT) {
            Logger.e(TAG, "Too few catalogDescriptors in composite catalog descriptor (count: ${catalogDescriptors.size}")
            return null
          }

          if (catalogDescriptors.size > ChanDescriptor.CompositeCatalogDescriptor.MAX_CATALOGS_COUNT) {
            Logger.e(TAG, "Too many catalogDescriptors in composite catalog descriptor (count: ${catalogDescriptors.size}")
            return null
          }

          val chanDescriptorJson = catalogDescriptors.map { catalogDescriptor ->
            return@map ChanDescriptorData(
              siteName = catalogDescriptor.siteName(),
              boardCode = catalogDescriptor.boardCode(),
              threadNo = null
            )
          }

          return NavHistoryElementData(chanDescriptorJson)
        }
      }
    }
  }

}

@JsonClass(generateAdapter = true)
data class ChanDescriptorData(
  val siteName: String,
  val boardCode: String,
  val threadNo: Long?
) {

  fun threadDescriptorOrNull(): ChanDescriptor.ThreadDescriptor? {
    if (threadNo == null || threadNo <= 0) {
      return null
    }

    return ChanDescriptor.ThreadDescriptor.create(siteName, boardCode, threadNo)
  }

  fun catalogDescriptor(): ChanDescriptor.CatalogDescriptor {
    return ChanDescriptor.CatalogDescriptor.create(siteName, boardCode)
  }

}