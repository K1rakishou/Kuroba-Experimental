package com.github.k1rakishou.common

class CookieBuilder(
  initialCookies: String? = null
) {
  private val _cookieParts = mutableListOf<Cookie>()

  init {
    if (initialCookies.isNotNullNorBlank()) {
      parseCookies(initialCookies)
        .forEach { cookie -> _cookieParts.add(cookie) }
    }
  }

  fun addOrReplace(key: String, value: String) {
    if (key.contains("=") || key.contains(";") || value.contains("=") || value.contains(";")) {
      error("Invalid cookie! key: '${key}', value: '${value}'")
    }

    val index = _cookieParts.indexOfFirst { (cookieKey, _) -> cookieKey.equals(key, ignoreCase = true) }
    if (index < 0) {
      _cookieParts.add(Cookie(key, value))
    } else {
      _cookieParts[index] = Cookie(key, value)
    }
  }

  fun addOrReplace(cookies: String?) {
    if (cookies == null) {
      return
    }

    parseCookies(cookies)
      .forEach { cookiesPart -> addOrReplace(cookiesPart.key, cookiesPart.value) }
  }

  fun isEmpty(): Boolean {
    return _cookieParts.isEmpty()
  }

  fun cookieParts(): List<Cookie> {
    return _cookieParts
  }

  fun build(): String {
    return buildString {
      _cookieParts.forEachIndexed { index, cookie ->
        if (index > 0) {
          append("; ")
        }

        val key = cookie.key
        val value = cookie.value

        append("${key}=${value}")
      }
    }
  }

  private fun parseCookies(cookies: String): List<Cookie> {
    return cookies
      .split(";")
      .map { part ->
        val split = part.trim().split("=")
        if (split.size != 2) {
          error("Bad cookie part: '${part}'")
        }

        return@map Cookie(key = split[0].trim(), value = split[1].trim())
      }
  }

  data class Cookie(
    val key: String,
    val value: String
  )

  companion object {
    private const val TAG = "CookieBuilder"
  }
}