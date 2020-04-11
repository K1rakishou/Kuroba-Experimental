package com.github.adamantcheese.chan.core.mapper

import com.github.adamantcheese.chan.core.model.PostHttpIcon
import com.github.adamantcheese.model.data.post.ChanPostHttpIconUnparsed

object ChanPostHttpIconUnparsedMapper {

    fun fromPostHttpIcon(postHttpIcon: PostHttpIcon): ChanPostHttpIconUnparsed {
        return ChanPostHttpIconUnparsed(
                postHttpIcon.url,
                postHttpIcon.name
        )
    }

}