package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.common.LoadBoardInfo
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.chan.core.site.http.login.Chan4LoginRequest
import com.github.k1rakishou.chan.core.site.http.login.Chan4LoginResponse
import com.github.k1rakishou.chan.core.site.http.report.PostReportData
import com.github.k1rakishou.chan.core.site.http.report.PostReportResult
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.core.site.sites.archive.NativeArchivePostList
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4.CaptchaType
import com.github.k1rakishou.chan.core.site.sites.search.Chan4SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import com.github.k1rakishou.persist_state.ReplyMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import okhttp3.Request

class Chan4Actions(
  private val chan4: Chan4
) : SiteActions {

  override suspend fun boards(): Flow<SiteBoards> {
    val request = Request.Builder()
      .url(chan4.endpoints.boards().toString())
      .get()
      .build()

    val siteBoards = Chan4BoardsRequest(
      siteDescriptor = chan4.descriptor,
      boardManager = chan4.boardManager,
      request = request,
      proxiedOkHttpClient = chan4.proxiedOkHttpClient
    )
      .execute()
      .mapErrorToValue { error -> SiteBoards.Result.Error(error) }

    return flowOf(siteBoards)
  }

  override suspend fun pages(board: ChanBoard): JsonReaderRequest.JsonReaderResponse<BoardPages> {
    val pagesUrl = chan4.endpoints.pages(board)
    if (pagesUrl == null) {
      return JsonReaderRequest.JsonReaderResponse.UnknownServerError(
        ClientException("Pages request not supported by ${board.boardDescriptor}")
      )
    }

    val request = Request.Builder()
      .url(pagesUrl)
      .get()
      .build()

    return Chan4PagesRequest(
      boardDescriptor = board.boardDescriptor,
      boardTotalPagesCount = board.pages,
      request = request,
      proxiedOkHttpClient = chan4.proxiedOkHttpClient
    ).execute()
  }

  override suspend fun post(replyChanDescriptor: ChanDescriptor, replyMode: ReplyMode): Flow<SiteActions.PostResult> {
    val replyCall = Chan4ReplyCall(
      site = chan4,
      replyChanDescriptor = replyChanDescriptor,
      replyMode = replyMode
    )

    return chan4.httpCallManager.makePostHttpCallWithProgress(replyCall, replyChanDescriptor)
      .map { replyCallResult ->
        when (replyCallResult) {
          is HttpCall.HttpCallWithProgressResult.Success -> {
            return@map SiteActions.PostResult.PostComplete(
              replyCallResult.httpCall.replyResponse
            )
          }
          is HttpCall.HttpCallWithProgressResult.Progress -> {
            return@map SiteActions.PostResult.UploadingProgress(
              replyCallResult.fileIndex,
              replyCallResult.totalFiles,
              replyCallResult.percent
            )
          }
          is HttpCall.HttpCallWithProgressResult.Fail -> {
            return@map SiteActions.PostResult.PostError(
              replyCallResult.error
            )
          }
        }
      }
  }

  override suspend fun delete(deleteRequest: DeleteRequest): SiteActions.DeleteResult {
    val deleteResult = chan4.httpCallManager.makeHttpCall(
      Chan4DeleteHttpCall(chan4, deleteRequest)
    )

    return when (deleteResult) {
      is HttpCall.HttpCallResult.Success -> {
        SiteActions.DeleteResult.DeleteComplete(
          deleteResult.httpCall.deleteResponse
        )
      }
      is HttpCall.HttpCallResult.Fail -> {
        SiteActions.DeleteResult.DeleteError(
          deleteResult.error
        )
      }
    }
  }

  override suspend fun <T : AbstractLoginRequest> login(loginRequest: T): SiteActions.LoginResult {
    val chan4LoginRequest = loginRequest as Chan4LoginRequest

    chan4.passUser.set(chan4LoginRequest.user)
    chan4.passPass.set(chan4LoginRequest.pass)

    val loginResult = chan4.httpCallManager.makeHttpCall(
      Chan4PassHttpCall(chan4, chan4LoginRequest)
    )

    when (loginResult) {
      is HttpCall.HttpCallResult.Success -> {
        val loginResponse = requireNotNull(loginResult.httpCall.loginResponse) { "loginResponse is null" }

        return when (loginResponse) {
          is Chan4LoginResponse.Success -> {
            chan4.passToken.set(loginResponse.authCookie)
            SiteActions.LoginResult.LoginComplete(loginResponse)
          }
          is Chan4LoginResponse.Failure -> {
            SiteActions.LoginResult.LoginError(loginResponse.errorMessage)
          }
        }
      }
      is HttpCall.HttpCallResult.Fail -> {
        return SiteActions.LoginResult.LoginError(loginResult.error.errorMessageOrClassName())
      }
    }
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  override fun postAuthenticate(): SiteAuthentication {
    return when (chan4.captchaType.get()) {
      CaptchaType.V2JS -> SiteAuthentication.fromCaptcha2(CAPTCHA_KEY, "https://boards.4chan.org")
      CaptchaType.V2NOJS -> SiteAuthentication.fromCaptcha2nojs(CAPTCHA_KEY, "https://boards.4chan.org")
      CaptchaType.CHAN4_CAPTCHA -> SiteAuthentication.endpointBased()
    }
  }

  override fun logout() {
    chan4.passToken.remove()
    chan4.passUser.remove()
    chan4.passPass.remove()
  }

  override fun isLoggedIn(): Boolean {
    return chan4.passToken.get().isNotEmpty()
  }

  override fun loginDetails(): Chan4LoginRequest {
    return Chan4LoginRequest(
      chan4.passUser.get(),
      chan4.passPass.get()
    )
  }

  override suspend fun <T : SearchParams> search(searchParams: T): SearchResult {
    searchParams as Chan4SearchParams
    val page = searchParams.getCurrentPage()
    val boardCode = searchParams.boardCode

    // https://find.4chan.org/?q=test&b=g&o=0
    val searchUrl = requireNotNull(chan4.endpoints.search())
      .newBuilder()
      .addQueryParameter("q", searchParams.query)
      .addBoardCodeParameter(boardCode)
      .addQueryParameter("o", page.toString())
      .build()

    val requestBuilder = Request.Builder()
      .url(searchUrl)
      .get()

    chan4.requestModifier.modifyGenericRequest(chan4, requestBuilder)

    return Chan4SearchRequest(
      request = requestBuilder.build(),
      proxiedOkHttpClient = chan4.proxiedOkHttpClient,
      searchParams = searchParams
    ).execute()
  }

  override suspend fun archive(boardDescriptor: BoardDescriptor, page: Int?): ModularResult<NativeArchivePostList> {
    val archiveUrl = requireNotNull(chan4.endpoints.boardArchive(boardDescriptor, page))

    val requestBuilder = Request.Builder()
      .url(archiveUrl)
      .get()

    chan4.requestModifier.modifyGenericRequest(chan4, requestBuilder)

    return Chan4ArchiveThreadsRequest(
      request = requestBuilder.build(),
      proxiedOkHttpClient = chan4.proxiedOkHttpClient
    ).execute()
  }

  override suspend fun <T : PostReportData> reportPost(
    postReportData: T
  ): PostReportResult {
    postReportData as PostReportData.Chan4

    return Chan4ReportPostRequest(
      siteManager = chan4.siteManager,
      proxiedOkHttpClient = chan4.proxiedOkHttpClient,
      postReportData = postReportData
    ).execute()
  }

  override suspend fun checkPostExists(
    chanDescriptor: ChanDescriptor,
    replyPostDescriptor: PostDescriptor
  ): ModularResult<Boolean> {
    return Chan4CheckPostExistsRequest(
      chan4 = chan4,
      chanDescriptor = chanDescriptor,
      replyPostDescriptor = replyPostDescriptor,
      proxiedOkHttpClient = chan4.proxiedOkHttpClient,
      chanThreadManager = chan4.chanThreadManager,
      replyManager = chan4.replyManager
    ).execute()
  }

  override fun clearPostingCookies() {
    chan4.chan4CaptchaCookie.setSync("")
    chan4.cloudFlareClearanceCookieMap.clear(sync = true)
    chan4.chan4CaptchaSettings.update(sync = true) { chan4CaptchaSetting ->
      chan4CaptchaSetting.copy(captchaTicket = null)
    }
  }

  override suspend fun loadBoardInfo(): Flow<SiteBoards> {
    return LoadBoardInfo(
      site = chan4,
      boardManager = chan4.boardManager
    ).execute()
  }

  private fun HttpUrl.Builder.addBoardCodeParameter(boardCode: String?): HttpUrl.Builder {
    if (boardCode.isNullOrEmpty()) {
      return this
    }

    return addQueryParameter("b", boardCode)
  }

  companion object {
    private const val CAPTCHA_KEY = "6Ldp2bsSAAAAAAJ5uyx_lx34lJeEpTLVkP5k04qc"
  }
}
