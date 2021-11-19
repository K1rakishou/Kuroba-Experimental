package com.github.k1rakishou.chan.utils

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNull
import org.junit.Test

class ConversionUtilsTest {
  private val MB = 1000 * 1000
  private val MiB = 1024 * 1024

  @Test
  fun simple_tests() {
    assertEquals(350L * MB, ConversionUtils.fileSizeRawToFileSizeInBytes("350.00 MB"))
    assertEquals(350L * MB, ConversionUtils.fileSizeRawToFileSizeInBytes("350 MB"))
    assertEquals(350L * MB, ConversionUtils.fileSizeRawToFileSizeInBytes("350        MB"))
    assertEquals(1000L, ConversionUtils.fileSizeRawToFileSizeInBytes("1 kb"))
    assertEquals(1024L, ConversionUtils.fileSizeRawToFileSizeInBytes("1024 B"))
    assertEquals(1L, ConversionUtils.fileSizeRawToFileSizeInBytes("1 B"))
    assertEquals(0L, ConversionUtils.fileSizeRawToFileSizeInBytes("0 B"))

    assertEquals(350L * MiB, ConversionUtils.fileSizeRawToFileSizeInBytes("350.00 MiB"))
    assertEquals(350L * MiB, ConversionUtils.fileSizeRawToFileSizeInBytes("350.00 mib"))
    assertEquals(350L * MiB, ConversionUtils.fileSizeRawToFileSizeInBytes("350.00 MIB"))
    assertEquals(1024L, ConversionUtils.fileSizeRawToFileSizeInBytes("1 KiB"))

    assertNull(ConversionUtils.fileSizeRawToFileSizeInBytes("1 TB"))
    assertNull(ConversionUtils.fileSizeRawToFileSizeInBytes("1 TiB"))
  }

  @Test
  fun tests_with_fraction() {
    assertEquals(((1L * MB) + (.1234f * MB.toFloat())).toLong(), ConversionUtils.fileSizeRawToFileSizeInBytes("1.1234 MB"))
    assertEquals(((1024L * MB) + (.0001234f * MB.toFloat())).toLong(), ConversionUtils.fileSizeRawToFileSizeInBytes("1024.0001234 MB"))
    assertEquals(((.1f * MB.toFloat())).toLong(), ConversionUtils.fileSizeRawToFileSizeInBytes("0.1 MB"))
    assertEquals(((10L * 1000) + (.01f * 1000f)).toLong(), ConversionUtils.fileSizeRawToFileSizeInBytes("10.01 KB"))
  }
}