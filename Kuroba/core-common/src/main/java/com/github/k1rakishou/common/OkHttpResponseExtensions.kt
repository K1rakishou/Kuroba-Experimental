package com.github.k1rakishou.common

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response

val MediaTypeTextHtml = "text/html".toMediaType()
val MediaTypeApplicationJson = "application/json".toMediaType()

fun Response.isContentTypeTextHtml(): Boolean {
  return tryExtractMediaType() == MediaTypeTextHtml
}

fun Response.isContentTypeApplicationJson(): Boolean {
  return tryExtractMediaType() == MediaTypeApplicationJson
}

fun Response.tryExtractMediaType(): MediaType? {
  val contentTypeParts = header("Content-Type")
    ?.split(";")
    ?.map { part -> part.trim() }
    ?: return null

  for (contentTypePart in contentTypeParts) {
    val mediaType = contentTypePart.toMediaTypeOrNull()
    if (mediaType != null) {
      return mediaType
    }
  }

  return null
}