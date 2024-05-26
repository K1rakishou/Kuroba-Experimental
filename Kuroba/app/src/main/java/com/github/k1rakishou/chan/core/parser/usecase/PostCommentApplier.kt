package com.github.k1rakishou.chan.core.parser.usecase

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.core.parser.MarkedPost
import com.github.k1rakishou.chan.core.parser.MarkedPostType
import com.github.k1rakishou.chan.core.parser.ParsedPostDataContext
import com.github.k1rakishou.chan.core.parser.RevealedSpoiler
import com.github.k1rakishou.chan.core.parser.TextPart
import com.github.k1rakishou.chan.core.parser.TextPartSpan
import com.github.k1rakishou.chan.utils.buildAnnotatedString
import com.github.k1rakishou.chan.utils.createAnnotationItem
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

interface PostCommentApplier {

  suspend fun applyTextPartsToAnnotatedString(
    postDescriptor: PostDescriptor,
    chanTheme: ChanTheme,
    markedPosts: Map<PostDescriptor, Set<MarkedPost>>,
    textParts: List<TextPart>,
    parsedPostDataContext: ParsedPostDataContext
  ): AnnotatedString

}

class PostCommentApplierImpl(
  // TODO: compose post cells.
//  private val postBindProcessorCoordinator: PostBindProcessorCoordinator
) : PostCommentApplier {

  override suspend fun applyTextPartsToAnnotatedString(
    postDescriptor: PostDescriptor,
    chanTheme: ChanTheme,
    markedPosts: Map<PostDescriptor, Set<MarkedPost>>,
    textParts: List<TextPart>,
    parsedPostDataContext: ParsedPostDataContext
  ): AnnotatedString {
    val capacity = textParts.sumOf { it.text.length }

    return buildAnnotatedString(capacity = capacity) {
      var totalLength = 0

      for (textPart in textParts) {
        val text = processTextPart(
          postDescriptor = postDescriptor,
          markedPosts = markedPosts,
          chanTheme = chanTheme,
          textPart = textPart,
          parsedPostDataContext = parsedPostDataContext,
          totalLength = totalLength
        )

        append(text)
        totalLength += text.length
      }
    }
  }

  private suspend fun processTextPart(
    postDescriptor: PostDescriptor,
    markedPosts: Map<PostDescriptor, Set<MarkedPost>>,
    chanTheme: ChanTheme,
    textPart: TextPart,
    parsedPostDataContext: ParsedPostDataContext,
    totalLength: Int,
  ): AnnotatedString {
    // TODO: compose post cells.
//    val appliedDataResult = postBindProcessorCoordinator.applyData(
//      textPart = textPart,
//      postDescriptor = postDescriptor
//    )
//
//    val textPartProcessed = appliedDataResult.textPart

    val resultString = buildAnnotatedString(capacity = textPart.text.length) {
      pushStyle(style = SpanStyle(fontSize = parsedPostDataContext.postCommentFontSizePixels.sp))

      try {
        // TODO: compose post cells.
//        val innerAnnotatedString = buildAnnotatedString(capacity = textPartText.length) {
//          appliedDataResult.applyToAnnotatedString(textPartText, this)
//        }
//        append(innerAnnotatedString)

        append(textPart.text)
      } catch (error: Throwable) {
        // TODO: compose post cells.
//        if (androidHelpers.isDevFlavor()) {
//          throw error
//        }
//
//        Logger.error(TAG) {
//          "applyToAnnotatedString() error: ${error.errorMessageOrClassName()}, " +
//            "textPartText: ${textPartText}, appliedDataResult: ${appliedDataResult}"
//        }

        append(textPart.text)
      }

      if (textPart.spans.isNotEmpty()) {
        processTextPartSpans(
          defaultPostCommentFontSize = parsedPostDataContext.postCommentFontSizePixels,
          markedPosts = markedPosts,
          spans = textPart.spans,
          chanTheme = chanTheme,
          parsedPostDataContext = parsedPostDataContext,
          totalLength = totalLength
        )
      }
    }

    return resultString
  }

  private fun AnnotatedString.Builder.processTextPartSpans(
    defaultPostCommentFontSize: Int,
    markedPosts: Map<PostDescriptor, Set<MarkedPost>>,
    spans: List<TextPartSpan>,
    chanTheme: ChanTheme,
    parsedPostDataContext: ParsedPostDataContext,
    totalLength: Int
  ) {
    for (span in spans) {
      var bgColor: Color = Color.Unspecified
      var fgColor: Color = Color.Unspecified
      var underline = false
      var linethrough = false
      var annotationTag: String? = null
      var annotationValue: String? = null
      var bold = false
      var italic = false
      var script: Script? = null
      var currentFontSize = defaultPostCommentFontSize
      var fontFamily: FontFamily? = null

      var start: Int? = null
      var end: Int? = null

      when (span) {
        is TextPartSpan.PartialSpan -> {
          start = span.start
          end = span.end
          underline = true

          when (span.linkSpan) {
            is TextPartSpan.Linkable.Url -> {
              fgColor = chanTheme.postLinkColorCompose
            }
            else -> {
              error("${span.linkSpan::class.java.simpleName} is not supported as a partial span")
            }
          }

          if (parsedPostDataContext.isParsingThread) {
            annotationTag = ANNOTATION_POST_LINKABLE
          }

          annotationValue = span.linkSpan.createAnnotationItem()
        }
        is TextPartSpan.BgColor -> {
          bgColor = Color(span.color)
        }
        is TextPartSpan.FgColor -> {
          fgColor = Color(span.color)
        }
        is TextPartSpan.BgColorId -> {
          bgColor = chanTheme.getComposeColorByColorId(span.colorId)
        }
        is TextPartSpan.FgColorId -> {
          fgColor = chanTheme.getComposeColorByColorId(span.colorId)
        }
        is TextPartSpan.Heading -> {
          currentFontSize = span.calculateNewFontSize(currentFontSize)
        }
        is TextPartSpan.FontSize -> {
          currentFontSize = span.calculateNewFontSize(currentFontSize)
        }
        is TextPartSpan.Spoiler -> {
          bgColor = chanTheme.postSpoilerColorCompose

          val shouldRevealSpoiler = matchesOpenedSpoilerPosition(
            startPos = totalLength,
            endPos = totalLength + this.length,
            revealedSpoilers = parsedPostDataContext.revealedSpoilers
          )

          fgColor = if (shouldRevealSpoiler) {
            chanTheme.postSpoilerRevealTextColorCompose
          } else {
            chanTheme.postSpoilerColorCompose
          }

          annotationTag = ANNOTATION_POST_SPOILER_TEXT
        }
        is TextPartSpan.Underline -> {
          underline = true
        }
        is TextPartSpan.Linethrough -> {
          linethrough = true
        }
        is TextPartSpan.Bold -> {
          bold = true
        }
        is TextPartSpan.Italic -> {
          italic = true
        }
        is TextPartSpan.Superscript -> {
          script = Script.Super
        }
        is TextPartSpan.Subscript -> {
          script = Script.Sub
        }
        is TextPartSpan.Monospace -> {
          fontFamily = FontFamily.Monospace
        }
        is TextPartSpan.Linkable -> {
          when (span) {
            is TextPartSpan.Linkable.Quote,
            is TextPartSpan.Linkable.Board,
            is TextPartSpan.Linkable.Search,
            is TextPartSpan.Linkable.Url -> {
              underline = true

              if (span is TextPartSpan.Linkable.Quote) {
                if (span.dead) {
                  this.append(" ")
                  this.append(DEAD_POSTFIX)

                  linethrough = true
                }

                if (span.postDescriptor.isOP() && !span.crossThread) {
                  this.append(" ")
                  this.append(OP_POSTFIX)
                }

                val markedPostInfoSet = markedPosts[span.postDescriptor]
                if (markedPostInfoSet != null && markedPostInfoSet.isNotEmpty()) {
                  markedPostInfoSet.forEach { markedPost ->
                    when (markedPost.markedPostType) {
                      MarkedPostType.MyPost -> {
                        this.append(" ")
                        this.append(YOU_POSTFIX)
                      }
                    }
                  }
                }

                if (span.crossThread) {
                  this.append(" ")
                  this.append(CROSS_THREAD_POSTFIX)
                }

                bold = parsedPostDataContext.boldPostDescriptor == span.postDescriptor
              }

              fgColor = if (span is TextPartSpan.Linkable.Url) {
                chanTheme.postLinkColorCompose
              } else if (
                span is TextPartSpan.Linkable.Quote
                && parsedPostDataContext.boldPostDescriptor == span.postDescriptor
              ) {
                ThemeEngine.manipulateColor(chanTheme.postQuoteColorCompose, 0.7f)
              } else {
                chanTheme.postQuoteColorCompose
              }
            }
          }

          if (parsedPostDataContext.isParsingThread) {
            annotationTag = ANNOTATION_POST_LINKABLE
          }

          annotationValue = span.createAnnotationItem()
        }
      }

      val fontWeight = if (bold) {
        FontWeight.ExtraBold
      } else {
        null
      }

      val fontStyle = if (italic) {
        FontStyle.Italic
      } else {
        null
      }

      val baselineShift = when (script) {
        Script.Sub -> BaselineShift.Subscript
        Script.Super -> BaselineShift.Superscript
        null -> null
      }

      val actualFontSize = if (currentFontSize == defaultPostCommentFontSize) {
        TextUnit.Unspecified
      } else {
        currentFontSize.sp
      }

      val spanStyle = SpanStyle(
        color = fgColor,
        fontSize = actualFontSize,
        background = bgColor,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontFamily = fontFamily,
        baselineShift = baselineShift,
        textDecoration = buildTextDecoration(underline, linethrough)
      )

      addStyle(
        style = spanStyle,
        start = start ?: 0,
        end = end ?: this.length
      )

      if (annotationTag != null) {
        addStringAnnotation(
          tag = annotationTag,
          annotation = annotationValue ?: "",
          start = start ?: 0,
          end = end ?: this.length
        )
      }
    }
  }

  private fun buildTextDecoration(
    underline: Boolean,
    linethrough: Boolean,
  ): TextDecoration? {
    if (!underline && !linethrough) {
      return null
    }

    var textDecoration = TextDecoration.None

    if (underline) {
      textDecoration += TextDecoration.Underline
    }

    if (linethrough) {
      textDecoration += TextDecoration.LineThrough
    }

    return textDecoration
  }

  private fun matchesOpenedSpoilerPosition(
    startPos: Int,
    endPos: Int,
    revealedSpoilers: Set<RevealedSpoiler>
  ): Boolean {
    for (position in revealedSpoilers) {
      if (position.start == startPos && position.end == endPos) {
        return true
      }
    }

    return false
  }

  private enum class Script {
    Sub,
    Super
  }

  companion object {
    private const val TAG = "PostCommentApplier"
    private const val ELLIPSIZE = "..."

    private const val SEARCH_QUERY_SPAN = "search_query_span"

    const val ANNOTATION_POST_LINKABLE = "[post_linkable]"
    const val ANNOTATION_POST_SPOILER_TEXT = "[spoiler_text]"
    const val ANNOTATION_INLINED_IMAGE = "[inlined_image]"

    private const val CROSS_THREAD_POSTFIX = "(CT) \u2192"
    private const val OP_POSTFIX = "(OP)"
    private const val DEAD_POSTFIX = "(Dead)"
    private const val YOU_POSTFIX = "(You)"

    val ALL_TAGS = mutableSetOf(
      ANNOTATION_POST_LINKABLE,
      ANNOTATION_POST_SPOILER_TEXT,
      ANNOTATION_INLINED_IMAGE
    )
  }

}