package com.github.adamantcheese.model.data

import com.github.adamantcheese.model.data.descriptor.PostDescriptor

data class ChanPostUnparsed(
        val postDescriptor: PostDescriptor,
        var replies: Int = -1,
        var threadImagesCount: Int = -1,
        var uniqueIps: Int = -1,
        var lastModified: Long = -1L,
        var unixTimestampSeconds: Long = -1L,
        var idColor: Int = 0,
        var filterHighlightedColor: Int = 0,
        var postComment: CharSequence? = "",
        var subject: String? = null,
        var name: String? = null,
        var tripcode: String? = null,
        var posterId: String? = null,
        var moderatorCapcode: String? = null,
        var subjectSpan: CharSequence? = null,
        var nameTripcodeIdCapcodeSpan: CharSequence? = null,
        var isOp: Boolean = false,
        var sticky: Boolean = false,
        var closed: Boolean = false,
        var archived: Boolean = false,
        var isLightColor: Boolean = false,
        var isSavedReply: Boolean = false,
        var filterStub: Boolean = false,
        var filterRemove: Boolean = false,
        var filterWatch: Boolean = false,
        var filterReplies: Boolean = false,
        var filterOnlyOP: Boolean = false,
        var filterSaved: Boolean = false
)