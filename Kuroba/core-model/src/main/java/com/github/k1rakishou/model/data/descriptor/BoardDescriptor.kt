package com.github.k1rakishou.model.data.descriptor

import com.github.k1rakishou.common.mutableListWithCap

class BoardDescriptor private constructor(
  val siteDescriptor: SiteDescriptor,
  val boardCode: String
) {

  fun siteName(): String = siteDescriptor.siteName

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is BoardDescriptor) return false

    if (siteDescriptor != other.siteDescriptor) return false
    if (boardCode != other.boardCode) return false

    return true
  }

  override fun hashCode(): Int {
    var result = siteDescriptor.hashCode()
    result = 31 * result + boardCode.hashCode()
    return result
  }

  override fun toString(): String {
    return "BD{${siteDescriptor.siteName}/$boardCode}"
  }

  companion object {
    // TODO(KurobaEx v0.7.0): use a growable array instead of array list here to get rid of @Synchronized
    private val CACHE = mutableListWithCap<BoardDescriptor>(128)

    @JvmStatic
    fun create(siteDescriptor: SiteDescriptor, boardCode: String): BoardDescriptor {
      return create(siteDescriptor.siteName, boardCode)
    }

    @JvmStatic
    @Synchronized
    fun create(siteName: String, boardCodeInput: String): BoardDescriptor {
      val boardCode = boardCodeInput.intern()

      for (boardDescriptor in CACHE) {
        if (boardDescriptor.siteDescriptor.siteName === siteName && boardDescriptor.boardCode === boardCode) {
          return boardDescriptor
        }
      }

      val newBoardDescriptor = BoardDescriptor(
        SiteDescriptor.create(siteName),
        boardCode
      )

      CACHE.add(newBoardDescriptor)
      return newBoardDescriptor
    }
  }
}