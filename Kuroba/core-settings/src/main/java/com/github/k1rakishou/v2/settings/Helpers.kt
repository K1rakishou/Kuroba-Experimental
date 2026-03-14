package com.github.k1rakishou.v2.settings

import java.nio.ByteBuffer

internal fun ByteBuffer.readString(): String? {
  if (this.remaining() < Int.SIZE_BYTES) {
    return null
  }

  val length = this.getInt()
  if (length <= 0) {
    return null
  }

  val bytes = ByteArray(length)
  this.get(bytes)

  return String(bytes, Charsets.UTF_8)
}

internal fun String?.writeToByteBuffer(): ByteBuffer {
  if (this == null) {
    return ByteBuffer.allocate(0)
  }

  val bytes = this.toByteArray(Charsets.UTF_8)

  return with(ByteBuffer.allocate(Int.SIZE_BYTES + bytes.size)) {
    putInt(bytes.size)
    put(bytes)
  }
}