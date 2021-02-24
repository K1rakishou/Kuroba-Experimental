package com.github.k1rakishou.model.data.id

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

inline class PostDBId(val id: Long)
inline class ThreadDBId(val id: Long)
inline class BoardDBId(val id: Long)
inline class ThreadBookmarkDBId(val id: Long)
inline class ThreadBookmarkDescriptor(val threadDescriptor: ChanDescriptor.ThreadDescriptor)