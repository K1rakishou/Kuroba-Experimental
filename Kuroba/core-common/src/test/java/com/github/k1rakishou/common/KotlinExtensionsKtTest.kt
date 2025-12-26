package com.github.k1rakishou.common

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class KotlinExtensionsKtTest {

  @Test
  fun testSingleCookie() {
    val requestBuilder = Request.Builder()
      .url("http://test.com")
      .get()

    requestBuilder
      .addOrReplaceCookieHeader("aaabbb=abc")
      .addOrReplaceCookieHeader("test_cookie=123")

    assertEquals("aaabbb=abc; test_cookie=123", requestBuilder.build().header("Cookie"))

    requestBuilder
      .addOrReplaceCookieHeader("test_cookie=124")

    assertEquals("aaabbb=abc; test_cookie=124", requestBuilder.build().header("Cookie"))

    requestBuilder
      .addOrReplaceCookieHeader("aaabbb=aaa")

    assertEquals("aaabbb=aaa; test_cookie=124", requestBuilder.build().header("Cookie"))

    requestBuilder
      .addOrReplaceCookieHeader("test_cookie=125")
      .addOrReplaceCookieHeader("aaabbb=bbb")

    assertEquals("aaabbb=bbb; test_cookie=125", requestBuilder.build().header("Cookie"))
  }

  @Test
  fun testMultipleCookies() {
    val requestBuilder = Request.Builder()
      .url("http://test.com")
      .get()

    requestBuilder
      .addOrReplaceCookieHeader("a=1; b=2; c=3")
      .addOrReplaceCookieHeader("d=4; e=5; f=777")

    assertEquals("a=1; b=2; c=3; d=4; e=5; f=777", requestBuilder.build().header("Cookie"))

    requestBuilder
      .addOrReplaceCookieHeader("a=10; b=20; c=33")

    assertEquals("a=10; b=20; c=33; d=4; e=5; f=777", requestBuilder.build().header("Cookie"))
  }

  @Test
  fun testInvalidCookie() {
    val requestBuilder = Request.Builder()
      .url("http://test.com")
      .get()

    requestBuilder
      .addOrReplaceCookieHeader("a=1; b=2; c=3")

    assertEquals("a=1; b=2; c=3", requestBuilder.build().header("Cookie"))
  }

}