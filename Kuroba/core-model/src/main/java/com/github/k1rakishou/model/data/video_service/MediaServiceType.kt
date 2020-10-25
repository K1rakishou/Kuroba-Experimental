package com.github.k1rakishou.model.data.video_service

enum class MediaServiceType(
  val typeValue: Int,
  val apiType: ApiType
) {
  Youtube(0, ApiType.Html),
  SoundCloud(1, ApiType.Html),
  Streamable(2, ApiType.Html);

  companion object {
    fun fromTypeValue(typeValue: Int): MediaServiceType {
      return when (typeValue) {
        0 -> Youtube
        1 -> SoundCloud
        2 -> Streamable
        else -> throw IllegalStateException("typeValue: $typeValue is not supported!")
      }
    }
  }
}

enum class ApiType {
  Json,
  Html
}