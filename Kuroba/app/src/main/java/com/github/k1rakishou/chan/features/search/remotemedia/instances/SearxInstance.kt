package com.github.k1rakishou.chan.features.search.remotemedia.instances

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.search.remotemedia.ImageSearchInstance
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.RemoteImageSearchSettings
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class SearxInstance(kurobaSettings: KurobaSettings) : ImageSearchInstance(
    kurobaSettings = kurobaSettings,
    type = RemoteImageSearchSettings.InstanceType.Searx,
    icon = R.drawable.searx_favicon
) {

    override val cookies: String? = null

    override fun baseUrl(): HttpUrl {
        return "https://searx.prvcy.eu".toHttpUrl()
    }

    override suspend fun updateCookies(newCookies: String) {
        // no-op
    }

    override suspend fun buildSearchUrl(baseUrl: HttpUrl, query: String, page: Int?): HttpUrl {
        return with(baseUrl.newBuilder()) {
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