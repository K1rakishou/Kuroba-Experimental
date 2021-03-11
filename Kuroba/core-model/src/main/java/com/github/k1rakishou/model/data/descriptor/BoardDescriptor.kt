package com.github.k1rakishou.model.data.descriptor

import com.github.k1rakishou.common.datastructure.LockFreeGrowableArray

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
    private val CACHE = LockFreeGrowableArray<BoardDescriptor>(128)

    @JvmStatic
    fun create(siteDescriptor: SiteDescriptor, boardCode: String): BoardDescriptor {
      return create(siteDescriptor.siteName, boardCode)
    }

    @JvmStatic
    fun create(siteName: String, boardCodeInput: String): BoardDescriptor {
      val boardCode = boardCodeInput.intern()

      return CACHE.getOrCreate(
        comparatorFunc = { boardDescriptor ->
          boardDescriptor.siteDescriptor.siteName === siteName && boardDescriptor.boardCode === boardCode
        },
        instantiatorFunc = {
          BoardDescriptor(SiteDescriptor.create(siteName), boardCode)
        }
      )
    }
  }
}