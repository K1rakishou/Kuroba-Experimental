package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.common.MultipartHttpCall
import com.github.k1rakishou.chan.core.site.common.vichan.VichanActions
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.chan.core.site.http.login.DvachLoginRequest
import com.github.k1rakishou.chan.core.site.http.login.DvachLoginResponse
import com.github.k1rakishou.chan.core.site.http.report.PostReportData
import com.github.k1rakishou.chan.core.site.http.report.PostReportResult
import com.github.k1rakishou.chan.core.site.limitations.PasscodePostingLimitationsInfo
import com.github.k1rakishou.chan.core.site.sites.archive.NativeArchivePostList
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach.CaptchaType
import com.github.k1rakishou.chan.core.site.sites.search.DvachSearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import com.github.k1rakishou.persist_state.ReplyMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import okhttp3.MultipartBody
import okhttp3.Request

class DvachActions(
  private val dvach: Dvach
) : VichanActions(dvach) {
  override fun setupPost(
    replyChanDescriptor: ChanDescriptor,
    call: MultipartHttpCall
  ): ModularResult<Unit> {
    return super.setupPost(replyChanDescriptor, call)
      .mapValue {
        if (replyChanDescriptor.isThreadDescriptor()) {
          // "thread" is already added in VichanActions.
          call.parameter("post", "New Reply")
        } else {
          call.parameter("post", "New Thread")
          call.parameter("page", "1")
        }

        return@mapValue
      }
  }

  override fun requirePrepare(): Boolean {
    return false
  }

  override suspend fun post(
    replyChanDescriptor: ChanDescriptor,
    replyMode: ReplyMode
  ): Flow<SiteActions.PostResult> {
    val replyCall = DvachReplyCall(
      site = dvach,
      replyChanDescriptor = replyChanDescriptor,
      replyMode = replyMode
    )

    return dvach.httpCallManager.makePostHttpCallWithProgress(replyCall, replyChanDescriptor)
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
            return@map SiteActions.PostResult.PostError(replyCallResult.error)
          }
        }
      }
  }

  override suspend fun delete(deleteRequest: DeleteRequest): SiteActions.DeleteResult {
    return super.delete(deleteRequest)
  }

  override suspend fun boards(): Flow<SiteBoards> {
    val dvachEndpoints = dvach.endpoints as DvachEndpoints

    val siteBoards = DvachBoardsRequest(
      dvach = dvach,
      siteDescriptor = dvach.descriptor,
      boardManager = dvach.boardManager,
      proxiedOkHttpClient = dvach.proxiedOkHttpClient,
      boardsRequestUrl = dvachEndpoints.boards(),
    ).execute()

    return flowOf(siteBoards)
  }

  override suspend fun <T : AbstractLoginRequest> login(loginRequest: T): SiteActions.LoginResult {
    val dvachLoginRequest = loginRequest as DvachLoginRequest
    dvach.passCode.set(dvachLoginRequest.passcode)

    val loginResult = dvach.httpCallManager.makeHttpCall(
      DvachGetPassCookieHttpCall(
        site = dvach,
        moshi = dvach.moshi,
        dvachLoginRequest = loginRequest
      )
    )

    when (loginResult) {
      is HttpCall.HttpCallResult.Success -> {
        val loginResponse = requireNotNull(loginResult.httpCall.loginResponse) { "loginResponse is null" }

        return when (loginResponse) {
          is DvachLoginResponse.Success -> {
            dvach.passCookie.set(loginResponse.authCookie)
            SiteActions.LoginResult.LoginComplete(loginResponse)
          }
          is DvachLoginResponse.Failure -> {
            SiteActions.LoginResult.LoginError(loginResponse.errorMessage)
          }
        }
      }
      is HttpCall.HttpCallResult.Fail -> {
        return SiteActions.LoginResult.LoginError(loginResult.error.errorMessageOrClassName())
      }
    }
  }

  override suspend fun getOrRefreshPasscodeInfo(resetCached: Boolean): SiteActions.GetPasscodeInfoResult {
    if (!isLoggedIn()) {
      return SiteActions.GetPasscodeInfoResult.NotLoggedIn
    }

    if (resetCached) {
      dvach.passCodeInfo.reset()
    }

    if (dvach.passCodeInfo.isNotDefault()) {
      val dvachPasscodeInfo = dvach.passCodeInfo.get()

      val maxAttachedFilesPerPost = dvachPasscodeInfo.files
      val maxTotalAttachablesSize = dvachPasscodeInfo.filesSize

      if (maxAttachedFilesPerPost != null && maxTotalAttachablesSize != null) {
        val passcodePostingLimitationsInfo = PasscodePostingLimitationsInfo(
          maxAttachedFilesPerPost,
          maxTotalAttachablesSize
        )

        return SiteActions.GetPasscodeInfoResult.Success(passcodePostingLimitationsInfo)
      }

      // fallthrough
    }

    if (!resetCached) {
      return SiteActions.GetPasscodeInfoResult.NotAllowedToRefreshFromNetwork
    }

    val passcodeInfoCall = DvachGetPasscodeInfoHttpCall(dvach)

    val passcodeInfoCallResult = dvach.httpCallManager.makeHttpCall(passcodeInfoCall)
    if (passcodeInfoCallResult is HttpCall.HttpCallResult.Fail) {
      return SiteActions.GetPasscodeInfoResult.Failure(passcodeInfoCallResult.error)
    }

    val passcodePostingLimitationsInfoResult = (passcodeInfoCallResult as HttpCall.HttpCallResult.Success)
      .httpCall.passcodePostingLimitationsInfoResult

    if (passcodePostingLimitationsInfoResult is ModularResult.Error) {
      return SiteActions.GetPasscodeInfoResult.Failure(passcodePostingLimitationsInfoResult.error)
    }

    val passcodePostingLimitationsInfo =
      (passcodePostingLimitationsInfoResult as ModularResult.Value).value

    val dvachPasscodeInfo = DvachPasscodeInfo(
      files = passcodePostingLimitationsInfo.maxAttachedFilesPerPost,
      filesSize = passcodePostingLimitationsInfo.maxTotalAttachablesSize
    )

    dvach.passCodeInfo.set(dvachPasscodeInfo)

    return SiteActions.GetPasscodeInfoResult.Success(passcodePostingLimitationsInfo)
  }

  override fun postAuthenticate(): SiteAuthentication {
    return when (dvach.captchaType.get()) {
      CaptchaType.V2JS -> dvach.captchaV2Js
      CaptchaType.V2NOJS -> dvach.captchaV2NoJs
      CaptchaType.V2_INVISIBLE -> dvach.captchaV2Invisible
      CaptchaType.DVACH_CAPTCHA -> dvach.dvachCaptcha
      CaptchaType.DVACH_CAPTCHA_PUZZLE -> dvach.dvachCaptchaPuzzle
      CaptchaType.DVACH_CAPTCHA_EMOJI -> dvach.dvachEmojiCaptcha
      else -> throw IllegalArgumentException()
    }
  }

  override fun logout() {
    dvach.passCode.remove()
    dvach.passCookie.remove()
    dvach.passCodeInfo.reset()
  }

  override fun isLoggedIn(): Boolean {
    return dvach.passCookie.get().isNotEmpty()
  }

  override fun loginDetails(): DvachLoginRequest {
    return DvachLoginRequest(dvach.passCode.get())
  }

  override suspend fun pages(
    board: ChanBoard
  ): JsonReaderRequest.JsonReaderResponse<BoardPages> {
    val requestBuilder = Request.Builder()
      .url(requireNotNull(dvach.endpoints.pages(board)))
      .get()

    dvach.requestModifier.modifyGenericRequest(dvach, requestBuilder)

    return DvachPagesRequest(
      chanBoard = board,
      request = requestBuilder.build(),
      proxiedOkHttpClient = dvach.proxiedOkHttpClient
    ).execute()
  }

  override suspend fun <T : SearchParams> search(searchParams: T): SearchResult {
    val dvachSearchParams = searchParams as DvachSearchParams
    val searchUrl = requireNotNull(dvach.endpoints.search())

    val formBuilder = MultipartBody.Builder().apply {
      setType(MultipartBody.FORM)
      addFormDataPart("board", dvachSearchParams.boardCode)
      addFormDataPart("text", dvachSearchParams.query)
    }

    val requestBuilder = Request.Builder()
      .url(searchUrl)
      .post(formBuilder.build())

    dvach.requestModifier.modifyGenericRequest(dvach, requestBuilder)

    return DvachSearchRequest(
      moshi = dvach.moshi,
      request = requestBuilder.build(),
      proxiedOkHttpClient = dvach.proxiedOkHttpClient,
      searchParams = dvachSearchParams,
      siteManager = dvach.siteManager
    ).execute()
  }

  override suspend fun archive(boardDescriptor: BoardDescriptor, page: Int?): ModularResult<NativeArchivePostList> {
    val archiveUrl = requireNotNull(dvach.endpoints.boardArchive(boardDescriptor, page))

    val requestBuilder = Request.Builder()
      .url(archiveUrl)
      .get()

    dvach.requestModifier.modifyGenericRequest(dvach, requestBuilder)

    return DvachArchiveThreadsRequest(
      request = requestBuilder.build(),
      proxiedOkHttpClient = dvach.proxiedOkHttpClient
    ).execute()
  }

  override suspend fun <T : PostReportData> reportPost(postReportData: T): PostReportResult {
    postReportData as PostReportData.Dvach

    return DvachReportPostRequest(
      site = dvach,
      moshi = dvach.moshi,
      proxiedOkHttpClient = dvach.proxiedOkHttpClient,
      postReportData = postReportData
    ).execute()
  }
}
