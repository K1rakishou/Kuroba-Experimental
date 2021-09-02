package com.github.k1rakishou.model.data.catalog

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

interface IChanCatalogSnapshot<T : ChanDescriptor.ICatalogDescriptor> {
  val catalogDescriptor: ChanDescriptor.ICatalogDescriptor
  val isUnlimitedCatalog: Boolean
  val isUnlimitedOrCompositeCatalog: Boolean
  val catalogThreadDescriptorList: List<ChanDescriptor.ThreadDescriptor>
  val catalogThreadDescriptorSet: Set<ChanDescriptor.ThreadDescriptor>
  val catalogPage: Int
  val isEndReached: Boolean
  val postsCount: Int

  fun isEmpty(): Boolean
  fun mergeWith(chanCatalogSnapshot: IChanCatalogSnapshot<T>)
  fun add(catalogSnapshotEntries: List<ChanDescriptor.ThreadDescriptor>)
  fun getNextCatalogPage(): Int?
  fun onCatalogLoaded(catalogPageToLoad: Int?)
  fun onEndOfUnlimitedCatalogReached()
  fun updateCatalogPage(overridePage: Int)

  companion object {
    const val START_PAGE_COMPOSITE_CATALOG = 0
    const val START_PAGE_UNLIMITED_CATALOG = 1

    fun <T : ChanDescriptor.ICatalogDescriptor> fromSortedThreadDescriptorList(
      catalogDescriptor: T,
      threadDescriptors: List<ChanDescriptor.ThreadDescriptor>,
      isUnlimitedCatalog: Boolean
    ): IChanCatalogSnapshot<T> {
      return when (catalogDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          ChanCatalogSnapshot(catalogDescriptor, isUnlimitedCatalog)
            .apply { add(threadDescriptors) }
            as IChanCatalogSnapshot<T>
        }
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          ChanCompositeCatalogSnapshot(catalogDescriptor)
            .apply { add(threadDescriptors) }
            as IChanCatalogSnapshot<T>
        }
        else -> error("Unexpected catalogDescriptor: ${catalogDescriptor.javaClass.simpleName}")
      }
    }
  }

}