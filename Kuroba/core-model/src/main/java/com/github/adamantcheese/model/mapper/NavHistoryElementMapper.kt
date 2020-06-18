package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.navigation.NavHistoryElement
import com.github.adamantcheese.model.data.navigation.NavHistoryElementInfo
import com.github.adamantcheese.model.entity.chan.ChanThreadEntity
import com.github.adamantcheese.model.entity.navigation.NavHistoryElementIdEntity
import com.github.adamantcheese.model.entity.navigation.NavHistoryElementInfoEntity
import com.github.adamantcheese.model.entity.navigation.NavHistoryFullDto

object NavHistoryElementMapper {

  fun toNavHistoryElementIdEntity(navHistoryElement: NavHistoryElement): NavHistoryElementIdEntity {
    return when (navHistoryElement) {
      is NavHistoryElement.Catalog -> {
        NavHistoryElementIdEntity(
          0L,
          navHistoryElement.descriptor.siteName(),
          navHistoryElement.descriptor.boardCode()
        )
      }
      is NavHistoryElement.Thread -> {
        NavHistoryElementIdEntity(
          0L,
          navHistoryElement.descriptor.siteName(),
          navHistoryElement.descriptor.boardCode(),
          navHistoryElement.descriptor.opNo
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
      navHistoryId,
      navHistoryElement.navHistoryElementInfo.thumbnailUrl,
      navHistoryElement.navHistoryElementInfo.title,
      order
    )
  }

  fun fromNavHistoryEntity(navHistoryEntity: NavHistoryFullDto): NavHistoryElement {
    if (navHistoryEntity.navHistoryElementIdEntity.threadNo == ChanThreadEntity.NO_THREAD_ID) {
      return NavHistoryElement.Catalog(
        ChanDescriptor.CatalogDescriptor.create(
          navHistoryEntity.navHistoryElementIdEntity.siteName,
          navHistoryEntity.navHistoryElementIdEntity.boardCode
        ),
        NavHistoryElementInfo(
          navHistoryEntity.navHistoryElementInfoEntity.thumbnailUrl,
          navHistoryEntity.navHistoryElementInfoEntity.title
        )
      )
    } else {
      return NavHistoryElement.Thread(
        ChanDescriptor.ThreadDescriptor.create(
          navHistoryEntity.navHistoryElementIdEntity.siteName,
          navHistoryEntity.navHistoryElementIdEntity.boardCode,
          navHistoryEntity.navHistoryElementIdEntity.threadNo
        ),
        NavHistoryElementInfo(
          navHistoryEntity.navHistoryElementInfoEntity.thumbnailUrl,
          navHistoryEntity.navHistoryElementInfoEntity.title
        )
      )
    }
  }

}