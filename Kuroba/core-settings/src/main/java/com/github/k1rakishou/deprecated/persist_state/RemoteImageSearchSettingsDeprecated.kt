package com.github.k1rakishou.deprecated.persist_state

import com.google.gson.annotations.SerializedName

@Deprecated("Deprecated")
data class RemoteImageSearchSettingsDeprecated(
    @SerializedName("last_used_search_type")
    val lastUsedSearchType: ImageSearchInstanceTypeDeprecated? = null,
    @SerializedName("settings")
    val settings: List<RemoteImageSearchInstanceSettingsDeprecated>?
) {

    fun yandex(): RemoteImageSearchInstanceSettingsDeprecated? {
        return settings?.firstOrNull { it.instanceType == ImageSearchInstanceTypeDeprecated.Yandex }
    }

    fun searx(): RemoteImageSearchInstanceSettingsDeprecated? {
        return settings?.firstOrNull { it.instanceType == ImageSearchInstanceTypeDeprecated.Searx }
    }

    fun update(
        instanceType: ImageSearchInstanceTypeDeprecated,
        updater: (RemoteImageSearchInstanceSettingsDeprecated) -> RemoteImageSearchInstanceSettingsDeprecated,
        creator: () -> RemoteImageSearchInstanceSettingsDeprecated
    ) {
        var updated = false
        val newSettings = mutableListOf<RemoteImageSearchInstanceSettingsDeprecated>()

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

//        val prev = PersistableChanState.remoteImageSearchSettings.get()
//        PersistableChanState.remoteImageSearchSettings.set(prev.copy(settings = newSettings))
    }

    fun byImageSearchInstanceType(searchInstanceType: ImageSearchInstanceTypeDeprecated): RemoteImageSearchInstanceSettingsDeprecated? {
        return when (searchInstanceType) {
            ImageSearchInstanceTypeDeprecated.Searx -> searx()
            ImageSearchInstanceTypeDeprecated.Yandex -> yandex()
        }
    }

    companion object {
        fun defaults(): RemoteImageSearchSettingsDeprecated {
            return RemoteImageSearchSettingsDeprecated(
                lastUsedSearchType = null,
                settings = listOf(
                    RemoteImageSearchInstanceSettingsDeprecated.searxDefaults(),
                    RemoteImageSearchInstanceSettingsDeprecated.yandexDefaults()
                )
            )
        }
    }
}

@Deprecated("Deprecated")
data class RemoteImageSearchInstanceSettingsDeprecated(
    @SerializedName("instance_type")
    val instanceType: ImageSearchInstanceTypeDeprecated,
    @SerializedName("base_url")
    val baseUrl: String,
    @SerializedName("cookies")
    val cookies: String?
) {
    companion object {
        fun searxDefaults(): RemoteImageSearchInstanceSettingsDeprecated {
            return RemoteImageSearchInstanceSettingsDeprecated(
                instanceType = ImageSearchInstanceTypeDeprecated.Searx,
                baseUrl = "https://searx.prvcy.eu",
                cookies = null
            )
        }

        fun yandexDefaults(): RemoteImageSearchInstanceSettingsDeprecated {
            return RemoteImageSearchInstanceSettingsDeprecated(
                instanceType = ImageSearchInstanceTypeDeprecated.Yandex,
                baseUrl = "https://yandex.com",
                cookies = null
            )
        }
    }
}

@Deprecated("Deprecated")
enum class ImageSearchInstanceTypeDeprecated(val type: Int) {
    Searx(0),
    Yandex(1)
}