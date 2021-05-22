package com.github.k1rakishou.model.data.id

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

@JvmInline
value class PostDBId(val id: Long)
@JvmInline
value class ThreadDBId(val id: Long)
@JvmInline
value class BoardDBId(val id: Long)
@JvmInline
value class ThreadBookmarkDBId(val id: Long)
@JvmInline
value class ThreadBookmarkDescriptor(val threadDescriptor: ChanDescriptor.ThreadDescriptor)