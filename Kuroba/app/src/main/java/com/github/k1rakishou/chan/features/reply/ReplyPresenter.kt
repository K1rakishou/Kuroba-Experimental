/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.features.reply

import android.content.Context
import android.text.Editable
import android.text.TextUtils
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.helper.CommentEditingHistory
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.repository.BoardFlagInfoRepository
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.features.posting.PostResult
import com.github.k1rakishou.chan.features.posting.PostingService
import com.github.k1rakishou.chan.features.posting.PostingServiceDelegate
import com.github.k1rakishou.chan.features.posting.PostingStatus
import com.github.k1rakishou.chan.features.posting.solvers.two_captcha.TwoCaptchaSolver
import com.github.k1rakishou.chan.features.reply.floating_message_actions.Chan4OpenBannedUrlClickAction
import com.github.k1rakishou.chan.features.reply.floating_message_actions.IFloatingReplyMessageClickAction
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.controller.CaptchaContainerController
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.OptionsSetting
import dagger.Lazy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class ReplyPresenter @Inject constructor(
  private val _replyManager: Lazy<ReplyManager>,
  private val _siteManager: Lazy<SiteManager>,
  private val _boardManager: Lazy<BoardManager>,
  private val _boardFlagInfoRepository: Lazy<BoardFlagInfoRepository>,
  private val _postingServiceDelegate: Lazy<PostingServiceDelegate>,
  private val _twoCaptchaSolver: Lazy<TwoCaptchaSolver>,
  private val _dialogFactory: Lazy<DialogFactory>,
  private val _globalWindowInsetsManager: Lazy<GlobalWindowInsetsManager>,
  private val _captchaHolder: Lazy<CaptchaHolder>,
  private val _themeEngine: Lazy<ThemeEngine>
) : CoroutineScope,
  CommentEditingHistory.CommentEditingHistoryListener {

  private var currentChanDescriptor: ChanDescriptor? = null
  private var previewOpen = false
  private var floatingReplyMessageClickAction: IFloatingReplyMessageClickAction? = null
  private var postingStatusUpdatesJob: Job? = null
  private var postingCheckLastErrorJob: Job? = null

  private val job = SupervisorJob()
  private val commentEditingHistory = CommentEditingHistory(this)

  val floatingReplyMessageHasClickAction: Boolean
    get() = floatingReplyMessageClickAction != null

  private val replyManager: ReplyManager
    get() = _replyManager.get()
  private val siteManager: SiteManager
    get() = _siteManager.get()
  private val boardManager: BoardManager
    get() = _boardManager.get()
  private val boardFlagInfoRepository: BoardFlagInfoRepository
    get() = _boardFlagInfoRepository.get()
  private val postingServiceDelegate: PostingServiceDelegate
    get() = _postingServiceDelegate.get()
  private val twoCaptchaSolver: TwoCaptchaSolver
    get() = _twoCaptchaSolver.get()
  private val dialogFactory: DialogFactory
    get() = _dialogFactory.get()
  private val globalWindowInsetsManager: GlobalWindowInsetsManager
    get() = _globalWindowInsetsManager.get()
  private val captchaHolder: CaptchaHolder
    get() = _captchaHolder.get()
  private val themeEngine: ThemeEngine
    get() = _themeEngine.get()

  private lateinit var callback: ReplyPresenterCallback
  private lateinit var context: Context

  private val highlightQuotesDebouncer = Debouncer(false)
  private val updatePostTextSpansDebouncer = Debouncer(false)
  private val rememberDraftDebounder = Debouncer(false)

  var isExpanded = false
    private set

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("ReplyPresenter")

  fun create(context: Context, callback: ReplyPresenterCallback) {
    this.context = context
    this.callback = callback
  }

  fun destroy() {
    job.cancelChildren()
  }

  suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor): Boolean {
    if (currentChanDescriptor != null) {
      unbindChanDescriptor()
    }

    this.currentChanDescriptor = chanDescriptor

    callback.bindReplyImages(chanDescriptor)

    val stringId = if (chanDescriptor.isThreadDescriptor()) {
      R.string.reply_comment_thread
    } else {
      R.string.reply_comment_board
    }

    replyManager.awaitUntilFilesAreLoaded()

    val thisBoard = getBoardByCurrentDescriptorOrNull()
    if (thisBoard != null) {
      callback.loadDraftIntoViews(chanDescriptor)
      callback.showCommentCounter(thisBoard.maxCommentChars > 0)
    }

    callback.setCommentHint(getString(stringId))

    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      postingStatusUpdatesJob?.cancel()
      postingStatusUpdatesJob = null
    } else {
      postingStatusUpdatesJob = launch {
        postingServiceDelegate.listenForPostingStatusUpdates(chanDescriptor)
          .collect { status -> processPostingStatusUpdates(status, chanDescriptor) }
      }
    }

    callback.setInputPage()
    return true
  }

  private fun getBoardByCurrentDescriptorOrNull(): ChanBoard? {
    val descriptor = currentChanDescriptor
      ?: return null

    if (descriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return null
    }

    return boardManager.byBoardDescriptor(descriptor.boardDescriptor())
  }

  private fun getSiteByCurrentDescriptorOrNull(): Site? {
    val descriptor = currentChanDescriptor
      ?: return null

    if (descriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return null
    }

    return siteManager.bySiteDescriptor(descriptor.siteDescriptor())
  }

  private suspend fun processPostingStatusUpdates(
    status: PostingStatus,
    chanDescriptor: ChanDescriptor
  ) {
    withContext(Dispatchers.Main) {
      if (status.chanDescriptor != currentChanDescriptor) {
        // The user may open another thread while the reply is being uploaded so we need to check
        // whether this even actually belongs to this catalog/thread.
        return@withContext
      }

      when (status) {
        is PostingStatus.Attached -> {
          Logger.d(TAG, "processPostingStatusUpdates($chanDescriptor) -> ${status.javaClass.simpleName}")
        }
        is PostingStatus.Enqueued,
        is PostingStatus.WaitingForSiteRateLimitToPass,
        is PostingStatus.WaitingForAdditionalService,
        is PostingStatus.BeforePosting -> {
          Logger.d(TAG, "processPostingStatusUpdates($chanDescriptor) -> ${status.javaClass.simpleName}")

          if (::callback.isInitialized) {
            callback.enableOrDisableReplyLayout()
          }
        }
        is PostingStatus.UploadingProgress,
        is PostingStatus.Uploaded -> {
          // no-op
        }
        is PostingStatus.AfterPosting -> {
          Logger.d(TAG, "processPostingStatusUpdates($chanDescriptor) -> " +
            "${status.javaClass.simpleName}, status.postResult=${status.postResult}")

          if (::callback.isInitialized) {
            callback.enableOrDisableReplyLayout()
          }

          when (val postResult = status.postResult) {
            PostResult.Canceled -> {
              onPostError(CancellationException("Canceled"))
            }
            is PostResult.Error -> {
              onPostError(postResult.throwable)
            }
            is PostResult.Success -> {
              onPostComplete(
                chanDescriptor = status.chanDescriptor,
                replyResponse = postResult.replyResponse,
                replyMode = postResult.replyMode,
                retrying = postResult.retrying
              )
            }
          }

          Logger.d(TAG, "processPostingStatusUpdates($chanDescriptor) consumeTerminalEvent(${status.chanDescriptor})")
          postingServiceDelegate.consumeTerminalEvent(status.chanDescriptor)
        }
      }
    }
  }

  fun unbindReplyImages() {
    currentChanDescriptor?.let { chanDescriptor ->
      callback.unbindReplyImages(chanDescriptor)
    }
  }

  fun unbindChanDescriptor() {
    closeAll()

    postingStatusUpdatesJob?.cancel()
    postingStatusUpdatesJob = null

    currentChanDescriptor = null
  }

  fun isCatalogReplyLayout(): Boolean? {
    if (currentChanDescriptor == null) {
      return null
    }

    return currentChanDescriptor is ChanDescriptor.ICatalogDescriptor
  }

  fun onOpen(open: Boolean) {
    if (!open) {
      return
    }

    processTextFormattingButtons()

    callback.focusComment()

    postingCheckLastErrorJob?.cancel()
    postingCheckLastErrorJob = null

    postingCheckLastErrorJob = launch {
      val descriptor = currentChanDescriptor
        ?: return@launch

      val lastUnsuccessfulReplyResponse = postingServiceDelegate.lastUnsuccessfulReplyResponseOrNull(descriptor)
      if (lastUnsuccessfulReplyResponse == null || lastUnsuccessfulReplyResponse.posted) {
        return@launch
      }

      onPostCompleteUnsuccessful(lastUnsuccessfulReplyResponse)
    }
  }

  private fun processTextFormattingButtons() {
    callback.openCommentQuoteButton(true)

    val chanBoard = getBoardByCurrentDescriptorOrNull()
      ?: return

    val is4chan = chanBoard.boardDescriptor.siteDescriptor.is4chan()
    val isDvach = chanBoard.boardDescriptor.siteDescriptor.isDvach()

    if (isDvach || chanBoard.spoilers) {
      callback.openCommentSpoilerButton(true)
    }

    if (is4chan && chanBoard.boardCode() == "g") {
      callback.openCommentCodeButton(true)
    }

    if (is4chan && chanBoard.boardCode() == "sci") {
      callback.openCommentEqnButton(true)
      callback.openCommentMathButton(true)
    }

    if (is4chan && (chanBoard.boardCode() == "jp" || chanBoard.boardCode() == "vip")) {
      callback.openCommentSJISButton(true)
    }

    if (isDvach) {
      callback.openCommentBoldButton(true)
      callback.openCommentItalicButton(true)
      callback.openCommentUnderlineButton(true)
      callback.openCommentStrikeThroughButton(true)
    }
  }

  fun onBack(): Boolean {
    if (isExpanded) {
      onMoreClicked()
      return true
    }

    return false
  }

  fun expandOrCollapse(expand: Boolean): Boolean {
    if (this.isExpanded == expand) {
      return false
    }

    onMoreClicked()
    return true
  }

  fun onMoreClicked() {
    this.isExpanded = this.isExpanded.not()

    callback.setExpanded(expanded = isExpanded, isCleaningUp = false)
    callback.openNameOptions(isExpanded)

    if (currentChanDescriptor?.isCatalogDescriptor() == true) {
      callback.openSubject(isExpanded)
    }

    getBoardByCurrentDescriptorOrNull()?.let { chanBoard ->
      if (isExpanded && chanBoard.countryFlags) {
        boardFlagInfoRepository.getLastUsedFlagKey(chanBoard.boardDescriptor)
          ?.let { lastUsedFlagKey -> callback.openFlag(lastUsedFlagKey) }
      } else {
        callback.hideFlag()
      }
    }
  }

  fun onAuthenticateClicked() {
    val descriptor = currentChanDescriptor
      ?: return

    val replyMode = siteManager.bySiteDescriptor(descriptor.siteDescriptor())
      ?.requireSettingBySettingId<OptionsSetting<ReplyMode>>(SiteSetting.SiteSettingId.LastUsedReplyMode)
      ?.get()
      ?: return

    showCaptcha(
      chanDescriptor = descriptor,
      replyMode = replyMode,
      autoReply = false,
      afterPostingAttempt = false
    )
  }

  fun showCaptcha(
    chanDescriptor: ChanDescriptor,
    replyMode: ReplyMode,
    autoReply: Boolean,
    afterPostingAttempt: Boolean,
    onFinished: ((Boolean) -> Unit)? = null
  ) {
    val controller = CaptchaContainerController(
      context = context,
      afterPostingAttempt = afterPostingAttempt,
      chanDescriptor = chanDescriptor
    ) { authenticationResult ->
      when (authenticationResult) {
        is CaptchaContainerController.AuthenticationResult.Failure -> {
          Logger.e(TAG, "CaptchaContainerController failure, ${authenticationResult.throwable}")

          dialogFactory.createSimpleInformationDialog(
            context = context,
            titleText = getString(R.string.reply_captcha_failure),
            descriptionText = authenticationResult.throwable.errorMessageOrClassName(),
            onDismissListener = {
              if (!autoReply) {
                onFinished?.invoke(false)
              }
            }
          )
        }
        is CaptchaContainerController.AuthenticationResult.Success -> {
          Logger.d(TAG, "CaptchaContainerController success")

          if (autoReply) {
            makeSubmitCall(chanDescriptor = chanDescriptor, replyMode = replyMode)
          } else {
            onFinished?.invoke(true)
          }
        }
      }
    }

    callback.hideKeyboard()

    launch {
      // Wait a little bit for the keyboard to get hidden
      delay(100)

      callback.presentController(controller)
    }
  }

  suspend fun onSubmitClicked(longClicked: Boolean) {
    val chanDescriptor = currentChanDescriptor
      ?: return

    if (!isReplyLayoutEnabled()) {
      postingServiceDelegate.cancel(chanDescriptor)
      return
    }

    val prevReplyMode = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
      ?.requireSettingBySettingId<OptionsSetting<ReplyMode>>(SiteSetting.SiteSettingId.LastUsedReplyMode)
      ?.get()
      ?: ReplyMode.Unknown

    if (longClicked || prevReplyMode == ReplyMode.Unknown) {
      showReplyOptions(chanDescriptor, prevReplyMode)
      return
    }

    if (!onPrepareToSubmit(false)) {
      return
    }

    submitOrAuthenticate(
      chanDescriptor = chanDescriptor,
      replyMode = prevReplyMode
    )
  }

  private fun showReplyOptions(chanDescriptor: ChanDescriptor, prevReplyMode: ReplyMode) {
    val menuItems = mutableListOf<FloatingListMenuItem>()
    val availableReplyModes = buildReplyModeOptions(chanDescriptor, prevReplyMode)

    val ignoreReplyCooldowns = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
      ?.requireSettingBySettingId<BooleanSetting>(SiteSetting.SiteSettingId.IgnoreReplyCooldowns)
    val lastUsedReplyMode = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
      ?.requireSettingBySettingId<OptionsSetting<ReplyMode>>(SiteSetting.SiteSettingId.LastUsedReplyMode)

    menuItems += FloatingListMenuItem(
      key = ACTION_REPLY_MODES,
      name = "Reply modes",
      more = availableReplyModes
    )
    menuItems += CheckableFloatingListMenuItem(
      key = ACTION_IGNORE_REPLY_COOLDOWNS,
      name = "Ignore reply cooldowns",
      isCurrentlySelected = ignoreReplyCooldowns?.get() == true
    )

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = menuItems,
      itemClickListener = { clickedItem ->
        if (clickedItem.key is Int) {
          when (clickedItem.key) {
            ACTION_REPLY_MODES -> {
              // ???
            }
            ACTION_IGNORE_REPLY_COOLDOWNS -> {
              ignoreReplyCooldowns?.toggle()
            }
          }
        } else if (clickedItem.key is ReplyMode) {
          val replyMode = clickedItem.key as? ReplyMode
            ?: return@FloatingListMenuController

          lastUsedReplyMode?.set(replyMode)

          callback.updateCaptchaContainerVisibility()
        }
      }
    )

    callback.presentController(floatingListMenuController)
  }

  private fun buildReplyModeOptions(
    chanDescriptor: ChanDescriptor,
    prevReplyMode: ReplyMode
  ): MutableList<FloatingListMenuItem> {
    val availableReplyModes = mutableListOf<FloatingListMenuItem>()
    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    val groupId = "reply_mode"

    if (site?.actions()?.postAuthenticate()?.type != SiteAuthentication.Type.NONE) {
      availableReplyModes += CheckableFloatingListMenuItem(
        key = ReplyMode.ReplyModeSolveCaptchaManually,
        name = getString(R.string.reply_mode_solve_captcha_and_post),
        groupId = groupId,
        isCurrentlySelected = prevReplyMode == ReplyMode.ReplyModeSolveCaptchaManually
      )
    }

    availableReplyModes += CheckableFloatingListMenuItem(
      key = ReplyMode.ReplyModeSendWithoutCaptcha,
      name = getString(R.string.reply_mode_attempt_post_without_captcha),
      groupId = groupId,
      isCurrentlySelected = prevReplyMode == ReplyMode.ReplyModeSendWithoutCaptcha
    )

    if (twoCaptchaSolver.isSiteCurrentCaptchaTypeSupported(chanDescriptor.siteDescriptor())
      && twoCaptchaSolver.isLoggedIn
    ) {
      availableReplyModes += CheckableFloatingListMenuItem(
        key = ReplyMode.ReplyModeSolveCaptchaAuto,
        name = getString(R.string.reply_mode_post_with_captcha_solver),
        groupId = groupId,
        isCurrentlySelected = prevReplyMode == ReplyMode.ReplyModeSolveCaptchaAuto
      )
    }

    if (site?.actions()?.isLoggedIn() == true) {
      availableReplyModes += CheckableFloatingListMenuItem(
        key = ReplyMode.ReplyModeUsePasscode,
        name = getString(R.string.reply_mode_post_with_passcode),
        groupId = groupId,
        isCurrentlySelected = prevReplyMode == ReplyMode.ReplyModeUsePasscode
      )
    }

    return availableReplyModes
  }

  private fun submitOrAuthenticate(chanDescriptor: ChanDescriptor, replyMode: ReplyMode) {
    if (replyMode == ReplyMode.ReplyModeSolveCaptchaManually && !captchaHolder.hasSolution()) {
      showCaptcha(
        chanDescriptor = chanDescriptor,
        replyMode = replyMode,
        autoReply = true,
        afterPostingAttempt = false
      )

      return
    }

    makeSubmitCall(chanDescriptor = chanDescriptor, replyMode = replyMode)
  }

  private fun onPrepareToSubmit(isAuthenticateOnly: Boolean): Boolean {
    val chanDescriptor = currentChanDescriptor
      ?: return false

    val hasSelectedFiles = replyManager.hasSelectedFiles()
      .peekError { error -> Logger.e(TAG, "hasSelectedFiles() error", error) }
      .valueOrNull()

    if (hasSelectedFiles == null) {
      callback.openMessage(getString(R.string.reply_failed_to_prepare_reply))
      return false
    }

    return replyManager.readReply(chanDescriptor) { reply ->
      callback.loadViewsIntoDraft(chanDescriptor)

      if (!isAuthenticateOnly && !hasSelectedFiles && reply.isCommentEmpty()) {
        callback.openMessage(getString(R.string.reply_comment_empty))
        return@readReply false
      }

      reply.resetCaptcha()
      return@readReply true
    }
  }

  fun updateInitialCommentEditingHistory(commentInputState: CommentEditingHistory.CommentInputState) {
    commentEditingHistory.updateInitialCommentEditingHistory(commentInputState)
  }

  fun updateCommentEditingHistory(commentInputState: CommentEditingHistory.CommentInputState) {
    commentEditingHistory.updateCommentEditingHistory(commentInputState)
  }

  fun onRevertChangeButtonClicked() {
    commentEditingHistory.onRevertChangeButtonClicked()
  }

  fun clearCommentChangeHistory() {
    commentEditingHistory.clear()
  }

  override fun updateRevertChangeButtonVisibility(isBufferEmpty: Boolean) {
    callback.updateRevertChangeButtonVisibility(isBufferEmpty)
  }

  override fun restoreComment(prevCommentInputState: CommentEditingHistory.CommentInputState) {
    callback.restoreComment(prevCommentInputState)
  }

  fun updateCommentCounter(text: CharSequence?) {
    if (text == null) {
      return
    }

    val chanBoard = getBoardByCurrentDescriptorOrNull()
    if (chanBoard == null || chanBoard.maxCommentChars < 0) {
      return
    }

    val length = text.toString().toByteArray(UTF_8).size

    callback.updateCommentCount(
      length,
      chanBoard.maxCommentChars,
      length > chanBoard.maxCommentChars
    )
  }

  fun loadViewsIntoDraft() {
    rememberDraftDebounder.post({
      val chanDescriptor = currentChanDescriptor
        ?: return@post

      callback.loadViewsIntoDraft(chanDescriptor)
    }, 100)
  }

  fun quote(post: ChanPost, withText: Boolean) {
    val comment = if (withText) {
      post.postComment.comment().toString()
    } else {
      null
    }

    handleQuote(post.postDescriptor, comment)
  }

  fun quote(postDescriptor: PostDescriptor, text: CharSequence) {
    handleQuote(postDescriptor, text.toString())
  }

  private fun handleQuote(postDescriptor: PostDescriptor, textQuote: String?) {
    val chanDescriptor = currentChanDescriptor
      ?: return

    callback.loadViewsIntoDraft(chanDescriptor)
    val selectStart = callback.selectionStart

    val resultLength = replyManager.readReply(chanDescriptor) { reply ->
      try {
        return@readReply reply.handleQuote(selectStart, postDescriptor.postNo, textQuote)
      } catch (error: Throwable) {
        throw RuntimeException("currentReplyComment='${reply.comment}', selectStart=${selectStart}, " +
          "postNo=${postDescriptor.postNo}, textQuote='$textQuote'")
      }
    }

    callback.loadDraftIntoViews(chanDescriptor)
    callback.adjustSelection(selectStart, resultLength)
    highlightQuotes()
  }

  private fun closeAll() {
    isExpanded = false
    previewOpen = false

    commentEditingHistory.clear()

    callback.highlightPosts(emptySet())
    callback.openMessage(null)
    callback.setExpanded(expanded = false, isCleaningUp = true)
    callback.openSubject(false)
    callback.hideFlag()
    callback.openNameOptions(false)
    callback.updateRevertChangeButtonVisibility(isBufferEmpty = true)
    removeFloatingReplyMessageClickAction()
  }

  private fun makeSubmitCall(
    chanDescriptor: ChanDescriptor,
    replyMode: ReplyMode,
    retrying: Boolean = false
  ) {
    closeAll()
    PostingService.enqueueReplyChanDescriptor(context, chanDescriptor, replyMode, retrying)
  }

  private fun onPostError(exception: Throwable) {
    BackgroundUtils.ensureMainThread()

    if (exception is CancellationException) {
      Logger.e(TAG, "onPostError: Canceled")
    } else {
      Logger.e(TAG, "onPostError", exception)
    }

    val errorMessage = getString(R.string.reply_error_message, exception.errorMessageOrClassName())
    callback.openMessage(errorMessage)
  }

  private suspend fun onPostComplete(
    chanDescriptor: ChanDescriptor,
    replyResponse: ReplyResponse,
    replyMode: ReplyMode,
    retrying: Boolean
  ) {
    BackgroundUtils.ensureMainThread()

    when {
      replyResponse.posted -> {
        Logger.d(TAG, "onPostComplete() posted==true replyResponse=$replyResponse")
        onPostedSuccessfully(prevChanDescriptor = chanDescriptor, replyResponse = replyResponse)
      }
      replyResponse.requireAuthentication -> {
        Logger.d(TAG, "onPostComplete() requireAuthentication==true replyResponse=$replyResponse")
        onPostCompleteUnsuccessful(replyResponse, additionalErrorMessage = null)

        showCaptcha(
          chanDescriptor = chanDescriptor,
          replyMode = replyMode,
          autoReply = true,
          afterPostingAttempt = true
        )
      }
      else -> {
        Logger.d(TAG, "onPostComplete() else branch replyResponse=$replyResponse, retrying=$retrying")

        if (retrying) {
          // To avoid infinite cycles
          onPostCompleteUnsuccessful(replyResponse, additionalErrorMessage = null)
          return
        }

        when (replyResponse.additionalResponseData) {
          ReplyResponse.AdditionalResponseData.NoOp -> {
            onPostCompleteUnsuccessful(replyResponse, additionalErrorMessage = null)
          }
        }
      }
    }
  }

  private fun onPostCompleteUnsuccessful(replyResponse: ReplyResponse, additionalErrorMessage: String? = null) {
    updateFloatingReplyMessageClickAction(replyResponse)

    val errorMessage = when {
      additionalErrorMessage != null -> {
        getString(R.string.reply_error_message, additionalErrorMessage)
      }
      replyResponse.errorMessage != null -> {
        getString(R.string.reply_error_message, replyResponse.errorMessageShort)
      }
      replyResponse.requireAuthentication -> {
        val errorMessage = if (TextUtils.isEmpty(replyResponse.errorMessage)) {
          getString(R.string.reply_error_authentication_required)
        } else {
          replyResponse.errorMessageShort
        }

        getString(R.string.reply_error_message, errorMessage)
      }
      else -> getString(R.string.reply_error_message, replyResponse)
    }

    Logger.e(TAG, "onPostCompleteUnsuccessful() error: $errorMessage")
    callback.openMessage(errorMessage)
  }

  private fun updateFloatingReplyMessageClickAction(replyResponse: ReplyResponse) {
    if (replyResponse.siteDescriptor?.is4chan() == true && replyResponse.probablyBanned) {
      this.floatingReplyMessageClickAction = Chan4OpenBannedUrlClickAction()
      return
    }

    removeFloatingReplyMessageClickAction()
  }

  private suspend fun onPostedSuccessfully(
    prevChanDescriptor: ChanDescriptor,
    replyResponse: ReplyResponse
  ) {
    val siteDescriptor = replyResponse.siteDescriptor

    Logger.d(TAG, "prevChanDescriptor() prevChanDescriptor=$prevChanDescriptor, " +
      "siteDescriptor=$siteDescriptor, replyResponse=$replyResponse")

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

    closeAll()
    highlightQuotes()

    callback.loadDraftIntoViews(newThreadDescriptor)
    callback.onPosted()

    if (prevChanDescriptor.isCatalogDescriptor()) {
      callback.showThread(newThreadDescriptor)
    }
  }

  fun highlightQuotes() {
    highlightQuotesDebouncer.post({
      val chanDescriptor = currentChanDescriptor
        ?: return@post

      val matcher = replyManager.readReply(chanDescriptor) { reply ->
        return@readReply QUOTE_PATTERN.matcher(reply.comment)
      }

      // Find all occurrences of >>\d+ with start and end between selectionStart
      val selectedQuotes = mutableSetOf<PostDescriptor>()

      while (matcher.find()) {
        val quote = matcher.group().substring(2)
        val postNo = quote.toLongOrNull()
          ?: continue

        if (postNo <= 0) {
          continue
        }

        selectedQuotes += PostDescriptor.create(chanDescriptor, postNo)
      }

      callback.highlightPosts(selectedQuotes)
    }, 250)
  }

  fun executeFloatingReplyMessageClickAction() {
    floatingReplyMessageClickAction?.execute()
    floatingReplyMessageClickAction = null
  }

  fun removeFloatingReplyMessageClickAction() {
    callback.hideReplyInputErrorMessage()
    floatingReplyMessageClickAction = null
  }

  suspend fun isReplyLayoutEnabled(): Boolean {
    val descriptor = currentChanDescriptor
      ?: return true

    return postingServiceDelegate.isReplyCurrentlyInProgress(descriptor)
  }

  fun updateSpans(commentText: Editable) {
    updatePostTextSpansDebouncer.post({
      ReplyCommentHelper.processReplyCommentHighlight(themeEngine, commentText)
    }, 125)
  }

  fun onReplyInputErrorMessageClicked() {
    val descriptor = currentChanDescriptor
      ?: return

    launch { postingServiceDelegate.cancel(descriptor) }
  }

  interface ReplyPresenterCallback {
    val chanDescriptor: ChanDescriptor?
    val selectionStart: Int

    fun isReplyLayoutOpened(): Boolean
    suspend fun enableOrDisableReplyLayout()
    fun loadViewsIntoDraft(chanDescriptor: ChanDescriptor)
    fun loadDraftIntoViews(chanDescriptor: ChanDescriptor)
    fun adjustSelection(start: Int, amount: Int)
    fun setInputPage()
    fun openMessage(message: String?)
    fun openMessage(message: String?, hideDelayMs: Int)
    fun openOrCloseReply(open: Boolean)
    fun onPosted()
    fun setCommentHint(hint: String?)
    fun showCommentCounter(show: Boolean)
    fun setExpanded(expanded: Boolean, isCleaningUp: Boolean)
    fun openNameOptions(open: Boolean)
    fun openSubject(open: Boolean)
    fun openFlag(lastUsedFlagKey: String)
    fun hideFlag()
    fun openCommentQuoteButton(open: Boolean)
    fun openCommentSpoilerButton(open: Boolean)
    fun openCommentCodeButton(open: Boolean)
    fun openCommentBoldButton(open: Boolean)
    fun openCommentItalicButton(open: Boolean)
    fun openCommentUnderlineButton(open: Boolean)
    fun openCommentStrikeThroughButton(open: Boolean)
    fun openCommentEqnButton(open: Boolean)
    fun openCommentMathButton(open: Boolean)
    fun openCommentSJISButton(open: Boolean)
    fun hideReplyInputErrorMessage()
    fun updateCommentCount(count: Int, maxCount: Int, over: Boolean)
    fun highlightPosts(postDescriptors: Set<PostDescriptor>)
    fun showThread(threadDescriptor: ChanDescriptor.ThreadDescriptor)
    fun focusComment()
    fun updateRevertChangeButtonVisibility(isBufferEmpty: Boolean)
    fun restoreComment(prevCommentInputState: CommentEditingHistory.CommentInputState)
    suspend fun bindReplyImages(chanDescriptor: ChanDescriptor)
    fun unbindReplyImages(chanDescriptor: ChanDescriptor)
    fun presentController(controller: Controller)
    fun hideKeyboard()
    fun updateCaptchaContainerVisibility()
  }

  companion object {
    private const val TAG = "ReplyPresenter"
    // matches for >>123, >>123 (text), >>>/fit/123
    private val QUOTE_PATTERN = Pattern.compile(">>\\d+")
    private val UTF_8 = StandardCharsets.UTF_8

    private const val ACTION_REPLY_MODES = 0
    private const val ACTION_IGNORE_REPLY_COOLDOWNS = 1
  }

}