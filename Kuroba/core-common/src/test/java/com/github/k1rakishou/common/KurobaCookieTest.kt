package com.github.k1rakishou.common

import junit.framework.Assert.assertEquals
import org.junit.Test

class KurobaCookieTest {
  @Test
  fun `should be able to extract cookie by key and Max-Age expiration parameter`() {
    val now = System.currentTimeMillis()

    val cookie = """
      POW_TOKEN=38bd34627525d66bfc2b4bb9e6cc5311d76e4311b3e53899e0704f3d903df403; Path=/; Max-Age=46800; SameSite=Lax; HttpOnly
    """.trimIndent()

    val kurobaCookie = KurobaCookie.fromRawCookie(cookie, "POW_TOKEN")!!
    assertEquals("38bd34627525d66bfc2b4bb9e6cc5311d76e4311b3e53899e0704f3d903df403", kurobaCookie.value)
    kurobaCookie.expiration as KurobaCookie.Expiration.Time

    val deltaSeconds = (kurobaCookie.expiration.expirationTimeMillis - now) / 60 / 1000
    assert(deltaSeconds in 46800..47000) { "Bad deltaSeconds" }

    assertEquals("/", kurobaCookie.path)
  }

  @Test
  fun `same as above but case sensitive`() {
    val now = System.currentTimeMillis()

    val cookie = """
      POW_TOKEN=38bd34627525d66bfc2b4bb9e6cc5311d76e4311b3e53899e0704f3d903df403; pAth=/; mAx-aGe=46800; SameSite=Lax; HttpOnly
    """.trimIndent()

    val kurobaCookie = KurobaCookie.fromRawCookie(cookie, "POW_TOKEN")!!
    assertEquals("38bd34627525d66bfc2b4bb9e6cc5311d76e4311b3e53899e0704f3d903df403", kurobaCookie.value)
    kurobaCookie.expiration as KurobaCookie.Expiration.Time

    val deltaSeconds = (kurobaCookie.expiration.expirationTimeMillis - now) / 60 / 1000
    assert(deltaSeconds in 46800..47000) { "Bad deltaSeconds" }

    assertEquals("/", kurobaCookie.path)
  }

  @Test
  fun `should be able to handle extra equal signs in cookie values`() {
    val now = System.currentTimeMillis()

    val cookie = """
      POW_TOKEN=38bd34627525d66bfc2b4bb9e6cc5311d76e4311b3e53899e0704f3d903df403==; Path=/; Max-Age=46800; SameSite=Lax; HttpOnly
    """.trimIndent()

    val kurobaCookie = KurobaCookie.fromRawCookie(cookie, "POW_TOKEN")!!
    assertEquals("38bd34627525d66bfc2b4bb9e6cc5311d76e4311b3e53899e0704f3d903df403==", kurobaCookie.value)
    kurobaCookie.expiration as KurobaCookie.Expiration.Time

    val deltaSeconds = (kurobaCookie.expiration.expirationTimeMillis - now) / 60 / 1000
    assert(deltaSeconds in 46800..47000) { "Bad deltaSeconds" }

    assertEquals("/", kurobaCookie.path)
  }

  @Test
  fun `should be able to extract cookie by key and Expires expiration parameter`() {
    val cookie = """
      POW_ID=57efc99ef502a4338d05d5493a1a40b417ddf5239e594c1b9788d2a4b2cb2500; Path=/test; Expires=Wed, 21 Oct 2026 07:28:00 GMT; SameSite=Lax; HttpOnly
    """.trimIndent()

    val kurobaCookie = KurobaCookie.fromRawCookie(cookie, "POW_ID")!!
    assertEquals("57efc99ef502a4338d05d5493a1a40b417ddf5239e594c1b9788d2a4b2cb2500", kurobaCookie.value)
    kurobaCookie.expiration as KurobaCookie.Expiration.Time

    val expirationTimeMillis = kurobaCookie.expiration.expirationTimeMillis
    assertEquals(1792567680000, expirationTimeMillis)

    assertEquals("/test", kurobaCookie.path)
  }

  @Test
  fun `should return null if cookie by key not found`() {
    val cookie = """
      POW_ID=57efc99ef502a4338d05d5493a1a40b417ddf5239e594c1b9788d2a4b2cb2500; Path=/; Expires=Wed, 21 Oct 2026 07:28:00 GMT; SameSite=Lax; HttpOnly
    """.trimIndent()

    val kurobaCookie = KurobaCookie.fromRawCookie(cookie, "test")
    assertEquals(null, kurobaCookie)
  }

  @Test
  fun `should return Session expiration type if there is no expiration parameter in the cookie`() {
    val cookie = """
      POW_ID=57efc99ef502a4338d05d5493a1a40b417ddf5239e594c1b9788d2a4b2cb2500; SameSite=Lax; HttpOnly
    """.trimIndent()

    val kurobaCookie = KurobaCookie.fromRawCookie(cookie, "POW_ID")!!
    assertEquals("57efc99ef502a4338d05d5493a1a40b417ddf5239e594c1b9788d2a4b2cb2500", kurobaCookie.value)
    kurobaCookie.expiration as KurobaCookie.Expiration.Session

    assertEquals("/", kurobaCookie.path)
  }

  @Test
  fun `should handle empty value`() {
    val cookie = """
      POW_ID=; Path=/; SameSite=Lax; HttpOnly
    """.trimIndent()

    val kurobaCookie = KurobaCookie.fromRawCookie(cookie, "POW_ID")!!
    assertEquals("", kurobaCookie.value)
    kurobaCookie.expiration as KurobaCookie.Expiration.Session
  }

  @Test
  fun `regression with Kohlchan cookies, expiration was parsed as Session`() {
    val cookie = """
      bypass=697b7bdcb5873aa82f4ddde6; expires=Thu, 05 Feb 2026 15:31:22 GMT; path=/
    """.trimIndent()

    val kurobaCookie = KurobaCookie.fromRawCookie(cookie, "bypass")!!
    assertEquals("697b7bdcb5873aa82f4ddde6", kurobaCookie.value)

    kurobaCookie.expiration as KurobaCookie.Expiration.Time
    assertEquals(kurobaCookie.expiration.expirationTimeMillis, 1770305482000L)

    assertEquals("/", kurobaCookie.path)
  }
}