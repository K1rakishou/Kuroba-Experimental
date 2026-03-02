package com.github.k1rakishou.chan.core.site.parser

import android.text.Spannable
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder

interface PostParser {
  fun parseNameAndSubject(builder: ChanPostBuilder)
  fun parseFull(builder: ChanPostBuilder, callback: Callback): ChanPost
  fun parseComment(post: ChanPostBuilder, commentRaw: CharSequence, callback: Callback): Spannable

  interface Callback {
    fun isSaved(postDescriptor: PostDescriptor): Boolean
    fun isHiddenOrRemoved(postDescriptor: PostDescriptor): Int
    fun isInternal(postDescriptor: PostDescriptor): Boolean
    fun isParsingCatalogPosts(): Boolean
  }

  companion object {
    const val NORMAL_POST: Int = -1
    const val HIDDEN_POST: Int = 0
    const val REMOVED_POST: Int = 1
  }
}
