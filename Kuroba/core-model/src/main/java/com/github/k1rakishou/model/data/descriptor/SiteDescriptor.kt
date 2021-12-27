package com.github.k1rakishou.model.data.descriptor

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class SiteDescriptor private constructor(
  val siteName: String
): Parcelable {
  fun is4chan(): Boolean {
    // Kinda bad, but Chan4 file is located in another module so for now it's impossible to use
    // it
    return siteName.equals("4chan", ignoreCase = true)
  }

  fun isDvach(): Boolean {
    return siteName.equals("2ch.hk", ignoreCase = true)
  }

  fun isDiochan(): Boolean {
    return siteName.equals("Diochan", ignoreCase = true)
  }

  fun isLainchan(): Boolean {
    return siteName.equals("Lainchan", ignoreCase = true)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SiteDescriptor) return false

    if (!siteName.equals(other.siteName, ignoreCase = true)) return false

    return true
  }

  override fun hashCode(): Int {
    return siteName.hashCode()
  }

  override fun toString(): String {
    return "SD{$siteName}"
  }

  companion object {
    fun create(siteNameInput: String): SiteDescriptor {
      val siteName = siteNameInput.intern()

      return SiteDescriptor(siteName)
    }
  }

}