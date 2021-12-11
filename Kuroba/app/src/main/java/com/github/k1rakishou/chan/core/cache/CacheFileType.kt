package com.github.k1rakishou.chan.core.cache

enum class CacheFileType(
  val id: Int,
  val diskSizePercent: Float
) {
  ThreadDownloaderThumbnail(0, 0.05f),
  BookmarkThumbnail(1, 0.05f),
  NavHistoryThumbnail(2, 0.05f),
  SiteIcon(3, 0.05f),
  PostMediaThumbnail(4, 0.05f),
  PostMediaFull(5, 0.65f),
  Other(6, 0.1f);

  fun calculateDiskSize(totalDiskCacheSize: Long): Long {
    return (totalDiskCacheSize.toFloat() * diskSizePercent).toLong()
  }

  companion object {

    fun checkValid() {
      val totalPercent = values().map { it.diskSizePercent }.sum()
      val diff = Math.abs(1f - totalPercent)

      check(diff < 0.001f) {
        "Bad totalPercent! diff=${diff}, totalPercent=${totalPercent}. Must be close to 0.0!"
      }

      check(values().size == values().distinctBy { it.id }.size) {
        "All ids must be unique!"
      }
    }

  }
}