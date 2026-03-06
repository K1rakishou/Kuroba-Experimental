package com.github.k1rakishou.chan.core.site.common

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody.ProgressRequestListener
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

abstract class MultipartHttpCall(site: Site) : HttpCall(site) {
  private val formBuilder = MultipartBody.Builder()
  private var url: HttpUrl? = null

  init {
    formBuilder.setType(MultipartBody.Companion.FORM)
  }

  fun url(url: HttpUrl): MultipartHttpCall {
    this.url = url
    return this
  }

  fun parameter(name: String, value: String): MultipartHttpCall {
    formBuilder.addFormDataPart(name, value)
    return this
  }

  fun fileParameter(name: String, filename: String, file: File): MultipartHttpCall {
    formBuilder.addFormDataPart(
      name = name,
      filename = filename,
      body = file.asRequestBody("application/octet-stream".toMediaType())
    )
    return this
  }

  override fun setup(
    requestBuilder: Request.Builder,
    progressListener: ProgressRequestListener?
  ) {
    val localUrl = requireNotNull(url)
    requestBuilder.url(localUrl)

    var referer = localUrl.scheme + "://" + localUrl.host
    if (localUrl.port != 80 && localUrl.port != 443) {
      referer += ":" + localUrl.port
    }

    requestBuilder.addHeader("Referer", referer)
    requestBuilder.post(formBuilder.build())
    site.requestModifier.modifyHttpCall(this, requestBuilder)
  }
}
