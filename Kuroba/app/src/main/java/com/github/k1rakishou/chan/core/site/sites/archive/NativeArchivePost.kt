package com.github.k1rakishou.chan.core.site.sites.archive

import android.text.Spanned

sealed class NativeArchivePost {
  data class Chan4NativeArchivePost(
    val threadNo: Long,
    val comment: Spanned
  ) : NativeArchivePost()
}