package com.github.k1rakishou.persist_state

import com.google.gson.annotations.SerializedName

data class RemoteImageSearchSettings(
    @SerializedName("last_used_search_type")
    val lastUsedSearchType: ImageSearchInstanceType? = null,
    @SerializedName("settings")
    val settings: List<RemoteImageSearchInstanceSettings>?
) {

    fun yandex(): RemoteImageSearchInstanceSettings? {
        return settings?.firstOrNull { it.instanceType == ImageSearchInstanceType.Yandex }
    }

    fun searx(): RemoteImageSearchInstanceSettings? {
        return settings?.firstOrNull { it.instanceType == ImageSearchInstanceType.Searx }
    }

    fun update(
        instanceType: ImageSearchInstanceType,
        updater: (RemoteImageSearchInstanceSettings) -> RemoteImageSearchInstanceSettings,
        creator: () -> RemoteImageSearchInstanceSettings
    ) {
        var updated = false
        val newSettings = mutableListOf<RemoteImageSearchInstanceSettings>()

        settings?.forEach { oldInstanceSettings ->
            if (oldInstanceSettings.instanceType == instanceType) {
                val updatedInstanceSettings = updater(oldInstanceSettings)
                if (oldInstanceSettings != updatedInstanceSettings) {
                    updated = true
                    newSettings += updatedInstanceSettings
                }
            } else {
                newSettings += oldInstanceSettings
            }
        }

        if (!updated) {
            newSettings += creator()
        }

        val prev = PersistableChanState.remoteImageSearchSettings.get()
        PersistableChanState.remoteImageSearchSettings.set(prev.copy(settings = newSettings))
    }

    fun byImageSearchInstanceType(searchInstanceType: ImageSearchInstanceType): RemoteImageSearchInstanceSettings? {
        return when (searchInstanceType) {
            ImageSearchInstanceType.Searx -> searx()
            ImageSearchInstanceType.Yandex -> yandex()
        }
    }

    companion object {
        fun defaults(): RemoteImageSearchSettings {
            return RemoteImageSearchSettings(
                lastUsedSearchType = null,
                settings = listOf(
                    RemoteImageSearchInstanceSettings.searxDefaults(),
                    RemoteImageSearchInstanceSettings.yandexDefaults()
                )
            )
        }
    }
}

data class RemoteImageSearchInstanceSettings(
    @SerializedName("instance_type")
    val instanceType: ImageSearchInstanceType,
    @SerializedName("base_url")
    val baseUrl: String,
    @SerializedName("cookies")
    val cookies: String?
) {
    companion object {
        fun searxDefaults(): RemoteImageSearchInstanceSettings {
            return RemoteImageSearchInstanceSettings(
                instanceType = ImageSearchInstanceType.Searx,
                baseUrl = "https://searx.prvcy.eu",
                cookies = null
            )
        }

        fun yandexDefaults(): RemoteImageSearchInstanceSettings {
            return RemoteImageSearchInstanceSettings(
                instanceType = ImageSearchInstanceType.Yandex,
                baseUrl = "https://yandex.com",
                cookies = null
            )
        }
    }
}

enum class ImageSearchInstanceType(val type: Int) {
    Searx(0),
    Yandex(1)
}