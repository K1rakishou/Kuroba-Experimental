package com.github.k1rakishou.chan.utils

import com.github.k1rakishou.common.StringUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class StringUtilsTest {

  @Test
  fun `test dirNameRemoveBadCharacters`() {
    val testString = "123abcабв. [|?*<\":>+\\[\\]/']\n\r"
    val expectedString = "123abcабв_"

    assertEquals(expectedString, StringUtils.dirNameRemoveBadCharacters(testString))
  }

  @Test
  fun `test fileNameRemoveBadCharacters`() {
    val testString = "123abcабв.txt [|?*<\":>+\\[\\]/']\n\r"
    val expectedString = "123abcабв.txt_"

    assertEquals(expectedString, StringUtils.fileNameRemoveBadCharacters(testString))
  }

}