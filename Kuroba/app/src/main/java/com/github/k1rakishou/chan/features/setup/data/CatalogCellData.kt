package com.github.k1rakishou.chan.features.setup.data

import com.github.k1rakishou.chan.ui.helper.BoardHelper
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

class CatalogCellData(
  val searchQuery: String?,
  val catalogDescriptor: ChanDescriptor.ICatalogDescriptor,
  val boardName: String,
  val description: String
) {
  val boardCodeFormatted by lazy {
    when (catalogDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> {
        return@lazy "/${catalogDescriptor.boardDescriptor.boardCode}/"
      }
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        return@lazy catalogDescriptor.catalogDescriptors.joinToString(
          separator = "+",
          transform = { catalogDescriptor -> "${catalogDescriptor.siteName()}/${catalogDescriptor.boardCode()}/" }
        )
      }
    }
  }

  val fullName by lazy {
    when (catalogDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> {
        return@lazy BoardHelper.getName(catalogDescriptor.boardDescriptor.boardCode, boardName)
      }
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        return@lazy catalogDescriptor.userReadableString()
      }
    }
  }

  val boardDescriptorOrNull: BoardDescriptor?
    get() {
      return when (catalogDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> catalogDescriptor.boardDescriptor
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          null
        }
      }
    }

  val boardCodeOrComposedBoardCodes: String
    get() {
      return when (catalogDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> catalogDescriptor.boardDescriptor.boardCode
        is ChanDescriptor.CompositeCatalogDescriptor -> catalogDescriptor.userReadableString()
      }
    }

  fun requireBoardDescriptor(): BoardDescriptor {
    return requireNotNull(boardDescriptorOrNull) {
      "boardDescriptorOrNull is null, catalogDescriptor: ${catalogDescriptor}"
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CatalogCellData

    if (catalogDescriptor != other.catalogDescriptor) return false

    return true
  }

  override fun hashCode(): Int {
    return catalogDescriptor.hashCode()
  }

  override fun toString(): String {
    return "BoardCellData(catalogDescriptor=$catalogDescriptor, fullName='$fullName', description='$description')"
  }

}