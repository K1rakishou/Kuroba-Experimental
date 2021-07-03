package com.github.k1rakishou.common

import java.io.IOException

class BadStatusResponseException(val status: Int) : IOException("Bad status code: $status")
class EmptyBodyResponseException : IOException("Response has no body")
class NotFoundException : IOException("Not found")