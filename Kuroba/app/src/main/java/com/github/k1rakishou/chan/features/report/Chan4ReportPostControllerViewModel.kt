package com.github.k1rakishou.chan.features.report

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.suspendConvertIntoJsoupDocument
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import okhttp3.MultipartBody
import okhttp3.Request
import org.jsoup.nodes.Node
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import javax.inject.Inject

class Chan4ReportPostControllerViewModel : BaseViewModel() {

  @Inject
  lateinit var okHttpClient: ProxiedOkHttpClient
  @Inject
  lateinit var siteManager: SiteManager

  private val cache = ConcurrentHashMap<BoardDescriptor, List<ReportCategory>>()

  private var _selectedCategoryId = mutableStateOf<Int?>(null)
  val selectedCategoryId: State<Int?>
    get() = _selectedCategoryId

  private var _reporting = mutableStateOf<Boolean>(false)
  val reporting: State<Boolean>
    get() = _reporting

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
  }

  fun updateSelectedCategoryId(categoryId: Int) {
    _selectedCategoryId.value = categoryId
  }

  fun resetSelectedCategoryId() {
    _selectedCategoryId.value = null
  }

  internal suspend fun loadReportCategories(
    postDescriptor: PostDescriptor
  ): ModularResult<List<ReportCategory>> {
    return ModularResult.Try {
      val fromCache = cache.get(postDescriptor.boardDescriptor())
      if (fromCache != null && fromCache.isNotEmpty()) {
        Logger.d(TAG, "loadReportCategories($postDescriptor) using cached categories: ${fromCache.size}")

        return@Try fromCache
      }

      val reportCategoriesEndpoint = String.format(
        Locale.ENGLISH,
        REPORT_POST_ENDPOINT_FORMAT,
        postDescriptor.boardDescriptor().boardCode,
        postDescriptor.postNo
      )

      Logger.d(TAG, "loadReportCategories($postDescriptor) reportCategoriesEndpoint=${reportCategoriesEndpoint}")

      val request = Request.Builder()
        .url(reportCategoriesEndpoint)
        .get()
        .build()

      val document = okHttpClient.okHttpClient().suspendConvertIntoJsoupDocument(request)
        .unwrap()

      val categorySelector = document.getElementById("cat-sel")
      if (categorySelector == null || categorySelector.children().isEmpty()) {
        val bodyElement = document.getElementsByTag("body").firstOrNull()
          ?: throw CommonClientException("<body> not found in response")

        val errorText = bodyElement.getElementsByTag("font").firstOrNull()?.wholeText()
          ?: throw CommonClientException("<font> not found inside of <body> tag")

        throw CommonClientException(errorText)
      }

      val reportCategories = categorySelector.children().mapNotNull { element ->
        if (element !is Node) {
          return@mapNotNull null
        }

        val html = element.outerHtml()
        Logger.d(TAG, "loadReportCategories($postDescriptor) processing option element: \'$html\'")

        val reportCategoryMatcher = REPORT_CATEGORY_PATTERN.matcher(html)
        if (!reportCategoryMatcher.find()) {
          return@mapNotNull null
        }

        val categoryId = reportCategoryMatcher.groupOrNull(1)?.toIntOrNull()
          ?: return@mapNotNull null
        val description = reportCategoryMatcher.groupOrNull(2)
          ?: return@mapNotNull null

        return@mapNotNull ReportCategory(
          id = categoryId,
          description = description
        )
      }

      if (reportCategories.isNotEmpty()) {
        cache.put(postDescriptor.boardDescriptor(), reportCategories)
      }

      Logger.d(TAG, "loadReportCategories($postDescriptor) loaded ${reportCategories.size} categories")
      return@Try reportCategories
    }
  }

  suspend fun reportPost(
    postDescriptor: PostDescriptor,
    captchaSolution: CaptchaSolution.ChallengeWithSolution,
    selectedCategoryId: Int
  ): ModularResult<ReportPostResult> {
    _reporting.value = true

    return ModularResult.Try {
      val reportPostEndpoint = String.format(
        Locale.ENGLISH,
        REPORT_POST_ENDPOINT_FORMAT,
        postDescriptor.boardDescriptor().boardCode,
        postDescriptor.postNo
      )

      Logger.d(TAG, "reportPost($postDescriptor, $selectedCategoryId) reportPostEndpoint=$reportPostEndpoint")

      val body = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("cat_id", selectedCategoryId.toString())
        .addFormDataPart("t-challenge", captchaSolution.challenge)
        .addFormDataPart("t-response", captchaSolution.solution)
        .addFormDataPart("board", postDescriptor.boardDescriptor().boardCode)
        .addFormDataPart("no", postDescriptor.postNo.toString())
        .build()

      val requestBuilder = Request.Builder()
        .url(reportPostEndpoint)
        .post(body)

      siteManager.bySiteDescriptor(postDescriptor.siteDescriptor())?.let { site ->
        site.requestModifier().modifyPostReportRequest(site, requestBuilder)
      }

      val document = okHttpClient.okHttpClient().suspendConvertIntoJsoupDocument(requestBuilder.build())
        .unwrap()

      val bodyElement = document.getElementsByTag("body").firstOrNull()
        ?: throw CommonClientException("<body> not found in response")

      val errorText = bodyElement.getElementsByTag("font").firstOrNull()?.wholeText()
        ?: throw CommonClientException("<font> not found inside of <body> tag")

      Logger.d(TAG, "reportPost($postDescriptor, $selectedCategoryId) errorText='$errorText'")

      if (errorText.contains(SUCCESS_TEXT, ignoreCase = true)) {
        return@Try ReportPostResult.Success
      }

      if (errorText.contains(CAPTCHA_REQUIRED_ERROR_TEXT, ignoreCase = true)) {
        return@Try ReportPostResult.CaptchaRequired
      }

      return@Try ReportPostResult.Error(errorMessage = errorText)
    }.finally { _reporting.value = false }
  }

  sealed class ReportPostResult {
    object Success : ReportPostResult()
    object CaptchaRequired : ReportPostResult()
    data class Error(val errorMessage: String) : ReportPostResult()
  }

  internal data class ReportCategory(
    val id: Int,
    val description: String
  )

  companion object {
    private const val TAG = "Chan4ReportPostControllerViewModel"
    private const val REPORT_POST_ENDPOINT_FORMAT = "https://sys.4chan.org/%s/imgboard.php?mode=report&no=%d"

    private val REPORT_CATEGORY_PATTERN = Pattern.compile("<option\\s+value=\\\"(\\d+)\\\">(.*?)<\\/option>")

    private const val SUCCESS_TEXT = "Report submitted"
    private const val CAPTCHA_REQUIRED_ERROR_TEXT = "You seem to have mistyped the CAPTCHA"
  }
}