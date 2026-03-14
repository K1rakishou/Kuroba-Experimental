package com.github.k1rakishou.chan.core.site.http

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody.ProgressRequestListener
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

abstract class HttpCall(val site: Site) {
  @Throws(IOException::class)
  abstract suspend fun setup(requestBuilder: Request.Builder, progressListener: ProgressRequestListener?)
  abstract suspend fun process(response: Response, result: String)
  
  sealed class HttpCallWithProgressResult<out T : HttpCall> {
    class Success<T: HttpCall>(val httpCall: T) : HttpCallWithProgressResult<T>()
    class Progress(val fileIndex: Int, val totalFiles: Int, val percent: Int) : HttpCallWithProgressResult<Nothing>()
    class Fail<T: HttpCall>(val httpCall: T, val error: Throwable) : HttpCallWithProgressResult<T>()
  }
  
  sealed class HttpCallResult<T : HttpCall> {
    class Success<T: HttpCall>(val httpCall: T) : HttpCallResult<T>()
    class Fail<T: HttpCall>(val httpCall: T, val error: Throwable) : HttpCallResult<T>()
  }

  class HttpCallNotCalledException : Exception("Http call was not called first")
  class BadResponseCodeException(code: Int) : Exception("Bad response code: $code")
  class BadResponseBodyException(details: String) : Exception("Bad response body: $details")

  companion object {
    private const val TAG = "HttpCall"
  }

}