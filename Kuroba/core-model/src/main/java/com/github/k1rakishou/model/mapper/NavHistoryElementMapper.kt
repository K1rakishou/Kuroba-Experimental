package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.navigation.NavHistoryElement
import com.github.k1rakishou.model.data.navigation.NavHistoryElementInfo
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity
import com.github.k1rakishou.model.entity.navigation.NavHistoryElementIdEntity
import com.github.k1rakishou.model.entity.navigation.NavHistoryElementInfoEntity
import com.github.k1rakishou.model.entity.navigation.NavHistoryFullDto

object NavHistoryElementMapper {

  fun toNavHistoryElementIdEntity(navHistoryElement: NavHistoryElement): NavHistoryElementIdEntity {
    return when (navHistoryElement) {
      is NavHistoryElement.Catalog -> {
        NavHistoryElementIdEntity(
          id = 0L,
          siteName = navHistoryElement.descriptor.siteName(),
          boardCode = navHistoryElement.descriptor.boardCode()
        )
      }
      is NavHistoryElement.Thread -> {
        NavHistoryElementIdEntity(
          id = 0L,
          siteName = navHistoryElement.descriptor.siteName(),
          boardCode = navHistoryElement.descriptor.boardCode(),
          threadNo = navHistoryElement.descriptor.threadNo
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

  fun fromNavHistoryEntity(navHistoryEntity: NavHistoryFullDto): NavHistoryElement {
    if (navHistoryEntity.navHistoryElementIdEntity.threadNo == ChanThreadEntity.NO_THREAD_ID) {
      return NavHistoryElement.Catalog(
        descriptor = ChanDescriptor.CatalogDescriptor.create(
          siteNameInput = navHistoryEntity.navHistoryElementIdEntity.siteName,
          boardCodeInput = navHistoryEntity.navHistoryElementIdEntity.boardCode
        ),
        navHistoryElementInfo = NavHistoryElementInfo(
          thumbnailUrl = navHistoryEntity.navHistoryElementInfoEntity.thumbnailUrl,
          title = navHistoryEntity.navHistoryElementInfoEntity.title,
          pinned = navHistoryEntity.navHistoryElementInfoEntity.pinned
        )
      )
    } else {
      return NavHistoryElement.Thread(
        descriptor = ChanDescriptor.ThreadDescriptor.create(
          siteName = navHistoryEntity.navHistoryElementIdEntity.siteName,
          boardCode = navHistoryEntity.navHistoryElementIdEntity.boardCode,
          threadNo = navHistoryEntity.navHistoryElementIdEntity.threadNo
        ),
        navHistoryElementInfo = NavHistoryElementInfo(
          thumbnailUrl = navHistoryEntity.navHistoryElementInfoEntity.thumbnailUrl,
          title = navHistoryEntity.navHistoryElementInfoEntity.title,
          pinned = navHistoryEntity.navHistoryElementInfoEntity.pinned
        )
      )
    }
  }

}