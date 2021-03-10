package com.github.k1rakishou.model.data.descriptor

import com.github.k1rakishou.common.mutableListWithCap

class SiteDescriptor private constructor(
  val siteName: String
) {
  fun is4chan(): Boolean {
    // Kinda bad, but Chan4 file is located in another module so for now it's impossible to use
    // it
    return siteName.equals("4chan", ignoreCase = true)
  }

  fun isDvach(): Boolean {
    return siteName.equals("2ch.hk", ignoreCase = true)
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
    // TODO(KurobaEx v0.7.0): use a growable array instead of array list here to get rid of @Synchronized
    private val CACHE = mutableListWithCap<SiteDescriptor>(24)

    @Synchronized
    fun create(siteNameInput: String): SiteDescriptor {
      val siteName = siteNameInput.intern()

      for (siteDescriptor in CACHE) {
        if (siteDescriptor.siteName === siteName) {
          return siteDescriptor
        }
      }

      val newSiteDescriptor = SiteDescriptor(siteName)
      CACHE.add(newSiteDescriptor)

      return newSiteDescriptor
    }
  }

}