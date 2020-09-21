package com.github.k1rakishou.model.util

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
