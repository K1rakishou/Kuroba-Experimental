package com.github.k1rakishou.chan.features.search.remotemedia.instances

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.search.remotemedia.ImageSearchInstance
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.RemoteImageSearchSettings
import com.github.k1rakishou.v2.parameters.RemoteImageSearchSettings.InstanceSettings
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class YandexInstance(kurobaSettings: KurobaSettings) : ImageSearchInstance(
    kurobaSettings = kurobaSettings,
    type = RemoteImageSearchSettings.InstanceType.Yandex,
    icon = R.drawable.yandex_favicon
) {

    private var _cookies: String? = null
    override val cookies: String?
        get() = _cookies

    init {
        _cookies = kurobaSettings.internal.remoteImageSearchSettings.readBlocking()
            .yandex()
            ?.cookies
    }

    override fun baseUrl(): HttpUrl {
        return "https://yandex.com".toHttpUrl()
    }

    override suspend fun updateCookies(newCookies: String) {
        _cookies = newCookies
        kurobaSettings.internal.remoteImageSearchSettings.readBlocking().update(
            internalSettings = kurobaSettings.internal,
            instanceType = RemoteImageSearchSettings.InstanceType.Yandex,
            updater = { settings -> settings.copy(cookies = newCookies) },
            creator = {
                InstanceSettings(
                    instanceType = RemoteImageSearchSettings.InstanceType.Yandex,
                    baseUrl = baseUrl().toString(),
                    cookies = newCookies
                )
            }
        )
    }

    override suspend fun buildSearchUrl(baseUrl: HttpUrl, query: String, page: Int?): HttpUrl {
        return with(baseUrl.newBuilder()) {
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