package com.github.k1rakishou.common

import okhttp3.HttpUrl
import java.io.IOException

interface ExceptionWithShortErrorMessage {
  fun shortErrorMessage(): String
}

class BadStatusResponseException(val status: Int) : IOException("Bad status: $status"), ExceptionWithShortErrorMessage {

  fun isAuthError(): Boolean {
    return status == 401
  }

  fun isForbiddenError(): Boolean {
    return status == 403
  }

  fun isNotFoundError(): Boolean {
    return status == 404
  }

  fun isRateLimitedError(): Boolean {
    return status == 429
  }

  fun isUnsatisfiableRangeStatus(): Boolean {
    return status == 416
  }

  override fun shortErrorMessage(): String {
    if (isAuthError()) {
      return "Auth error"
    }

    if (isForbiddenError()) {
      return "Forbidden error"
    }

    if (isNotFoundError()) {
      return "Not found"
    }

    if (isRateLimitedError()) {
      // 4chan returns 429 when too many report/post requests are made from
      // the same IP in a short window. The raw "Bad status: 429" message
      // surfaced in places like the post report dialog (issue #1102) is
      // confusing because it does not tell the user what to do. Show a
      // short, actionable message instead.
      return "Rate limited (429), try again later"
    }

    return "Bad status: ${status}"
  }

  companion object {
    fun notFoundResponse(): BadStatusResponseException = BadStatusResponseException(404)
  }

}

class EmptyBodyResponseException : IOException("Response has no body"), ExceptionWithShortErrorMessage {
  override fun shortErrorMessage(): String {
    return message!!
  }
}

class NotFoundException : IOException("Not found"), ExceptionWithShortErrorMessage {
  override fun shortErrorMessage(): String {
    return message!!
  }
}

class BadContentTypeException(contentType: String?) :
  Exception("Unexpected content type: '${contentType}'"), ExceptionWithShortErrorMessage {
  override fun shortErrorMessage(): String {
    return message!!
  }
}

class ParsingException(message: String) : IOException(message), ExceptionWithShortErrorMessage {
  override fun shortErrorMessage(): String {
    return "Parsing error"
  }
}

enum class FirewallType {
  Cloudflare,
  DvachAntiSpam,
  YandexSmartCaptcha
}

class FirewallDetectedException(
  val firewallType: FirewallType,
  val requestUrl: HttpUrl
) : IOException("Url '$requestUrl' cannot be opened without going through ${firewallType} checks first!")