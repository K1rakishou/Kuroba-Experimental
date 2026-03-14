package com.github.k1rakishou.core_spannable.parcelable_spannable_string.v1

import android.text.SpannableString
import android.text.style.CharacterStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.Log
import com.github.k1rakishou.common.data.ArchiveType
import com.github.k1rakishou.common.setSpanSafe
import com.github.k1rakishou.core_spannable.AbsoluteSizeSpanHashed
import com.github.k1rakishou.core_spannable.BackgroundColorIdSpan
import com.github.k1rakishou.core_spannable.BackgroundColorSpanHashed
import com.github.k1rakishou.core_spannable.ForegroundColorIdSpan
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed
import com.github.k1rakishou.core_spannable.ParcelableSpan
import com.github.k1rakishou.core_spannable.ParcelableSpanInfo
import com.github.k1rakishou.core_spannable.ParcelableSpanType
import com.github.k1rakishou.core_spannable.ParcelableSpannableString
import com.github.k1rakishou.core_spannable.ParcelableSpans
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_spannable.PostLinkableType
import com.github.k1rakishou.core_spannable.PostLinkableValue
import com.github.k1rakishou.core_spannable.parcelable_spannable_string.ParcelableStringMapper

internal object ParcelableSpannableStringMapperV1 : ParcelableStringMapper {
  private const val MAPPER_VERSION = 1
  private const val TAG = "PSS_MapperV1"

  override val version: Int
    get() = MAPPER_VERSION

  override fun toParcelableSpannableString(
    charSequence: CharSequence?
  ): ParcelableSpannableString? {
    if (charSequence == null || charSequence.isEmpty()) {
      return null
    }

    val spanInfoList = mutableListOf<ParcelableSpanInfo>()
    val spannableString = SpannableString(charSequence)
    val spans = spannableString.getSpans(0, spannableString.length, CharacterStyle::class.java)

    for (span in spans) {
      val spanStart = spannableString.getSpanStart(span)
      val spanEnd = spannableString.getSpanEnd(span)
      val flags = spannableString.getSpanFlags(span)

      if (span is ForegroundColorSpanHashed) {
        val spanInfo = ParcelableSpanInfo(
          spanStart = spanStart,
          spanEnd = spanEnd,
          flags = flags,
          parcelableTypeRaw = ParcelableSpanType.ForegroundColorSpanType.value,
          parcelableSpan = ParcelableSpan.ForegroundColor(span.foregroundColor)
        )

        spanInfoList += spanInfo
        continue
      }

      if (span is BackgroundColorSpanHashed) {
        val spanInfo = ParcelableSpanInfo(
          spanStart = spanStart,
          spanEnd = spanEnd,
          flags = flags,
          parcelableTypeRaw = ParcelableSpanType.BackgroundColorSpanType.value,
          parcelableSpan = ParcelableSpan.BackgroundColor(span.backgroundColor)
        )

        spanInfoList += spanInfo
        continue
      }

      if (span is ForegroundColorIdSpan) {
        val spanInfo = ParcelableSpanInfo(
          spanStart = spanStart,
          spanEnd = spanEnd,
          flags = flags,
          parcelableTypeRaw = ParcelableSpanType.ForegroundColorIdSpan.value,
          parcelableSpan = ParcelableSpan.ForegroundColorId(span.chanThemeColorId)
        )

        spanInfoList += spanInfo
        continue
      }

      if (span is BackgroundColorIdSpan) {
        val spanInfo = ParcelableSpanInfo(
          spanStart = spanStart,
          spanEnd = spanEnd,
          flags = flags,
          parcelableTypeRaw = ParcelableSpanType.BackgroundColorIdSpan.value,
          parcelableSpan = ParcelableSpan.BackgroundColorId(span.chanThemeColorId)
        )

        spanInfoList += spanInfo
        continue
      }

      if (span is StrikethroughSpan) {
        val spanInfo = ParcelableSpanInfo(
          spanStart = spanStart,
          spanEnd = spanEnd,
          flags = flags,
          parcelableTypeRaw = ParcelableSpanType.StrikethroughSpanType.value,
          parcelableSpan = ParcelableSpan.Strikethrough
        )

        spanInfoList += spanInfo
        continue
      }

      if (span is StyleSpan) {
        val spanInfo = ParcelableSpanInfo(
          spanStart = spanStart,
          spanEnd = spanEnd,
          flags = flags,
          parcelableTypeRaw = ParcelableSpanType.StyleSpanType.value,
          parcelableSpan = ParcelableSpan.Style(span.style)
        )

        spanInfoList += spanInfo
        continue
      }

      if (span is TypefaceSpan) {
        val family = span.family
        if (family.isNullOrEmpty()) {
          val spanInfo = ParcelableSpanInfo(
            spanStart = spanStart,
            spanEnd = spanEnd,
            flags = flags,
            parcelableTypeRaw = ParcelableSpanType.TypefaceSpanType.value,
            parcelableSpan = ParcelableSpan.Typeface(family!!)
          )

          spanInfoList += spanInfo
        }

        continue
      }

      if (span is AbsoluteSizeSpanHashed) {
        val spanInfo = ParcelableSpanInfo(
          spanStart = spanStart,
          spanEnd = spanEnd,
          flags = flags,
          parcelableTypeRaw = ParcelableSpanType.AbsoluteSizeSpanHashed.value,
          parcelableSpan = ParcelableSpan.AbsoluteSize(span.size)
        )

        spanInfoList += spanInfo
        continue
      }

      if (span is PostLinkable) {
        val spanInfo = serializePostLinkable(span, spanStart, spanEnd, flags)
        if (spanInfo != null) {
          spanInfoList += spanInfo
        }

        continue
      }
    }

    return ParcelableSpannableString(
      parcelableSpans = ParcelableSpans(version = MAPPER_VERSION, spanInfoList),
      text = charSequence.toString()
    )
  }

  override fun fromParcelableSpannableString(
    parcelableSpannableString: ParcelableSpannableString?,
    revealTextSpoilers: Boolean
  ): CharSequence {
    if (parcelableSpannableString == null || parcelableSpannableString.text.isEmpty()) {
      return ""
    }

    val spanInfoList = parcelableSpannableString.parcelableSpans.spanInfoList
    val spannableString = SpannableString(parcelableSpannableString.text)

    for (spanInfo in spanInfoList) {
      val parcelableType = ParcelableSpanType.from(spanInfo.parcelableTypeRaw)
      val parcelableSpan = spanInfo.parcelableSpan

      when (parcelableType) {
        ParcelableSpanType.ForegroundColorSpanType -> {
          spannableString.setSpanSafe(
            span = ForegroundColorSpanHashed((parcelableSpan as ParcelableSpan.ForegroundColor).color),
            start = spanInfo.spanStart,
            end = spanInfo.spanEnd,
            flags = spanInfo.flags
          )
        }
        ParcelableSpanType.ForegroundColorIdSpan -> {
          spannableString.setSpanSafe(
            span = ForegroundColorIdSpan((parcelableSpan as ParcelableSpan.ForegroundColorId).colorId),
            start = spanInfo.spanStart,
            end = spanInfo.spanEnd,
            flags = spanInfo.flags
          )
        }
        ParcelableSpanType.BackgroundColorSpanType -> {
          spannableString.setSpanSafe(
            span = BackgroundColorSpanHashed((parcelableSpan as ParcelableSpan.BackgroundColor).color),
            start = spanInfo.spanStart,
            end = spanInfo.spanEnd,
            flags = spanInfo.flags
          )
        }
        ParcelableSpanType.BackgroundColorIdSpan -> {
          spannableString.setSpanSafe(
            span = BackgroundColorIdSpan((parcelableSpan as ParcelableSpan.BackgroundColorId).colorId),
            start = spanInfo.spanStart,
            end = spanInfo.spanEnd,
            flags = spanInfo.flags
          )
        }
        ParcelableSpanType.StrikethroughSpanType -> {
          spannableString.setSpanSafe(
            span = StrikethroughSpan(),
            start = spanInfo.spanStart,
            end = spanInfo.spanEnd,
            flags = spanInfo.flags
          )
        }
        ParcelableSpanType.AbsoluteSizeSpanHashed -> {
          spannableString.setSpanSafe(
            span = AbsoluteSizeSpanHashed((parcelableSpan as ParcelableSpan.AbsoluteSize).size),
            start = spanInfo.spanStart,
            end = spanInfo.spanEnd,
            flags = spanInfo.flags
          )
        }
        ParcelableSpanType.StyleSpanType -> {
          spannableString.setSpanSafe(
            span = StyleSpan((parcelableSpan as ParcelableSpan.Style).style),
            start = spanInfo.spanStart,
            end = spanInfo.spanEnd,
            flags = spanInfo.flags
          )
        }
        ParcelableSpanType.TypefaceSpanType -> {
          spannableString.setSpanSafe(
            span = TypefaceSpan((parcelableSpan as ParcelableSpan.Typeface).family),
            start = spanInfo.spanStart,
            end = spanInfo.spanEnd,
            flags = spanInfo.flags
          )
        }
        ParcelableSpanType.PostLinkable -> {
          val postLinkable = extractPostLinkableSpan(
            parcelableSpan = (parcelableSpan as ParcelableSpan.PostLinkable),
            revealTextSpoilers = revealTextSpoilers
          )
          if (postLinkable == null) {
            continue
          }

          spannableString.setSpanSafe(
            span = postLinkable,
            start = spanInfo.spanStart,
            end = spanInfo.spanEnd,
            flags = spanInfo.flags
          )
        }
        ParcelableSpanType.Unknown -> parcelableSpannableString.text
      }
    }

    return spannableString
  }

  private fun serializePostLinkable(
    postLinkable: PostLinkable,
    spanStart: Int,
    spanEnd: Int,
    flags: Int
  ): ParcelableSpanInfo? {
    var parcelableSpan: ParcelableSpan? = null

    when (postLinkable.type) {
      PostLinkable.Type.DEAD -> {
        when (val postLinkableValue = postLinkable.linkableValue) {
          is PostLinkable.Value.ThreadOrPostLink -> {
            parcelableSpan = ParcelableSpan.PostLinkable(
              key = postLinkable.key.toString(),
              postLinkableTypeRaw = PostLinkableType.Dead.value,
              postLinkableValue = PostLinkableValue.ThreadOrPost(
                boardCode = postLinkableValue.board,
                threadNo = postLinkableValue.threadId,
                postNo = postLinkableValue.postId,
                postSubNo = postLinkableValue.postSubId
              )
            )
          }
          is PostLinkable.Value.PostIdValue -> {
            val postId = postLinkable.linkableValue.extractPostIdOrNull()
            if (postId != null) {
              val postNo = postId.postNo
              val postSubNo = postId.postSubNo

              parcelableSpan = ParcelableSpan.PostLinkable(
                key = postLinkable.key.toString(),
                postLinkableTypeRaw = PostLinkableType.Dead.value,
                postLinkableValue = PostLinkableValue.Dead(
                  postNo = postNo,
                  postSubNo = postSubNo
                )
              )
            }
          }
          else -> {
            // no-op
          }
        }
      }
      PostLinkable.Type.QUOTE -> {
        val postId = postLinkable.linkableValue.extractPostIdOrNull()
        if (postId != null) {
          val postNo = postId.postNo
          val postSubNo = postId.postSubNo

          parcelableSpan = ParcelableSpan.PostLinkable(
            key = postLinkable.key.toString(),
            postLinkableTypeRaw = PostLinkableType.Quote.value,
            postLinkableValue = PostLinkableValue.Quote(
              postNo = postNo,
              postSubNo = postSubNo
            )
          )
        }
      }
      PostLinkable.Type.LINK -> {
        val link = (postLinkable.linkableValue as? PostLinkable.Value.StringValue)?.value

        if (link != null) {
          parcelableSpan = ParcelableSpan.PostLinkable(
            key = postLinkable.key.toString(),
            postLinkableTypeRaw = PostLinkableType.Link.value,
            postLinkableValue = PostLinkableValue.Link(
              link = link.toString()
            )
          )
        }
      }
      PostLinkable.Type.SPOILER -> {
        parcelableSpan = ParcelableSpan.PostLinkable(
          key = postLinkable.key.toString(),
          postLinkableTypeRaw = PostLinkableType.Spoiler.value,
          postLinkableValue = PostLinkableValue.Spoiler
        )
      }
      PostLinkable.Type.THREAD -> {
        val threadLink = postLinkable.linkableValue
        if (threadLink !is PostLinkable.Value.ThreadOrPostLink) {
          return null
        }

        parcelableSpan = ParcelableSpan.PostLinkable(
          key = postLinkable.key.toString(),
          postLinkableTypeRaw = PostLinkableType.Thread.value,
          postLinkableValue = PostLinkableValue.ThreadOrPost(
            boardCode = threadLink.board,
            threadNo = threadLink.threadId,
            postNo = threadLink.postId,
            postSubNo = threadLink.postSubId
          )
        )
      }
      PostLinkable.Type.BOARD -> {
        val stringValue = postLinkable.linkableValue
        if (stringValue !is PostLinkable.Value.StringValue) {
          return null
        }

        parcelableSpan = ParcelableSpan.PostLinkable(
          key = postLinkable.key.toString(),
          postLinkableTypeRaw = PostLinkableType.Board.value,
          postLinkableValue = PostLinkableValue.Board(
            boardCode = stringValue.value.toString()
          )
        )
      }
      PostLinkable.Type.SEARCH -> {
        val searchLink = postLinkable.linkableValue
        if (searchLink !is PostLinkable.Value.SearchLink) {
          return null
        }

        parcelableSpan = ParcelableSpan.PostLinkable(
          key = postLinkable.key.toString(),
          postLinkableTypeRaw = PostLinkableType.Search.value,
          postLinkableValue = PostLinkableValue.Search(
            boardCode = searchLink.board,
            searchQuery = searchLink.query
          )
        )
      }
      PostLinkable.Type.ARCHIVE -> {
        val archiveThreadLink = postLinkable.linkableValue
        if (archiveThreadLink !is PostLinkable.Value.ArchiveThreadLink) {
          return null
        }

        parcelableSpan = ParcelableSpan.PostLinkable(
          key = postLinkable.key.toString(),
          postLinkableTypeRaw = PostLinkableType.Archive.value,
          postLinkableValue = PostLinkableValue.Archive(
            archiveDomain = archiveThreadLink.archiveType.domain,
            boardCode = archiveThreadLink.board,
            threadNo = archiveThreadLink.threadId,
            postNo = archiveThreadLink.postId ?: 0,
            postSubNo = archiveThreadLink.postSubId ?: 0
          )
        )
      }
      PostLinkable.Type.QUOTE_TO_HIDDEN_OR_REMOVED_POST -> {
        // We don't want to serialize these PostLinkables
        return null
      }
    }

    if (parcelableSpan == null) {
      return null
    }

    return ParcelableSpanInfo(
      spanStart = spanStart,
      spanEnd = spanEnd,
      flags = flags,
      parcelableTypeRaw = ParcelableSpanType.PostLinkable.value,
      parcelableSpan = parcelableSpan
    )
  }

  private fun extractPostLinkableSpan(
    parcelableSpan: ParcelableSpan.PostLinkable,
    revealTextSpoilers: Boolean
  ): PostLinkable? {
    val postLinkableType = PostLinkableType.from(parcelableSpan.postLinkableTypeRaw)
    if (postLinkableType == null) {
      Log.e(TAG, "extractPostLinkableSpan() unknown postLinkableTypeRaw: ${parcelableSpan.postLinkableTypeRaw}")
      return null
    }

    val key = parcelableSpan.key
    val postLinkableValue = parcelableSpan.postLinkableValue

    when (postLinkableType) {
      PostLinkableType.Archive -> {
        postLinkableValue as PostLinkableValue.Archive

        if (!ArchiveType.hasDomain(postLinkableValue.archiveDomain)) {
          return null
        }

        val archiveType = ArchiveType.byDomain(postLinkableValue.archiveDomain)
          ?: return null

        return PostLinkable(
          key = key,
          linkableValue = PostLinkable.Value.ArchiveThreadLink(
            archiveType = archiveType,
            board = postLinkableValue.boardCode,
            threadId = postLinkableValue.threadNo,
            postId = postLinkableValue.postNo,
            postSubId = postLinkableValue.postSubNo
          ),
          type = PostLinkable.Type.ARCHIVE,
          revealTextSpoilers = false,
        )
      }
      PostLinkableType.Quote -> {
        postLinkableValue as PostLinkableValue.Quote

        val linkableValue = PostLinkable.Value.PostIdValue(
          postNo = postLinkableValue.postNo,
          postSubNo = postLinkableValue.postSubNo.takeIf { value -> value >= 0L } ?: 0L
        )

        return PostLinkable(
          key = key,
          linkableValue = linkableValue,
          type = PostLinkable.Type.QUOTE,
          revealTextSpoilers = false,
        )
      }
      PostLinkableType.Board -> {
        postLinkableValue as PostLinkableValue.Board

        return PostLinkable(
          key = key,
          linkableValue = PostLinkable.Value.StringValue(
            value = postLinkableValue.boardCode
          ),
          type = PostLinkable.Type.BOARD,
          revealTextSpoilers = false,
        )
      }
      PostLinkableType.Link -> {
        postLinkableValue as PostLinkableValue.Link

        return PostLinkable(
          key = key,
          linkableValue = PostLinkable.Value.StringValue(
            value = postLinkableValue.link
          ),
          type = PostLinkable.Type.LINK,
          revealTextSpoilers = false,
        )
      }
      PostLinkableType.Spoiler -> {
        return PostLinkable(
          key = key,
          linkableValue = PostLinkable.Value.NoValue,
          type = PostLinkable.Type.SPOILER,
          revealTextSpoilers = revealTextSpoilers,
        )
      }
      PostLinkableType.Thread -> {
        postLinkableValue as PostLinkableValue.ThreadOrPost

        return PostLinkable(
          key = key,
          linkableValue = PostLinkable.Value.ThreadOrPostLink(
            board = postLinkableValue.boardCode,
            threadId = postLinkableValue.threadNo,
            postId = postLinkableValue.postNo,
            postSubId = postLinkableValue.postSubNo
          ),
          type = PostLinkable.Type.THREAD,
          revealTextSpoilers = false
        )
      }
      PostLinkableType.Search -> {
        postLinkableValue as PostLinkableValue.Search

        return PostLinkable(
          key = key,
          linkableValue = PostLinkable.Value.SearchLink(
            board = postLinkableValue.boardCode,
            query = postLinkableValue.searchQuery
          ),
          type = PostLinkable.Type.SEARCH,
          revealTextSpoilers = false
        )
      }
      PostLinkableType.Dead -> {
        val linkableValue = when (postLinkableValue) {
          is PostLinkableValue.ThreadOrPost -> {
            PostLinkable.Value.ThreadOrPostLink(
              board = postLinkableValue.boardCode,
              threadId = postLinkableValue.threadNo,
              postId = postLinkableValue.postNo,
              postSubId = postLinkableValue.postSubNo,
            )
          }
          is PostLinkableValue.Dead,
          is PostLinkableValue.Quote -> {
            val postNo = when (postLinkableValue) {
              is PostLinkableValue.Dead -> postLinkableValue.postNo
              is PostLinkableValue.Quote -> postLinkableValue.postNo
            }

            val postSubNo = when (postLinkableValue) {
              is PostLinkableValue.Dead -> postLinkableValue.postSubNo
              is PostLinkableValue.Quote -> postLinkableValue.postSubNo
            }

            PostLinkable.Value.PostIdValue(
              postNo = postNo,
              postSubNo = postSubNo.takeIf { value -> value >= 0L } ?: 0L
            )
          }
          else -> return null
        }

        return PostLinkable(
          key = key,
          linkableValue = linkableValue,
          type = PostLinkable.Type.DEAD,
          revealTextSpoilers = false
        )
      }
    }
  }

}