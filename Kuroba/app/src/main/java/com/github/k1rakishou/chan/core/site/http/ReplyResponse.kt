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

import com.github.k1rakishou.chan.features.posting.LastReplyRepository
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor.Companion.create
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

class ReplyResponse {
  @JvmField
  @get:Synchronized
  @set:Synchronized
  var posted = false

  @JvmField
  @get:Synchronized
  @set:Synchronized
  var errorMessage: CharSequence? = null

  @JvmField
  @get:Synchronized
  @set:Synchronized
  var siteDescriptor: SiteDescriptor? = null

  @JvmField
  @get:Synchronized
  @set:Synchronized
  var boardCode = ""

  @JvmField
  @get:Synchronized
  @set:Synchronized
  var threadNo = 0L

  @JvmField
  @get:Synchronized
  @set:Synchronized
  var postNo = 0L

  @JvmField
  @get:Synchronized
  @set:Synchronized
  var password = ""

  @JvmField
  @get:Synchronized
  @set:Synchronized
  var banInfo: BanInfo? = null

  @get:Synchronized
  @set:Synchronized
  var requireAuthentication = false

  @get:Synchronized
  @set:Synchronized
  var additionalResponseData: AdditionalResponseData = AdditionalResponseData.NoOp

  @get:Synchronized
  @set:Synchronized
  var rateLimitInfo: RateLimitInfo? = null

  @get:Synchronized
  @set:Synchronized
  var captchaSolution: CaptchaSolution? = null

  val errorMessageShort: CharSequence?
    get() = errorMessage?.take(256)

  @get:Synchronized
  val postDescriptorOrNull: PostDescriptor?
    get() {
      if (banInfo != null || requireAuthentication || postNo <= 0L || threadNo <= 0) {
        return null
      }

      return create(siteDescriptor!!.siteName, boardCode, threadNo, postNo)
    }

  constructor() {

  }

  constructor(other: ReplyResponse) : this(
    other.posted,
    other.errorMessage,
    other.siteDescriptor,
    other.boardCode,
    other.threadNo,
    other.postNo,
    other.password,
    other.banInfo,
    other.requireAuthentication,
    other.additionalResponseData,
    other.rateLimitInfo,
  )

  constructor(
    posted: Boolean,
    errorMessage: CharSequence?,
    siteDescriptor: SiteDescriptor?,
    boardCode: String,
    threadNo: Long,
    postNo: Long,
    password: String,
    banInfo: BanInfo?,
    requireAuthentication: Boolean,
    additionalResponseData: AdditionalResponseData,
    rateLimitInfo: RateLimitInfo?
  ) {
    this.posted = posted
    this.errorMessage = errorMessage
    this.siteDescriptor = siteDescriptor
    this.boardCode = boardCode
    this.threadNo = threadNo
    this.postNo = postNo
    this.password = password
    this.banInfo = banInfo
    this.requireAuthentication = requireAuthentication
    this.additionalResponseData = additionalResponseData
    this.rateLimitInfo = rateLimitInfo
  }

  fun asFormattedText(): String {
    return buildString(capacity = 128) {
      appendLine("ThreadId: ${siteDescriptor?.siteName}/${boardCode}/${threadNo}/${postNo}")
      appendLine("Posted: ${posted}")
      appendLine("RequireAuthentication: ${requireAuthentication}")
      appendLine("Password: ${password}")
      appendLine("BanInfo: ${banInfo}")
      appendLine("RequireAuthentication: ${requireAuthentication}")
      appendLine("ErrorMessage: ${errorMessageShort}")
      appendLine("RateLimitInfo: ${rateLimitInfo}")

      if (additionalResponseData !is AdditionalResponseData.NoOp) {
        appendLine("AdditionalResponseData: ${additionalResponseData}")
      }
    }
  }

  sealed class AdditionalResponseData {
    data object NoOp : AdditionalResponseData()
  }

  data class RateLimitInfo(
    val actualTimeToWaitMs: Long,
    val cooldownInfo: LastReplyRepository.CooldownInfo
  )

  sealed interface BanInfo {
    data object Banned : BanInfo
    data object Warned : BanInfo
  }

  override fun toString(): String {
    return "ReplyResponse{" +
      "posted=" + posted +
      ", errorMessage='" + errorMessageShort + '\'' +
      ", siteDescriptor=" + siteDescriptor +
      ", boardCode='" + boardCode + '\'' +
      ", threadNo=" + threadNo +
      ", postNo=" + postNo +
      ", password='" + password + '\'' +
      ", banInfo=" + banInfo +
      ", requireAuthentication=" + requireAuthentication +
      ", rateLimitInfo=" + rateLimitInfo +
      ", additionalResponseData=" + additionalResponseData +
      '}'
  }

}