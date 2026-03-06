package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.DeleteResponse
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody.ProgressRequestListener
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.regex.Matcher
import java.util.regex.Pattern

class Chan4DeleteHttpCall(
  site: Site,
  private val deleteRequest: DeleteRequest
) : HttpCall(site) {
  val deleteResponse: DeleteResponse = DeleteResponse()

  override fun setup(
    requestBuilder: Request.Builder,
    progressListener: ProgressRequestListener?
  ) {
    val formBuilder = FormBody.Builder()
    formBuilder.add(deleteRequest.post.postNo().toString(), "delete")

    if (deleteRequest.imageOnly) {
      formBuilder.add("onlyimgdel", "on")
    }

    formBuilder.add("mode", "usrdel")
    formBuilder.add("pwd", deleteRequest.savedReply.passwordOrEmptyString())

    requestBuilder.url(requireNotNull(site.endpoints.delete(deleteRequest.post)))
    requestBuilder.post(formBuilder.build())
    site.requestModifier.modifyHttpCall(this, requestBuilder)
  }

  override fun process(response: Response, result: String) {
    val errorMessageMatcher: Matcher = ERROR_MESSAGE.matcher(result)

    if (errorMessageMatcher.find()) {
      deleteResponse.errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().ownText()
    } else {
      deleteResponse.deleted = true
    }
  }

  companion object {
    private val ERROR_MESSAGE: Pattern = Pattern.compile("\"errmsg\"[^>]*>(.*?)</span")
  }
}
