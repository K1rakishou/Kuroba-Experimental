package com.github.k1rakishou.chan.core.parser.usecase

import android.text.format.DateUtils
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.MarkedPostManager
import com.github.k1rakishou.chan.core.parser.MarkedPost
import com.github.k1rakishou.chan.core.parser.MarkedPostType
import com.github.k1rakishou.chan.core.parser.ParsedPostDataContext
import com.github.k1rakishou.chan.core.parser.ParsedPostDataRaw
import com.github.k1rakishou.chan.core.parser.PostViewMode
import com.github.k1rakishou.chan.core.parser.TextPart
import com.github.k1rakishou.chan.core.parser.TextPartSpan
import com.github.k1rakishou.chan.core.parser.repository.PostReplyChainRepository
import com.github.k1rakishou.chan.core.site.parser_v2.PostCommentParser
import com.github.k1rakishou.chan.ui.compose.data.PostDescriptorUi
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.buildAnnotatedString
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.comment.HtmlUnescape
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostIcon
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.collections.immutable.toPersistentSet
import java.util.Locale

interface CalculateParsedPostDataUseCase {

  suspend fun calculate(
    postIndex: Int,
    chanPost: ChanPost,
    parsedPostDataContext: ParsedPostDataContext,
    chanTheme: ChanTheme,
  ): ParsedPostDataRaw

}

class CalculateParsedPostDataUseCaseImpl(
  private val postCommentParser: PostCommentParser,
  private val postReplyChainRepository: PostReplyChainRepository,
  private val postCommentApplier: PostCommentApplier,
  private val markedPostManager: MarkedPostManager,
  private val appResources: AppResources
) : CalculateParsedPostDataUseCase {

  override suspend fun calculate(
    postIndex: Int,
    chanPost: ChanPost,
    parsedPostDataContext: ParsedPostDataContext,
    chanTheme: ChanTheme,
  ): ParsedPostDataRaw {
    BackgroundUtils.ensureBackgroundThread()

    val postDescriptor = chanPost.postDescriptor
    val postCommentUnparsed = chanPost.postComment.originalUnparsedComment
    val postSubjectUnparsed = chanPost.subject
    val postTimeMs = chanPost.timestampInMillis
    // TODO: compose post cells.
    val opMark = false
    val sage = chanPost.isSage
    val posterName = chanPost.name
    val tripcode = chanPost.tripcode
    val posterId = chanPost.posterId
    val postIcons = chanPost.postIcons
    val postImages = chanPost.postImages
    val deleted = chanPost.isDeleted
    val archived = (chanPost as? ChanOriginalPost)?.archived == true
    val closed = (chanPost as? ChanOriginalPost)?.closed == true
    val sticky = (chanPost as? ChanOriginalPost)?.sticky == true
    val endless = (chanPost as? ChanOriginalPost)?.endless == true
    val threadRepliesTotal = (chanPost as? ChanOriginalPost)?.catalogRepliesCount
    val threadImagesTotal = (chanPost as? ChanOriginalPost)?.catalogImagesCount
    val threadPostersTotal = (chanPost as? ChanOriginalPost)?.uniqueIps

    try {
      val textParts = postCommentParser.parsePostComment(
        postCommentUnparsed = postCommentUnparsed,
        postDescriptor = postDescriptor
      )

      val repliesTo = if (parsedPostDataContext.isParsingThread) {
        processReplyChains(postDescriptor, textParts)
      } else {
        emptySet()
      }

      val processedPostComment = postCommentApplier.applyTextPartsToAnnotatedString(
        postDescriptor = postDescriptor,
        markedPosts = getMarkedPostInfoSetForQuoteSpans(textParts),
        chanTheme = chanTheme,
        textParts = textParts,
        parsedPostDataContext = parsedPostDataContext
      )

      val postSubjectParsed = unescapePostSubject(postSubjectUnparsed) ?: ""

      val isPostMarkedAsMine = markedPostManager.getMarkedPosts(postDescriptor)
        .any { markedPost -> markedPost.markedPostType == MarkedPostType.MyPost }

      val isReplyToPostMarkedAsMine = kotlin.run {
        return@run markedPostManager.getManyMarkedPosts(repliesTo)
          .takeIf { map -> map.isNotEmpty() }
          ?.any { (_, markedPosts) ->
            markedPosts.takeIf { posts -> posts.isNotEmpty() }
              ?.any { markedPost -> markedPost.markedPostType == MarkedPostType.MyPost }
              ?: false
          }
          ?: false
      }

      return ParsedPostDataRaw(
        parsedPostParts = textParts,
        repliesTo = repliesTo
          .map { repliesToPostDescriptor -> PostDescriptorUi(repliesToPostDescriptor) }
          .toPersistentSet(),
        parsedPostComment = processedPostComment.text,
        processedPostComment = processedPostComment,
        parsedPostSubject = postSubjectParsed,
        processedPostSubject = parseAndProcessPostSubject(
          chanTheme = chanTheme,
          postIndex = postIndex,
          postDescriptor = postDescriptor,
          postTimeMs = postTimeMs,
          opMark = opMark,
          sage = sage,
          posterName = posterName,
          posterTripcode = tripcode,
          posterId = posterId,
          postIcons = postIcons,
          postImages = postImages,
          postSubjectParsed = postSubjectParsed,
          archived = archived,
          deleted = deleted,
          closed = closed,
          sticky = sticky,
          endless = endless,
          parsedPostDataContext = parsedPostDataContext
        ),
        postFooterText = formatFooterText(
          postDescriptor = postDescriptor,
          threadRepliesTotal = threadRepliesTotal,
          threadImagesTotal = threadImagesTotal,
          threadPostersTotal = threadPostersTotal,
          parsedPostDataContext = parsedPostDataContext
        ),
        isPostMarkedAsMine = isPostMarkedAsMine,
        isReplyToPostMarkedAsMine = isReplyToPostMarkedAsMine,
        parsedPostDataContext = parsedPostDataContext,
      )
    } catch (error: Throwable) {
      Logger.error(TAG) { "Error parsing ${postDescriptor}! Error: ${error.errorMessageOrClassName()}" }

      val postComment = "An exception has been thrown while parsing \'${postDescriptor.userReadableString()}\' post!\n" +
        "Error message: \'${error.errorMessageOrClassName()}\'\n" +
        "-------------------------------------\n" +
        "Report this error on Github."

      val postCommentAnnotated = buildAnnotatedString(capacity = postComment.length) {
        pushStyle(style = SpanStyle(color = chanTheme.accentColorCompose, fontWeight = FontWeight.SemiBold))
        append(postComment)
      }

      return ParsedPostDataRaw(
        parsedPostParts = emptyList(),
        repliesTo = emptySet(),
        parsedPostComment = postComment,
        processedPostComment = postCommentAnnotated,
        parsedPostSubject = "",
        processedPostSubject = AnnotatedString(""),
        postFooterText = AnnotatedString(""),
        isPostMarkedAsMine = false,
        isReplyToPostMarkedAsMine = false,
        parsedPostDataContext = parsedPostDataContext,
      )
    }
  }

  suspend fun parseAndProcessPostSubject(
    chanTheme: ChanTheme,
    postIndex: Int,
    postDescriptor: PostDescriptor,
    postTimeMs: Long?,
    opMark: Boolean,
    sage: Boolean,
    posterName: String?,
    posterTripcode: String?,
    posterId: String?,
    postIcons: List<ChanPostIcon>,
    postImages: List<ChanPostImage>?,
    postSubjectParsed: String,
    archived: Boolean,
    deleted: Boolean,
    closed: Boolean,
    sticky: Boolean,
    endless: Boolean,
    parsedPostDataContext: ParsedPostDataContext
  ): AnnotatedString {
    val compactMode = parsedPostDataContext.postViewMode != PostViewMode.List

    return buildAnnotatedString(capacity = postSubjectParsed.length) {
      if (postSubjectParsed.isNotBlank()) {
        val subjectAnnotatedString = AnnotatedString(
          text = postSubjectParsed,
          spanStyle = SpanStyle(
            color = chanTheme.postSubjectColorCompose,
          )
        )

        append(subjectAnnotatedString)
      }

      val canAppendPostNameSection = !compactMode
        && (posterName.isNotNullNorBlank() || posterTripcode.isNotNullNorBlank() || posterId.isNotNullNorBlank())

      if (canAppendPostNameSection) {
        if (length > 0) {
          append("\n")
        }

        appendNameTripcodeId(
          chanTheme = chanTheme,
          opMark = opMark,
          sage = sage,
          posterName = posterName,
          posterTripcode = posterTripcode,
          posterId = posterId
        )
      }

      if (!compactMode) {
        if (length > 0) {
          append("\n")
        }

        append(
          buildAnnotatedString(capacity = 32) {
            pushStyle(SpanStyle(color = chanTheme.postDetailsColorCompose))

            if (parsedPostDataContext.isParsingThread && postIndex > 0) {
              append("#")
              append(postIndex.toString())
              append(AppConstants.Companion.Strings.TextSeparator)
            }

            append("No. ")
            append(postDescriptor.postNo.toString())
          }
        )

        if (postTimeMs != null) {
          append(
            buildAnnotatedString(capacity = 32) {
              pushStyle(SpanStyle(color = chanTheme.postDetailsColorCompose))

              val timeString = DateUtils.getRelativeTimeSpanString(
                postTimeMs,
                System.currentTimeMillis(),
                DateUtils.SECOND_IN_MILLIS,
                0
              ).toString()

              append(AppConstants.Companion.Strings.TextSeparator)
              append(timeString)
            }
          )
        }

        val imagesCount = postImages?.size ?: 0
        if (imagesCount == 1) {
          append("\n")
          appendImagesInfo(
            chanTheme = chanTheme,
            postImages = postImages
          )
        }
      }

      if (postIcons.isNotEmpty() || archived || deleted || closed || sticky || endless) {
        if (length > 0) {
          append("\n")
        }

        appendPostIcons(
          archived = archived,
          deleted = deleted,
          closed = closed,
          sticky = sticky,
          endless = endless,
          postIcons = postIcons,
          chanTheme = chanTheme
        )
      }
    }
  }

  private fun AnnotatedString.Builder.appendPostIcons(
    archived: Boolean,
    deleted: Boolean,
    closed: Boolean,
    sticky: Boolean,
    endless: Boolean,
    postIcons: List<ChanPostIcon>,
    chanTheme: ChanTheme
  ) {
    append(
      buildAnnotatedString(capacity = 16) {
        if (archived) {
          appendInlineContent(id = PostCellIcon.Archived.id)
        }

        if (deleted) {
          if (length > 0) {
            append("  ")
          }

          appendInlineContent(id = PostCellIcon.Deleted.id)
        }

        if (closed) {
          if (length > 0) {
            append("  ")
          }

          appendInlineContent(id = PostCellIcon.Closed.id)
        }

        if (sticky) {
          if (length > 0) {
            append("  ")
          }

          appendInlineContent(id = PostCellIcon.Sticky.id)

          if (endless) {
            append("  ")
            appendInlineContent(id = PostCellIcon.RollingSticky.id)
          }
        }

        if (postIcons.isNotEmpty()) {
          postIcons.forEach { flag ->
            if (length > 0) {
              append("  ")
            }

            when (flag) {
              is ChanPostIcon.CountryFlag -> {
                appendInlineContent(id = PostCellIcon.CountryFlag.id)

                if (flag.flagName.isNotNullNorBlank()) {
                  append("  ")

                  withStyle(
                    SpanStyle(
                      color = chanTheme.postDetailsColorCompose,
                      fontStyle = FontStyle.Italic
                    )
                  ) {
                    append(flag.flagName)
                  }
                }
              }
              is ChanPostIcon.BoardFlag -> {
                appendInlineContent(id = PostCellIcon.BoardFlag.id)

                if (flag.flagName.isNotNullNorBlank()) {
                  append("  ")

                  withStyle(
                    SpanStyle(
                      color = chanTheme.postDetailsColorCompose,
                      fontStyle = FontStyle.Italic
                    )
                  ) {
                    append(flag.flagName)
                  }
                }
              }
              is ChanPostIcon.CustomFlag -> {
                // TODO: compose post cells.
                TODO()
              }
              is ChanPostIcon.DvachFlag -> {
                // TODO: compose post cells.
                TODO()
              }
              is ChanPostIcon.Since4Pass -> {
                // TODO: compose post cells.
                TODO()
              }
            }
          }
        }
      }
    )
  }

  private fun AnnotatedString.Builder.appendImagesInfo(
    chanTheme: ChanTheme,
    postImages: List<ChanPostImage>?
  ) {
    val imagesInfoAnnotatedString = buildAnnotatedString(capacity = 64) {
      pushStyle(SpanStyle(color = chanTheme.postDetailsColorCompose))

      if (postImages!!.size > 1) {
        val imagesCount = postImages.size
        val totalFileSize = postImages.sumOf { it.size }

        append(imagesCount.toString())
        append(" ")
        append("files")
        append(", ")
        append(ChanPostUtils.getReadableFileSize(totalFileSize))
      } else {
        val postImage = postImages.first()

        postImage.originalFileNameForPostCell()?.let { fileName ->
          withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
            append(fileName)
          }
        }

        postImage.extension?.let { extension ->
          append(" ")
          append(extension.uppercase(Locale.ENGLISH))
        }

        if (length > 0) {
          append(" ")
        }

        append(postImage.imageWidth.toString())
        append("x")
        append(postImage.imageHeight.toString())
        append(" ")
        append(ChanPostUtils.getReadableFileSize(postImage.size))
      }
    }

    append(imagesInfoAnnotatedString)
  }

  private fun AnnotatedString.Builder.appendNameTripcodeId(
    chanTheme: ChanTheme,
    opMark: Boolean,
    sage: Boolean,
    posterName: String?,
    posterTripcode: String?,
    posterId: String?
  ) {
    append(
      buildAnnotatedString(capacity = 32) {
        pushStyle(SpanStyle(color = chanTheme.postNameColorCompose))

        if (posterName.isNotNullNorBlank()) {
          if (length > 0) {
            append(" ")
          }

          if (opMark) {
            withStyle(SpanStyle(color = chanTheme.accentColorCompose)) {
              append("#OP")
              append(" ")
            }
          }

          append(posterName)

          if (sage) {
            withStyle(SpanStyle(color = chanTheme.accentColorCompose)) {
              append(" ")
              append("SAGE")
            }
          }
        }

        if (posterTripcode.isNotNullNorBlank()) {
          if (length > 0) {
            append(" ")
          }

          append(posterTripcode)
        }

        if (posterId.isNotNullNorBlank()) {
          if (length > 0) {
            append(" ")
          }

          withStyle(SpanStyle(color = calculatePosterIdTextColor(chanTheme, posterId))) {
            append(posterId)
          }
        }
      }
    )
  }

  private fun calculatePosterIdTextColor(
    chanTheme: ChanTheme,
    posterId: String
  ): Color {
    // Stolen from the 4chan extension
    val hash: Int = posterId.hashCode()

    val r = hash shr 24 and 0xff
    val g = hash shr 16 and 0xff
    val b = hash shr 8 and 0xff
    val posterIdTextColor = (0xff shl 24) + (r shl 16) + (g shl 8) + b

    val posterIdColorHSL = ThemeEngine.colorToHsl(posterIdTextColor)

    // Make the posterId text color darker if it's too light and the current theme's back color is
    // also light and vice versa
    if (chanTheme.isBackColorDark && posterIdColorHSL.lightness < 0.5) {
      posterIdColorHSL.lightness = .7f
    } else if (chanTheme.isBackColorLight && posterIdColorHSL.lightness > 0.5) {
      posterIdColorHSL.lightness = .3f
    }

    return Color(ThemeEngine.hslToColor(posterIdColorHSL))
  }

  private suspend fun formatFooterText(
    postDescriptor: PostDescriptor,
    threadRepliesTotal: Int?,
    threadImagesTotal: Int?,
    threadPostersTotal: Int?,
    parsedPostDataContext: ParsedPostDataContext
  ): AnnotatedString? {
    val isCatalogMode = parsedPostDataContext.isParsingCatalog
    val compactMode = parsedPostDataContext.postViewMode != PostViewMode.List
    val hasThreadInfo = threadImagesTotal != null || threadRepliesTotal != null || threadPostersTotal != null

    if (isCatalogMode && hasThreadInfo) {
      val text = buildString(capacity = 32) {
        threadRepliesTotal
          ?.takeIf { repliesCount -> repliesCount > 0 }
          ?.let { repliesCount ->
            val stringId = if (compactMode) {
              R.plurals.reply_with_number_compact
            } else {
              R.plurals.reply_with_number
            }

            val repliesText = appResources.quantityString(
              stringId,
              repliesCount,
              repliesCount
            )

            append(repliesText)
          }

        threadImagesTotal
          ?.takeIf { imagesCount -> imagesCount > 0 }
          ?.let { imagesCount ->
            if (isNotEmpty()) {
              append(", ")
            }

            val stringId = if (compactMode) {
              R.plurals.image_with_number_compact
            } else {
              R.plurals.image_with_number
            }

            val imagesText = appResources.quantityString(
              stringId,
              imagesCount,
              imagesCount
            )

            append(imagesText)
          }

        threadPostersTotal
          ?.takeIf { postersCount -> postersCount > 0 }
          ?.let { postersCount ->
            if (isNotEmpty()) {
              append(", ")
            }

            val stringId = if (compactMode) {
              R.plurals.poster_with_number_compact
            } else {
              R.plurals.poster_with_number
            }

            val imagesText = appResources.quantityString(
              stringId,
              postersCount,
              postersCount
            )

            append(imagesText)
          }
      }

      return AnnotatedString(text)
    }

    val repliesFrom = postReplyChainRepository.getRepliesFrom(postDescriptor)
    if (repliesFrom.isNotEmpty()) {
      val repliesFromCount = repliesFrom.size

      val text = appResources.quantityString(
        R.plurals.image_with_number,
        repliesFromCount,
        repliesFromCount
      )

      return AnnotatedString(text)
    }

    return null
  }

  enum class PostCellIcon(val id: String) {
    Deleted("id_post_deleted"),
    Closed("id_post_deleted"),
    Archived("id_post_archived"),
    Sticky("id_post_sticky"),
    RollingSticky("id_post_rolling_sticky"),
    CountryFlag("id_post_country_flag"),
    BoardFlag("id_post_board_flag")
  }

  private fun unescapePostSubject(postSubjectUnparsed: String?): String? {
    if (postSubjectUnparsed.isNullOrBlank()) {
      return null
    }

    return HtmlUnescape.unescape(postSubjectUnparsed)
  }

  private suspend fun processReplyChains(
    postDescriptor: PostDescriptor,
    textParts: List<TextPart>
  ): Set<PostDescriptor> {
    val repliesTo = mutableSetOf<PostDescriptor>()

    for (textPart in textParts) {
      for (textPartSpan in textPart.spans) {
        if (textPartSpan !is TextPartSpan.Linkable) {
          continue
        }

        when (textPartSpan) {
          is TextPartSpan.Linkable.Board,
          is TextPartSpan.Linkable.Search,
          is TextPartSpan.Linkable.Url -> continue
          is TextPartSpan.Linkable.Quote -> {
            if (textPartSpan.crossThread) {
              continue
            }

            repliesTo += textPartSpan.postDescriptor
          }
        }
      }
    }

    if (repliesTo.isNotEmpty()) {
      postReplyChainRepository.insertRepliesTo(postDescriptor, repliesTo)
    }

    return repliesTo
  }

  private suspend fun getMarkedPostInfoSetForQuoteSpans(
    textParts: List<TextPart>
  ): Map<PostDescriptor, Set<MarkedPost>> {
    val foundQuotes = mutableSetOf<PostDescriptor>()

    for (textPart in textParts) {
      for (span in textPart.spans) {
        if (span is TextPartSpan.Linkable.Quote && !span.crossThread) {
          foundQuotes += span.postDescriptor
        }
      }
    }

    if (foundQuotes.isEmpty()) {
      return emptyMap()
    }

    return markedPostManager.getManyMarkedPosts(foundQuotes)
  }

  companion object {
    private const val TAG = "CalculateParsedPostDataUseCase"
  }

}