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

import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor.Companion.create
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

class ReplyResponse {
  @JvmField
  var posted = false
  @JvmField
  var errorMessage: String? = null
  @JvmField
  var siteDescriptor: SiteDescriptor? = null
  @JvmField
  var boardCode = ""
  @JvmField
  var threadNo = 0L
  @JvmField
  var postNo = 0L
  @JvmField
  var password = ""
  @JvmField
  var probablyBanned = false

  var requireAuthentication = false

  var additionalResponseData: AdditionalResponseData? = null

  val postDescriptorOrNull: PostDescriptor?
    get() {
      if (probablyBanned || requireAuthentication || postNo <= 0L || threadNo <= 0) {
        return null
      }

      return create(siteDescriptor!!.siteName, boardCode, threadNo, postNo)
    }

  sealed class AdditionalResponseData {
    object DvachAntiSpamCheckDetected : AdditionalResponseData()
  }

  override fun toString(): String {
    return "ReplyResponse{" +
      "posted=" + posted +
      ", errorMessage='" + errorMessage + '\'' +
      ", siteDescriptor=" + siteDescriptor +
      ", boardCode='" + boardCode + '\'' +
      ", threadNo=" + threadNo +
      ", postNo=" + postNo +
      ", password='" + password + '\'' +
      ", probablyBanned=" + probablyBanned +
      ", requireAuthentication=" + requireAuthentication +
      '}'
  }
}