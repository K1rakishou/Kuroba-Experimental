package com.github.k1rakishou.common

import com.github.k1rakishou.common.StringUtils.splitOnce

class CookieBuilder(
  initialCookies: String? = null
) {
  private val _cookieParts = mutableListOf<Cookie>()

  init {
    if (initialCookies.isNotNullNorBlank()) {
      parseCookies(initialCookies)
        .forEach { cookie -> addOrReplace(cookie.key, cookie.value) }
    }
  }

  fun addOrReplace(cookies: String?) {
    if (cookies == null) {
      return
    }

    parseCookies(cookies)
      .forEach { cookiesPart -> addOrReplace(cookiesPart.key, cookiesPart.value) }
  }

  fun addOrReplace(key: String, value: String) {
    if (value.isEmpty()) {
      return
    }

    if (key.contains("=") || key.contains(";") || value.contains(";")) {
      error("Invalid cookie! key: '${key}', value: '${value}'")
    }

    val index = _cookieParts.indexOfFirst { (cookieKey, _) -> cookieKey.equals(key, ignoreCase = true) }
    if (index < 0) {
      _cookieParts.add(Cookie(key, value))
    } else {
      _cookieParts[index] = Cookie(key, value)
    }
  }

  fun get(key: String): Cookie? {
    return _cookieParts.firstOrNull { cookie -> cookie.key.equals(key, ignoreCase = true) }
  }

  fun containsAll(keys: List<String>): Boolean {
    return keys.all { key ->
      _cookieParts.any { cookie -> cookie.key.equals(key, ignoreCase = true) }
    }
  }

  fun isEmpty(): Boolean {
    return _cookieParts.isEmpty()
  }

  fun cookieParts(): List<Cookie> {
    return _cookieParts
  }

  fun retainAllIn(keys: Collection<String>) {
    val retained = mutableListOf<Cookie>()

    _cookieParts.forEach { cookie ->
      if (keys.any { key -> key.equals(cookie.key, ignoreCase = true) }) {
        retained.add(cookie)
      }
    }

    _cookieParts.clear()
    _cookieParts.addAll(retained)
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
      .split("; ")
      .map { part ->
        val split = part.splitOnce("=")
          ?: error("Bad cookie part: '${part}'")

        return@map Cookie(
          key = split.first.trim(),
          value = split.second.trim()
        )
      }
  }

  data class Cookie(
    val key: String,
    val value: String
  )

  override fun toString(): String {
    return _cookieParts.joinToString(separator = "; ", transform = { cookie -> "${cookie.key}=${cookie.value}" })
  }

  companion object {
    private const val TAG = "CookieBuilder"
  }
}