package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.post.ChanPostHttpIcon
import com.github.k1rakishou.model.entity.chan.post.ChanPostHttpIconEntity

object ChanPostHttpIconMapper {

  fun toEntity(ownerPostId: Long, chanPostHttpIcon: ChanPostHttpIcon): ChanPostHttpIconEntity {
    return ChanPostHttpIconEntity(
      iconUrl = chanPostHttpIcon.iconUrl,
      ownerPostId = ownerPostId,
      iconName = chanPostHttpIcon.iconName
    )
  }

  fun fromEntity(postIcon: ChanPostHttpIconEntity): ChanPostHttpIcon {
    return ChanPostHttpIcon(
      postIcon.iconUrl,
      postIcon.iconName
    )
  }

}