package com.github.k1rakishou.chan.core.site.http

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import java.io.IOException
import java.util.concurrent.CancellationException

open class ProgressRequestBody : RequestBody {
  private val fileIndex: Int
  private val totalFiles: Int

  private var delegate: RequestBody
  private var listener: ProgressRequestListener
  private var progressSink: ProgressSink? = null

  constructor(
    delegate: RequestBody,
    listener: ProgressRequestListener
  ) {
    this.fileIndex = 1
    this.totalFiles = 1
    this.delegate = delegate
    this.listener = listener
  }

  constructor(
    fileIndex: Int,
    totalFiles: Int,
    delegate: RequestBody,
    listener: ProgressRequestListener
  ) {
    this.fileIndex = fileIndex
    this.totalFiles = totalFiles
    this.delegate = delegate
    this.listener = listener
  }

  override fun contentType(): MediaType? {
    return delegate.contentType()
  }

  @Throws(IOException::class)
  override fun contentLength(): Long {
    return delegate.contentLength()
  }

  @Throws(IOException::class)
  override fun writeTo(sink: BufferedSink) {
    val localProgressSink = ProgressSink(sink)
    progressSink = localProgressSink

    val bufferedSink = localProgressSink.buffer()
    delegate.writeTo(bufferedSink)
    bufferedSink.flush()
  }

  protected inner class ProgressSink(delegate: Sink) : ForwardingSink(delegate) {
    private var bytesWritten: Long = 0
    private var lastPercent = 0

    override fun write(source: Buffer, byteCount: Long) {
      super.write(source, byteCount)

      if (bytesWritten == 0L) {
        try {
          // so we can know that the uploading has just started
          listener.onRequestProgress(fileIndex, totalFiles, 0)
        } catch (cancellationException: CancellationException) {
          throw IOException("Canceled")
        }
      }

      bytesWritten += byteCount

      if (contentLength() > 0) {
        val percent = (maxPercent * bytesWritten / contentLength()).toInt()
        if (percent - lastPercent >= percentStep) {
          lastPercent = percent

          // OkHttp will explode if the listener throws anything other than IOException
          // so we need to wrap those exceptions into IOException. For now only
          // CancellationException was found to be thrown somewhere deep inside the listener.
          try {
            listener.onRequestProgress(fileIndex, totalFiles, percent)
          } catch (cancellationException: CancellationException) {
            throw IOException("Canceled")
          }
        }
      }
    }
  }

  interface ProgressRequestListener {
    fun onRequestProgress(fileIndex: Int, totalFiles: Int, percent: Int)
  }

  companion object {
    private const val maxPercent = 100
    private const val percentStep = 1
  }
}
