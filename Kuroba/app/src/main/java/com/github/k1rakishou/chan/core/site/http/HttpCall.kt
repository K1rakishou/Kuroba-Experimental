/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.site.http

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody.ProgressRequestListener
import okhttp3.Request
import okhttp3.Response

/**
 * Http calls are an abstraction over a normal OkHttp call.
 *
 * These HttpCalls are used for emulating &lt;form&gt; elements used for posting, reporting, deleting, etc.
 *
 * Implement [.setup] and [.process].
 * `setup()` is called on the main thread, set up up the request builder here. `execute()` is
 * called on a worker thread after the response was executed, do something with the response here.
 */
abstract class HttpCall(val site: Site) {

  abstract fun setup(requestBuilder: Request.Builder, progressListener: ProgressRequestListener?)
  abstract fun process(response: Response, result: String)
  
  sealed class HttpCallWithProgressResult<out T : HttpCall> {
    class Success<T: HttpCall>(val httpCall: T) : HttpCallWithProgressResult<T>()
    class Progress(val percent: Int) : HttpCallWithProgressResult<Nothing>()
    class Fail<T: HttpCall>(val httpCall: T, val error: Throwable) : HttpCallWithProgressResult<T>()
  }
  
  sealed class HttpCallResult<T : HttpCall> {
    class Success<T: HttpCall>(val httpCall: T) : HttpCallResult<T>()
    class Fail<T: HttpCall>(val httpCall: T, val error: Throwable) : HttpCallResult<T>()
  }

  companion object {
    private const val TAG = "HttpCall"
  }

}