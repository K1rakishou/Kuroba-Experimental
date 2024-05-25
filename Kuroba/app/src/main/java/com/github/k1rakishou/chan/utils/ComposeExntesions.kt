package com.github.k1rakishou.chan.utils

import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.chan.core.parser.TextPartSpan
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import java.nio.charset.StandardCharsets

inline fun buildAnnotatedString(
  capacity: Int,
  builder: (AnnotatedString.Builder).() -> Unit
): AnnotatedString {
  return AnnotatedString.Builder(capacity = capacity.coerceAtLeast(16))
    .apply(builder)
    .toAnnotatedString()
}

fun Buffer.writeUtfString(string: String) {
  writeLong(string.length.toLong())
  writeString(string, StandardCharsets.UTF_8)
}

fun Buffer.readUtfString(): String {
  val length = readLong()
  return readString(length, StandardCharsets.UTF_8)
}

fun AnnotatedString.Range<String>.extractLinkableAnnotationItem(): TextPartSpan.Linkable? {
  if (item.isEmpty()) {
    return null
  }

  val base64Decoded = item.decodeBase64()
    ?: return null

  val buffer = Buffer()
  buffer.write(base64Decoded)

  return TextPartSpan.Linkable.deserialize(buffer)
}

fun TextPartSpan.Linkable.createAnnotationItem(): String {
  return serialize().readByteString().base64()
}