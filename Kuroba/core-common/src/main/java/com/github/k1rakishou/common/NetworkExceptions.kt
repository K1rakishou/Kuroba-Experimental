package com.github.k1rakishou.common

import java.io.IOException

class BadStatusResponseException(val status: Int) : IOException("Bad status code: $status") {

  fun isAuthError(): Boolean {
    return status == 401
  }

  fun isForbiddenError(): Boolean {
    return status == 403
  }

  fun isNotFoundError(): Boolean {
    return status == 404
  }

}

class EmptyBodyResponseException : IOException("Response has no body")
class NotFoundException : IOException("Not found")