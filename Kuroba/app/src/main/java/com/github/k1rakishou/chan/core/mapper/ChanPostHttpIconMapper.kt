package com.github.k1rakishou.chan.core.mapper

import com.github.k1rakishou.chan.core.model.PostHttpIcon
import com.github.k1rakishou.model.data.post.ChanPostHttpIcon

object ChanPostHttpIconMapper {

    @JvmStatic
    fun fromPostHttpIcon(postHttpIcon: PostHttpIcon): ChanPostHttpIcon {
        return ChanPostHttpIcon(
                postHttpIcon.url,
                postHttpIcon.name
        )
    }

    @JvmStatic
    fun toPostIcon(chanPostHttpIcon: ChanPostHttpIcon): PostHttpIcon {
        return PostHttpIcon(
                chanPostHttpIcon.iconUrl,
                chanPostHttpIcon.iconName
        )
    }

}