package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.fsaf.file.AbstractFile
import io.reactivex.exceptions.CompositeException
import javax.net.ssl.SSLException

internal sealed class FileCacheException(message: String) : Exception(message) {

  internal class CancellationException(val state: DownloadState, url: String)
    : FileCacheException("CancellationException for request with " +
    "url=$url, state=${state.javaClass.simpleName}")

  internal class FileNotFoundOnTheServerException
    : FileCacheException("FileNotFoundOnTheServerException")

  internal class CouldNotMarkFileAsDownloaded(val output: AbstractFile)
    : FileCacheException("Couldn't mark file as downloaded, file path = ${output.getFullPath()}")

  internal class NoResponseBodyException
    : FileCacheException("NoResponseBodyException")

  internal class CouldNotCreateOutputCacheFile(url: String)
    : FileCacheException("Could not create output cache file, url = $url")

  internal class OutputFileDoesNotExist(val path: String)
    : FileCacheException("OutputFileDoesNotExist path = $path")

  internal class ChunkFileDoesNotExist(val path: String)
    : FileCacheException("ChunkFileDoesNotExist path = $path")

  internal class HttpCodeException(val statusCode: Int)
    : FileCacheException("HttpCodeException statusCode = $statusCode")

  internal class BadOutputFileException(
    val path: String,
    val exists: Boolean,
    val isFile: Boolean,
    val canWrite: Boolean,
    val cacheFileType: CacheFileType,
  ) : FileCacheException("Bad output file, exists=$exists, isFile=$isFile, canWrite=$canWrite, cacheFileType=$cacheFileType, path=$path")
}

internal fun logErrorsAndExtractErrorMessage(tag: String, prefix: String, error: Throwable): String {
  return if (error is CompositeException) {
    val sb = StringBuilder()

    for ((index, exception) in error.exceptions.withIndex()) {
      sb.append(
        "$prefix ($index), " +
          "class = ${exception.javaClass.simpleName}, " +
          "message = ${exception.message}"
      ).append(";\n")
    }

    val result = sb.toString()

    if (shouldPrintFullStacktrace(error)) {
      logError(tag, result, error)
    } else {
      logError(tag, result)
    }

    result
  } else {
    val msg = "$prefix, class = ${error.javaClass.simpleName}, message = ${error.message}"

    if (shouldPrintFullStacktrace(error)) {
      logError(tag, msg, error)
    } else {
      logError(tag, msg)
    }

    msg
  }
}

private fun shouldPrintFullStacktrace(error: Throwable): Boolean {
  return when (error) {
    is FileCacheException -> false
    is SSLException -> false
    else -> true
  }
}