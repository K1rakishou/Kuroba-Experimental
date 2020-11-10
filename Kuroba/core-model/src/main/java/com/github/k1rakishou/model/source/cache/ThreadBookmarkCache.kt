package com.github.k1rakishou.model.source.cache

import com.github.k1rakishou.model.data.bookmark.ThreadBookmark
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

class ThreadBookmarkCache : GenericSuspendableCacheSource<ChanDescriptor.ThreadDescriptor, ThreadBookmark>()