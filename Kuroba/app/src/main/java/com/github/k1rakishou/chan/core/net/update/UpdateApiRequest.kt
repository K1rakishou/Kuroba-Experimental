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

import android.text.Spanned
import androidx.core.text.toSpanned
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.net.update.UpdateApiRequest.ReleaseUpdateApiResponse
import com.github.k1rakishou.common.jsonArray
import com.github.k1rakishou.common.jsonObject
import com.google.gson.stream.JsonReader
import dagger.Lazy
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.regex.Matcher
import java.util.regex.Pattern

class UpdateApiRequest(
  request: Request,
  proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>,
  private val isRelease: Boolean
) : JsonReaderRequest<ReleaseUpdateApiResponse>(request, proxiedOkHttpClient) {
  
  override suspend fun readJson(reader: JsonReader): ReleaseUpdateApiResponse {
    val response = ReleaseUpdateApiResponse()
    
    reader.jsonObject {
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "tag_name" -> readVersionCode(response, reader)
          "name" -> response.updateTitle = reader.nextString()
          "assets" -> readApkUrl(reader, response)
          "body" -> readBody(reader, response)
          else -> reader.skipValue()
        }
      }
    }
    
    if (response.versionCode == 0 || response.apkURL == null || response.body == null) {
      throw UpdateRequestError("Update API response is incomplete! " +
        "versionCode=${response.versionCode}, " +
        "apkURL=${response.apkURL}, " +
        "hasBody=${response.body != null}")
    }
    
    return response
  }
  
  private fun readBody(reader: JsonReader, responseRelease: ReleaseUpdateApiResponse) {
    val updateComment = reader.nextString()
    responseRelease.body = "Changelog:\n${updateComment}".trimIndent().toSpanned()
  }
  
  private fun readApkUrl(reader: JsonReader, responseRelease: ReleaseUpdateApiResponse) {
    try {
      reader.jsonArray {
        while (hasNext()) {
          if (responseRelease.apkURL == null) {
            jsonObject {
              while (hasNext()) {
                if ("browser_download_url" == nextName()) {
                  responseRelease.apkURL = nextString().toHttpUrl()
                } else {
                  skipValue()
                }
              }
            }
          } else {
            skipValue()
          }
        }
      }
    } catch (e: Exception) {
      throw UpdateRequestError("No APK URL!")
    }
  }
  
  @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
  private fun readVersionCode(responseRelease: ReleaseUpdateApiResponse, reader: JsonReader) {
    try {
      responseRelease.versionCodeString = reader.nextString()
      val versionPattern = if (isRelease) {
        RELEASE_VERSION_CODE_PATTERN
      } else {
        BETA_VERSION_CODE_PATTERN
      }

      val versionMatcher = versionPattern.matcher(responseRelease.versionCodeString)
      
      if (versionMatcher.find()) {
        responseRelease.versionCode = calculateVersionCode(versionMatcher)
      }
    } catch (e: Exception) {
      throw UpdateRequestError("Tag name wasn't of the form v(major).(minor).(patch)!")
    }
  }
  
  @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
  private fun calculateVersionCode(versionMatcher: Matcher) =
    versionMatcher.group(3).toInt() +
      versionMatcher.group(2).toInt() * 100 +
      versionMatcher.group(1).toInt() * 10000

  class ReleaseUpdateApiResponse(
    var versionCode: Int = 0,
    var versionCodeString: String? = null,
    var updateTitle: String = "",
    var apkURL: HttpUrl? = null,
    var body: Spanned? = null
  ) {

    override fun toString(): String {
      return "ReleaseUpdateApiResponse{versionCode=$versionCode, versionCodeString=${versionCodeString}, " +
        "updateTitle={$updateTitle}, apkURL=${apkURL}, body=${body?.take(60)}"
    }
  }

  companion object {
    private const val TAG = "ReleaseUpdateApiRequest"

    private val RELEASE_VERSION_CODE_PATTERN = Pattern.compile("v(\\d+?)\\.(\\d{1,2})\\.(\\d{1,2})-release$")
    private val BETA_VERSION_CODE_PATTERN = Pattern.compile("v(\\d+?)\\.(\\d{1,2})\\.(\\d{1,2})-beta$")
  }
}