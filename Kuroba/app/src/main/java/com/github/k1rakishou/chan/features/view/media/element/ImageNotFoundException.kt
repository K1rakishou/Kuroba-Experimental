package com.github.k1rakishou.chan.features.view.media.element

import okhttp3.HttpUrl

class ImageNotFoundException(url: HttpUrl) : Exception("Image \'$url\' not found")