package com.github.k1rakishou.chan.utils

import junit.framework.Assert.assertEquals
import org.junit.Test

class ConversionUtilsTest {

  @Test
  fun simple_tests() {
    assertEquals(350L * 1024 * 1024, ConversionUtils.fileSizeRawToFileSizeInBytes("350.00 MB"))
    assertEquals(350L * 1024 * 1024, ConversionUtils.fileSizeRawToFileSizeInBytes("350 MB"))
    assertEquals(350L * 1024 * 1024, ConversionUtils.fileSizeRawToFileSizeInBytes("350        MB"))
    assertEquals(1024L, ConversionUtils.fileSizeRawToFileSizeInBytes("1 kb"))
    assertEquals(1024L, ConversionUtils.fileSizeRawToFileSizeInBytes("1024 B"))
    assertEquals(1L, ConversionUtils.fileSizeRawToFileSizeInBytes("1 B"))
    assertEquals(0L, ConversionUtils.fileSizeRawToFileSizeInBytes("0 B"))
  }

  @Test
  fun tests_with_fraction() {
    assertEquals(((1L * 1024 * 1024) + (.1234f * 1024f * 1024f)).toLong(), ConversionUtils.fileSizeRawToFileSizeInBytes("1.1234 MB"))
    assertEquals(((1024L * 1024 * 1024) + (.0001234f * 1024f * 1024f)).toLong(), ConversionUtils.fileSizeRawToFileSizeInBytes("1024.0001234 MB"))
    assertEquals(((.1f * 1024f * 1024f)).toLong(), ConversionUtils.fileSizeRawToFileSizeInBytes("0.1 MB"))
    assertEquals(((10L * 1024) + (.01f * 1024f)).toLong(), ConversionUtils.fileSizeRawToFileSizeInBytes("10.01 KB"))
  }
}