package com.github.adamantcheese.model.util

import android.util.JsonReader
import android.util.JsonToken

internal fun Throwable.errorMessageOrClassName(): String {
  if (message != null) {
    return message!!
  }

  return this::class.java.name
}

fun removeExtensionIfPresent(filename: String): String {
  val index = filename.lastIndexOf('.')
  if (index < 0) {
    return filename
  }

  return filename.substring(0, index)
}

fun extractFileNameExtension(filename: String): String? {
  val index = filename.lastIndexOf('.')
  return if (index == -1) {
    null
  } else {
    filename.substring(index + 1)
  }
}

fun JsonReader.nextStringOrNull(): String? {
  if (peek() == JsonToken.NULL) {
    skipValue()
    return null
  }

  val value = nextString()
  if (value.isNullOrEmpty()) {
    return null
  }

  return value
}

fun <T : Any?> JsonReader.jsonObject(func: JsonReader.() -> T): T {
  beginObject()

  try {
    return func(this)
  } finally {
    endObject()
  }
}