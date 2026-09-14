package com.github.k1rakishou.chan.features.view.media

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundPostUrlTest {

  @Test
  fun `underscore escaped url is restored`() {
    assertEquals("files.catbox.moe%2Fa0dpk9.m4a", restoreUnderscoreEscapedUrl("files.catbox.moe_2Fa0dpk9.m4a"))
    assertEquals("files.catbox.moe%2fa0dpk9.m4a", restoreUnderscoreEscapedUrl("files.catbox.moe_2fa0dpk9.m4a"))
  }

  @Test
  fun `normal urls are left untouched`() {
    assertEquals("files.catbox.moe/abc_12.mp3", restoreUnderscoreEscapedUrl("files.catbox.moe/abc_12.mp3"))
    assertEquals("files.catbox.moe%2Fabc_12.mp3", restoreUnderscoreEscapedUrl("files.catbox.moe%2Fabc_12.mp3"))
    assertEquals("files.catbox.moe%2Fa0dpk9.m4a", restoreUnderscoreEscapedUrl("files.catbox.moe%2Fa0dpk9.m4a"))
  }
}
