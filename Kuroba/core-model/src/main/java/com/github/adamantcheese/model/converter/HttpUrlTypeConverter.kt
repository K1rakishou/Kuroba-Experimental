package com.github.adamantcheese.model.converter

import androidx.room.TypeConverter
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class HttpUrlTypeConverter {

    @TypeConverter
    fun toHttpUrl(url: String): HttpUrl {
        return url.toHttpUrl()
    }

    @TypeConverter
    fun fromHttpUrl(url: HttpUrl): String {
        return url.toString()
    }

}