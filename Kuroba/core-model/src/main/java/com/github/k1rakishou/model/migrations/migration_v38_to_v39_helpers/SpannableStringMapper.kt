package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers

import android.text.SpannableString
import android.text.style.CharacterStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import com.github.k1rakishou.common.data.ArchiveType
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.common.setSpanSafe
import com.github.k1rakishou.core_spannable.AbsoluteSizeSpanHashed
import com.github.k1rakishou.core_spannable.BackgroundColorIdSpan
import com.github.k1rakishou.core_spannable.BackgroundColorSpanHashed
import com.github.k1rakishou.core_spannable.ForegroundColorIdSpan
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableArchiveLinkValue
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableBoardLinkValue
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableLinkValue
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableQuoteValue
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableSearchLinkValue
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableSpoilerValue
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableThreadOrPostLinkValue
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableValue
import com.google.gson.Gson

@Deprecated(message = "This was deprecated in favor of ParcelableSpannableString so use ParcelableSpannableStringMapper instead. This class only exist for the 38 to 39 database migration")
object SpannableStringMapper {
  private const val TAG = "SpannableStringMapper"

  @JvmStatic
  fun serializeSpannableString(
    gson: Gson,
    charSequence: CharSequence?
  ): SerializableSpannableString? {
    if (charSequence == null || charSequence.isEmpty()) {
      return null
    }

    val serializableSpannableString = SerializableSpannableString()
    val spannableString = SpannableString(charSequence)
    val spans = spannableString.getSpans(0, spannableString.length, CharacterStyle::class.java)

    for (span in spans) {
      val spanStart = spannableString.getSpanStart(span)
      val spanEnd = spannableString.getSpanEnd(span)
      val flags = spannableString.getSpanFlags(span)

      if (span is ForegroundColorSpanHashed) {
        val serializableForegroundColorSpan = SerializableForegroundColorSpan(span.foregroundColor)
        val spanInfo = SerializableSpanInfo(
          SerializableSpanType.ForegroundColorSpanHashedType,
          spanStart,
          spanEnd,
          flags
        )

        spanInfo.spanData = gson.toJson(serializableForegroundColorSpan)
        serializableSpannableString.addSpanInfo(spanInfo)

        continue
      }

      if (span is ForegroundColorIdSpan) {
        val spanInfo = SerializableSpanInfo(
          SerializableSpanType.ColorizableForegroundColorSpan,
          spanStart,
          spanEnd,
          flags
        )

        spanInfo.spanData = gson.toJson(SerializableColorizableForegroundColorSpan(span.chanThemeColorId))
        serializableSpannableString.addSpanInfo(spanInfo)

        continue
      }

      if (span is BackgroundColorSpanHashed) {
        val serializableBackgroundColorSpan = SerializableBackgroundColorSpan(span.backgroundColor)
        val spanInfo = SerializableSpanInfo(
          SerializableSpanType.BackgroundColorSpanHashedType,
          spanStart,
          spanEnd,
          flags
        )

        spanInfo.spanData = gson.toJson(serializableBackgroundColorSpan)
        serializableSpannableString.addSpanInfo(spanInfo)

        continue
      }

      if (span is BackgroundColorIdSpan) {
        val spanInfo = SerializableSpanInfo(
          SerializableSpanType.ColorizableBackgroundColorSpan,
          spanStart,
          spanEnd,
          flags
        )

        spanInfo.spanData = gson.toJson(SerializableColorizableBackgroundColorSpan(span.chanThemeColorId))
        serializableSpannableString.addSpanInfo(spanInfo)

        continue
      }

      if (span is StrikethroughSpan) {
        val spanInfo = SerializableSpanInfo(
          SerializableSpanType.StrikethroughSpanType,
          spanStart,
          spanEnd,
          flags
        )

        spanInfo.spanData = null
        serializableSpannableString.addSpanInfo(spanInfo)

        continue
      }

      if (span is StyleSpan) {
        val serializableStyleSpan = SerializableStyleSpan(span.style)
        val spanInfo = SerializableSpanInfo(
          SerializableSpanType.StyleSpanType,
          spanStart,
          spanEnd,
          flags
        )

        spanInfo.spanData = gson.toJson(serializableStyleSpan)
        serializableSpannableString.addSpanInfo(spanInfo)

        continue
      }

      if (span is TypefaceSpan) {
        val serializableTypefaceSpan = SerializableTypefaceSpan(span.family)
        val spanInfo = SerializableSpanInfo(
          SerializableSpanType.TypefaceSpanType,
          spanStart,
          spanEnd,
          flags
        )

        spanInfo.spanData = gson.toJson(serializableTypefaceSpan)
        serializableSpannableString.addSpanInfo(spanInfo)

        continue
      }

      if (span is AbsoluteSizeSpanHashed) {
        val serializableAbsoluteSizeSpan = SerializableAbsoluteSizeSpan(span.size)
        val spanInfo = SerializableSpanInfo(
          SerializableSpanType.AbsoluteSizeSpanHashed,
          spanStart,
          spanEnd,
          flags
        )

        spanInfo.spanData = gson.toJson(serializableAbsoluteSizeSpan)
        serializableSpannableString.addSpanInfo(spanInfo)

        continue
      }

      if (span is PostLinkable) {
        serializePostLinkable(gson, serializableSpannableString, span, spanStart, spanEnd, flags)
        continue
      }
    }

    serializableSpannableString.text = charSequence.toString()
    return serializableSpannableString
  }

  private fun serializePostLinkable(
    gson: Gson,
    serializableSpannableString: SerializableSpannableString,
    postLinkable: PostLinkable,
    spanStart: Int,
    spanEnd: Int,
    flags: Int
  ) {
    val spanInfo = SerializableSpanInfo(
      SerializableSpanType.PostLinkable,
      spanStart,
      spanEnd,
      flags
    )
    val serializablePostLinkableSpan = SerializablePostLinkableSpan(postLinkable.key.toString())
    var postLinkableValueJson: String? = null

    when (postLinkable.type) {
      PostLinkable.Type.DEAD -> {
        when (val postLinkableValue = postLinkable.linkableValue) {
          is PostLinkable.Value.ThreadOrPostLink -> {
            postLinkableValueJson = gson.toJson(
              PostLinkableThreadOrPostLinkValue(
                SerializablePostLinkableType.Dead,
                postLinkableValue.board,
                postLinkableValue.threadId,
                postLinkableValue.postId
              )
            )
          }
          is PostLinkable.Value.LongValue -> {
            val postId = postLinkable.linkableValue.extractValueOrNull()
            if (postId == null) {
              postLinkableValueJson = null
            } else {
              postLinkableValueJson = gson.toJson(
                PostLinkableQuoteValue(
                  SerializablePostLinkableType.Dead,
                  postId
                )
              )
            }
          }
          else -> {
            postLinkableValueJson = null
          }
        }

        serializablePostLinkableSpan.setPostLinkableType(SerializablePostLinkableType.Dead.typeValue)
      }
      PostLinkable.Type.QUOTE -> {
        val postId = postLinkable.linkableValue.extractValueOrNull()

        if (postId != null) {
          postLinkableValueJson = gson.toJson(
            PostLinkableQuoteValue(
              SerializablePostLinkableType.Quote,
              postId
            )
          )
          serializablePostLinkableSpan.setPostLinkableType(SerializablePostLinkableType.Quote.typeValue)
        }
      }
      PostLinkable.Type.LINK -> {
        val link = (postLinkable.linkableValue as? PostLinkable.Value.StringValue)?.value

        if (link != null) {
          postLinkableValueJson = gson.toJson(
            PostLinkableLinkValue(
              SerializablePostLinkableType.Link,
              link.toString()
            )
          )
          serializablePostLinkableSpan.setPostLinkableType(SerializablePostLinkableType.Link.typeValue)
        }
      }
      PostLinkable.Type.SPOILER -> {
        postLinkableValueJson = gson.toJson(PostLinkableSpoilerValue(SerializablePostLinkableType.Spoiler))
        serializablePostLinkableSpan.setPostLinkableType(SerializablePostLinkableType.Spoiler.typeValue)
      }
      PostLinkable.Type.THREAD -> {
        val threadLink = postLinkable.linkableValue
        if (threadLink !is PostLinkable.Value.ThreadOrPostLink) {
          throw RuntimeException(
            "PostLinkable value is not of ThreadLink type, key = "
              + postLinkable.key + ", type = "
              + postLinkable.type.name)
        }

        postLinkableValueJson = gson.toJson(
          PostLinkableThreadOrPostLinkValue(
            SerializablePostLinkableType.Thread,
            threadLink.board,
            threadLink.threadId,
            threadLink.postId
          )
        )
        serializablePostLinkableSpan.setPostLinkableType(SerializablePostLinkableType.Thread.typeValue)
      }
      PostLinkable.Type.BOARD -> {
        val stringValue = postLinkable.linkableValue

        if (stringValue !is PostLinkable.Value.StringValue) {
          throw RuntimeException(
            "PostLinkable value is not of StringValue type, key = "
              + postLinkable.key + ", type = "
              + postLinkable.type.name)
        }

        postLinkableValueJson = gson.toJson(
          PostLinkableBoardLinkValue(
            SerializablePostLinkableType.Board,
            stringValue.value.toString()
          )
        )
        serializablePostLinkableSpan.setPostLinkableType(SerializablePostLinkableType.Board.typeValue)
      }
      PostLinkable.Type.SEARCH -> {
        val searchLink = postLinkable.linkableValue
        if (searchLink !is PostLinkable.Value.SearchLink) {
          throw RuntimeException(
            "PostLinkable value is not of SearchLink type, key = "
              + postLinkable.key + ", type = "
              + postLinkable.type.name
          )
        }

        postLinkableValueJson = gson.toJson(
          PostLinkableSearchLinkValue(
            SerializablePostLinkableType.Search,
            searchLink.board,
            searchLink.query
          )
        )

        serializablePostLinkableSpan.setPostLinkableType(SerializablePostLinkableType.Search.typeValue)
      }
      PostLinkable.Type.ARCHIVE -> {
        val archiveThreadLink = postLinkable.linkableValue
        if (archiveThreadLink !is PostLinkable.Value.ArchiveThreadLink) {
          throw RuntimeException(
            "PostLinkable value is not of ArchiveThreadLink type, key = "
              + postLinkable.key + ", type = "
              + postLinkable.type.name
          )
        }

        postLinkableValueJson = gson.toJson(
          PostLinkableArchiveLinkValue(
            SerializablePostLinkableType.Archive,
            archiveThreadLink.archiveType.domain,
            archiveThreadLink.board,
            archiveThreadLink.threadId,
            archiveThreadLink.postId
          )
        )

        serializablePostLinkableSpan.setPostLinkableType(SerializablePostLinkableType.Archive.typeValue)
      }
      else -> throw IllegalArgumentException("Not implemented for type " + postLinkable.type.name)
    }

    if (postLinkableValueJson == null) {
      return
    }

    serializablePostLinkableSpan.postLinkableValueJson = postLinkableValueJson
    spanInfo.spanData = gson.toJson(serializablePostLinkableSpan)
    serializableSpannableString.addSpanInfo(spanInfo)
  }

  @JvmStatic
  fun deserializeSpannableString(
    gson: Gson,
    serializableSpannableString: SerializableSpannableString?
  ): CharSequence {
    if (serializableSpannableString == null || serializableSpannableString.text.isEmpty()) {
      return ""
    }

    val spannableString = SpannableString(serializableSpannableString.text)
    for (spanInfo in serializableSpannableString.spanInfoList) {
      when (SerializableSpanType.from(spanInfo.spanType)) {
        SerializableSpanType.ForegroundColorSpanHashedType -> {
          val serializableForegroundColorSpan = gson.fromJson(
            spanInfo.spanData,
            SerializableForegroundColorSpan::class.java
          )
          spannableString.setSpanSafe(
            ForegroundColorSpanHashed(serializableForegroundColorSpan.foregroundColor),
            spanInfo.spanStart,
            spanInfo.spanEnd,
            spanInfo.flags
          )
        }
        SerializableSpanType.ColorizableForegroundColorSpan -> {
          val colorizableForegroundColorSpan = gson.fromJson(
            spanInfo.spanData,
            SerializableColorizableForegroundColorSpan::class.java
          )

          spannableString.setSpanSafe(
            ForegroundColorIdSpan(colorizableForegroundColorSpan.colorId),
            spanInfo.spanStart,
            spanInfo.spanEnd,
            spanInfo.flags
          )
        }
        SerializableSpanType.BackgroundColorSpanHashedType -> {
          val serializableBackgroundColorSpan = gson.fromJson(
            spanInfo.spanData,
            SerializableBackgroundColorSpan::class.java
          )
          spannableString.setSpanSafe(
            BackgroundColorSpanHashed(serializableBackgroundColorSpan.backgroundColor),
            spanInfo.spanStart,
            spanInfo.spanEnd,
            spanInfo.flags
          )
        }
        SerializableSpanType.ColorizableBackgroundColorSpan -> {
          val colorizableBackgroundColorSpan = gson.fromJson(
            spanInfo.spanData,
            SerializableColorizableBackgroundColorSpan::class.java
          )

          spannableString.setSpanSafe(
            BackgroundColorIdSpan(colorizableBackgroundColorSpan.colorId),
            spanInfo.spanStart,
            spanInfo.spanEnd,
            spanInfo.flags
          )
        }
        SerializableSpanType.StrikethroughSpanType -> {
          spannableString.setSpanSafe(StrikethroughSpan(),
            spanInfo.spanStart,
            spanInfo.spanEnd,
            spanInfo.flags
          )
        }
        SerializableSpanType.StyleSpanType -> {
          val serializableStyleSpan = gson.fromJson(
            spanInfo.spanData,
            SerializableStyleSpan::class.java
          )

          spannableString.setSpanSafe(StyleSpan(serializableStyleSpan.style),
            spanInfo.spanStart,
            spanInfo.spanEnd,
            spanInfo.flags
          )
        }
        SerializableSpanType.TypefaceSpanType -> {
          val serializableTypefaceSpan = gson.fromJson(
            spanInfo.spanData,
            SerializableTypefaceSpan::class.java
          )

          spannableString.setSpanSafe(TypefaceSpan(serializableTypefaceSpan.family),
            spanInfo.spanStart,
            spanInfo.spanEnd,
            spanInfo.flags
          )
        }
        SerializableSpanType.AbsoluteSizeSpanHashed -> {
          val serializableAbsoluteSizeSpan = gson.fromJson(
            spanInfo.spanData,
            SerializableAbsoluteSizeSpan::class.java
          )

          spannableString.setSpanSafe(
            AbsoluteSizeSpanHashed(serializableAbsoluteSizeSpan.size),
            spanInfo.spanStart,
            spanInfo.spanEnd,
            spanInfo.flags
          )
        }
        SerializableSpanType.PostLinkable -> deserializeAndApplyPostLinkableSpan(
          gson,
          spannableString,
          spanInfo
        )
      }.exhaustive
    }

    return spannableString
  }

  private fun deserializeAndApplyPostLinkableSpan(
    gson: Gson,
    spannableString: SpannableString,
    spanInfo: SerializableSpanInfo
  ) {
    val serializablePostLinkableSpan = gson.fromJson(
      spanInfo.spanData,
      SerializablePostLinkableSpan::class.java
    )

    val postLinkable = extractPostLinkable(gson, serializablePostLinkableSpan)
      ?: return

    spannableString.setSpanSafe(
      postLinkable,
      spanInfo.spanStart,
      spanInfo.spanEnd,
      spanInfo.flags
    )
  }

  private fun extractPostLinkable(
    gson: Gson,
    serializablePostLinkableSpan: SerializablePostLinkableSpan
  ): PostLinkable? {
    when (serializablePostLinkableSpan.postLinkableType) {
      SerializablePostLinkableType.Dead -> {
        val postLinkableValue: PostLinkableValue = try {
          // New PostLinkableValue for DEAD link type
          var postLinkableValue: PostLinkableValue = gson.fromJson(
            serializablePostLinkableSpan.postLinkableValueJson,
            PostLinkableThreadOrPostLinkValue::class.java
          )

          if (!postLinkableValue.isValid) {
            postLinkableValue = gson.fromJson(
              serializablePostLinkableSpan.postLinkableValueJson,
              PostLinkableQuoteValue::class.java
            )
          }

          postLinkableValue
        } catch (ignored: Throwable) {
          // Old and deprecated PostLinkableValue for DEAD link type
          gson.fromJson(
            serializablePostLinkableSpan.postLinkableValueJson,
            PostLinkableQuoteValue::class.java
          )
        }

        when (postLinkableValue) {
          is PostLinkableThreadOrPostLinkValue -> {
            return PostLinkable(
              serializablePostLinkableSpan.key,
              PostLinkable.Value.ThreadOrPostLink(
                postLinkableValue.board,
                postLinkableValue.threadId,
                postLinkableValue.postId
              ),
              PostLinkable.Type.DEAD
            )
          }
          is PostLinkableQuoteValue -> {
            return PostLinkable(
              serializablePostLinkableSpan.key,
              PostLinkable.Value.LongValue(postLinkableValue.postId),
              PostLinkable.Type.DEAD
            )
          }
          else -> return null
        }
      }
      SerializablePostLinkableType.Quote -> {
        val postLinkableQuoteValue = gson.fromJson(
          serializablePostLinkableSpan.postLinkableValueJson,
          PostLinkableQuoteValue::class.java
        )

        return PostLinkable(
          serializablePostLinkableSpan.key,
          PostLinkable.Value.LongValue(postLinkableQuoteValue.postId),
          PostLinkable.Type.QUOTE
        )
      }
      SerializablePostLinkableType.Link -> {
        val postLinkableLinkValue = gson.fromJson(
          serializablePostLinkableSpan.postLinkableValueJson,
          PostLinkableLinkValue::class.java
        )

        return PostLinkable(
          serializablePostLinkableSpan.key,
          PostLinkable.Value.StringValue(postLinkableLinkValue.link),
          PostLinkable.Type.LINK
        )
      }
      SerializablePostLinkableType.Spoiler -> {
        return PostLinkable(
          serializablePostLinkableSpan.key,
          PostLinkable.Value.NoValue,
          PostLinkable.Type.SPOILER
        )
      }
      SerializablePostLinkableType.Thread -> {
        val postLinkThreadLinkValue = gson.fromJson(
          serializablePostLinkableSpan.postLinkableValueJson,
          PostLinkableThreadOrPostLinkValue::class.java
        )

        return PostLinkable(
          serializablePostLinkableSpan.key,
          PostLinkable.Value.ThreadOrPostLink(
            postLinkThreadLinkValue.board,
            postLinkThreadLinkValue.threadId,
            postLinkThreadLinkValue.postId
          ),
          PostLinkable.Type.THREAD
        )
      }
      SerializablePostLinkableType.Board -> {
        val postLinkableBoardLinkValue = gson.fromJson(
          serializablePostLinkableSpan.postLinkableValueJson,
          PostLinkableBoardLinkValue::class.java
        )

        return PostLinkable(
          serializablePostLinkableSpan.key,
          PostLinkable.Value.StringValue(
            postLinkableBoardLinkValue.boardLink
          ),
          PostLinkable.Type.BOARD
        )
      }
      SerializablePostLinkableType.Search -> {
        val postLinkableSearchLinkValue = gson.fromJson(
          serializablePostLinkableSpan.postLinkableValueJson,
          PostLinkableSearchLinkValue::class.java
        )

        return PostLinkable(
          serializablePostLinkableSpan.key,
          PostLinkable.Value.SearchLink(
            postLinkableSearchLinkValue.board,
            postLinkableSearchLinkValue.search
          ),
          PostLinkable.Type.SEARCH
        )
      }
      SerializablePostLinkableType.Archive -> {
        val postLinkableArchiveLinkValue = gson.fromJson(
          serializablePostLinkableSpan.postLinkableValueJson,
          PostLinkableArchiveLinkValue::class.java
        )

        if (!ArchiveType.hasDomain(postLinkableArchiveLinkValue.archiveDomain)) {
          return null
        }

        val archiveType = ArchiveType.byDomain(postLinkableArchiveLinkValue.archiveDomain)
          ?: return null

        val archiveThreadLink = PostLinkable.Value.ArchiveThreadLink(
          archiveType,
          postLinkableArchiveLinkValue.boardCode,
          postLinkableArchiveLinkValue.threadNo,
          postLinkableArchiveLinkValue.postNo
        )

        return PostLinkable(
          archiveThreadLink.urlText(),
          archiveThreadLink,
          PostLinkable.Type.ARCHIVE
        )
      }
      else -> return null
    }
  }

}