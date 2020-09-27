package com.github.k1rakishou.chan.core.mapper

import android.text.SpannableString
import android.text.style.CharacterStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import com.github.k1rakishou.chan.ui.text.span.AbsoluteSizeSpanHashed
import com.github.k1rakishou.chan.ui.text.span.BackgroundColorSpanHashed
import com.github.k1rakishou.chan.ui.text.span.ForegroundColorSpanHashed
import com.github.k1rakishou.chan.ui.text.span.PostLinkable
import com.github.k1rakishou.chan.ui.theme.ChanTheme
import com.github.k1rakishou.model.data.serializable.spans.*
import com.github.k1rakishou.model.data.serializable.spans.SerializablePostLinkableSpan.PostLinkableType
import com.github.k1rakishou.model.data.serializable.spans.SerializableSpannableString.SpanInfo
import com.github.k1rakishou.model.data.serializable.spans.SerializableSpannableString.SpanType
import com.github.k1rakishou.model.data.serializable.spans.linkable.*
import com.google.gson.Gson

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
                val serializableForegroundColorSpan =
                        SerializableForegroundColorSpan(span.foregroundColor)
                val spanInfo = SpanInfo(
                        SpanType.ForegroundColorSpanHashedType,
                        spanStart,
                        spanEnd,
                        flags
                )

                spanInfo.spanData = gson.toJson(serializableForegroundColorSpan)
                serializableSpannableString.addSpanInfo(spanInfo)
            }
            if (span is BackgroundColorSpanHashed) {
                val serializableBackgroundColorSpan =
                        SerializableBackgroundColorSpan(span.backgroundColor)
                val spanInfo = SpanInfo(
                        SpanType.BackgroundColorSpanHashedType,
                        spanStart,
                        spanEnd,
                        flags
                )

                spanInfo.spanData = gson.toJson(serializableBackgroundColorSpan)
                serializableSpannableString.addSpanInfo(spanInfo)
            }
            if (span is StrikethroughSpan) {
                val spanInfo = SpanInfo(
                        SpanType.StrikethroughSpanType,
                        spanStart,
                        spanEnd,
                        flags
                )

                spanInfo.spanData = null
                serializableSpannableString.addSpanInfo(spanInfo)
            }
            if (span is StyleSpan) {
                val serializableStyleSpan = SerializableStyleSpan(span.style)
                val spanInfo = SpanInfo(
                        SpanType.StyleSpanType,
                        spanStart,
                        spanEnd,
                        flags
                )

                spanInfo.spanData = gson.toJson(serializableStyleSpan)
                serializableSpannableString.addSpanInfo(spanInfo)
            }
            if (span is TypefaceSpan) {
                val serializableTypefaceSpan = SerializableTypefaceSpan(span.family)
                val spanInfo = SpanInfo(
                        SpanType.TypefaceSpanType,
                        spanStart,
                        spanEnd,
                        flags
                )

                spanInfo.spanData = gson.toJson(serializableTypefaceSpan)
                serializableSpannableString.addSpanInfo(spanInfo)
            }
            if (span is AbsoluteSizeSpanHashed) {
                val serializableAbsoluteSizeSpan = SerializableAbsoluteSizeSpan(span.size)
                val spanInfo = SpanInfo(
                        SpanType.AbsoluteSizeSpanHashed,
                        spanStart,
                        spanEnd,
                        flags
                )

                spanInfo.spanData = gson.toJson(serializableAbsoluteSizeSpan)
                serializableSpannableString.addSpanInfo(spanInfo)
            }

            if (span is PostLinkable) {
                serializePostLinkable(gson, serializableSpannableString, span, spanStart, spanEnd, flags)
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
        val spanInfo = SpanInfo(
                SpanType.PostLinkable,
                spanStart,
                spanEnd,
                flags
        )
        val serializablePostLinkableSpan = SerializablePostLinkableSpan(postLinkable.key.toString())
        var postLinkableValueJson: String? = null

        when (postLinkable.type) {
            PostLinkable.Type.DEAD -> {
                val postId = postLinkable.linkableValue.extractLongOrNull()

                if (postId != null) {
                    postLinkableValueJson = gson.toJson(
                      PostLinkableQuoteValue(
                        PostLinkableType.Dead,
                        postId
                      )
                    )
                    serializablePostLinkableSpan.setPostLinkableType(PostLinkableType.Dead.typeValue)
                }
            }
            PostLinkable.Type.QUOTE -> {
                val postId = postLinkable.linkableValue.extractLongOrNull()

                if (postId != null) {
                    postLinkableValueJson = gson.toJson(
                        PostLinkableQuoteValue(
                            PostLinkableType.Quote,
                            postId
                        )
                    )
                    serializablePostLinkableSpan.setPostLinkableType(PostLinkableType.Quote.typeValue)
                }
            }
            PostLinkable.Type.LINK -> {
                val link = (postLinkable.linkableValue as? PostLinkable.Value.StringValue)?.value

                if (link != null) {
                    postLinkableValueJson = gson.toJson(
                      PostLinkableLinkValue(
                        PostLinkableType.Link,
                        link.toString()
                      )
                    )
                    serializablePostLinkableSpan.setPostLinkableType(PostLinkableType.Link.typeValue)
                }
            }
            PostLinkable.Type.SPOILER -> {
                postLinkableValueJson = gson.toJson(
                        PostLinkableSpoilerValue(PostLinkableType.Spoiler)
                )

                serializablePostLinkableSpan.setPostLinkableType(PostLinkableType.Spoiler.typeValue)
            }
            PostLinkable.Type.THREAD -> {
                if (postLinkable.linkableValue !is PostLinkable.Value.ThreadLink) {
                    throw RuntimeException(
                            "PostLinkable value is not of ThreadLink type, key = "
                                    + postLinkable.key + ", type = "
                                    + postLinkable.type.name)
                }

                val threadLink = postLinkable.linkableValue

                postLinkableValueJson = gson.toJson(
                        PostLinkThreadLinkValue(
                                PostLinkableType.Thread,
                                threadLink.board,
                                threadLink.threadId,
                                threadLink.postId
                        )
                )
                serializablePostLinkableSpan.setPostLinkableType(PostLinkableType.Thread.typeValue)
            }
            PostLinkable.Type.BOARD -> {
                if (postLinkable.linkableValue !is PostLinkable.Value.StringValue) {
                    throw RuntimeException(
                      "PostLinkable value is not of StringValue type, key = "
                        + postLinkable.key + ", type = "
                        + postLinkable.type.name)
                }

                postLinkableValueJson = gson.toJson(
                    PostLinkableBoardLinkValue(
                        PostLinkableType.Board,
                        postLinkable.linkableValue.value.toString()
                    )
                )
                serializablePostLinkableSpan.setPostLinkableType(PostLinkableType.Board.typeValue)
            }
            PostLinkable.Type.SEARCH -> {
                if (postLinkable.linkableValue !is PostLinkable.Value.SearchLink) {
                    throw RuntimeException(
                            "PostLinkable value is not of SearchLink type, key = "
                                    + postLinkable.key + ", type = "
                                    + postLinkable.type.name)
                }

                val searchLink = postLinkable.linkableValue
                postLinkableValueJson = gson.toJson(
                        PostLinkableSearchLinkValue(
                                PostLinkableType.Search,
                                searchLink.board,
                                searchLink.search
                        )
                )

                serializablePostLinkableSpan.setPostLinkableType(PostLinkableType.Search.typeValue)
            }
            else -> throw IllegalArgumentException("Not implemented for type " + postLinkable.type.name)
        }

        serializablePostLinkableSpan.postLinkableValueJson = postLinkableValueJson
        spanInfo.spanData = gson.toJson(serializablePostLinkableSpan)
        serializableSpannableString.addSpanInfo(spanInfo)
    }

    @JvmStatic
    fun deserializeSpannableString(
            gson: Gson,
            serializableSpannableString: SerializableSpannableString?,
            currentTheme: ChanTheme
    ): CharSequence {
        if (serializableSpannableString == null || serializableSpannableString.text.isEmpty()) {
            return ""
        }

        val spannableString = SpannableString(serializableSpannableString.text)
        for (spanInfo in serializableSpannableString.spanInfoList) {
            when (SpanType.from(spanInfo.spanType)) {
                SpanType.ForegroundColorSpanHashedType -> {
                    val serializableForegroundColorSpan = gson.fromJson(
                            spanInfo.spanData,
                            SerializableForegroundColorSpan::class.java
                    )
                    spannableString.setSpan(
                            ForegroundColorSpanHashed(serializableForegroundColorSpan.foregroundColor),
                            spanInfo.spanStart,
                            spanInfo.spanEnd,
                            spanInfo.flags
                    )
                }
                SpanType.BackgroundColorSpanHashedType -> {
                    val serializableBackgroundColorSpan = gson.fromJson(
                            spanInfo.spanData,
                            SerializableBackgroundColorSpan::class.java
                    )
                    spannableString.setSpan(
                            BackgroundColorSpanHashed(serializableBackgroundColorSpan.backgroundColor),
                            spanInfo.spanStart,
                            spanInfo.spanEnd,
                            spanInfo.flags
                    )
                }
                SpanType.StrikethroughSpanType -> spannableString.setSpan(StrikethroughSpan(),
                        spanInfo.spanStart,
                        spanInfo.spanEnd,
                        spanInfo.flags
                )
                SpanType.StyleSpanType -> {
                    val serializableStyleSpan = gson.fromJson(
                            spanInfo.spanData,
                            SerializableStyleSpan::class.java
                    )

                    spannableString.setSpan(StyleSpan(serializableStyleSpan.style),
                            spanInfo.spanStart,
                            spanInfo.spanEnd,
                            spanInfo.flags
                    )
                }
                SpanType.TypefaceSpanType -> {
                    val serializableTypefaceSpan = gson.fromJson(
                            spanInfo.spanData,
                            SerializableTypefaceSpan::class.java
                    )

                    spannableString.setSpan(TypefaceSpan(serializableTypefaceSpan.family),
                            spanInfo.spanStart,
                            spanInfo.spanEnd,
                            spanInfo.flags
                    )
                }
                SpanType.AbsoluteSizeSpanHashed -> {
                    val serializableAbsoluteSizeSpan = gson.fromJson(
                            spanInfo.spanData,
                            SerializableAbsoluteSizeSpan::class.java
                    )

                    spannableString.setSpan(AbsoluteSizeSpanHashed(serializableAbsoluteSizeSpan.size),
                            spanInfo.spanStart,
                            spanInfo.spanEnd,
                            spanInfo.flags
                    )
                }
                SpanType.PostLinkable -> deserializeAndApplyPostLinkableSpan(
                        gson,
                        spannableString,
                        spanInfo,
                        currentTheme
                )
            }
        }
        return spannableString
    }

    private fun deserializeAndApplyPostLinkableSpan(
            gson: Gson,
            spannableString: SpannableString,
            spanInfo: SpanInfo,
            currentTheme: ChanTheme
    ) {
        val serializablePostLinkableSpan = gson.fromJson(
                spanInfo.spanData,
                SerializablePostLinkableSpan::class.java
        )

        val postLinkable = when (serializablePostLinkableSpan.postLinkableType) {
            PostLinkableType.Dead -> {
                val postLinkableQuoteValue = gson.fromJson(
                  serializablePostLinkableSpan.postLinkableValueJson,
                  PostLinkableQuoteValue::class.java
                )

                PostLinkable(
                  currentTheme,
                  serializablePostLinkableSpan.key,
                  PostLinkable.Value.LongValue(postLinkableQuoteValue.postId),
                  PostLinkable.Type.DEAD
                )
            }
            PostLinkableType.Quote -> {
                val postLinkableQuoteValue = gson.fromJson(
                        serializablePostLinkableSpan.postLinkableValueJson,
                        PostLinkableQuoteValue::class.java
                )

                PostLinkable(
                        currentTheme,
                        serializablePostLinkableSpan.key,
                        PostLinkable.Value.LongValue(postLinkableQuoteValue.postId),
                        PostLinkable.Type.QUOTE
                )
            }
            PostLinkableType.Link -> {
                val postLinkableLinkValue = gson.fromJson(
                        serializablePostLinkableSpan.postLinkableValueJson,
                        PostLinkableLinkValue::class.java
                )

                PostLinkable(
                        currentTheme,
                        serializablePostLinkableSpan.key,
                        PostLinkable.Value.StringValue(postLinkableLinkValue.link),
                        PostLinkable.Type.LINK
                )
            }
            PostLinkableType.Spoiler -> PostLinkable(
                    currentTheme,
                    serializablePostLinkableSpan.key,
                    PostLinkable.Value.IntegerValue(currentTheme.postSpoilerColor),
                    PostLinkable.Type.SPOILER
            )
            PostLinkableType.Thread -> {
                val postLinkThreadLinkValue = gson.fromJson(
                        serializablePostLinkableSpan.postLinkableValueJson,
                        PostLinkThreadLinkValue::class.java
                )

                PostLinkable(
                        currentTheme,
                        serializablePostLinkableSpan.key,
                        PostLinkable.Value.ThreadLink(
                            postLinkThreadLinkValue.board,
                            postLinkThreadLinkValue.threadId,
                            postLinkThreadLinkValue.postId
                        ),
                        PostLinkable.Type.THREAD
                )
            }
            PostLinkableType.Board -> {
                val postLinkableBoardLinkValue = gson.fromJson(
                        serializablePostLinkableSpan.postLinkableValueJson,
                        PostLinkableBoardLinkValue::class.java
                )
                PostLinkable(
                        currentTheme,
                        serializablePostLinkableSpan.key,
                        PostLinkable.Value.StringValue(postLinkableBoardLinkValue.boardLink),
                        PostLinkable.Type.BOARD
                )
            }
            PostLinkableType.Search -> {
                val postLinkableSearchLinkValue = gson.fromJson(
                        serializablePostLinkableSpan.postLinkableValueJson,
                        PostLinkableSearchLinkValue::class.java
                )
                PostLinkable(currentTheme,
                        serializablePostLinkableSpan.key,
                        PostLinkable.Value.SearchLink(
                            postLinkableSearchLinkValue.board,
                            postLinkableSearchLinkValue.search
                        ),
                        PostLinkable.Type.SEARCH
                )
            }
            else -> throw IllegalArgumentException("Not implemented for type " +
                    serializablePostLinkableSpan.postLinkableType.name)
        }

        spannableString.setSpan(
                postLinkable,
                spanInfo.spanStart,
                spanInfo.spanEnd,
                spanInfo.flags
        )
    }
}