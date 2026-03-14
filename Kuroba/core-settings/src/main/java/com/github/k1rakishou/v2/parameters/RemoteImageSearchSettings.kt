package com.github.k1rakishou.v2.parameters

import com.github.k1rakishou.v2.InternalSettings
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoteImageSearchSettings(
  @field:Json("last_used_search_type")
  val lastUsedSearchType: InstanceType? = null,
  @field:Json("settings")
  val settings: List<InstanceSettings>?
) {

  fun yandex(): InstanceSettings? {
    return settings?.firstOrNull { it.instanceType == InstanceType.Yandex }
  }

  fun searx(): InstanceSettings? {
    return settings?.firstOrNull { it.instanceType == InstanceType.Searx }
  }

  suspend fun update(
    internalSettings: InternalSettings,
    instanceType: InstanceType,
    updater: (InstanceSettings) -> InstanceSettings,
    creator: () -> InstanceSettings
  ) {
    var updated = false
    val newSettings = mutableListOf<InstanceSettings>()

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

    val prev = internalSettings.remoteImageSearchSettings.read()
    internalSettings.remoteImageSearchSettings.write(prev.copy(settings = newSettings))
  }

  fun byImageSearchInstanceType(searchInstanceType: InstanceType): InstanceSettings? {
    return when (searchInstanceType) {
      InstanceType.Searx -> searx()
      InstanceType.Yandex -> yandex()
    }
  }

  @JsonClass(generateAdapter = true)
  data class InstanceSettings(
    @field:Json("instance_type")
    val instanceType: InstanceType,
    @field:Json("base_url")
    val baseUrl: String,
    @field:Json("cookies")
    val cookies: String?
  ) {
    companion object {
      fun searxDefaults(): InstanceSettings {
        return InstanceSettings(
          instanceType = InstanceType.Searx,
          baseUrl = "https://searx.prvcy.eu",
          cookies = null
        )
      }

      fun yandexDefaults(): InstanceSettings {
        return InstanceSettings(
          instanceType = InstanceType.Yandex,
          baseUrl = "https://yandex.com",
          cookies = null
        )
      }
    }
  }

  enum class InstanceType(val type: Int) {
    Searx(0),
    Yandex(1)
  }

  companion object {
    fun defaults(): RemoteImageSearchSettings {
      return RemoteImageSearchSettings(
        lastUsedSearchType = null,
        settings = listOf(
          InstanceSettings.searxDefaults(),
          InstanceSettings.yandexDefaults()
        )
      )
    }
  }
}