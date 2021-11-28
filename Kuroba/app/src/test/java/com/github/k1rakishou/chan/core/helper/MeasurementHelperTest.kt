package com.github.k1rakishou.chan.core.helper

import junit.framework.Assert.assertEquals
import org.junit.Test

class MeasurementHelperTest {

  @Test
  fun test_simple_vertical_measurement() {
    val measureHelper = MeasurementHelper()

    val result = measureHelper.measure {
      vertical {
        element { 10 }
        element { 20 }
        element { 30 }
      }
    }

    assertEquals(60, result.totalHeight)
    assertEquals(0, result.totalWidth)
  }

  @Test
  fun test_simple_horizontal_measurement() {
    val measureHelper = MeasurementHelper()

    val result = measureHelper.measure {
      horizontal {
        element { 10 }
        element { 20 }
        element { 30 }
      }
    }

    assertEquals(60, result.totalWidth)
    assertEquals(0, result.totalHeight)
  }

  @Test
  fun test_simple_vertical_maxof_measurement() {
    val measureHelper = MeasurementHelper()

    val result = measureHelper.measure {
      maxOfVertical {
        element { 10 }
        element { 45 }
        element { 30 }
      }
    }

    assertEquals(0, result.totalWidth)
    assertEquals(45, result.totalHeight)
  }

  @Test
  fun test_mixed_measurement() {
    val measureHelper = MeasurementHelper()

    val result = measureHelper.measure {
      vertical {
        element { 15 }

        horizontal {
          element { 10 }
          element { 20 }
          element { 30 }
        }

        element { 25 }
      }
    }

    assertEquals(60, result.totalWidth)
    assertEquals(40, result.totalHeight)
  }

}