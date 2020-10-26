package com.github.k1rakishou.common

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Test

class KotlinExtensionsHighLowMapTest {

  @Test
  fun `test empty input`() {
    val exampleList = listOf<Int>()
    val resultList = exampleList.highLowMap { element -> element }

    assertTrue(resultList.isEmpty())
  }

  @Test
  fun `test one element`() {
    val exampleList = listOf<Int>(1)
    val resultList = exampleList.highLowMap { element -> element }

    assertEquals(1, resultList.size)
    assertEquals(1, resultList.first())
  }

  @Test
  fun `test two elements`() {
    val exampleList = listOf<Int>(1, 2)
    val resultList = exampleList.highLowMap { element -> element }

    assertEquals(2, resultList.size)
    assertEquals(2, resultList[0])
    assertEquals(1, resultList[1])
  }

  @Test
  fun `test many elements odd size`() {
    val exampleList = listOf<Int>(1, 2, 3, 4, 5, 6, 7, 8, 9)
    val resultList = exampleList.highLowMap { element -> element }

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
    val resultList = exampleList.highLowMap { element -> element }

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

}