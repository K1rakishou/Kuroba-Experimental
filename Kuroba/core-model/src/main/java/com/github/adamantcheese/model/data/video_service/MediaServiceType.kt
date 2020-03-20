package com.github.adamantcheese.model.data.video_service

enum class MediaServiceType(val typeValue: Int) {
    Youtube(0);

    companion object {
        fun fromTypeValue(typeValue: Int): MediaServiceType {
            return when (typeValue) {
                0 -> Youtube
                else -> throw IllegalStateException("typeValue: $typeValue is not supported!")
            }
        }
    }
}