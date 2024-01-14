package com.github.k1rakishou.chan.features.reply_image_search.instances

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.reply_image_search.ImageSearchInstance
import com.github.k1rakishou.chan.features.reply_image_search.ImageSearchInstanceType
import com.github.k1rakishou.persist_state.PersistableChanState
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class YandexInstance : ImageSearchInstance(
    type = ImageSearchInstanceType.Yandex,
    icon = R.drawable.yandex_favicon
) {

    private var _cookies: String? = null
    override val cookies: String?
        get() = _cookies

    init {
        _cookies = PersistableChanState.yandexImageSearchCookies.get()
            .takeIf { cookiesString -> cookiesString.isNotEmpty() }
    }

    override suspend fun baseUrl(): HttpUrl {
        return "https://yandex.com".toHttpUrl()
    }

    override fun updateCookies(newCookies: String) {
        _cookies = newCookies
        PersistableChanState.yandexImageSearchCookies.set(newCookies)
    }

    override suspend fun buildSearchUrl(query: String, page: Int?): HttpUrl {
        return with(baseUrl().newBuilder()) {
            addPathSegment("images")
            addPathSegment("search")
            addQueryParameter("text", query)

            if (page != null) {
                addQueryParameter("p", "${page}")
            }

            build()
        }
    }

}