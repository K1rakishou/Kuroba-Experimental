package com.github.k1rakishou.model.data.navigation

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

sealed class NavHistoryElement(
  open val navHistoryElementInfo: NavHistoryElementInfo
) {

  abstract fun descriptor(): ChanDescriptor

  class CompositeCatalog(
    val descriptor: ChanDescriptor.CompositeCatalogDescriptor,
    override val navHistoryElementInfo: NavHistoryElementInfo
  ) : NavHistoryElement(navHistoryElementInfo) {

    override fun descriptor(): ChanDescriptor = descriptor

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as CompositeCatalog

      if (descriptor != other.descriptor) return false
      if (navHistoryElementInfo != other.navHistoryElementInfo) return false

      return true
    }

    override fun hashCode(): Int {
      var result = descriptor.hashCode()
      result = 31 * result + navHistoryElementInfo.hashCode()
      return result
    }

    override fun toString(): String {
      return "CompositeCatalog(descriptor=$descriptor, navHistoryElementInfo=$navHistoryElementInfo)"
    }

  }

  class Catalog(
    val descriptor: ChanDescriptor.CatalogDescriptor,
    override val navHistoryElementInfo: NavHistoryElementInfo
  ) : NavHistoryElement(navHistoryElementInfo) {

    override fun descriptor(): ChanDescriptor = descriptor

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Catalog

      if (descriptor != other.descriptor) return false

      return true
    }

    override fun hashCode(): Int {
      return descriptor.hashCode()
    }

    override fun toString(): String {
      return "NavElement.Catalog(descriptor=$descriptor, info=$navHistoryElementInfo)"
    }

  }

  class Thread(
    val descriptor: ChanDescriptor.ThreadDescriptor,
    override val navHistoryElementInfo: NavHistoryElementInfo
  ) : NavHistoryElement(navHistoryElementInfo) {

    override fun descriptor(): ChanDescriptor = descriptor

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Thread

      if (descriptor != other.descriptor) return false

      return true
    }

    override fun hashCode(): Int {
      return descriptor.hashCode()
    }

    override fun toString(): String {
      return "NavElement.Thread(descriptor=$descriptor, info=$navHistoryElementInfo)"
    }

  }

}