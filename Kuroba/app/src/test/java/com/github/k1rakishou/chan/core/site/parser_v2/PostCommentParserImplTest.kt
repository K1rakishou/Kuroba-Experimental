package com.github.k1rakishou.chan.core.site.parser_v2

import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.parser.TextPart
import com.github.k1rakishou.chan.core.parser.TextPartSpan
import com.github.k1rakishou.chan.core.repository.StaticHtmlColorRepositoryImpl
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.comment.HtmlParserPool
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class PostCommentParserImplTest {

  private lateinit var siteManager: SiteManager
  private lateinit var postCommentParser: PostCommentParser

  private val postDescriptor = PostDescriptor.Companion.create("Test", "test", 1234L, 12345L)
  private val dvachPostParser = DvachPostParser(StaticHtmlColorRepositoryImpl())

  @Before
  fun setup() {
    Logger.setupUnitTest()

    siteManager = Mockito.mock(SiteManager::class.java)

    postCommentParser = PostCommentParserImpl(
      siteManager,
      HtmlParserPool()
    )
  }

  @Test
  fun `Test spoiler span wrapping other spans`() = runTest {
    val comment = """
      <span class="spoiler"><em>Также можешь почитать писанину анончика: <a href="https://author.today/work/51427" target="_blank" rel="nofollow noopener noreferrer">https://author.today/work/51427</a> и <a href="https://author.today/work/148606" target="_blank" rel="nofollow noopener noreferrer">https://author.today/work/148606</a> </em></span>
    """.trimIndent()

    val textParts = postCommentParser.parsePostComment(
      postParser = dvachPostParser,
      postCommentUnparsed = comment,
      postDescriptor = postDescriptor
    )

    assertTextPart(
      textPart = textParts[0],
      text = "Также можешь почитать писанину анончика: ",
      spans = listOf(TextPartSpan.Italic, TextPartSpan.Spoiler)
    )

    assertTextPart(
      textPart = textParts[1],
      text = "https://author.today/work/51427",
      spans = listOf(
        TextPartSpan.Linkable.Url("https://author.today/work/51427"),
        TextPartSpan.Italic,
        TextPartSpan.PartialSpan(start = 0, end = 31, linkSpan = TextPartSpan.Linkable.Url("https://author.today/work/51427")),
        TextPartSpan.Spoiler
      )
    )

    assertTextPart(
      textPart = textParts[2],
      text = " и ",
      spans = listOf(TextPartSpan.Italic, TextPartSpan.Spoiler)
    )

    assertTextPart(
      textPart = textParts[3],
      text = "https://author.today/work/148606",
      spans = listOf(
        TextPartSpan.Linkable.Url("https://author.today/work/148606"),
        TextPartSpan.Italic,
        TextPartSpan.PartialSpan(start = 0, end = 32, linkSpan = TextPartSpan.Linkable.Url("https://author.today/work/148606")),
        TextPartSpan.Spoiler
      )
    )

    assertTextPart(
      textPart = textParts[4],
      text = " ",
      spans = listOf(TextPartSpan.Italic, TextPartSpan.Spoiler)
    )
  }

  private fun assertTextPart(textPart: TextPart, text: String, spans: List<TextPartSpan>) {
    assertEquals(text, textPart.text)

    val textPartSpans = textPart.spans

    spans.forEachIndexed { index, expectedSpan ->
      val actualSpan = textPartSpans[index]
      assertEquals(expectedSpan, actualSpan)
    }
  }

}