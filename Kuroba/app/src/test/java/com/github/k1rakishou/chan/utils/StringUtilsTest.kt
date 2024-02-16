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

  @Test
  fun `test calculateSimilarity`() {
    assertEquals(1f, StringUtils.calculateSimilarity("", ""), 0.0001f)
    assertEquals(0f, StringUtils.calculateSimilarity("abd", "def"), 0.0001f)
    assertEquals(0f, StringUtils.calculateSimilarity("", "samples"), 0.0001f)
    assertEquals(0f, StringUtils.calculateSimilarity("example", ""), 0.0001f)
    assertEquals(0.9f, StringUtils.calculateSimilarity("1234567890", "123456789+"), 0.0001f)
    assertEquals(0.9f, StringUtils.calculateSimilarity("+234567890", "1234567890"), 0.0001f)

    assertEquals(
      0.9090909f,
      StringUtils.calculateSimilarity("(私は)厚い本をもらったけど読む時間がない。", "(私は)厚い本をもらったけど読む時間が。"),
      0.0001f
    )
  }

  @Test
  fun `test find json end 1`() {
    val html = """
            <!DOCTYPE html><html><head><meta charset="utf-8"><title></title>
            <script>window.parent.postMessage({"twister":{"ticket":"123","pcd":300,"pcd_msg":"Please wait a while before making a thread"}}, '*');</script>
            </head><body></body></html>
        """.trimIndent()

    val postMessageFunc = ".postMessage("

    val jsonStart = html.indexOf(postMessageFunc) + postMessageFunc.length

    val jsonEnd = StringUtils.findJsonEnd(html, jsonStart)!!
    val json = html.substring(jsonStart, jsonEnd)

    assertEquals(
      "{\"twister\":{\"ticket\":\"123\",\"pcd\":300,\"pcd_msg\":\"Please wait a while before making a thread\"}}",
      json
    )
  }

  @Test
  fun `test find json end 2`() {
    val html = """
            <!DOCTYPE html><html><head><meta charset="utf-8"><title></title>
            <script>window.parent.postMessage({"twister":{"challenge":"123","ttl":120,"cd":40,"img":"img","img_width":290,"img_height":80,"bg":"bg","bg_width":339}}, '*');</script>
            </head><body></body></html>
        """.trimIndent()

    val postMessageFunc = ".postMessage("

    val jsonStart = html.indexOf(postMessageFunc) + postMessageFunc.length

    val jsonEnd = StringUtils.findJsonEnd(html, jsonStart)!!
    val json = html.substring(jsonStart, jsonEnd)

    assertEquals(
      "{\"twister\":{\"challenge\":\"123\",\"ttl\":120,\"cd\":40,\"img\":\"img\",\"img_width\":290,\"img_height\":80,\"bg\":\"bg\",\"bg_width\":339}}",
      json
    )
  }

}