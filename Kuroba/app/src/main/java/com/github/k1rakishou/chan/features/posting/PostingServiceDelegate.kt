package com.github.k1rakishou.chan.features.posting

import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.repository.LastReplyRepository
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.features.posting.solver.TwoCaptchaSolver
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.helper.PostHelper
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.withLockNonCancellable
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanSavedReply
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class PostingServiceDelegate(
  private val appScope: CoroutineScope,
  private val appConstants: AppConstants,
  private val replyManager: ReplyManager,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val bookmarksManager: BookmarksManager,
  private val savedReplyManager: SavedReplyManager,
  private val chanThreadManager: ChanThreadManager,
  private val lastReplyRepository: LastReplyRepository,
  private val chanPostRepository: ChanPostRepository,
  private val twoCaptchaSolver: TwoCaptchaSolver,
  private val captchaHolder: CaptchaHolder
) {
  private val mutex = Mutex()
  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

  @GuardedBy("mutex")
  private val activeReplyDescriptors = hashMapOf<ChanDescriptor, ReplyInfo>()

  private val _stopServiceEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val _updateMainNotificationFlow = MutableSharedFlow<MainNotificationInfo>(extraBufferCapacity = 1)
  private val _updateChildNotificationFlow = MutableSharedFlow<ChildNotificationInfo>(extraBufferCapacity = 1)
  private val _closeChildNotificationFlow = MutableSharedFlow<ChanDescriptor>(extraBufferCapacity = 1)

  fun listenForStopServiceEvents(): SharedFlow<Unit> {
    return _stopServiceEventFlow
  }

  fun listenForMainNotificationUpdates(): SharedFlow<MainNotificationInfo> {
    return _updateMainNotificationFlow
  }

  fun listenForChildNotificationUpdates(): SharedFlow<ChildNotificationInfo> {
    return _updateChildNotificationFlow.asSharedFlow()
  }

  fun listenForChildNotificationsToClose(): SharedFlow<ChanDescriptor> {
    return _closeChildNotificationFlow.asSharedFlow()
  }

  suspend fun cancelAll() {
    Logger.d(TAG, "cancelAll()")

    mutex.withLockNonCancellable {
      activeReplyDescriptors.values.forEach { replyInfo ->
        replyInfo.cancelReplyUpload()
      }

      twoCaptchaSolver.cancelAll()
    }
  }

  suspend fun cancel(chanDescriptor: ChanDescriptor) {
    Logger.d(TAG, "cancel($chanDescriptor)")

    mutex.withLockNonCancellable {
      activeReplyDescriptors[chanDescriptor]?.cancelReplyUpload()
      twoCaptchaSolver.cancel(chanDescriptor)
    }
  }

  suspend fun listenForPostingStatusUpdates(chanDescriptor: ChanDescriptor): StateFlow<PostingStatus> {
    Logger.d(TAG, "listenForPostingStatusUpdates($chanDescriptor)")

    return mutex.withLock {
      if (activeReplyDescriptors.containsKey(chanDescriptor)) {
        return@withLock activeReplyDescriptors[chanDescriptor]!!.statusUpdates
      }

      activeReplyDescriptors.put(
        chanDescriptor,
        ReplyInfo(chanDescriptor = chanDescriptor, initialStatus = PostingStatus.Attached(chanDescriptor))
      )

      return@withLock activeReplyDescriptors[chanDescriptor]!!.statusUpdates
    }
  }

  fun onNewReply(chanDescriptor: ChanDescriptor, retrying: Boolean) {
    BackgroundUtils.ensureMainThread()

    serializedCoroutineExecutor.post {
      val shouldStartCoroutine = mutex.withLock { addOrUpdateReplyInfo(chanDescriptor, retrying) }
      if (!shouldStartCoroutine) {
        return@post
      }

      val result = awaitUntilEverythingIsInitialized(chanDescriptor)
      if (result is ModularResult.Error) {
        Logger.e(TAG, "awaitUntilEverythingIsInitialized($chanDescriptor) error", result.error)
        return@post
      }

      Logger.d(TAG, "onNewReply($chanDescriptor)")
      updateMainNotification()

      val job = supervisorScope {
        appScope.launch { onNewReplyInternal(chanDescriptor) }
      }

      doWithReplyInfo(chanDescriptor) {
        activeJob.compareAndSet(null, job)
      }
    }
  }

  private suspend fun CoroutineScope.onNewReplyInternal(chanDescriptor: ChanDescriptor) {
    val postedSuccessfully = AtomicBoolean(false)

    try {
      processNewReply(chanDescriptor, postedSuccessfully)
    } catch (error: Throwable) {
      if (error is CancellationException) {
        Logger.e(TAG, "Posting canceled $chanDescriptor")

        doWithReplyInfo(chanDescriptor) {
          updateStatus(PostingStatus.AfterPosting(chanDescriptor, PostResult.Canceled))
        }
      } else {
        Logger.e(TAG, "Unhandled exception", error)

        doWithReplyInfo(chanDescriptor) {
          updateStatus(PostingStatus.AfterPosting(chanDescriptor, PostResult.Error(error)))
        }
      }
    }

    if (!postedSuccessfully.get()) {
      replyManager.restoreFiles(chanDescriptor)
    }

    updateMainNotification()

    val canceled = doWithReplyInfo(chanDescriptor) { canceled } ?: true
    if (canceled || !isActive) {
      _closeChildNotificationFlow.emit(chanDescriptor)
    }

    val allRepliesProcessed = mutex.withLock {
      if (activeReplyDescriptors.isEmpty()) {
        return@withLock true
      }

      return@withLock activeReplyDescriptors.values.all { replyInfo ->
        return@all replyInfo.currentStatus is PostingStatus.Attached
          || replyInfo.currentStatus.isTerminalEvent()
      }
    }

    if (allRepliesProcessed) {
      Logger.d(TAG, "All replies processed, stopping the service")
      _stopServiceEventFlow.emit(Unit)
    }
  }

  private fun addOrUpdateReplyInfo(chanDescriptor: ChanDescriptor, retrying: Boolean): Boolean {
    if (activeReplyDescriptors.containsKey(chanDescriptor)) {
      val replyInfo = activeReplyDescriptors[chanDescriptor]!!

      val shouldStartCoroutine = when (replyInfo.currentStatus) {
        is PostingStatus.Attached,
        is PostingStatus.AfterPosting -> true
        is PostingStatus.Enqueued,
        is PostingStatus.Progress,
        is PostingStatus.BeforePosting,
        is PostingStatus.WaitingForAdditionalService -> false
      }

      if (replyInfo.currentStatus is PostingStatus.Attached
        || replyInfo.currentStatus is PostingStatus.AfterPosting
      ) {
        replyInfo.reset(chanDescriptor)
      }

      return shouldStartCoroutine || replyInfo.canceled
    }

    activeReplyDescriptors[chanDescriptor] = ReplyInfo(
      initialStatus = PostingStatus.Enqueued(chanDescriptor),
      chanDescriptor = chanDescriptor,
      retrying = AtomicBoolean(retrying)
    )

    return true
  }

  private suspend fun updateMainNotification() {
    val activeRepliesCount = mutex.withLock {
      return@withLock activeReplyDescriptors.values
        .count { replyInfo -> replyInfo.currentStatus.isActive() }
    }

    _updateMainNotificationFlow.emit(MainNotificationInfo(activeRepliesCount))
  }

  private suspend fun updateChildNotification(
    chanDescriptor: ChanDescriptor,
    status: ChildNotificationInfo.Status
  ) {
    _updateChildNotificationFlow.emit(ChildNotificationInfo(chanDescriptor, status))
  }

  private suspend fun processNewReply(
    chanDescriptor: ChanDescriptor,
    postedSuccessfully: AtomicBoolean
  ) {
    BackgroundUtils.ensureMainThread()

    updateChildNotification(chanDescriptor, ChildNotificationInfo.Status.Preparing(chanDescriptor))

    val reply = replyManager.getReplyOrNull(chanDescriptor)
    if (reply == null) {
      Logger.e(TAG, "No reply found for chanDescriptor: $chanDescriptor")
      emitTerminalEvent(
        chanDescriptor = chanDescriptor,
        postResult = PostResult.Error(IOException("Canceled"))
      )

      return
    }

    ensureNotCanceled(chanDescriptor)

    val retrying = doWithReplyInfo(chanDescriptor) { retrying.get() } ?: false
    makeSubmitCall(chanDescriptor, postedSuccessfully, retrying)
  }

  private suspend fun makeSubmitCall(
    chanDescriptor: ChanDescriptor,
    postedSuccessfully: AtomicBoolean,
    retrying: Boolean
  ) {
    Logger.d(TAG, "makeSubmitCall() chanDescriptor=${chanDescriptor}")

    val siteDescriptor = chanDescriptor.siteDescriptor()
    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())

    if (site == null) {
      Logger.e(TAG, "makeSubmitCall() siteManager.bySiteDescriptor($siteDescriptor) -> null")

      emitTerminalEvent(
        chanDescriptor = chanDescriptor,
        postResult = PostResult.Error(
          throwable = PostingException("Failed to resolve site by site descriptor ${siteDescriptor}")
        )
      )

      return
    }

    val takeFilesResult = replyManager.takeSelectedFiles(chanDescriptor)
      .safeUnwrap { error ->
        Logger.e(TAG, "makeSubmitCall() takeSelectedFiles(${chanDescriptor}) error")
        emitTerminalEvent(chanDescriptor, PostResult.Error(error))
        return
      }

    if (!takeFilesResult) {
      emitTerminalEvent(
        chanDescriptor = chanDescriptor,
        postResult = PostResult.Error(
          throwable = IOException("Failed to move attached files into reply")
        )
      )
      return
    }

    makePostInternal(
      site = site,
      chanDescriptor = chanDescriptor,
      retrying = retrying,
      postedSuccessfully = postedSuccessfully
    )
  }

  private suspend fun makePostInternal(
    site: Site,
    chanDescriptor: ChanDescriptor,
    retrying: Boolean,
    postedSuccessfully: AtomicBoolean
  ) {
    ensureNotCanceled(chanDescriptor)

    val serviceName = twoCaptchaSolver.name
    doWithReplyInfo(chanDescriptor) { updateStatus(PostingStatus.WaitingForAdditionalService(serviceName, chanDescriptor)) }
    updateChildNotification(chanDescriptor, ChildNotificationInfo.Status.WaitingForAdditionalService(serviceName))

    val availableAttempts = AtomicInteger(MAX_ATTEMPTS)

    while (true) {
      val result = processAntiCaptchaService(chanDescriptor)
      Logger.d(TAG, "processAntiCaptchaService($chanDescriptor) -> $result, attempt ${availableAttempts.get()}")

      when (result) {
        is AntiCaptchaServiceResult.ContinuePosting -> {
          val token = result.token
          if (token.isNotNullNorBlank()) {
            replyManager.readReply(chanDescriptor) { reply ->
              reply.initCaptchaInfo(challenge = null, response = token)
            }
          }

          break
        }
        AntiCaptchaServiceResult.Exit -> {
          return
        }
        is AntiCaptchaServiceResult.Wait -> {
          delay(result.waitTimeMs)
        }
      }

      if (availableAttempts.decrementAndGet() <= 0) {
        val errorMessage = "All post attempts were exhausted while waiting for AntiCaptcha service response"

        Logger.e(TAG, errorMessage)
        emitTerminalEvent(chanDescriptor, PostResult.Error(PostingException(errorMessage)))

        return
      }
    }

    ensureNotCanceled(chanDescriptor)
    doWithReplyInfo(chanDescriptor) { updateStatus(PostingStatus.BeforePosting(chanDescriptor)) }

    site.actions()
      .post(chanDescriptor)
      .catch { error ->
        Logger.e(TAG, "SiteActions.PostResult.PostError($chanDescriptor) " +
          "unhandled error: ${error.errorMessageOrClassName()}")

        emitTerminalEvent(chanDescriptor, PostResult.Error(error))
      }
      .collect { postResult ->
        when (postResult) {
          is SiteActions.PostResult.UploadingProgress -> {
            val status = PostingStatus.Progress(
              chanDescriptor = chanDescriptor,
              fileIndex = postResult.fileIndex,
              totalFiles = postResult.totalFiles,
              percent = postResult.percent
            )

            val uploadingStatus = ChildNotificationInfo.Status.Uploading(status.toOverallPercent())
            Logger.d(TAG, "uploadingStatus=${uploadingStatus.totalProgress}")

            updateChildNotification(chanDescriptor, uploadingStatus)
            doWithReplyInfo(chanDescriptor) { updateStatus(status) }
          }
          is SiteActions.PostResult.PostError -> {
            Logger.e(
              TAG, "SiteActions.PostResult.PostError($chanDescriptor) " +
              "error: ${postResult.error.errorMessageOrClassName()}")
            emitTerminalEvent(chanDescriptor, PostResult.Error(postResult.error))
          }
          is SiteActions.PostResult.PostComplete -> {
            if (!postResult.replyResponse.posted) {
              Logger.d(TAG, "SiteActions.PostResult.PostComplete($chanDescriptor) failed to post")

              emitTerminalEvent(
                chanDescriptor = chanDescriptor,
                postResult = PostResult.Success(
                  replyResponse = postResult.replyResponse,
                  retrying = retrying
                )
              )

              return@collect
            }

            try {
              onPostedSuccessfully(chanDescriptor, postResult.replyResponse)
              postedSuccessfully.set(true)

              emitTerminalEvent(
                chanDescriptor = chanDescriptor,
                postResult = PostResult.Success(
                  replyResponse = postResult.replyResponse,
                  retrying = retrying
                )
              )

              Logger.d(TAG, "SiteActions.PostResult.PostComplete($chanDescriptor) success")
            } catch (error: Throwable) {
              emitTerminalEvent(chanDescriptor, PostResult.Error(error))
              Logger.e(TAG, "SiteActions.PostResult.PostComplete($chanDescriptor) error", error)
            }
          }
        }
      }
  }

  private suspend fun processAntiCaptchaService(
    chanDescriptor: ChanDescriptor
  ): AntiCaptchaServiceResult {
    val tokenAlreadySet = replyManager.readReply(chanDescriptor) { reply -> reply.captchaResponse != null }
    if (tokenAlreadySet) {
      Logger.d(TAG, "processAntiCaptchaService($chanDescriptor) reply already contains a token, use it instead")
      return AntiCaptchaServiceResult.ContinuePosting(null)
    }

    if (captchaHolder.hasToken()) {
      Logger.d(TAG, "processAntiCaptchaService($chanDescriptor) already has token, use it instead")
      return AntiCaptchaServiceResult.ContinuePosting(captchaHolder.token)
    }

    val twoCaptchaResult = twoCaptchaSolver.solve(chanDescriptor)
      .safeUnwrap { error ->
        if (error is IOException) {
          Logger.e(TAG, "processAntiCaptchaService() twoCaptchaSolver.solve($chanDescriptor) " +
            "error=${error.errorMessageOrClassName()}")

          return AntiCaptchaServiceResult.Wait(TwoCaptchaSolver.LONG_WAIT_TIME_MS)
        }

        Logger.e(TAG, "processAntiCaptchaService() twoCaptchaSolver.solve($chanDescriptor) error", error)

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(error)
        )

        return AntiCaptchaServiceResult.Exit
      }

    when (twoCaptchaResult) {
      is TwoCaptchaSolver.TwoCaptchaResult.BadSiteKey -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        val exception = PostingException("Cannot use captcha solver, bad site key. " +
            "Solver='${twoCaptchaResult.solverName}'")

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(exception)
        )

        return AntiCaptchaServiceResult.Exit
      }
      is TwoCaptchaSolver.TwoCaptchaResult.BadSiteBaseUrl -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        val exception = PostingException("Cannot use captcha solver, bad site key. " +
          "Solver='${twoCaptchaResult.solverName}'. url=${twoCaptchaResult.url}")

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(exception)
        )

        return AntiCaptchaServiceResult.Exit
      }
      is TwoCaptchaSolver.TwoCaptchaResult.SolverBadApiKey -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        val exception = PostingException("Cannot use captcha solver, bad site key. " +
          "Solver='${twoCaptchaResult.solverName}'")

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(exception)
        )

        return AntiCaptchaServiceResult.Exit
      }
      is TwoCaptchaSolver.TwoCaptchaResult.SolverBadApiUrl -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        val exception = PostingException("Cannot use captcha solver, bad solver url: " +
          "\'${twoCaptchaResult.url}\'. Check the base url in the captcha solver settings. " +
          "Solver='${twoCaptchaResult.solverName}'")

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(exception)
        )

        return AntiCaptchaServiceResult.Exit
      }
      is TwoCaptchaSolver.TwoCaptchaResult.BadBalance -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(
            throwable = PostingException("BadBalance. balance=${twoCaptchaResult.balance}")
          )
        )

        return AntiCaptchaServiceResult.Exit
      }
      is TwoCaptchaSolver.TwoCaptchaResult.BadBalanceResponse -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        val errorCodeString = twoCaptchaResult.twoCaptchaBalanceResponse.response.requestRaw
        val errorText = twoCaptchaResult.twoCaptchaBalanceResponse.response.errorTextOrDefault()

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(
            throwable = PostingException("BadBalanceResponse. Error=$errorCodeString, Error text=${errorText}")
          )
        )

        return AntiCaptchaServiceResult.Exit
      }
      is TwoCaptchaSolver.TwoCaptchaResult.BadSolveCaptchaResponse -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        val errorCodeString = twoCaptchaResult.twoCaptchaSolveCaptchaResponse.response.requestRaw
        val errorText = twoCaptchaResult.twoCaptchaSolveCaptchaResponse.response.errorTextOrDefault()

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(
            throwable = PostingException("BadSolveCaptchaResponse. Error=$errorCodeString, Error text=${errorText}")
          )
        )

        return AntiCaptchaServiceResult.Exit
      }
      is TwoCaptchaSolver.TwoCaptchaResult.BadCheckCaptchaSolutionResponse -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        val errorCodeString = twoCaptchaResult.twoCaptchaCheckSolutionResponse.response.requestRaw
        val errorText = twoCaptchaResult.twoCaptchaCheckSolutionResponse.response.errorTextOrDefault()

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(
            throwable = PostingException("BadCheckCaptchaSolutionResponse. Error=$errorCodeString, Error text=${errorText}")
          )
        )

        return AntiCaptchaServiceResult.Exit
      }
      is TwoCaptchaSolver.TwoCaptchaResult.UnknownError -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(
            throwable = PostingException("UnknownError, message=${twoCaptchaResult.message}")
          )
        )

        return AntiCaptchaServiceResult.Exit
      }
      is TwoCaptchaSolver.TwoCaptchaResult.WaitingForSolution -> {
        Logger.d(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")
        return AntiCaptchaServiceResult.Wait(twoCaptchaResult.waitTime)
      }
      is TwoCaptchaSolver.TwoCaptchaResult.CaptchaNotNeeded -> {
        Logger.d(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")
        return AntiCaptchaServiceResult.ContinuePosting(null)
      }
      is TwoCaptchaSolver.TwoCaptchaResult.NotSupported -> {
        Logger.d(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")
        return AntiCaptchaServiceResult.ContinuePosting(null)
      }
      is TwoCaptchaSolver.TwoCaptchaResult.SolverDisabled -> {
        Logger.d(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")
        return AntiCaptchaServiceResult.ContinuePosting(null)
      }
      is TwoCaptchaSolver.TwoCaptchaResult.Solution -> {
        val solutionResponse = twoCaptchaResult.twoCaptchaCheckSolutionResponse
        if (!solutionResponse.isOk()) {
          val errorCodeString = solutionResponse.response.requestRaw
          val errorText = solutionResponse.response.errorTextOrDefault()
          val fullMessage = "Failed to solve captcha. Error=$errorCodeString, Error text=${errorText}"

          Logger.e(TAG, fullMessage)

          emitTerminalEvent(
            chanDescriptor = chanDescriptor,
            postResult = PostResult.Error(PostingException(fullMessage))
          )

          return AntiCaptchaServiceResult.Exit
        }

        val solutionTrimmed = StringUtils.trimCaptchaResponseToken(solutionResponse.response.requestRaw)
        Logger.d(TAG, "Got solution: \'${solutionTrimmed}\'")

        return AntiCaptchaServiceResult.ContinuePosting(solutionResponse.response.requestRaw)
      }
    }
  }

  private suspend fun emitTerminalEvent(
    chanDescriptor: ChanDescriptor,
    postResult: PostResult
  ) {
    doWithReplyInfo(chanDescriptor) { updateStatus(PostingStatus.AfterPosting(chanDescriptor, postResult)) }

    when (postResult) {
      PostResult.Canceled -> {
        updateChildNotification(chanDescriptor, ChildNotificationInfo.Status.Canceled)
      }
      is PostResult.Error -> {
        val errorMessage = postResult.throwable.errorMessageOrClassName()
        updateChildNotification(chanDescriptor, ChildNotificationInfo.Status.Error(errorMessage))
      }
      is PostResult.Success -> {
        val replyResponse = postResult.replyResponse
        if (replyResponse.posted) {
          updateChildNotification(chanDescriptor, ChildNotificationInfo.Status.Posted(chanDescriptor))
          return
        }

        val errorMessage = when {
          replyResponse.errorMessage.isNotNullNorBlank() -> replyResponse.errorMessage!!
          replyResponse.probablyBanned -> getString(R.string.post_service_response_probably_banned)
          replyResponse.requireAuthentication -> getString(R.string.post_service_response_authentication_required)
          replyResponse.additionalResponseData != null -> {
            when (replyResponse.additionalResponseData!!) {
              ReplyResponse.AdditionalResponseData.DvachAntiSpamCheckDetected -> {
                getString(R.string.post_service_response_dvach_antispam_detected)
              }
            }
          }
          else -> getString(R.string.post_service_response_unknown_error)
        }

        updateChildNotification(chanDescriptor, ChildNotificationInfo.Status.Error(errorMessage))
      }
    }
  }

  private suspend fun onPostedSuccessfully(
    prevChanDescriptor: ChanDescriptor,
    replyResponse: ReplyResponse
  ) {
    val siteDescriptor = replyResponse.siteDescriptor
    replyManager.cleanupFiles(prevChanDescriptor, notifyListeners = true)

    if (siteDescriptor == null) {
      Logger.e(TAG, "onPostedSuccessfully() siteDescriptor==null")
      return
    }

    // if the thread being presented has changed in the time waiting for this call to
    // complete, the loadable field in ReplyPresenter will be incorrect; reconstruct
    // the loadable (local to this method) from the reply response
    val localSite = siteManager.bySiteDescriptor(siteDescriptor)
    if (localSite == null) {
      Logger.e(TAG, "onPostedSuccessfully() localSite==null")
      return
    }

    val boardDescriptor = BoardDescriptor.create(siteDescriptor, replyResponse.boardCode)

    val localBoard = boardManager.byBoardDescriptor(boardDescriptor)
    if (localBoard == null) {
      Logger.e(TAG, "onPostedSuccessfully() localBoard==null")
      return
    }

    val threadNo = if (replyResponse.threadNo <= 0L) {
      replyResponse.postNo
    } else {
      replyResponse.threadNo
    }

    val newThreadDescriptor = ChanDescriptor.ThreadDescriptor.create(
      localSite.name(),
      localBoard.boardCode(),
      threadNo
    )

    lastReplyRepository.putLastReply(newThreadDescriptor.boardDescriptor)

    if (prevChanDescriptor.isCatalogDescriptor()) {
      lastReplyRepository.putLastThread(prevChanDescriptor.boardDescriptor())
    }

    val createThreadSuccess = chanPostRepository.createEmptyThreadIfNotExists(newThreadDescriptor)
      .peekError { error ->
        Logger.e(TAG, "Failed to create empty thread in the database for $newThreadDescriptor", error)
      }
      .valueOrNull() == true

    if (createThreadSuccess && ChanSettings.postPinThread.get()) {
      bookmarkThread(newThreadDescriptor, threadNo)
    }

    val responsePostDescriptor = replyResponse.postDescriptorOrNull
    if (responsePostDescriptor != null) {
      val password = if (replyResponse.password.isNotEmpty()) {
        replyResponse.password
      } else {
        null
      }

      savedReplyManager.saveReply(ChanSavedReply(responsePostDescriptor, password))
    } else {
      Logger.e(TAG, "Couldn't create responsePostDescriptor, replyResponse=${replyResponse}")
    }

    replyManager.readReply(newThreadDescriptor) { newReply ->
      replyManager.readReply(prevChanDescriptor) { prevReply ->
        val prevName = prevReply.postName
        val prevFlag = prevReply.flag

        newReply.resetAfterPosting()
        newReply.postName = prevName
        newReply.flag = prevFlag
      }
    }
  }

  private fun bookmarkThread(
    newThreadDescriptor: ChanDescriptor,
    threadNo: Long
  ) {
    if (newThreadDescriptor is ChanDescriptor.ThreadDescriptor) {
      if (bookmarksManager.exists(newThreadDescriptor)) {
        return
      }

      val thread = chanThreadManager.getChanThread(newThreadDescriptor)
      val bookmarkThreadDescriptor = newThreadDescriptor.toThreadDescriptor(threadNo)

      // reply
      val createBookmarkResult = if (thread != null) {
        val originalPost = thread.getOriginalPost()
        val title = ChanPostUtils.getTitle(originalPost, newThreadDescriptor)
        val thumbnail = originalPost.firstImage()?.actualThumbnailUrl

        bookmarksManager.createBookmark(bookmarkThreadDescriptor, title, thumbnail)
      } else {
        bookmarksManager.createBookmark(bookmarkThreadDescriptor)
      }

      if (!createBookmarkResult) {
        Logger.e(TAG, "bookmarkThread() Failed to create bookmark with newThreadDescriptor=$newThreadDescriptor, " +
          "threadDescriptor: $bookmarkThreadDescriptor, newThreadDescriptor=$newThreadDescriptor, " +
          "threadNo=$threadNo")
      }
    } else {
      val bookmarkThreadDescriptor = ChanDescriptor.ThreadDescriptor.create(
        boardDescriptor = newThreadDescriptor.boardDescriptor(),
        threadNo = threadNo
      )

      if (bookmarksManager.exists(bookmarkThreadDescriptor)) {
        return
      }

      val title = replyManager.readReply(newThreadDescriptor) { reply ->
        PostHelper.getTitle(reply)
      }

      val createBookmarkResult = bookmarksManager.createBookmark(
        bookmarkThreadDescriptor,
        title
      )

      if (!createBookmarkResult) {
        Logger.e(TAG, "bookmarkThread() Failed to create bookmark with newThreadDescriptor=$newThreadDescriptor, " +
            "threadDescriptor: $bookmarkThreadDescriptor, newThreadDescriptor=$newThreadDescriptor, " +
            "threadNo=$threadNo")
      }
    }
  }

  private suspend fun awaitUntilEverythingIsInitialized(chanDescriptor: ChanDescriptor): ModularResult<Unit> {
    return ModularResult.Try {
      withContext(Dispatchers.IO) {
        replyManager.reloadFilesFromDisk(appConstants).unwrap()

        if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
          savedReplyManager.preloadForThread(chanDescriptor)
        }
      }

      replyManager.awaitUntilFilesAreLoaded()
      siteManager.awaitUntilInitialized()
      boardManager.awaitUntilInitialized()
      chanPostRepository.awaitUntilInitialized()
      bookmarksManager.awaitUntilInitialized()
      chanThreadManager.awaitUntilDependenciesInitialized()
    }
  }

  private suspend fun ensureNotCanceled(replyDescriptor: ChanDescriptor) {
    val canceled = mutex.withLock {
      val replyInfo = activeReplyDescriptors[replyDescriptor]
      return@withLock replyInfo == null || replyInfo.canceled
    }

    if (canceled) {
      throw CancellationException("Canceled")
    }
  }

  private suspend fun <T : Any?> doWithReplyInfo(
    replyDescriptor: ChanDescriptor,
    func: ReplyInfo.() -> T
  ): T? {
    return mutex.withLock {
      activeReplyDescriptors[replyDescriptor]?.let { replyInfo -> func(replyInfo) }
    }
  }

  suspend fun isReplyCurrentlyInProgress(replyDescriptor: ChanDescriptor): Boolean {
    return mutex.withLock {
      val isReplyUploadActive = activeReplyDescriptors[replyDescriptor]
        ?.currentStatus
        ?.isActive()
        ?: false

      return@withLock isReplyUploadActive.not()
    }
  }

  suspend fun consumeTerminalEvent(replyDescriptor: ChanDescriptor) {
    val consumed = mutex.withLock {
      val replyInfo = activeReplyDescriptors[replyDescriptor]
      if (replyInfo == null) {
        return@withLock false
      }

      if (!replyInfo.currentStatus.isTerminalEvent()) {
        return@withLock false
      }

      replyInfo.updateStatus(PostingStatus.Attached(replyDescriptor))
      return@withLock true
    }

    Logger.d(TAG, "consumeTerminalEvent($replyDescriptor) consumed=$consumed")
  }

  class ReplyInfo(
    initialStatus: PostingStatus,
    private val chanDescriptor: ChanDescriptor,
    private val _status: MutableStateFlow<PostingStatus> = MutableStateFlow(initialStatus),
    val retrying: AtomicBoolean = AtomicBoolean(false)
  ) {
    private val _canceled = AtomicBoolean(false)

    val currentStatus: PostingStatus
      get() = _status.value
    val statusUpdates: StateFlow<PostingStatus>
      get() = _status.asStateFlow()
    val activeJob = AtomicReference<Job>(null)

    val canceled: Boolean
      get() = activeJob.get() == null || _canceled.get()

    @Synchronized
    fun updateStatus(newStatus: PostingStatus) {
      Logger.d(TAG, "updateStatus($chanDescriptor) -> ${newStatus.javaClass.simpleName}")
      _status.value = newStatus
    }

    @Synchronized
    fun cancelReplyUpload() {
      _canceled.set(true)

      val job = activeJob.get() ?: return
      job.cancel()

      activeJob.set(null)
    }

    @Synchronized
    fun reset(chanDescriptor: ChanDescriptor) {
      retrying.set(false)
      _canceled.set(false)
      activeJob.set(null)

      _status.value = PostingStatus.Enqueued(chanDescriptor)
    }

  }

  sealed class AntiCaptchaServiceResult {
    data class Wait(val waitTimeMs: Long) : AntiCaptchaServiceResult()

    object Exit : AntiCaptchaServiceResult() {
      override fun toString(): String {
        return "Exit"
      }
    }

    data class ContinuePosting(val token: String?) : AntiCaptchaServiceResult()
  }

  sealed class PostResult {
    object Canceled : PostResult()

    data class Error(
      val throwable: Throwable
    ) : PostResult()

    data class Success(
      val replyResponse: ReplyResponse,
      val retrying: Boolean
    ) : PostResult()
  }

  sealed class PostingStatus {
    abstract val chanDescriptor: ChanDescriptor

    fun isActive(): Boolean {
      return when (this) {
        is AfterPosting,
        is Attached -> false
        is WaitingForAdditionalService,
        is BeforePosting,
        is Enqueued,
        is Progress -> true
      }
    }

    fun isTerminalEvent(): Boolean {
      return when (this) {
        is Attached,
        is Enqueued,
        is Progress,
        is WaitingForAdditionalService,
        is BeforePosting -> false
        is AfterPosting -> true
      }
    }

    data class Attached(
      override val chanDescriptor: ChanDescriptor
    ) : PostingStatus()

    data class Enqueued(
      override val chanDescriptor: ChanDescriptor
    ) : PostingStatus()

    data class WaitingForAdditionalService(
      val serviceName: String,
      override val chanDescriptor: ChanDescriptor
    ) : PostingStatus()

    data class BeforePosting(
      override val chanDescriptor: ChanDescriptor
    ) : PostingStatus()

    data class Progress(
      override val chanDescriptor: ChanDescriptor,
      val fileIndex: Int,
      val totalFiles: Int,
      val percent: Int
    ) : PostingStatus() {
      fun toOverallPercent(): Float {
        val index = (fileIndex - 1).coerceAtLeast(0).toFloat()

        val totalProgress = totalFiles.toFloat() * 100f
        val currentProgress = (index * 100f) + percent.toFloat()

        return currentProgress / totalProgress
      }
    }

    data class AfterPosting(
      override val chanDescriptor: ChanDescriptor,
      val postResult: PostResult
    ) : PostingStatus()
  }

  data class MainNotificationInfo(val activeRepliesCount: Int)

  data class ChildNotificationInfo(
    val chanDescriptor: ChanDescriptor,
    val status: Status
  ) {

    val canCancel: Boolean
      get() {
        return when (status) {
          is Status.Uploading,
          is Status.WaitingForAdditionalService -> true
          is Status.Preparing,
          is Status.Error,
          is Status.Posted,
          Status.Canceled -> false
        }
      }

    val isOngoing: Boolean
      get() {
        return when (status) {
          is Status.Uploading,
          is Status.WaitingForAdditionalService -> true
          is Status.Error,
          is Status.Preparing,
          is Status.Posted,
          Status.Canceled -> false
        }
      }

    sealed class Status(
      val statusText: String
    ) {
      class Preparing(
        chanDescriptor: ChanDescriptor
      ) : Status("Preparing to send a reply to ${chanDescriptor.userReadableString()}")

      data class WaitingForAdditionalService(
        val serviceName: String
      ): Status("Waiting for additional service: \"${serviceName}\"")

      data class Uploading(
        val totalProgress: Float
      ) : Status("Uploading ${(totalProgress * 100f).toInt()}%...")

      data class Posted(
        val chanDescriptor: ChanDescriptor
      ) : Status("Posted in ${chanDescriptor.userReadableString()}")

      data class Error(
        val errorMessage: String
      ) : Status("Failed to post, error=${errorMessage}")

      object Canceled : Status("Canceled")
    }

    companion object {
      fun default(chanDescriptor: ChanDescriptor): ChildNotificationInfo {
        return ChildNotificationInfo(chanDescriptor, Status.Preparing(chanDescriptor))
      }
    }
  }

  class PostingException(message: String) : Exception(message)

  companion object {
    private const val TAG = "PostingServiceDelegate"
    private const val MAX_ATTEMPTS = 16
  }

}