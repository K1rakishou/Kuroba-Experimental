package com.github.k1rakishou.chan.core.site.parser

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Test

class CommentParserHelperTest {

  @Test
  fun `split only text`() {
    val text = "Test text"

    val ranges = CommentParserHelper.splitTextIntoRanges(text)
    assertEquals(1, ranges.size)
    assertTrue(ranges.first() is CommentParserHelper.TextRange)

    val textRange = (ranges.first() as CommentParserHelper.TextRange)
    assertEquals(0, textRange.start)
    assertEquals(text.length, textRange.end)
  }

  @Test
  fun `split only link`() {
    val text = "https://archived.moe/a/thread/209942808#209942808"

    val ranges = CommentParserHelper.splitTextIntoRanges(text)
    assertEquals(1, ranges.size)
    assertTrue(ranges.first() is CommentParserHelper.LinkRange)

    val linkRange = (ranges.first() as CommentParserHelper.LinkRange)
    assertEquals(0, linkRange.start)
    assertEquals(text.length, linkRange.end)
  }

  @Test
  fun `split text and link`() {
    val text = "test https://archived.moe/a/thread/209942808#209942808"

    val ranges = CommentParserHelper.splitTextIntoRanges(text)
    assertEquals(2, ranges.size)

    val textRange = ranges.first()
    val linkRange = ranges.last()

    assertTrue(textRange is CommentParserHelper.TextRange)
    assertTrue(linkRange is CommentParserHelper.LinkRange)

    assertEquals("test ", text.substring(textRange.start, textRange.end))
    assertEquals("https://archived.moe/a/thread/209942808#209942808", text.substring(linkRange.start, linkRange.end))
  }

  @Test
  fun `split link and text`() {
    val text = "https://archived.moe/a/thread/209942808#209942808 test"

    val ranges = CommentParserHelper.splitTextIntoRanges(text)
    assertEquals(2, ranges.size)

    val linkRange = ranges.first()
    val textRange = ranges.last()

    assertTrue(textRange is CommentParserHelper.TextRange)
    assertTrue(linkRange is CommentParserHelper.LinkRange)

    assertEquals(" test", text.substring(textRange.start, textRange.end))
    assertEquals("https://archived.moe/a/thread/209942808#209942808", text.substring(linkRange.start, linkRange.end))
  }

  @Test
  fun `split mixed`() {
    val text = "test " +
      "https://archived.moe/a/thread/209942808#209942808 " +
      "test " +
      "https://archive.4plebs.org/x/thread/26413443/#26413443 " +
      "test " +
      "https://archiveofsins.com/t/thread/948290#948708 " +
      "test " +
      "https://arch.b4k.co/g/thread/78132268/#78132487 " +
      "test " +
      "https://desuarchive.org/a/thread/209958649#209960414 " +
      "test " +
      "https://archive.rebeccablacktech.com/g/thread/78154894#78154894 " +
      "test " +
      "https://tokyochronos.net/g/thread/78136348#78136931 " +
      "test " +
      "https://archive.nyafuu.org/c/thread/3743666#3746655 " +
      "test " +
      "https://boards.fireden.net/sci/thread/12218490#12218510"

    val ranges = CommentParserHelper.splitTextIntoRanges(text)
    assertEquals(18, ranges.size)

    ranges.forEachIndexed { index, range ->
      if (index % 2 == 0) {
        assertTrue(range is CommentParserHelper.TextRange)

        if (index == 0) {
          assertEquals("test ", text.substring(range.start, range.end))
        } else {
          assertEquals(" test ", text.substring(range.start, range.end))
        }
      } else {
        assertTrue(range is CommentParserHelper.LinkRange)
      }
    }

    val expectedLinks = arrayOf(
      "https://archived.moe/a/thread/209942808#209942808",
      "https://archive.4plebs.org/x/thread/26413443/#26413443",
      "https://archiveofsins.com/t/thread/948290#948708",
      "https://arch.b4k.co/g/thread/78132268/#78132487",
      "https://desuarchive.org/a/thread/209958649#209960414",
      "https://archive.rebeccablacktech.com/g/thread/78154894#78154894",
      "https://tokyochronos.net/g/thread/78136348#78136931",
      "https://archive.nyafuu.org/c/thread/3743666#3746655",
      "https://boards.fireden.net/sci/thread/12218490#12218510"
    )

    val linkRanges = ranges.filterIsInstance<CommentParserHelper.LinkRange>()

    linkRanges.forEachIndexed { index, range ->
      val expectedLink = expectedLinks[index]
      val actualLink = text.substring(range.start, range.end)

      assertEquals(expectedLink, actualLink)
    }
  }

}