package com.github.k1rakishou.chan.core.base.okhttp.interceptor

import androidx.annotation.GuardedBy
import okhttp3.Interceptor

abstract class KurobaOkHttpInterceptor : Interceptor {
  @GuardedBy("this")
  var okHttpType: String = "null"
    set(value) {
      synchronized(this) { field = value }
    }
    get() = synchronized(this) { field }
}