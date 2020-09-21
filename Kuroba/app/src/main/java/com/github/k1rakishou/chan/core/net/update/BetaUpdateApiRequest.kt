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
package com.github.k1rakishou.chan.core.net.update

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.common.jsonObject
import com.google.gson.stream.JsonReader
import okhttp3.Request

class BetaUpdateApiRequest(
  request: Request,
  okHttpClient: ProxiedOkHttpClient
) : JsonReaderRequest<BetaUpdateApiRequest.DevUpdateApiResponse>(
  JsonRequestType.BetaUpdateApiJsonRequest,
  request,
  okHttpClient
) {
  
  override suspend fun readJson(reader: JsonReader): DevUpdateApiResponse {
    var responseCode: Int? = null
    var commitHash: String? = null
  
    reader.jsonObject {
      while (hasNext()) {
        when (nextName()) {
          "apk_version" -> responseCode = nextInt()
          "commit_hash" -> commitHash = nextString()
          else -> reader.skipValue()
        }
      }
    }
  
    if (responseCode == null || commitHash == null) {
      throw UpdateRequestError("Update API response is incomplete, issue with beta apk server API!")
    }
  
    return DevUpdateApiResponse(responseCode!!, commitHash!!)
  }
  
  data class DevUpdateApiResponse(
    val versionCode: Int,
    val commitHash: String
  )
  
  companion object {
    private const val TAG = "BetaUpdateApiRequest"
  }
}