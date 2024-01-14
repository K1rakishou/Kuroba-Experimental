package com.github.k1rakishou.chan.features.reply_image_search.instances

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.reply_image_search.ImageSearchInstance
import com.github.k1rakishou.chan.features.reply_image_search.ImageSearchInstanceType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class SearxInstance : ImageSearchInstance(
    type = ImageSearchInstanceType.Searx,
    icon = R.drawable.searx_favicon
) {

    override val cookies: String? = null

    override suspend fun baseUrl(): HttpUrl {
        return "https://searx.prvcy.eu".toHttpUrl()
    }

    override fun updateCookies(newCookies: String) {
        // no-op
    }

    override suspend fun buildSearchUrl(query: String, page: Int?): HttpUrl {
        return with(baseUrl().newBuilder()) {
            addPathSegment("search")
            addQueryParameter("q", query)
            addQueryParameter("categories", "images")
            addQueryParameter("language", "en-US")
            addQueryParameter("format", "json")

            if (page != null && page > 0) {
                addQueryParameter("pageno", "${page}")
            }

            build()
        }
    }

}