package com.github.k1rakishou.chan.utils

import android.util.Base64
import android.util.Base64InputStream
import android.util.Base64OutputStream
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.HashingSink
import okio.HashingSource
import okio.blackholeSink
import okio.buffer
import okio.source
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

object HashingUtil {

  fun fileHash(inputFile: File): String? {
    if (!inputFile.exists()) {
      return null
    }

    return inputStreamMd5(inputFile.inputStream())
  }

  fun fileBase64(inputFile: File, flags: Int = Base64.DEFAULT): String? {
    if (!inputFile.exists()) {
      return null
    }

    return ByteArrayOutputStream().use { outputStream ->
      Base64OutputStream(outputStream, flags).use { base64FilterStream ->
        inputFile.inputStream().use { inputStream ->
          inputStream.copyTo(base64FilterStream)
        }
      }

      return@use outputStream.toString()
    }
  }

  fun stringBase64Decode(string: String, flags: Int = Base64.DEFAULT): String {
    return ByteArrayOutputStream().use { outputStream ->
      string.byteInputStream().use { inputStream ->
        Base64InputStream(inputStream, flags).use { base64FilterStream ->
          base64FilterStream.copyTo(outputStream)
        }
      }

      return@use outputStream.toString()
    }
  }

  fun inputStreamMd5(inputStream: InputStream): String {
    return HashingSource.md5(inputStream.source()).use { hashingSource ->
      return@use hashingSource.buffer().use { source ->
        source.readAll(blackholeSink())
        return@use hashingSource.hash.hex()
      }
    }
  }

  fun stringHash(inputString: String): String {
    return inputString.encodeUtf8().md5().hex()
  }

  fun stringsHash(inputStrings: Collection<String>): String {
    return HashingSink.sha256(blackholeSink()).use { hashingSink ->
      hashingSink.buffer().outputStream().use { outputStream ->
        inputStrings.forEach { inputString -> inputString.encodeUtf8().write(outputStream) }
      }

      return@use hashingSink.hash.hex()
    }
  }

  @JvmStatic
  fun byteArrayHashSha256HexString(byteArray: ByteArray): String {
    return byteArray.toByteString(0, byteArray.size).sha256().hex()
  }

}