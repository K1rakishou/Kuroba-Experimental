package com.github.k1rakishou.common.network

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong

fun ResponseBody.asProgressResponseBody(): ProgressResponseBody {
  if (this is ProgressResponseBody) {
    error("It's better to not wrap one ProgressResponseBody into another one")
  }

  return ProgressResponseBody(delegate = this)
}

class ProgressResponseBody(
  private val delegate: ResponseBody
) : ResponseBody() {
  private val _progressFlow = MutableSharedFlow<ProgressEvent>(
    extraBufferCapacity = 32,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val progressFlow: SharedFlow<ProgressEvent>
    get() = _progressFlow.asSharedFlow()

  override fun contentLength(): Long = delegate.contentLength()
  override fun contentType(): MediaType? = delegate.contentType()

  override fun source(): BufferedSource {
    return ProgressSink(delegate.source()).buffer()
  }

  private inner class ProgressSink(delegate: BufferedSource) : ForwardingSource(delegate) {
    private val bytesRead = AtomicLong(0L)
    private val previousPercent = AtomicInt(-1)

    override fun read(sink: Buffer, byteCount: Long): Long {
      val read = super.read(sink, byteCount)

      val contentLength = contentLength()
      if (contentLength > 0) {
        val progressEvent = ProgressEvent(
          readBytes = bytesRead.addAndFetch(read),
          totalBytes = contentLength
        )

        if (progressEvent.percent - previousPercent.load() >= 1) {
          _progressFlow.tryEmit(progressEvent)
          previousPercent.store(progressEvent.percent)
        }
      }

      return read
    }
  }

  data class ProgressEvent(
    val readBytes: Long,
    val totalBytes: Long
  ) {
    val progress: Float
      get() = readBytes.toFloat() / totalBytes.toFloat()

    val percent: Int
      get() = (progress * 100f).toInt()
  }
}