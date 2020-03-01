package com.github.adamantcheese.chan.core.loader.impl

import android.text.Spanned
import android.text.style.CharacterStyle
import com.github.adamantcheese.chan.core.loader.LoaderResult
import com.github.adamantcheese.chan.core.loader.LoaderType
import com.github.adamantcheese.chan.core.loader.OnDemandContentLoader
import com.github.adamantcheese.chan.core.loader.PostLoaderData
import io.reactivex.Single
import okhttp3.OkHttpClient

class PostExtraContentLoader(
        private val okHttpClient: OkHttpClient
) : OnDemandContentLoader(LoaderType.PostExtraContentLoader) {

    override fun isAlreadyCached(postLoaderData: PostLoaderData): Boolean {
        // TODO
        return false
    }

    override fun startLoading(postLoaderData: PostLoaderData): Single<LoaderResult> {
        val comment = postLoaderData.post.comment
        if (comment.isEmpty() || comment !is Spanned) {
            return reject()
        }

        val spans = parseSpans(comment)
        if (spans.isEmpty()) {
            return reject()
        }

        return success()
    }

    private fun parseSpans(comment: Spanned): List<CommentSpan> {
        val spans = comment.getSpans(0, comment.length, CharacterStyle::class.java)
        if (spans.isEmpty()) {
            return emptyList()
        }

        return spans.map { span ->
            return@map CommentSpan(span, comment.getSpanStart(span), comment.getSpanEnd(span))
        }
    }

    private data class CommentSpan(
            val span: CharacterStyle,
            val start: Int,
            val end: Int
    )

    override fun cancelLoading(postLoaderData: PostLoaderData) {
        // TODO
    }

}