package com.github.k1rakishou.chan.features.image_saver

interface ResolveDuplicateImagesView {
  fun showToastMessage(message: String)
  fun onDuplicateResolvingCompleted()
}