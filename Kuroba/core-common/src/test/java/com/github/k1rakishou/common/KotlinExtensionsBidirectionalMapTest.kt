package com.github.k1rakishou.common

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Test

class KotlinExtensionsBidirectionalMapTest {

  @Test
  fun `test empty input`() {
    val exampleList = listOf<Int>()
    val resultList = exampleList.bidirectionalMap { element -> element }

    assertTrue(resultList.isEmpty())
  }

  @Test
  fun `test one element`() {
    val exampleList = listOf<Int>(1)
    val resultList = exampleList.bidirectionalMap { element -> element }

    assertEquals(1, resultList.size)
    assertEquals(1, resultList.first())
  }

  @Test
  fun `test two elements`() {
    val exampleList = listOf<Int>(1, 2)
    val resultList = exampleList.bidirectionalMap { element -> element }

    assertEquals(2, resultList.size)
    assertEquals(2, resultList[0])
    assertEquals(1, resultList[1])
  }

  @Test
  fun `test many elements odd size`() {
    val exampleList = listOf<Int>(1, 2, 3, 4, 5, 6, 7, 8, 9)
    val resultList = exampleList.bidirectionalMap { element -> element }

    assertEquals(9, resultList.size)
    assertEquals(5, resultList[0])
    assertEquals(6, resultList[1])
    assertEquals(4, resultList[2])
    assertEquals(7, resultList[3])
    assertEquals(3, resultList[4])
    assertEquals(8, resultList[5])
    assertEquals(2, resultList[6])
    assertEquals(9, resultList[7])
    assertEquals(1, resultList[8])
  }

  @Test
  fun `test many elements even size`() {
    val exampleList = listOf<Int>(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
    val resultList = exampleList.bidirectionalMap { element -> element }

    assertEquals(10, resultList.size)
    assertEquals(5, resultList[0])
    assertEquals(6, resultList[1])
    assertEquals(4, resultList[2])
    assertEquals(7, resultList[3])
    assertEquals(3, resultList[4])
    assertEquals(8, resultList[5])
    assertEquals(2, resultList[6])
    assertEquals(9, resultList[7])
    assertEquals(1, resultList[8])
    assertEquals(0, resultList[9])
  }

  @Test
  fun `test many elements startPosition is 0`() {
    val exampleList = listOf<Int>(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
    val resultList = exampleList.bidirectionalMap(startPosition = 0) { element -> element }

    assertEquals(10, resultList.size)
    assertEquals(0, resultList[0])
    assertEquals(1, resultList[1])
    assertEquals(2, resultList[2])
    assertEquals(3, resultList[3])
    assertEquals(4, resultList[4])
    assertEquals(5, resultList[5])
    assertEquals(6, resultList[6])
    assertEquals(7, resultList[7])
    assertEquals(8, resultList[8])
    assertEquals(9, resultList[9])
  }

  @Test
  fun `test many elements startPosition is 9`() {
    val exampleList = listOf<Int>(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
    val resultList = exampleList.bidirectionalMap(startPosition = 9) { element -> element }

    assertEquals(10, resultList.size)
    assertEquals(9, resultList[0])
    assertEquals(8, resultList[1])
    assertEquals(7, resultList[2])
    assertEquals(6, resultList[3])
    assertEquals(5, resultList[4])
    assertEquals(4, resultList[5])
    assertEquals(3, resultList[6])
    assertEquals(2, resultList[7])
    assertEquals(1, resultList[8])
    assertEquals(0, resultList[9])
  }

}