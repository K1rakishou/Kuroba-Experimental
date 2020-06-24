package com.github.adamantcheese.model.data.descriptor

class SiteDescriptor(
  val siteName: String
) {
  fun is4chan(): Boolean {
    // Kinda bad, but Chan4 file is located in another module so for now it's impossible to use
    // it
    return siteName.equals("4chan", ignoreCase = true)
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

}