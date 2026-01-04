package com.github.k1rakishou.chan.ui.captcha.chan4

import org.junit.Assert.assertEquals
import org.junit.Test

class Chan4CaptchaTitleFormatterTest {
  @Test
  fun `invalid display property value should be unwrapped`() {
    val title = """
      Find the image that has <b>exactly</b> <b style="display:none">3</b><b style="display:nonen">6 empty boxes</b>.
    """.trimIndent()

    val output = Chan4CaptchaTitleFormatter().format(title)
    assertEquals("Find the image that has exactly 6 empty boxes.", output.text)
    assertEquals(2, output.spanStyles.size)
  }

  @Test
  fun `opacity property value is set to 0`() {
    val title = """
      Use the scroll bar below to find the image that has <b>exactly</b> <b style="display:nnone">3 empty boxes</b><b style="opacity:0;width:1px;display:inline-block;">5</b>, then click Next.
    """.trimIndent()

    val output = Chan4CaptchaTitleFormatter().format(title)
    assertEquals("Find the image that has exactly 3 empty boxes", output.text)
    assertEquals(2, output.spanStyles.size)
  }

  @Test
  fun `width property value is too small`() {
    val title = """
      Use the scroll bar below to find the image that has <b>exactly</b> <b style="display:nnone">3 empty boxes</b><b style="width:1px;display:inline-block;">5</b>, then click Next.
    """.trimIndent()

    val output = Chan4CaptchaTitleFormatter().format(title)
    assertEquals("Find the image that has exactly 3 empty boxes", output.text)
    assertEquals(2, output.spanStyles.size)
  }
}