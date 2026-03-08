package com.github.k1rakishou.chan.features.download.media

interface ResolveDuplicateImagesView {
  fun showToastMessage(message: String)
  fun onDuplicateResolvingCompleted()
}