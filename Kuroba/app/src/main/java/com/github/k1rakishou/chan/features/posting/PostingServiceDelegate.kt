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
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.features.posting.solvers.two_captcha.TwoCaptchaResult
import com.github.k1rakishou.chan.features.posting.solvers.two_captcha.TwoCaptchaSolver
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
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
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.OptionsSetting
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit
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
  private val _lastReplyRepository: Lazy<LastReplyRepository>,
  private val chanPostRepository: ChanPostRepository,
  private val twoCaptchaSolver: Lazy<TwoCaptchaSolver>,
  private val captchaHolder: Lazy<CaptchaHolder>,
  private val captchaDonation: Lazy<CaptchaDonation>
) {
  private val mutex = Mutex()
  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

  @GuardedBy("mutex")
  private val activeReplyDescriptors = hashMapOf<ChanDescriptor, ReplyInfo>()

  private val _stopServiceEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val _updateMainNotificationFlow = MutableSharedFlow<MainNotificationInfo>(extraBufferCapacity = Channel.UNLIMITED)
  private val _updateChildNotificationFlow = MutableSharedFlow<ChildNotificationInfo>(extraBufferCapacity = Channel.UNLIMITED)
  private val _closeChildNotificationFlow = MutableSharedFlow<ChanDescriptor>(extraBufferCapacity = Channel.UNLIMITED)

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
        if (!replyInfo.cancelReplyUpload()) {
          return@withLockNonCancellable
        }

        updateChildNotification(
          chanDescriptor = replyInfo.chanDescriptor,
          status = ChildNotificationInfo.Status.Canceled
        )
        _closeChildNotificationFlow.emit(replyInfo.chanDescriptor)
      }

      twoCaptchaSolver.get().cancelAll()
    }

    checkAllRepliesProcessed()
  }

  suspend fun cancel(chanDescriptor: ChanDescriptor) {
    Logger.d(TAG, "cancel($chanDescriptor)")

    mutex.withLockNonCancellable {
      twoCaptchaSolver.get().cancel(chanDescriptor)

      val replyInfo = activeReplyDescriptors[chanDescriptor]
      if (replyInfo != null && !replyInfo.cancelReplyUpload()) {
        return@withLockNonCancellable
      }

      updateChildNotification(
        chanDescriptor = chanDescriptor,
        status = ChildNotificationInfo.Status.Canceled
      )
      _closeChildNotificationFlow.emit(chanDescriptor)
    }

    checkAllRepliesProcessed()
  }

  suspend fun listenForPostingStatusUpdates(chanDescriptor: ChanDescriptor): StateFlow<PostingStatus> {
    Logger.d(TAG, "listenForPostingStatusUpdates($chanDescriptor)")

    return mutex.withLock {
      if (activeReplyDescriptors.containsKey(chanDescriptor)) {
        return@withLock activeReplyDescriptors[chanDescriptor]!!.statusUpdates
      }

      val replyMode = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
        ?.requireSettingBySettingId<OptionsSetting<ReplyMode>>(SiteSetting.SiteSettingId.LastUsedReplyMode)
        ?.get()
        ?: ReplyMode.ReplyModeSolveCaptchaManually

      activeReplyDescriptors.put(
        chanDescriptor,
        ReplyInfo(
          chanDescriptor = chanDescriptor,
          initialStatus = PostingStatus.Attached(chanDescriptor),
          initialReplyMode = replyMode
        )
      )

      return@withLock activeReplyDescriptors[chanDescriptor]!!.statusUpdates
    }
  }

  fun onNewReply(chanDescriptor: ChanDescriptor, replyMode: ReplyMode, retrying: Boolean) {
    BackgroundUtils.ensureMainThread()

    serializedCoroutineExecutor.post {
      val shouldStartCoroutine = mutex.withLock { addOrUpdateReplyInfo(chanDescriptor, replyMode, retrying) }
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

      val job = appScope.launch {
        supervisorScope { onNewReplyInternal(chanDescriptor) }
      }

      readReplyInfo(chanDescriptor) { setJob(job) }
    }
  }

  private suspend fun CoroutineScope.onNewReplyInternal(chanDescriptor: ChanDescriptor) {
    Logger.d(TAG, "onNewReplyInternal($chanDescriptor)")
    val postedSuccessfully = AtomicBoolean(false)

    try {
      processNewReply(chanDescriptor, postedSuccessfully)
    } catch (error: Throwable) {
      if (error is CancellationException) {
        Logger.e(TAG, "Posting canceled $chanDescriptor")

        updateChildNotification(
          chanDescriptor = chanDescriptor,
          status = ChildNotificationInfo.Status.Canceled
        )

        readReplyInfo(chanDescriptor) {
          updateStatus(PostingStatus.AfterPosting(chanDescriptor, PostResult.Canceled))
        }
      } else {
        Logger.e(TAG, "Unhandled exception", error)

        updateChildNotification(
          chanDescriptor = chanDescriptor,
          status = ChildNotificationInfo.Status.Error(error.errorMessageOrClassName())
        )

        readReplyInfo(chanDescriptor) {
          updateStatus(PostingStatus.AfterPosting(chanDescriptor, PostResult.Error(error)))
        }
      }
    }

    if (!postedSuccessfully.get()) {
      replyManager.restoreFiles(chanDescriptor)
    }

    val allRepliesPerBoardProcessed = mutex.withLock {
      if (activeReplyDescriptors.isEmpty()) {
        return@withLock true
      }

      return@withLock activeReplyDescriptors.values
        .filter { info -> info.chanDescriptor.boardDescriptor() == chanDescriptor.boardDescriptor() }
        .all { info -> info.currentStatus is PostingStatus.Attached || info.currentStatus.isTerminalEvent() }
    }

    if (allRepliesPerBoardProcessed) {
      Logger.d(TAG, "All replies for board ${chanDescriptor.boardDescriptor()} processed, " +
        "resetting the cooldowns")

      // All replies for the same board as the current reply have been processed. We need to reset
      // the cooldowns and update last reply attempt time just to be sure everything is up to date.
      _lastReplyRepository.get().onPostAttemptFinished(chanDescriptor)
    }

    updateMainNotification()

    val canceled = readReplyInfo(chanDescriptor) { canceled }
    if (canceled || !isActive) {
      _closeChildNotificationFlow.emit(chanDescriptor)
    }

    checkAllRepliesProcessed()
  }

  private suspend fun checkAllRepliesProcessed() {
    val allRepliesProcessed = mutex.withLock {
      if (activeReplyDescriptors.isEmpty()) {
        return@withLock true
      }

      return@withLock activeReplyDescriptors.values.all { replyInfo ->
        if (replyInfo.currentStatus is PostingStatus.Attached) {
          return@all true
        }

        if (replyInfo.currentStatus !is PostingStatus.AfterPosting) {
          return@all false
        }

        val postResult = (replyInfo.currentStatus as PostingStatus.AfterPosting).postResult
        if (postResult is PostResult.Success && postResult.replyResponse.posted) {
          return@all true
        }

        if (postResult is PostResult.Canceled) {
          return@all true
        }

        return@all false
      }
    }

    if (allRepliesProcessed) {
      Logger.d(TAG, "All replies processed, stopping the service")
      _stopServiceEventFlow.emit(Unit)
    }
  }

  private fun addOrUpdateReplyInfo(
    chanDescriptor: ChanDescriptor,
    replyMode: ReplyMode,
    retrying: Boolean
  ): Boolean {
    if (activeReplyDescriptors.containsKey(chanDescriptor)) {
      val replyInfo = activeReplyDescriptors[chanDescriptor]!!
      replyInfo.replyModeRef.set(replyMode)
      replyInfo.retrying.set(retrying)

      val shouldStartCoroutine = when (replyInfo.currentStatus) {
        is PostingStatus.Attached,
        is PostingStatus.AfterPosting -> true
        is PostingStatus.Enqueued,
        is PostingStatus.UploadingProgress,
        is PostingStatus.Uploaded,
        is PostingStatus.BeforePosting,
        is PostingStatus.WaitingForSiteRateLimitToPass,
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
      initialReplyMode = replyMode,
      chanDescriptor = chanDescriptor,
      retrying = AtomicBoolean(retrying),
      replyModeRef = AtomicReference(replyMode)
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

    Logger.d(TAG, "processNewReply($chanDescriptor)")
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
      Logger.e(TAG, "makeSubmitCall() takeFilesResult == false")

      emitTerminalEvent(
        chanDescriptor = chanDescriptor,
        postResult = PostResult.Error(
          throwable = IOException("Failed to move attached files into reply")
        )
      )
      return
    }

    runPostWaitQueueLoop(
      site = site,
      chanDescriptor = chanDescriptor,
      postedSuccessfully = postedSuccessfully
    )
  }

  private suspend fun runPostWaitQueueLoop(
    site: Site,
    chanDescriptor: ChanDescriptor,
    postedSuccessfully: AtomicBoolean
  ) {
    Logger.d(TAG, "runPostWaitQueueLoop(${site.siteDescriptor()}, $chanDescriptor)")
    ensureNotCanceled(chanDescriptor)

    readReplyInfo(chanDescriptor) {
      updateStatus(PostingStatus.WaitingForSiteRateLimitToPass(chanDescriptor))
    }

    val startTime = System.currentTimeMillis()
    val automaticallySolvedCaptchasCount = AtomicInteger()

    Logger.d(TAG, "runPostWaitQueueLoop($chanDescriptor) started at $startTime")
    val lastReplyRepository = _lastReplyRepository.get()

    while (true) {
      ensureNotCanceled(chanDescriptor)

      if (System.currentTimeMillis() - startTime > MAX_POST_QUEUE_TIME_MS) {
        val timeSpent = System.currentTimeMillis() - startTime
        Logger.e(TAG, "runPostWaitQueueLoop($chanDescriptor) spent too much time in the loop " +
          "(${timeSpent}ms), exiting")

        cancel(chanDescriptor)
        break
      }

      if (automaticallySolvedCaptchasCount.get() > MAX_AUTO_SOLVED_CAPTCHAS_COUNT) {
        val solvedCount = automaticallySolvedCaptchasCount.get()
        Logger.e(TAG, "runPostWaitQueueLoop($chanDescriptor) solved too many captcha ($solvedCount), exiting")

        cancel(chanDescriptor)
        break
      }

      var timeToWaitMs = POST_LOOP_DELAY_MS

      // First we need to take the oldest post from all posts in the activeReplyDescriptors
      if (isOldestEnqueuedReply(chanDescriptor)) {
        val replyMode = readReplyInfo(chanDescriptor) { replyModeRef.get() }
        val hasFiles = replyManager.readReply(chanDescriptor) { reply -> reply.hasFiles() }

        Logger.d(TAG, "runPostWaitQueueLoop($chanDescriptor) oldest enqueued reply descriptor: $chanDescriptor")

        // Then we need to check whether we can post or need to wait a timeout before posting
        val remainingWaitTime = lastReplyRepository.getTimeUntilNextThreadCreationOrReply(
          chanDescriptor = chanDescriptor,
          replyMode = replyMode,
          hasAttachedImages = hasFiles
        )

        check(remainingWaitTime >= 0) { "Bad remainingWaitTime: $remainingWaitTime" }

        if (remainingWaitTime > 0) {
          // Can't post yet, need to wait
          updateChildNotification(
            chanDescriptor = chanDescriptor,
            status = ChildNotificationInfo.Status.WaitingForSiteRateLimitToPass(
              remainingWaitTimeMs = remainingWaitTime,
              boardDescriptor = chanDescriptor.boardDescriptor()
            )
          )

          timeToWaitMs = (remainingWaitTime + POST_LOOP_DELAY_MS)
            .coerceIn(POST_LOOP_DELAY_MS, POST_LOOP_DELAY_MAX_MS)
        } else {
          // Possibly can post. Now we need to check whether somebody is already trying to post on
          // this board. We only allow one post being processed per board at a time.

          val attemptToStartPosting = lastReplyRepository.attemptToStartPosting(chanDescriptor)
          if (attemptToStartPosting) {
            Logger.d(TAG, "runPostWaitQueueLoop($chanDescriptor) remainingWaitTime: ${remainingWaitTime}, " +
              "attemptToStartPosting=$attemptToStartPosting, " +
              "automaticallySolvedCaptchasCount=${automaticallySolvedCaptchasCount.get()}")

            // We managed to take the permission to post
            val actualPostResult = AtomicReference<ActualPostResult>(ActualPostResult.FailedToPost)

            try {
              runPostWaitForCaptchaLoop(
                site = site,
                chanDescriptor = chanDescriptor,
                automaticallySolvedCaptchasCount = automaticallySolvedCaptchasCount,
                actualPostResult = actualPostResult
              )
            } finally {
              lastReplyRepository.endPostingAttempt(chanDescriptor)
            }

            Logger.d(TAG, "runPostWaitQueueLoop($chanDescriptor) makePostInternal() -> $actualPostResult")

            when (actualPostResult.get()) {
              null,
              is ActualPostResult.Posted -> {
                postedSuccessfully.set(true)
                break
              }
              is ActualPostResult.RateLimited -> {
                // We were rate limited, now we need to wait again
                continue
              }
              is ActualPostResult.FailedToPost -> {
                // Failed to post for whatever reason and we can't continue. We need to notify the
                // user about the error.
                break
              }
            }
          }
        }
      }

      Logger.d(TAG, "runPostWaitQueueLoop($chanDescriptor) waiting ${timeToWaitMs}ms...")
      delay(timeToWaitMs)
    }

    Logger.d(TAG, "runPostQueueLoop($chanDescriptor) took ${System.currentTimeMillis() - startTime}ms")
  }

  private suspend fun runPostWaitForCaptchaLoop(
    site: Site,
    chanDescriptor: ChanDescriptor,
    automaticallySolvedCaptchasCount: AtomicInteger,
    actualPostResult: AtomicReference<ActualPostResult>
  ) {
    val serviceName = twoCaptchaSolver.get().name
    Logger.d(TAG, "runPostWaitForCaptchaLoop(${site.siteDescriptor()}, $chanDescriptor)")

    readReplyInfo(chanDescriptor) {
      updateStatus(PostingStatus.WaitingForAdditionalService(serviceName, chanDescriptor))
    }

    val availableAttempts = AtomicInteger(MAX_ATTEMPTS)

    while (true) {
      ensureNotCanceled(chanDescriptor)
      val result = processAntiCaptchaService(site, availableAttempts.get(), chanDescriptor)
      ensureNotCanceled(chanDescriptor)

      Logger.d(TAG, "runPostWaitForCaptchaLoop() processAntiCaptchaService($chanDescriptor) -> $result, " +
        "attempt ${availableAttempts.get()}")

      when (result) {
        is AntiCaptchaServiceResult.Solution,
        is AntiCaptchaServiceResult.AlreadyHaveSolution -> {
          val replyMode = readReplyInfo(chanDescriptor) { replyModeRef.get() }
          val retrying = readReplyInfo(chanDescriptor) { retrying.get() }

          val hasValidCaptcha = hasValidCaptcha(chanDescriptor, result, automaticallySolvedCaptchasCount)
          Logger.d(TAG, "requiresAuthentication($chanDescriptor) replyMode=$replyMode, hasValidCaptcha=$hasValidCaptcha")

          val canPostWithoutCaptcha = when (replyMode) {
            null,
            ReplyMode.Unknown,
            ReplyMode.ReplyModeSolveCaptchaAuto -> false
            ReplyMode.ReplyModeSendWithoutCaptcha,
            ReplyMode.ReplyModeUsePasscode -> true
            ReplyMode.ReplyModeSolveCaptchaManually -> {
              // Allow posting without forcing to solve captcha if the user already has a pre-solved captcha
              hasValidCaptcha
            }
          }

          if (!hasValidCaptcha && !canPostWithoutCaptcha) {
            val replyResponse = ReplyResponse()
              .also { response -> response.requireAuthentication = true }

            emitTerminalEvent(
              chanDescriptor = chanDescriptor,
              postResult = PostResult.Success(replyResponse, replyMode, retrying)
            )

            return
          }

          break
        }
        AntiCaptchaServiceResult.ExitLoop -> {
          return
        }
        is AntiCaptchaServiceResult.WaitNextIteration -> {
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
    readReplyInfo(chanDescriptor) { updateStatus(PostingStatus.BeforePosting(chanDescriptor)) }

    callPostDelegate(
      site = site,
      chanDescriptor = chanDescriptor,
      actualPostResult = actualPostResult
    )
  }

  private fun hasValidCaptcha(
    chanDescriptor: ChanDescriptor,
    result: AntiCaptchaServiceResult,
    automaticallySolvedCaptchasCount: AtomicInteger
  ): Boolean {
    val captchaSolution = when (result) {
      AntiCaptchaServiceResult.ExitLoop,
      is AntiCaptchaServiceResult.WaitNextIteration -> {
        throw RuntimeException("Shouldn't be called here")
      }
      is AntiCaptchaServiceResult.Solution -> {
        // This called when the captcha was solved by anti-captcha service, meaning we paid
        // money for that. We don't want to accidentally waste all the money if we ever somehow
        // get into an infinite loop here so we need to check how many was solved and exit if
        // there were too many.
        automaticallySolvedCaptchasCount.incrementAndGet()
        result.solution
      }
      is AntiCaptchaServiceResult.AlreadyHaveSolution -> {
        // We already have a token (Most likely the user pre-solved captcha manually).
        result.solution
      }
    }

    return replyManager.readReply(chanDescriptor) { reply ->
      var actualCaptchaSolution = captchaSolution
      if (actualCaptchaSolution?.isTokenEmpty() == true) {
        actualCaptchaSolution = null
      }

      reply.setCaptcha(challenge = null, captchaSolution = actualCaptchaSolution)
      return@readReply reply.captchaSolution != null
    }
  }

  private suspend fun callPostDelegate(
    site: Site,
    chanDescriptor: ChanDescriptor,
    actualPostResult: AtomicReference<ActualPostResult>
  ) {
    Logger.d(TAG, "callPostDelegate(${site.siteDescriptor()}, $chanDescriptor)")
    val replyMode = readReplyInfo(chanDescriptor) { replyModeRef.get() }

    if (replyMode == ReplyMode.Unknown) {
      emitTerminalEvent(chanDescriptor, PostResult.Error(PostingException("ReplyMode is not set")))
      return
    }

    var prevUploadingProgressNotifyTime = 0L

    site.actions()
      .post(chanDescriptor, replyMode)
      .catch { error ->
        Logger.e(TAG, "SiteActions.PostResult.PostError($chanDescriptor) " +
            "unhandled error: ${error.errorMessageOrClassName()}")

        emitTerminalEvent(chanDescriptor, PostResult.Error(error))
      }
      .collect { postResult ->
        when (postResult) {
          is SiteActions.PostResult.UploadingProgress -> {
            val status = PostingStatus.UploadingProgress(
              chanDescriptor = chanDescriptor,
              fileIndex = postResult.fileIndex,
              totalFiles = postResult.totalFiles,
              percent = postResult.percent
            )

            // Update the notification only once in 100ms
            val now = System.currentTimeMillis()
            if (now - prevUploadingProgressNotifyTime < 100) {
              return@collect
            }

            prevUploadingProgressNotifyTime = now

            updateChildNotification(
              chanDescriptor = chanDescriptor,
              status = ChildNotificationInfo.Status.Uploading(status.toTotalPercent())
            )
            readReplyInfo(chanDescriptor) { updateStatus(status) }
          }
          is SiteActions.PostResult.PostError -> {
            Logger.e(TAG, "SiteActions.PostResult.PostError($chanDescriptor) " +
                "error: ${postResult.error.errorMessageOrClassName()}")

            updateChildNotification(
              chanDescriptor = chanDescriptor,
              status = ChildNotificationInfo.Status.Uploaded()
            )
            readReplyInfo(chanDescriptor) { updateStatus(PostingStatus.Uploaded(chanDescriptor)) }

            emitTerminalEvent(chanDescriptor, PostResult.Error(postResult.error))
          }
          is SiteActions.PostResult.PostComplete -> {
            Logger.d(TAG, "SiteActions.PostResult.PostComplete")

            updateChildNotification(
              chanDescriptor = chanDescriptor,
              status = ChildNotificationInfo.Status.Uploaded()
            )
            readReplyInfo(chanDescriptor) { updateStatus(PostingStatus.Uploaded(chanDescriptor)) }

            onPostComplete(chanDescriptor, postResult, actualPostResult)
          }
        }
      }
  }

  /**
   * We fully uploaded a post (with all it's attachments) to the server and got a response.
   * */
  private suspend fun onPostComplete(
    chanDescriptor: ChanDescriptor,
    postResult: SiteActions.PostResult.PostComplete,
    actualPostResult: AtomicReference<ActualPostResult>
  ) {
    val retrying = readReplyInfo(chanDescriptor) { retrying.get() }
    val replyMode = readReplyInfo(chanDescriptor) { replyModeRef.get() }

    if (!postResult.replyResponse.posted) {
      Logger.d(TAG, "SiteActions.PostResult.PostComplete($chanDescriptor) failed to post, " +
          "response=${postResult.replyResponse}")

      if (postResult.replyResponse.rateLimitInfo != null) {
        onRateLimitedByServer(
          rateLimitInfo = postResult.replyResponse.rateLimitInfo!!,
          actualPostResult = actualPostResult,
          chanDescriptor = chanDescriptor
        )
      } else if (postResult.replyResponse.banInfo != null) {
        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Banned(
            banMessage = postResult.replyResponse.errorMessage,
            banInfo = postResult.replyResponse.banInfo!!
          )
        )
      } else {
        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Success(
            replyResponse = postResult.replyResponse,
            replyMode = replyMode,
            retrying = retrying
          )
        )
      }

      return
    }

    try {
      onPostedSuccessfully(chanDescriptor, postResult.replyResponse)
      actualPostResult.set(ActualPostResult.Posted)

      emitTerminalEvent(
        chanDescriptor = chanDescriptor,
        postResult = PostResult.Success(
          replyResponse = postResult.replyResponse,
          replyMode = replyMode,
          retrying = retrying
        )
      )

      Logger.d(TAG, "SiteActions.PostResult.PostComplete($chanDescriptor) success")
    } catch (error: Throwable) {
      emitTerminalEvent(chanDescriptor, PostResult.Error(error))
      Logger.e(TAG, "SiteActions.PostResult.PostComplete($chanDescriptor) error", error)
    }
  }

  /**
   * We detected that the server declined our post with some kind of "You must wait X minutes Y
   * seconds before posting" message.
   * */
  private suspend fun onRateLimitedByServer(
    rateLimitInfo: ReplyResponse.RateLimitInfo,
    actualPostResult: AtomicReference<ActualPostResult>,
    chanDescriptor: ChanDescriptor
  ) {
    // We were rate limited. We don't want to emit terminal event here because we are
    // going to continue the queue loop. But we need to reset the captcha, update
    // cooldownInfo in the lastReplyRepository and update the notification.
    val timeToWaitMs = rateLimitInfo.actualTimeToWaitMs.coerceAtLeast(POST_LOOP_DELAY_MS)

    actualPostResult.set(ActualPostResult.RateLimited(timeToWaitMs))

    replyManager.readReply(chanDescriptor) { reply ->
      reply.resetCaptcha()
    }

    _lastReplyRepository.get().onPostAttemptFinished(
      chanDescriptor = chanDescriptor,
      postedSuccessfully = false,
      newCooldownInfo = rateLimitInfo.cooldownInfo
    )

    updateChildNotification(
      chanDescriptor = chanDescriptor,
      status = ChildNotificationInfo.Status.WaitingForSiteRateLimitToPass(
        remainingWaitTimeMs = rateLimitInfo.cooldownInfo.currentPostingCooldownMs,
        boardDescriptor = chanDescriptor.boardDescriptor()
      )
    )
  }

  /**
   * Check whether a reply with this [chanDescriptor] is the oldest among all replies with
   * [PostingStatus.WaitingForSiteRateLimitToPass] status. We always attempt to post the oldest
   * queued replies first. Basically it's a FIFO queue.
   * */
  private suspend fun isOldestEnqueuedReply(chanDescriptor: ChanDescriptor): Boolean {
    return mutex.withLock {
      if (activeReplyDescriptors.isEmpty()) {
        return@withLock true
      }

      var actualOldestReplyDescriptor: ChanDescriptor? = null
      var oldestEnqueuedReplyTime: Long = Long.MAX_VALUE

      for ((descriptor, replyInfo) in activeReplyDescriptors.entries) {
        if (replyInfo.currentStatus !is PostingStatus.WaitingForSiteRateLimitToPass) {
          continue
        }

        if (replyInfo.enqueuedAt.get() < oldestEnqueuedReplyTime) {
          oldestEnqueuedReplyTime = replyInfo.enqueuedAt.get()
          actualOldestReplyDescriptor = descriptor
        }
      }

      if (actualOldestReplyDescriptor == null) {
        return@withLock true
      }

      return@withLock actualOldestReplyDescriptor == chanDescriptor
    }
  }

  /**
   * Handles the results of captcha solvers.
   * */
  private suspend fun processAntiCaptchaService(
    site: Site,
    availableAttempts: Int,
    chanDescriptor: ChanDescriptor
  ): AntiCaptchaServiceResult {
    val replyMode = readReplyInfo(chanDescriptor) { replyModeRef.get() }

    if (site.actions().isLoggedIn() && replyMode == ReplyMode.ReplyModeUsePasscode) {
      Logger.d(TAG, "processAntiCaptchaService($chanDescriptor) logged in and reply mode is ReplyModeUsePasscode")
      return AntiCaptchaServiceResult.AlreadyHaveSolution(null)
    }

    val prevCaptchaSolution = replyManager.readReply(chanDescriptor) { reply -> reply.captchaSolution }
    if (prevCaptchaSolution != null && !prevCaptchaSolution.isTokenEmpty()) {
      Logger.d(TAG, "processAntiCaptchaService($chanDescriptor) reply already contains a token, use it instead")
      return AntiCaptchaServiceResult.AlreadyHaveSolution(prevCaptchaSolution)
    }

    if (captchaHolder.get().hasSolution()) {
      Logger.d(TAG, "processAntiCaptchaService($chanDescriptor) already has token, use it instead")
      return AntiCaptchaServiceResult.AlreadyHaveSolution(captchaHolder.get().solution)
    }

    if (replyMode != ReplyMode.ReplyModeSolveCaptchaAuto) {
      Logger.d(TAG, "processAntiCaptchaService($chanDescriptor) replyMode does not allow using " +
        "captcha solver, replyMode=${replyMode}")
      return AntiCaptchaServiceResult.AlreadyHaveSolution(prevCaptchaSolution)
    }

    // Only update the notification after we are sure that we will call the captcha solver service.
    val updateChildNotificationFunc: suspend () -> Unit = {
      updateChildNotification(
        chanDescriptor = chanDescriptor,
        status = ChildNotificationInfo.Status.WaitingForAdditionalService(
          availableAttempts = availableAttempts,
          serviceName = twoCaptchaSolver.get().name
        )
      )
    }

    val twoCaptchaResult = twoCaptchaSolver.get().solve(chanDescriptor, updateChildNotificationFunc)
      .safeUnwrap { error ->
        if (error is IOException) {
          Logger.e(TAG, "processAntiCaptchaService() twoCaptchaSolver.solve($chanDescriptor) " +
            "error=${error.errorMessageOrClassName()}")

          return AntiCaptchaServiceResult.WaitNextIteration(TwoCaptchaSolver.LONG_WAIT_TIME_MS)
        }

        Logger.e(TAG, "processAntiCaptchaService() twoCaptchaSolver.solve($chanDescriptor) error", error)

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(error)
        )

        return AntiCaptchaServiceResult.ExitLoop
      }

    when (twoCaptchaResult) {
      is TwoCaptchaResult.BadSiteKey -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        val exception = PostingException("Cannot use captcha solver, bad site key. " +
            "Solver='${twoCaptchaResult.solverName}'")

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(exception)
        )

        return AntiCaptchaServiceResult.ExitLoop
      }
      is TwoCaptchaResult.BadSiteBaseUrl -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        val exception = PostingException("Cannot use captcha solver, bad site key. " +
          "Solver='${twoCaptchaResult.solverName}'. url=${twoCaptchaResult.url}")

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(exception)
        )

        return AntiCaptchaServiceResult.ExitLoop
      }
      is TwoCaptchaResult.SolverBadApiKey -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        val exception = PostingException("Cannot use captcha solver, bad API key. " +
          "Check that your API key is valid. Solver='${twoCaptchaResult.solverName}'")

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(exception)
        )

        return AntiCaptchaServiceResult.ExitLoop
      }
      is TwoCaptchaResult.SolverBadApiUrl -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        val exception = PostingException("Cannot use captcha solver, bad solver url: " +
          "\'${twoCaptchaResult.url}\'. Check the base url in the captcha solver settings. " +
          "Solver='${twoCaptchaResult.solverName}'")

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(exception)
        )

        return AntiCaptchaServiceResult.ExitLoop
      }
      is TwoCaptchaResult.BadBalance -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(
            throwable = PostingException("BadBalance. balance=${twoCaptchaResult.balance}")
          )
        )

        return AntiCaptchaServiceResult.ExitLoop
      }
      is TwoCaptchaResult.BadBalanceResponse -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        val errorCodeString = twoCaptchaResult.twoCaptchaBalanceResponse.response.requestRaw
        val errorText = twoCaptchaResult.twoCaptchaBalanceResponse.response.errorTextOrDefault()

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(
            throwable = PostingException("BadBalanceResponse. Error=$errorCodeString, Error text=${errorText}")
          )
        )

        return AntiCaptchaServiceResult.ExitLoop
      }
      is TwoCaptchaResult.BadSolveCaptchaResponse -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        val errorCodeString = twoCaptchaResult.twoCaptchaSolveCaptchaResponse.response.requestRaw
        val errorText = twoCaptchaResult.twoCaptchaSolveCaptchaResponse.response.errorTextOrDefault()

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(
            throwable = PostingException("BadSolveCaptchaResponse. Error=$errorCodeString, Error text=${errorText}")
          )
        )

        return AntiCaptchaServiceResult.ExitLoop
      }
      is TwoCaptchaResult.BadCheckCaptchaSolutionResponse -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        val errorCodeString = twoCaptchaResult.twoCaptchaCheckSolutionResponse.response.requestRaw
        val errorText = twoCaptchaResult.twoCaptchaCheckSolutionResponse.response.errorTextOrDefault()

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(
            throwable = PostingException("BadCheckCaptchaSolutionResponse. Error=$errorCodeString, Error text=${errorText}")
          )
        )

        return AntiCaptchaServiceResult.ExitLoop
      }
      is TwoCaptchaResult.UnknownError -> {
        Logger.e(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")

        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(
            throwable = PostingException("UnknownError, message=${twoCaptchaResult.message}")
          )
        )

        return AntiCaptchaServiceResult.ExitLoop
      }
      is TwoCaptchaResult.WaitingForSolution -> {
        Logger.d(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")
        return AntiCaptchaServiceResult.WaitNextIteration(twoCaptchaResult.waitTime)
      }
      is TwoCaptchaResult.CaptchaNotNeeded,
      is TwoCaptchaResult.NotSupported,
      is TwoCaptchaResult.SolverDisabled -> {
        Logger.d(TAG, "processAntiCaptchaService($chanDescriptor) -> $twoCaptchaResult")
        return AntiCaptchaServiceResult.AlreadyHaveSolution(prevCaptchaSolution)
      }
      is TwoCaptchaResult.Solution -> {
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

          return AntiCaptchaServiceResult.ExitLoop
        }

        Logger.d(TAG, "Got solution: \'${StringUtils.formatToken(solutionResponse.response.requestRaw)}\'")
        val solution = CaptchaSolution.SimpleTokenSolution(solutionResponse.response.requestRaw)

        return AntiCaptchaServiceResult.Solution(solution)
      }
    }
  }

  /**
   * A terminal event is emitted when we cannot proceed further without user interaction. When this
   * event is emitted we will quit the postQueueLoop. The postingStatus will be set to
   * PostingStatus.AfterPosting. Captcha will be reset.
   * */
  private suspend fun emitTerminalEvent(
    chanDescriptor: ChanDescriptor,
    postResult: PostResult
  ) {
    Logger.d(TAG, "emitTerminalEvent($chanDescriptor, $postResult)")

    readReplyInfo(chanDescriptor) {
      updateStatus(PostingStatus.AfterPosting(chanDescriptor, postResult))
    }

    replyManager.readReply(chanDescriptor) { reply ->
      reply.resetCaptcha()
    }

    val lastReplyRepository = _lastReplyRepository.get()

    when (val result = postResult) {
      PostResult.Canceled -> {
        updateChildNotification(chanDescriptor, ChildNotificationInfo.Status.Canceled)
        lastReplyRepository.onPostAttemptFinished(
          chanDescriptor = chanDescriptor,
          postedSuccessfully = false
        )
      }
      is PostResult.Error -> {
        val errorMessage = result.throwable.errorMessageOrClassName()

        updateChildNotification(chanDescriptor, ChildNotificationInfo.Status.Error(errorMessage))
        lastReplyRepository.onPostAttemptFinished(
          chanDescriptor = chanDescriptor,
          postedSuccessfully = false
        )
      }
      is PostResult.Banned -> {
        val errorMessage = when (result.banInfo) {
          is ReplyResponse.BanInfo.Banned -> getString(R.string.post_service_response_probably_banned)
          is ReplyResponse.BanInfo.Warned -> getString(R.string.post_service_response_probably_warned)
        }

        updateChildNotification(chanDescriptor, ChildNotificationInfo.Status.Error(errorMessage))
        lastReplyRepository.onPostAttemptFinished(
          chanDescriptor = chanDescriptor,
          postedSuccessfully = false
        )
      }
      is PostResult.Success -> {
        val replyResponse = result.replyResponse
        if (replyResponse.posted) {
          updateChildNotification(chanDescriptor, ChildNotificationInfo.Status.Posted(chanDescriptor))
          lastReplyRepository.onPostAttemptFinished(
            chanDescriptor = chanDescriptor,
            postedSuccessfully = true
          )
          return
        }

        if (replyResponse.rateLimitInfo != null) {
          val cooldownInfo = replyResponse.rateLimitInfo!!.cooldownInfo

          updateChildNotification(
            chanDescriptor = chanDescriptor,
            status = ChildNotificationInfo.Status.WaitingForSiteRateLimitToPass(
              remainingWaitTimeMs = cooldownInfo.currentPostingCooldownMs,
              boardDescriptor = chanDescriptor.boardDescriptor()
            )
          )

          lastReplyRepository.onPostAttemptFinished(
            chanDescriptor = chanDescriptor,
            postedSuccessfully = false,
            newCooldownInfo = cooldownInfo
          )

          return
        }

        val errorMessage = when {
          replyResponse.errorMessage.isNotNullNorBlank() -> replyResponse.errorMessage!!
          replyResponse.banInfo != null -> {
            when (replyResponse.banInfo!!) {
              is ReplyResponse.BanInfo.Banned -> getString(R.string.post_service_response_probably_banned)
              is ReplyResponse.BanInfo.Warned -> getString(R.string.post_service_response_probably_warned)
            }
          }
          replyResponse.requireAuthentication -> getString(R.string.post_service_response_authentication_required)
          replyResponse.additionalResponseData !is ReplyResponse.AdditionalResponseData.NoOp -> {
            when (replyResponse.additionalResponseData) {
              ReplyResponse.AdditionalResponseData.NoOp -> "NoOp"
            }
          }
          else -> getString(R.string.post_service_response_unknown_error)
        }

        updateChildNotification(chanDescriptor, ChildNotificationInfo.Status.Error(errorMessage))
        lastReplyRepository.onPostAttemptFinished(
          chanDescriptor = chanDescriptor,
          postedSuccessfully = false
        )
      }
    }
  }

  /**
   * Successfully managed to post. Do the cleanup stuff, bookmark the thread if needed and reset the
   * reply.
   * */
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

    val databaseId = chanPostRepository.createEmptyThreadIfNotExists(newThreadDescriptor)
      .peekError { error ->
        Logger.e(TAG, "Failed to create empty thread in the database for $newThreadDescriptor", error)
      }
      .valueOrNull() ?: -1L

    val createThreadSuccess = databaseId >= 0L

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

      val comment = replyManager.readReply(prevChanDescriptor) { prevReply -> prevReply.comment }
      val subject = chanThreadManager.getChanThread(responsePostDescriptor.threadDescriptor())
        ?.getOriginalPost()
        ?.let { chanOriginalPost -> ChanPostUtils.getTitle(chanOriginalPost, prevChanDescriptor) }

      val chanSavedReply = ChanSavedReply(
        postDescriptor = responsePostDescriptor,
        comment = comment,
        subject = subject,
        password = password
      )

      savedReplyManager.saveReply(chanSavedReply)
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

    replyManager.deleteCachedDraftFromDisk(prevChanDescriptor)

    if (ChanSettings.donateSolvedCaptchaForGreaterGood.get() == ChanSettings.NullableBoolean.True) {
      replyResponse.captchaSolution?.let { captchaSolution ->
        when (captchaSolution) {
          is CaptchaSolution.ChallengeWithSolution -> {
            captchaDonation.get().donateCaptcha(prevChanDescriptor, captchaSolution)
          }

          is CaptchaSolution.SimpleTokenSolution -> {
            // no-op
          }
        }
      }
    }
  }

  private fun bookmarkThread(
    newDescriptor: ChanDescriptor,
    threadNo: Long
  ) {
    if (newDescriptor is ChanDescriptor.ThreadDescriptor) {
      if (bookmarksManager.contains(newDescriptor)) {
        return
      }

      val thread = chanThreadManager.getChanThread(newDescriptor)
      val bookmarkThreadDescriptor = newDescriptor.toThreadDescriptor(threadNo)

      // Reply in an existing thread
      val createBookmarkResult = if (thread != null) {
        val originalPost = thread.getOriginalPost()
        val title = ChanPostUtils.getTitle(originalPost, newDescriptor)
        val thumbnail = originalPost?.firstImage()?.actualThumbnailUrl

        bookmarksManager.createBookmark(bookmarkThreadDescriptor, title, thumbnail)
      } else {
        bookmarksManager.createBookmark(bookmarkThreadDescriptor)
      }

      if (!createBookmarkResult) {
        Logger.e(TAG, "bookmarkThread() Failed to create bookmark with newThreadDescriptor=$newDescriptor, " +
          "threadDescriptor: $bookmarkThreadDescriptor, newThreadDescriptor=$newDescriptor, " +
          "threadNo=$threadNo")
      }

      return
    }

    if (newDescriptor is ChanDescriptor.CatalogDescriptor) {
      val bookmarkThreadDescriptor = ChanDescriptor.ThreadDescriptor.create(
        boardDescriptor = newDescriptor.boardDescriptor(),
        threadNo = threadNo
      )

      if (bookmarksManager.contains(bookmarkThreadDescriptor)) {
        return
      }

      // New thread
      val title = replyManager.readReply(newDescriptor) { reply ->
        PostHelper.getTitle(reply)
      }

      val createBookmarkResult = bookmarksManager.createBookmark(
        bookmarkThreadDescriptor,
        title
      )

      if (!createBookmarkResult) {
        Logger.e(TAG, "bookmarkThread() Failed to create bookmark with newThreadDescriptor=$newDescriptor, " +
            "threadDescriptor: $bookmarkThreadDescriptor, newThreadDescriptor=$newDescriptor, " +
            "threadNo=$threadNo")
      }

      return
    }

    // newThreadDescriptor is ChanDescriptor.CompositeCatalogDescriptor
    // no-op
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

  private suspend fun <T : Any?> readReplyInfo(
    replyDescriptor: ChanDescriptor,
    func: ReplyInfo.() -> T
  ): T {
    return mutex.withLock {
      activeReplyDescriptors[replyDescriptor]
        ?.let { replyInfo -> func(replyInfo) }
        ?: throw CancellationException("Canceled")
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

  /**
   * We need to "consume" the AfterPosting event (meaning replace it with Attached event) so
   * that we don't show "Posted successfully" every time we open a thread where we have
   * recently posted.
   * */
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

  suspend fun lastUnsuccessfulReplyResponseOrNull(replyDescriptor: ChanDescriptor): ReplyResponse? {
    return mutex.withLock {
      return@withLock activeReplyDescriptors[replyDescriptor]?.lastUnsuccessfulReplyResponse
    }
  }

  sealed class ActualPostResult {
    object Posted : ActualPostResult() {
      override fun toString(): String {
        return "Posted"
      }
    }
    object FailedToPost : ActualPostResult() {
      override fun toString(): String {
        return "FailedToPost"
      }
    }
    data class RateLimited(val timeMs: Long) : ActualPostResult()
  }

  companion object {
    private const val TAG = "PostingServiceDelegate"
    private const val MAX_ATTEMPTS = 30
    private const val POST_LOOP_DELAY_MS = 1_000L
    private const val POST_LOOP_DELAY_MAX_MS = 10_000L
    private const val MAX_AUTO_SOLVED_CAPTCHAS_COUNT = 10

    private val MAX_POST_QUEUE_TIME_MS = TimeUnit.MINUTES.toMillis(30)
  }

}