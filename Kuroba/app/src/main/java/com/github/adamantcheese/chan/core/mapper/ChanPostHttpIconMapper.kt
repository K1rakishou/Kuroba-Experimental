package com.github.adamantcheese.chan.core.mapper

import com.github.adamantcheese.chan.core.model.PostHttpIcon
import com.github.adamantcheese.model.data.post.ChanPostHttpIcon

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