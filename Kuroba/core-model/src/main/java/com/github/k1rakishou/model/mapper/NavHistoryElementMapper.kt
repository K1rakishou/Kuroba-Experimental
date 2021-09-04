package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.navigation.NavHistoryElement
import com.github.k1rakishou.model.data.navigation.NavHistoryElementData
import com.github.k1rakishou.model.data.navigation.NavHistoryElementInfo
import com.github.k1rakishou.model.entity.navigation.NavHistoryElementIdEntity
import com.github.k1rakishou.model.entity.navigation.NavHistoryElementInfoEntity
import com.github.k1rakishou.model.entity.navigation.NavHistoryFullDto
import com.squareup.moshi.Moshi

object NavHistoryElementMapper {
  private const val TAG = "NavHistoryElementMapper"

  fun toNavHistoryElementIdEntity(navHistoryElement: NavHistoryElement, moshi: Moshi): NavHistoryElementIdEntity? {
    when (navHistoryElement) {
      is NavHistoryElement.Catalog -> {
        val navHistoryElementDataJson = NavHistoryElementData.fromChanDescriptor(navHistoryElement.descriptor)
          ?.let { navHistoryElementDataJson ->
            return@let moshi
              .adapter(NavHistoryElementData::class.java)
              .toJson(navHistoryElementDataJson)
          }
          ?: return null

        return NavHistoryElementIdEntity(
          id = 0L,
          navHistoryElementDataJson = navHistoryElementDataJson,
          type = NavHistoryElementIdEntity.TYPE_CATALOG_DESCRIPTOR
        )
      }
      is NavHistoryElement.CompositeCatalog -> {
        val navHistoryElementDataJson = NavHistoryElementData.fromChanDescriptor(navHistoryElement.descriptor)
          ?.let { navHistoryElementDataJson ->
            return@let moshi
              .adapter(NavHistoryElementData::class.java)
              .toJson(navHistoryElementDataJson)
          }
          ?: return null

        return NavHistoryElementIdEntity(
          id = 0L,
          navHistoryElementDataJson = navHistoryElementDataJson,
          type = NavHistoryElementIdEntity.TYPE_COMPOSITE_CATALOG_DESCRIPTOR
        )
      }
      is NavHistoryElement.Thread -> {
        val navHistoryElementDataJson = NavHistoryElementData.fromChanDescriptor(navHistoryElement.descriptor)
          ?.let { navHistoryElementDataJson ->
            return@let moshi
              .adapter(NavHistoryElementData::class.java)
              .toJson(navHistoryElementDataJson)
          }
          ?: return null

        return NavHistoryElementIdEntity(
          id = 0L,
          navHistoryElementDataJson = navHistoryElementDataJson,
          type = NavHistoryElementIdEntity.TYPE_THREAD_DESCRIPTOR
        )
      }
    }
  }

  fun toNavHistoryElementInfoEntity(
    navHistoryId: Long,
    navHistoryElement: NavHistoryElement,
    order: Int
  ): NavHistoryElementInfoEntity {
    return NavHistoryElementInfoEntity(
      ownerNavHistoryId = navHistoryId,
      thumbnailUrl = navHistoryElement.navHistoryElementInfo.thumbnailUrl,
      title = navHistoryElement.navHistoryElementInfo.title,
      order = order,
      pinned = navHistoryElement.navHistoryElementInfo.pinned
    )
  }

  fun fromNavHistoryEntity(navHistoryEntity: NavHistoryFullDto, moshi: Moshi): NavHistoryElement? {
    val navHistoryElementDataJson = navHistoryEntity.navHistoryElementIdEntity.navHistoryElementDataJson

    val navHistoryElementData = moshi.adapter(NavHistoryElementData::class.java)
      .fromJson(navHistoryElementDataJson)

    if (navHistoryElementData == null) {
      return null
    }

    val navHistoryElementInfo = NavHistoryElementInfo(
      navHistoryEntity.navHistoryElementInfoEntity.thumbnailUrl,
      navHistoryEntity.navHistoryElementInfoEntity.title,
      navHistoryEntity.navHistoryElementInfoEntity.pinned
    )

    when (val type = navHistoryEntity.navHistoryElementIdEntity.type) {
      NavHistoryElementIdEntity.TYPE_THREAD_DESCRIPTOR -> {
        val threadDescriptor = navHistoryElementData.chanDescriptorsData.firstOrNull()
          ?.threadDescriptorOrNull()
          ?: return null

        return NavHistoryElement.Thread(threadDescriptor, navHistoryElementInfo)
      }
      NavHistoryElementIdEntity.TYPE_CATALOG_DESCRIPTOR -> {
        val catalogDescriptor = navHistoryElementData.chanDescriptorsData.firstOrNull()
          ?.catalogDescriptor()
          ?: return null

        return NavHistoryElement.Catalog(catalogDescriptor, navHistoryElementInfo)
      }
      NavHistoryElementIdEntity.TYPE_COMPOSITE_CATALOG_DESCRIPTOR -> {
        val catalogDescriptors = navHistoryElementData.chanDescriptorsData.map { it.catalogDescriptor() }

        return NavHistoryElement.CompositeCatalog(
          descriptor = ChanDescriptor.CompositeCatalogDescriptor.create(catalogDescriptors),
          navHistoryElementInfo = navHistoryElementInfo
        )
      }
      else -> {
        Logger.e(TAG, "fromNavHistoryEntity() Unknown type: $type")
        return null
      }
    }
  }

}