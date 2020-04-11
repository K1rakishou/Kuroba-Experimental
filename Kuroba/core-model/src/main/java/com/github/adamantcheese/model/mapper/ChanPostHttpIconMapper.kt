package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.post.ChanPostHttpIconUnparsed
import com.github.adamantcheese.model.entity.ChanPostHttpIconEntity

object ChanPostHttpIconMapper {

    fun toEntity(ownerPostId: Long, chanPostHttpIconUnparsed: ChanPostHttpIconUnparsed): ChanPostHttpIconEntity {
        return ChanPostHttpIconEntity(
                iconUrl = chanPostHttpIconUnparsed.iconUrl,
                ownerPostId = ownerPostId,
                iconName = chanPostHttpIconUnparsed.iconName
        )
    }

}