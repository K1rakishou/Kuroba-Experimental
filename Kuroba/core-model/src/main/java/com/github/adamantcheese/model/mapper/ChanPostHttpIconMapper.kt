package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.post.ChanPostHttpIcon
import com.github.adamantcheese.model.entity.chan.post.ChanPostHttpIconEntity

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