package com.github.k1rakishou.model.source.cache

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class GenericCacheSourceTest {

  @Test
  fun `test cache trim`() {
    val cache = GenericSuspendableCacheSource<String, String>(2, 4, 2)

    runBlocking {
      cache.store("1", "a")
      cache.store("2", "b")
      assertEquals(2, cache.size())

      cache.store("3", "c")
      cache.store("4", "d")
      cache.store("5", "e")
      assertEquals(3, cache.size())
      assertTrue(cache.contains("3"))
      assertEquals("c", cache.get("3"))
      assertTrue(cache.contains("4"))
      assertEquals("d", cache.get("4"))
      assertTrue(cache.contains("5"))
      assertEquals("e", cache.get("5"))

      cache.store("6", "f")
      assertEquals(4, cache.size())
      assertTrue(cache.contains("3"))
      assertEquals("c", cache.get("3"))
      assertTrue(cache.contains("4"))
      assertEquals("d", cache.get("4"))
      assertTrue(cache.contains("5"))
      assertEquals("e", cache.get("5"))
      assertTrue(cache.contains("6"))
      assertEquals("f", cache.get("6"))

      cache.store("7", "g")
      assertEquals(3, cache.size())
      assertTrue(cache.contains("5"))
      assertEquals("e", cache.get("5"))
      assertTrue(cache.contains("6"))
      assertEquals("f", cache.get("6"))
      assertTrue(cache.contains("7"))
      assertEquals("g", cache.get("7"))
    }
  }

}