package com.github.k1rakishou.common

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class KotlinExtensionsKtTest {

    @Test
    fun testAddOrReplaceCookieHeader() {
        val requestBuilder = Request.Builder()
            .url("http://test.com")
            .get()

        requestBuilder
            .addOrReplaceCookieHeader("tttaaa=abc")
            .addOrReplaceCookieHeader("test_cookie=123")

        assertEquals("tttaaa=abc; test_cookie=123", requestBuilder.build().header("Cookie"))

        requestBuilder
            .addOrReplaceCookieHeader("test_cookie=124")

        assertEquals("tttaaa=abc; test_cookie=124", requestBuilder.build().header("Cookie"))

        requestBuilder
            .addOrReplaceCookieHeader("tttaaa=aaa")

        assertEquals("tttaaa=aaa; test_cookie=124", requestBuilder.build().header("Cookie"))

        requestBuilder
            .addOrReplaceCookieHeader("test_cookie=125")
            .addOrReplaceCookieHeader("tttaaa=bbb")

        assertEquals("tttaaa=bbb; test_cookie=125", requestBuilder.build().header("Cookie"))
    }

}