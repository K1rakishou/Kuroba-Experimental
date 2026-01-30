package com.github.k1rakishou.common

import com.github.k1rakishou.common.StringUtils.splitOnce
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import org.joda.time.format.DateTimeFormat
import java.util.Locale

@JsonClass(generateAdapter = true)
data class KurobaCookie(
  val value: String,
  val expiration: Expiration,
  val path: String = "/"
) {
  fun expired(currentTimeMillis: Long): Boolean {
    when (val expiration = this.expiration) {
      KurobaCookie.Expiration.Session -> {
        // Session cookies are removed at the start of the app
        return false
      }
      KurobaCookie.Expiration.Never -> {
        // This is for cookies that are only ever deleted manually, so return false here
        return false
      }
      is KurobaCookie.Expiration.Time -> {
        if (currentTimeMillis >= expiration.expirationTimeMillis) {
          // Cookie expired
          return true
        }
      }
    }

    return false
  }

  fun expirationMillis(): Long? {
    return when (val expiration = this.expiration) {
      KurobaCookie.Expiration.Session -> {
        return null
      }
      KurobaCookie.Expiration.Never -> {
        Long.MAX_VALUE
      }
      is KurobaCookie.Expiration.Time -> {
        expiration.expirationTimeMillis
      }
    }
  }

  fun expirationTimeFormatted(): String {
    val expirationMillis = expirationMillis()
    if (expirationMillis == null) {
      return ""
    }

    return HttpDateFormatter.print(expirationMillis)
  }

  sealed interface Expiration {
    data object Session : Expiration
    data object Never : Expiration
    data class Time(val expirationTimeMillis: Long) : Expiration
  }

  override fun toString(): String {
    val expirationString = when (expiration) {
      Expiration.Session -> "Until app restart"
      Expiration.Never -> "Never (Manual)"
      is Expiration.Time -> HttpDateFormatter.print(expiration.expirationTimeMillis)
    }

    return "KurobaCookie(value: ${value}, expiration: ${expirationString})"
  }

  companion object {
    private const val TAG = "KurobaCookie"

    val HttpDateFormatter = DateTimeFormat
      .forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
      .withLocale(Locale.ENGLISH)
      .withZoneUTC()

    val MillisPerMinute = 1000 * 60

    fun fromRawCookie(rawCookie: String, expectedKey: String): KurobaCookie? {
      val cookieParts = rawCookie.split(";")
      return fromCookieParts(cookieParts, expectedKey)
    }

    fun fromKeyValue(key: String?, value: String?): KurobaCookie? {
      if (key.isNullOrBlank() || value == null) {
        return null
      }

      return fromCookieParts(
        cookieParts = listOf("${key}=${value}"),
        expectedKey = key
      )
    }

    fun fromCookieParts(
      cookieParts: List<String>,
      expectedKey: String
    ): KurobaCookie? {
      if (cookieParts.isEmpty()) {
        return null
      }

      val currentTime = System.currentTimeMillis()

      var resultValue: String? = null
      var resultExpiration: Expiration? = null
      var resultPath: String = "/"

      for (cookiePart in cookieParts) {
        val splitCookiePart = cookiePart.trim().splitOnce("=")
          ?: continue

        val key = splitCookiePart.first.trim()
        val value = splitCookiePart.second.trim()

        if (key == expectedKey) {
          resultValue = value
          continue
        }

        if (key.equals("Max-Age", ignoreCase = true)) {
          val maxAgeMinutes = value.toIntOrNull()
          if (maxAgeMinutes != null) {
            val expirationTimeMillis = currentTime + (maxAgeMinutes * 60 * 1000L)
            resultExpiration = Expiration.Time(expirationTimeMillis)
          }

          continue
        }

        if (key.equals("Expires", ignoreCase = true)) {
          try {
            val expirationTime = HttpDateFormatter.parseDateTime(value).millis
            resultExpiration = Expiration.Time(expirationTimeMillis = expirationTime)
          } catch (error: Throwable) {
            Logger.error(TAG, error) { "Failed to parse Expires parameter: '${value}'" }
          }

          continue
        }

        if (key.equals("Path", ignoreCase = true)) {
          resultPath = value
          continue
        }
      }

      if (resultValue == null) {
        return null
      }

      if (resultExpiration == null) {
        resultExpiration = Expiration.Session
      }

      return KurobaCookie(
        value = resultValue,
        expiration = resultExpiration,
        path = resultPath
      )
    }
  }
}

class KurobaCookieExpirationAdapter : JsonAdapter<KurobaCookie.Expiration>() {

  @FromJson
  override fun fromJson(reader: JsonReader): KurobaCookie.Expiration {
    when {
      reader.peek() == JsonReader.Token.STRING -> {
        val value = reader.nextString()
        if (value == "session") {
          return KurobaCookie.Expiration.Session
        } else if (value == "never") {
          return KurobaCookie.Expiration.Never
        }

        throw JsonDataException("Unknown expiration type: $value")
      }
      reader.peek() == JsonReader.Token.BEGIN_OBJECT -> {
        reader.beginObject()
        var expirationTimeMillis: Long? = null

        while (reader.hasNext()) {
          when (reader.nextName()) {
            "expirationTimeMillis" -> expirationTimeMillis = reader.nextLong()
            else -> reader.skipValue()
          }
        }

        reader.endObject()

        val timeMillis = expirationTimeMillis
          ?: throw JsonDataException("Missing expirationTimeMillis")
        return KurobaCookie.Expiration.Time(timeMillis)
      }
      else -> throw JsonDataException("Expected STRING or OBJECT but was ${reader.peek()}")
    }
  }

  @ToJson
  override fun toJson(writer: JsonWriter, value: KurobaCookie.Expiration?) {
    when (value) {
      null -> writer.nullValue()
      is KurobaCookie.Expiration.Session -> {
        writer.value("session")
      }
      is KurobaCookie.Expiration.Never -> {
        writer.value("never")
      }
      is KurobaCookie.Expiration.Time -> {
        writer.beginObject()
        writer
          .name("expirationTimeMillis")
          .value(value.expirationTimeMillis)
        writer.endObject()
      }
    }
  }
}