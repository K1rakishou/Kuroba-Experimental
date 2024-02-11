package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.cache.CacheFileType
import okhttp3.HttpUrl
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal open class FileDownloadRequest(
  val mediaUrl: HttpUrl,
  val total: AtomicLong = AtomicLong(0),
  // A handle to cancel the current download
  val cancelableDownload: CancelableDownload,
  val extraInfo: DownloadRequestExtraInfo,
  // Chunks to delete from the disk upon download success or error
  val chunks: MutableSet<Chunk> = mutableSetOf(),
  val cacheFileType: CacheFileType
) {
  private var output: File? = null
  private var chunksCount = AtomicInteger(-1)
  private var totalDownloaded: LongArray = LongArray(0)

  @Synchronized
  fun chunksCount(count: Int) {
    chunksCount.set(count)
    totalDownloaded = LongArray(0) { 0 }
  }

  @Synchronized
  fun updateDownloaded(chunkIndex: Int, downloaded: Long) {
    if (totalDownloaded.getOrNull(chunkIndex) == null) {
      return
    }

    totalDownloaded[chunkIndex] += downloaded
  }

  @Synchronized
  fun calculateDownloaded(): Long {
    return totalDownloaded.sumOf { it }
  }

  @Synchronized
  fun setOutputFile(outputFile: File) {
    if (output != null) {
      throw IllegalStateException("Output file is already set!")
    }

    this.output = outputFile
  }

  @Synchronized
  fun getOutputFile(): File? {
    return output
  }

  override fun toString(): String {
    val outputFileName = synchronized(this) {
      if (output == null) {
        "<null>"
      } else {
        output!!.name
      }
    }

    return "[FileDownloadRequest: url: '$mediaUrl', outputFileName: '$outputFileName']"
  }

}

class DownloadRequestExtraInfo(
  val fileSize: Long = -1L,
  val fileHash: String? = null,
  val isGalleryBatchDownload: Boolean = false,
  val isPrefetchDownload: Boolean = false,
)