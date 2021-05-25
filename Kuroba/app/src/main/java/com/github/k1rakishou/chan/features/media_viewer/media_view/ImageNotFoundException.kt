package com.github.k1rakishou.chan.features.media_viewer.media_view

import okhttp3.HttpUrl

class ImageNotFoundException(url: HttpUrl) : Exception("Image \'$url\' not found")