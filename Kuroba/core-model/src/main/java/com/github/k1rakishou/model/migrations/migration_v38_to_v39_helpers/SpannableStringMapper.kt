package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers

import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.Log
import com.github.k1rakishou.common.data.ArchiveType
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.setSpanSafe
import com.github.k1rakishou.core_spannable.AbsoluteSizeSpanHashed
import com.github.k1rakishou.core_spannable.BackgroundColorIdSpan
import com.github.k1rakishou.core_spannable.BackgroundColorSpanHashed
import com.github.k1rakishou.core_spannable.ForegroundColorIdSpan
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableArchiveLinkValue
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableBoardLinkValue
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableLinkValue
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableQuoteValue
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableSearchLinkValue
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableThreadOrPostLinkValue
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable.PostLinkableValue
import com.google.gson.Gson

@Deprecated(message = "This was deprecated in favor of ParcelableSpannableString so use ParcelableSpannableStringMapper instead. This class only exist for the 38 to 39 database migration")
object SpannableStringMapper {
  private const val TAG = "SpannableStringMapper"

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
      if (spanInfo.spanData.isNullOrEmpty()) {
        continue
      }

      when (SerializableSpanType.from(spanInfo.spanType)) {
        null -> continue
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
        SerializableSpanType.ColorizableForegroundColorSpan -> {
          try {
            val colorizableForegroundColorSpan = gson.fromJson(
              spanInfo.spanData,
              SerializableColorizableForegroundColorSpan::class.java
            )

            if (colorizableForegroundColorSpan?.colorId != null) {
              spannableString.setSpanSafe(
                ForegroundColorIdSpan(colorizableForegroundColorSpan.colorId),
                spanInfo.spanStart,
                spanInfo.spanEnd,
                spanInfo.flags
              )

              continue
            }

            val colorizableForegroundColorSpanString = gson.fromJson(
              spanInfo.spanData,
              SerializableColorizableForegroundColorSpanName::class.java
            )

            if (colorizableForegroundColorSpanString?.colorId.isNotNullNorEmpty()) {
              val colorId = ChanThemeColorId.byName(colorizableForegroundColorSpanString.colorId!!)
              if (colorId != null) {
                spannableString.setSpanSafe(
                  ForegroundColorIdSpan(colorId),
                  spanInfo.spanStart,
                  spanInfo.spanEnd,
                  spanInfo.flags
                )

                continue
              }
            }

            Log.e(TAG, "KurobaEx deserializeSpannableString() ColorizableForegroundColorSpan " +
              "failed to convert spanData=${spanInfo.spanData}")
          } catch (error: Throwable) {
            Log.e(TAG, "KurobaEx deserializeSpannableString() ColorizableForegroundColorSpan " +
              "error: ${error.errorMessageOrClassName()}, spanInfo.spanData=${spanInfo.spanData}")
          }
        }
        SerializableSpanType.ColorizableBackgroundColorSpan -> {
          try {
            val colorizableBackgroundColorSpan = gson.fromJson(
              spanInfo.spanData,
              SerializableColorizableBackgroundColorSpan::class.java
            )

            if (colorizableBackgroundColorSpan?.colorId != null) {
              spannableString.setSpanSafe(
                BackgroundColorIdSpan(colorizableBackgroundColorSpan.colorId),
                spanInfo.spanStart,
                spanInfo.spanEnd,
                spanInfo.flags
              )

              continue
            }

            val colorizableBackgroundColorSpanString = gson.fromJson(
              spanInfo.spanData,
              SerializableColorizableBackgroundColorSpanName::class.java
            )

            if (colorizableBackgroundColorSpanString?.colorId.isNotNullNorEmpty()) {
              val colorId = ChanThemeColorId.byName(colorizableBackgroundColorSpanString.colorId!!)
              if (colorId != null) {
                spannableString.setSpanSafe(
                  BackgroundColorIdSpan(colorId),
                  spanInfo.spanStart,
                  spanInfo.spanEnd,
                  spanInfo.flags
                )

                continue
              }
            }

            Log.e(TAG, "KurobaEx deserializeSpannableString() ColorizableBackgroundColorSpan " +
              "failed to convert spanData=${spanInfo.spanData}")
          } catch (error: Throwable) {
            Log.e(TAG, "KurobaEx deserializeSpannableString() ColorizableBackgroundColorSpan " +
              "error: ${error.errorMessageOrClassName()}, spanInfo.spanData=${spanInfo.spanData}")
          }
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
        SerializableSpanType.PostLinkable -> {
          deserializeAndApplyPostLinkableSpan(
            gson,
            spannableString,
            spanInfo
          )
        }
      }
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