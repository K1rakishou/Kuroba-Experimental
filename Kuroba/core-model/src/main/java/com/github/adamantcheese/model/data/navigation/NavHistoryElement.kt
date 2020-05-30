package com.github.adamantcheese.model.data.navigation

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor

sealed class NavHistoryElement(
  open val navHistoryElementInfo: NavHistoryElementInfo
) {

  class Catalog(
    val descriptor: ChanDescriptor.CatalogDescriptor,
    override val navHistoryElementInfo: NavHistoryElementInfo
  ) : NavHistoryElement(navHistoryElementInfo) {

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