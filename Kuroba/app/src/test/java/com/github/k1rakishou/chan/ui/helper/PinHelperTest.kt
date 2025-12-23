package com.github.k1rakishou.chan.ui.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class PinHelperTest {
  @Test
  fun validate() {
    assertEquals("0", PinHelper.getShortUnreadCount(0))
    assertEquals("999", PinHelper.getShortUnreadCount(999))
    assertEquals("1.0k", PinHelper.getShortUnreadCount(1000))
    assertEquals("1.6k", PinHelper.getShortUnreadCount(1600))
    assertEquals("999.0k", PinHelper.getShortUnreadCount(999_000))
    assertEquals("1.0m", PinHelper.getShortUnreadCount(1_000_000))
    assertEquals("1.6m", PinHelper.getShortUnreadCount(1_600_000))
    assertEquals("999.0m", PinHelper.getShortUnreadCount(999_000_000))
    assertEquals("1.0b", PinHelper.getShortUnreadCount(1_000_000_000))
    assertEquals("1.6b", PinHelper.getShortUnreadCount(1_600_000_000))
    assertEquals("2.1b", PinHelper.getShortUnreadCount(Int.MAX_VALUE))
    assertEquals("???", PinHelper.getShortUnreadCount(Int.MIN_VALUE))
  }
}