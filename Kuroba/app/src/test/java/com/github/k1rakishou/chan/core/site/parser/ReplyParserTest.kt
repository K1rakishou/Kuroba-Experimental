package com.github.k1rakishou.chan.core.site.parser

import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.ParserRepository
import com.github.k1rakishou.chan.core.site.sites.Diochan
import com.github.k1rakishou.chan.core.site.sites.Lainchan
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class ReplyParserTest {
  lateinit var siteManager: SiteManager
  lateinit var archivesManager: ArchivesManager
  lateinit var parserRepository: ParserRepository
  lateinit var replyParser: ReplyParser

  @Before
  fun init() {
    AndroidUtils.init(RuntimeEnvironment.application)
    ShadowLog.stream = System.out

    siteManager = Mockito.mock(SiteManager::class.java)
    archivesManager = Mockito.mock(ArchivesManager::class.java)
    parserRepository = ParserRepository(archivesManager)

    replyParser = ReplyParser(siteManager, parserRepository)
  }

  @Test
  fun `test extract quotes from 2ch_hk comment`() {
    whenever(siteManager.bySiteDescriptor(any())).thenReturn(Dvach())

    val replies = replyParser.extractCommentReplies(SiteDescriptor.create("2ch.hk"), DVACH_PARSER_TEST_COMMENT)
    assertEquals(2, replies.size)
    assertTrue(replies.all { it is ReplyParser.ExtractedQuote.FullQuote })

    assertEquals("b", (replies[0] as ReplyParser.ExtractedQuote.FullQuote).boardCode)
    assertEquals(223016610, (replies[0] as ReplyParser.ExtractedQuote.FullQuote).threadId)
    assertEquals(223019606, (replies[0] as ReplyParser.ExtractedQuote.FullQuote).postId)

    assertEquals("b", (replies[1] as ReplyParser.ExtractedQuote.FullQuote).boardCode)
    assertEquals(223016610, (replies[1] as ReplyParser.ExtractedQuote.FullQuote).threadId)
    assertEquals(223019770, (replies[1] as ReplyParser.ExtractedQuote.FullQuote).postId)
  }

  @Test
  fun `test extract quotes from 4chan_org comment`() {
    whenever(siteManager.bySiteDescriptor(any())).thenReturn(Chan4())

    val replies = replyParser.extractCommentReplies(SiteDescriptor.create("4chan.org"), COMMON_PARSER_TEST_COMMENT)
    assertEquals(3, replies.size)
    assertTrue(replies.all { it is ReplyParser.ExtractedQuote.Quote })

    assertEquals(296403883, (replies[0] as ReplyParser.ExtractedQuote.Quote).postId)
    assertEquals(296404474, (replies[1] as ReplyParser.ExtractedQuote.Quote).postId)
    assertEquals(296404782, (replies[2] as ReplyParser.ExtractedQuote.Quote).postId)
  }

  @Test
  fun `test extract quotes from diochan_com comment`() {
    whenever(siteManager.bySiteDescriptor(any())).thenReturn(Diochan())

    val replies = replyParser.extractCommentReplies(SiteDescriptor.create("diochan.com"), VICHAN_PARSER_TEST_COMMENT)
    assertEquals(2, replies.size)
    assertTrue(replies.all { it is ReplyParser.ExtractedQuote.Quote })

    assertEquals(28948, (replies[0] as ReplyParser.ExtractedQuote.Quote).postId)
    assertEquals(28950, (replies[1] as ReplyParser.ExtractedQuote.Quote).postId)
  }

  @Test
  fun `test extract quotes from lainchan_org comment`() {
    whenever(siteManager.bySiteDescriptor(any())).thenReturn(Lainchan())

    val replies = replyParser.extractCommentReplies(SiteDescriptor.create("lainchan.org"), VICHAN_PARSER_TEST_COMMENT)
    assertEquals(2, replies.size)
    assertTrue(replies.all { it is ReplyParser.ExtractedQuote.Quote })

    assertEquals(28948, (replies[0] as ReplyParser.ExtractedQuote.Quote).postId)
    assertEquals(28950, (replies[1] as ReplyParser.ExtractedQuote.Quote).postId)
  }

  companion object {
    private const val DVACH_PARSER_TEST_COMMENT = "<a href=\\\"/b/res/223016610.html#223019606\\\" " +
      "class=\\\"post-reply-link\\\" data-thread=\\\"223016610\\\" data-num=\\\"223019606\\\">>>223019606</a><br>" +
      "<a href=\\\"/b/res/223016610.html#223019770\\\" class=\\\"post-reply-link\\\" data-thread=\\\"223016610\\\" " +
      "data-num=\\\"223019770\\\">>>223019770</a><br><br>Лол. Вспоминаю Лиона Эль Джонса, который " +
      "родную планету почти уничтожил, что бы добраться до глав гада, а потом решил его не убивать." +
      "<br>Тоже херовый сюжет, мм?<br>Или звёздные войны, где Люк хотел отомстить за смерть родителей, " +
      "учителя, целой планеты, но потом ВНИЗАПНО передумывает убивать Дарт Вэйдэра.<br>А ну точно, " +
      "ЭТО ДРУГОЕ РЯЯ. Извините. Как я сразу не заметил.<br><span class=\\\"spoiler\\\">и тебе добра, " +
      "няша</span>"

    private const val COMMON_PARSER_TEST_COMMENT = "<a href=\\\"#p296403883\\\" class=\\\"quotelink\\\">" +
      "&gt;&gt;296403883</a><br><a href=\\\"#p296404474\\\" class=\\\"quotelink\\\">&gt;&gt;296404474</a>" +
      "<br><a href=\\\"#p296404782\\\" class=\\\"quotelink\\\">&gt;&gt;296404782</a>"

    private const val VICHAN_PARSER_TEST_COMMENT = "<a onclick=\\\"highlightReply('28948', event);" +
      "\\\" href=\\\"/Ω/res/28808.html#28948\\\">&gt;&gt;28948</a><br/><a onclick=\\\"" +
      "highlightReply('28950', event);\\\" href=\\\"/Ω/res/28808.html#28950\\\">&gt;&gt;28950</a><br/>" +
      "oh, also, you don't *necessarily* get the same coins out as you put in. " +
      "The tumbler just gives you some coins."
  }
}