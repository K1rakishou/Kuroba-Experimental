package com.github.k1rakishou.model.data.descriptor

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class BoardDescriptor private constructor(
  val siteDescriptor: SiteDescriptor,
  val boardCode: String
) : Parcelable {

  fun siteName(): String = siteDescriptor.siteName

  fun userReadableString(): String {
    return "${siteDescriptor.siteName}/${boardCode}/"
  }

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
    @JvmStatic
    fun create(siteDescriptor: SiteDescriptor, boardCode: String): BoardDescriptor {
      return create(siteDescriptor.siteName, boardCode)
    }

    @JvmStatic
    fun create(siteName: String, boardCodeInput: String): BoardDescriptor {
      val boardCode = boardCodeInput.intern()

      return BoardDescriptor(SiteDescriptor.create(siteName), boardCode)
    }
  }
}