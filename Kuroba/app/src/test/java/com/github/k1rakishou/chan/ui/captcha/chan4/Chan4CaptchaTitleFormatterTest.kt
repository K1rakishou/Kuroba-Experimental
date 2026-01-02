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
    assertEquals(
      "Find the image that has exactly 6 empty boxes.",
      output.text
    )
  }
}