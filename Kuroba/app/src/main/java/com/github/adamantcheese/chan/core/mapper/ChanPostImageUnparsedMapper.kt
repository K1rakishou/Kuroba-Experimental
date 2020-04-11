package com.github.adamantcheese.chan.core.mapper

import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.model.data.post.ChanPostImageUnparsed

object ChanPostImageUnparsedMapper {

    @JvmStatic
    fun fromPostImage(postImage: PostImage): ChanPostImageUnparsed {
        return ChanPostImageUnparsed(
                postImage.serverFilename,
                postImage.thumbnailUrl,
                postImage.spoilerThumbnailUrl,
                postImage.imageUrl,
                postImage.filename,
                postImage.extension,
                postImage.imageWidth,
                postImage.imageHeight,
                postImage.spoiler(),
                postImage.isInlined,
                postImage.fileHash,
                postImage.type
        )
    }

}