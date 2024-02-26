package com.github.k1rakishou.chan.features.reply.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.site.PostFormatterButton
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import dagger.Lazy

class PostFormattingButtonsFactory(
  private val boardManagerLazy: Lazy<BoardManager>,
  private val themeEngineLazy: Lazy<ThemeEngine>
) {
  private val boardManager: BoardManager
    get() = boardManagerLazy.get()
  private val themeEngine: ThemeEngine
    get() = themeEngineLazy.get()

  suspend fun createPostFormattingButtons(boardDescriptor: BoardDescriptor): List<PostFormatterButton> {
    boardManager.awaitUntilInitialized()

    val chanBoard = boardManager.byBoardDescriptor(boardDescriptor)
    val is4chan = chanBoard?.boardDescriptor?.siteDescriptor?.is4chan() == true
    val isDvach = chanBoard?.boardDescriptor?.siteDescriptor?.isDvach() == true

    return buildList {
      add(quoteButton())

      if (isDvach || chanBoard?.spoilers == true) {
        add(spoilerButton())
      }

      if (is4chan && chanBoard?.boardCode() == "g") {
        add(codeButton())
      }

      if (is4chan && chanBoard?.boardCode() == "sci") {
        add(equationButton())
        add(mathButton())
      }

      if (is4chan && (chanBoard?.boardCode() == "jp" || chanBoard?.boardCode() == "vip")) {
        add(sjisButton())
      }

      if (isDvach) {
        add(boldButton())
        add(italicButton())
        add(underlineButton())
        add(strikethroughButton())
      }
    }
  }

  private fun strikethroughButton() = PostFormatterButton(
    title = AnnotatedString(text = "[s]", spanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)),
    openTag = "[s]",
    closeTag = "[/s]"
  )

  private fun underlineButton() = PostFormatterButton(
    title = AnnotatedString(text = "[u]", spanStyle = SpanStyle(textDecoration = TextDecoration.Underline)),
    openTag = "[u]",
    closeTag = "[/u]"
  )

  private fun italicButton() = PostFormatterButton(
    title = AnnotatedString(text = "[i]", spanStyle = SpanStyle(fontStyle = FontStyle.Italic)),
    openTag = "[i]",
    closeTag = "[/i]"
  )

  private fun boldButton() = PostFormatterButton(
    title = AnnotatedString(text = "[b]", spanStyle = SpanStyle(fontWeight = FontWeight.Bold)),
    openTag = "[b]",
    closeTag = "[/b]"
  )

  private fun sjisButton() = PostFormatterButton(
    title = AnnotatedString("[sjis]"),
    openTag = "[sjis]",
    closeTag = "[/sjis]"
  )

  private fun quoteButton() = PostFormatterButton(
    title = AnnotatedString(text = ">", spanStyle = SpanStyle(color = themeEngine.chanTheme.postInlineQuoteColorCompose)),
    openTag = ">",
    closeTag = ""
  )

  private fun mathButton() = PostFormatterButton(
    title = AnnotatedString("[math]"),
    openTag = "[math]",
    closeTag = "[/math]"
  )

  private fun equationButton() = PostFormatterButton(
    title = AnnotatedString("[eqn]"),
    openTag = "[eqn]",
    closeTag = "[/eqn]"
  )

  private fun codeButton() = PostFormatterButton(
    title = AnnotatedString("[c]", spanStyle = SpanStyle(fontFamily = FontFamily.Monospace)),
    openTag = "[code]",
    closeTag = "[/code]"
  )

  private fun spoilerButton() = PostFormatterButton(
    title = AnnotatedString(
      text = "[SP]",
      spanStyle = SpanStyle(background = Color.Black, color = Color.White)
    ),
    openTag = "[spoiler]", closeTag = "[/spoiler]"
  )

}