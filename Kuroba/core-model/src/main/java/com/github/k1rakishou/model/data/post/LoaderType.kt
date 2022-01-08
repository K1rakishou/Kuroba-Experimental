package com.github.k1rakishou.model.data.post

enum class LoaderType(val arrayIndex: Int) {
  PrefetchLoader(0),
  PostExtraContentLoader(1),
  Chan4CloudFlareImagePreLoader(2),
  PostHighlightFilterLoader(3),
  ThirdEyeLoader(4);

  companion object {
    val COUNT = values().size
  }
}