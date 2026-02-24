package com.github.k1rakishou.chan.ui.captcha.chan4

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Chan4CaptchaTitleFormatterTest {
  @Test
  fun `invalid display property value should be unwrapped`() {
    val title = """
      Find the image that has <b>exactly</b> <b style="display:none">3</b><b style="display:nonen">6 empty boxes</b>.
    """.trimIndent()

    val annotated =
      (Chan4CaptchaTitleFormatter().format(title) as Chan4CaptchaTitleFormatter.Title.TextWithImage).annotated
    assertEquals("Find the image that has exactly 6 empty boxes.", annotated.text)
    assertEquals(2, annotated.spanStyles.size)
  }

  @Test
  fun `opacity property value is set to 0`() {
    val title = """
      Use the scroll bar below to find the image that has <b>exactly</b> <b style="display:nnone">3 empty boxes</b><b style="opacity:0;width:1px;display:inline-block;">5</b>, then click Next.
    """.trimIndent()

    val annotated =
      (Chan4CaptchaTitleFormatter().format(title) as Chan4CaptchaTitleFormatter.Title.TextWithImage).annotated
    assertEquals("Find the image that has exactly 3 empty boxes", annotated.text)
    assertEquals(2, annotated.spanStyles.size)
  }

  @Test
  fun `width property value is too small`() {
    val title = """
      Use the scroll bar below to find the image that has <b>exactly</b> <b style="display:nnone">3 empty boxes</b><b style="width:1px;display:inline-block;">5</b>, then click Next.
    """.trimIndent()

    val annotated =
      (Chan4CaptchaTitleFormatter().format(title) as Chan4CaptchaTitleFormatter.Title.TextWithImage).annotated
    assertEquals("Find the image that has exactly 3 empty boxes", annotated.text)
    assertEquals(2, annotated.spanStyles.size)
  }

  @Test
  fun `captcha image in title`() {
    val title = """
      <img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADwAAAA8BAMAAADI0sRBAAAAHlBMVEUFAwWGhYa7u7tPTk8wLzDq7OqipKRsbmwZGRjNzs51vc44AAAACXBIWXMAAA7EAAAOxAGVKw4bAAAB7UlEQVQ4jbWVy26rMBCGRwKJvADlCaJuc6Qj0SU7uiXSEOUBULZ2CUzYdZez7SLK6x6PbyXEZtVaiozz+fLPP4MBWm3wg7gr1vCI7RrOWzzFscQiP8bx0KgZTRTf1M4DFhH8oTfethHcHayAIJZo+jG8Wh6e1cwGPZS6TyJnQ627fQSPRhN+hfH2VXdDE8TS+VkG8WgWk0jkyR00wxf7WF2HvcrdAr8Q6JDOQDf8QptZcKLxVejAd9nHfsAm389w10x4LKqUzWHvbydp4tNYIm7v2PRwJZGx99JlVuO86ZAmLMSGoMxt3LfWYSw4HyrTsEnJJXRkdYwnM1+yumzy5ZAfDLZuUq7U+YFSWWo8ovsD7+rnfdplGvMmpk3q6d+D30D0XVyC57tyEInBdz//s86td3pgsBtru4/E3qmknsEeoMdk7Wbvaq47FqYxe6kDYbsH7Z2AFLw8Pu4vWbvZuwTeIfOY1aodlN0HdkGp29Cu/MYXqADeUvP+qHf4M5vHrkRBDZCZGn14h8GpUoG4Sm6XmFvpK7kO4VklB/HF2rcLryYb658ijM8Gd0/KTbOJH4PK/ZXZp2EsrLQsjG1mff4X2GT2Mr9cHu4hwaPqGsOc2T5ycZnl5VzY08eiShfbPeIekjVMol7Fi/ar+D8RSvQIaeK46gAAAABJRU5ErkJggg==" style="float:right;margin:10px 0 0 10px">Use the scroll bar below to find the image that contains the object on the right, then click Next.
    """.trimIndent()

    val output = Chan4CaptchaTitleFormatter().format(title) as Chan4CaptchaTitleFormatter.Title.TextWithImage

    val annotated = output.annotated
    val images = output.images

    assertEquals("Find the image that contains the object on the right", annotated.text)
    assertEquals(0, annotated.spanStyles.size)
    assertEquals(1, images.size)
  }
}