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
package com.github.adamantcheese.chan.core.presenter

import android.content.Context
import android.text.TextUtils
import android.widget.Toast
import androidx.exifinterface.media.ExifInterface
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.core.manager.ReplyManager
import com.github.adamantcheese.chan.core.model.ChanThread
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.chan.core.model.orm.SavedReply
import com.github.adamantcheese.chan.core.repository.BoardRepository
import com.github.adamantcheese.chan.core.repository.LastReplyRepository
import com.github.adamantcheese.chan.core.repository.SiteRepository
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.site.Site
import com.github.adamantcheese.chan.core.site.SiteActions
import com.github.adamantcheese.chan.core.site.SiteAuthentication
import com.github.adamantcheese.chan.core.site.http.Reply
import com.github.adamantcheese.chan.core.site.http.ReplyResponse
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutCallback
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutInterface
import com.github.adamantcheese.chan.ui.helper.ImagePickDelegate
import com.github.adamantcheese.chan.ui.helper.ImagePickDelegate.ImagePickCallback
import com.github.adamantcheese.chan.ui.helper.PostHelper
import com.github.adamantcheese.chan.utils.*
import com.github.adamantcheese.chan.utils.PostUtils.getReadableFileSize
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

class ReplyPresenter @Inject constructor(
  private val context: Context,
  private val replyManager: ReplyManager,
  private val databaseManager: DatabaseManager,
  private val lastReplyRepository: LastReplyRepository,
  private val siteRepository: SiteRepository,
  private val boardRepository: BoardRepository,
  private val bookmarksManager: BookmarksManager
) : AuthenticationLayoutCallback, ImagePickCallback, CoroutineScope {

  enum class Page {
    INPUT, AUTHENTICATION, LOADING
  }

  var page = Page.INPUT
    private set

  private var bound = false
  private var chanDescriptor: ChanDescriptor? = null
  private var previewOpen = false
  private var pickingFile = false
  private var selectedQuote = -1

  private lateinit var job: Job
  private lateinit var callback: ReplyPresenterCallback
  private lateinit var draft: Reply
  private lateinit var board: Board
  private lateinit var site: Site

  var isExpanded = false
    private set

  val isAttachedFileSupportedForReencoding: Boolean
    get() = if (!::draft.isInitialized || draft.file == null) {
      false
    } else {
      BitmapUtils.isFileSupportedForReencoding(draft.file)
    }

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("ReplyPresenter")

  fun create(callback: ReplyPresenterCallback) {
    this.callback = callback
  }

  fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
    if (this.chanDescriptor != null) {
      unbindChanDescriptor()
    }

    this.job = SupervisorJob()
    this.bound = true
    this.chanDescriptor = chanDescriptor

    draft = replyManager.getReply(chanDescriptor)

    if (TextUtils.isEmpty(draft.name)) {
      draft.name = ChanSettings.postDefaultName.get()
    }

    val stringId = if (chanDescriptor.isThreadDescriptor()) {
      R.string.reply_comment_thread
    } else {
      R.string.reply_comment_board
    }

    val thisBoard = boardRepository.getFromBoardDescriptor(chanDescriptor.boardDescriptor())
    requireNotNull(thisBoard) { "Couldn't get board by board descriptor: ${chanDescriptor.boardDescriptor()}" }
    this.board = thisBoard

    val thisSite = siteRepository.bySiteDescriptor(chanDescriptor.siteDescriptor())
    requireNotNull(thisSite) { "Couldn't get site by site descriptor: ${chanDescriptor.siteDescriptor()}" }
    this.site = thisSite

    callback.loadDraftIntoViews(draft)
    callback.updateCommentCount(0, board.maxCommentChars, false)
    callback.setCommentHint(AndroidUtils.getString(stringId))
    callback.showCommentCounter(board.maxCommentChars > 0)

    if (draft.file != null) {
      showPreview(draft.fileName, draft.file)
    }

    switchPage(Page.INPUT)
  }

  fun unbindChanDescriptor() {
    bound = false

    if (::job.isInitialized) {
      job.cancel()
    }

    draft.file = null
    draft.fileName = ""

    callback.loadViewsIntoDraft(draft)
    replyManager.putReply(chanDescriptor, draft)

    closeAll()
  }

  fun onOpen(open: Boolean) {
    if (open) {
      callback.focusComment()
    }
  }

  fun onBack(): Boolean {
    when {
      page == Page.LOADING -> {
        return true
      }
      page == Page.AUTHENTICATION -> {
        switchPage(Page.INPUT)
        return true
      }
      isExpanded -> {
        onMoreClicked()
        return true
      }
      else -> return false
    }
  }

  fun onMoreClicked() {
    isExpanded = !isExpanded

    callback.setExpanded(isExpanded)
    callback.openNameOptions(isExpanded)

    if (chanDescriptor!!.isCatalogDescriptor()) {
      callback.openSubject(isExpanded)
    }

    if (previewOpen) {
      callback.openFileName(isExpanded)
      if (board.spoilers) {
        callback.openSpoiler(isExpanded, false)
      }
    }

    val is4chan = board.site is Chan4
    callback.openCommentQuoteButton(isExpanded)

    if (board.spoilers) {
      callback.openCommentSpoilerButton(isExpanded)
    }

    if (is4chan && board.code == "g") {
      callback.openCommentCodeButton(isExpanded)
    }

    if (is4chan && board.code == "sci") {
      callback.openCommentEqnButton(isExpanded)
      callback.openCommentMathButton(isExpanded)
    }

    if (is4chan && (board.code == "jp" || board.code == "vip")) {
      callback.openCommentSJISButton(isExpanded)
    }

    if (is4chan && board.code == "pol") {
      callback.openFlag(isExpanded)
    }
  }

  fun onAttachClicked(longPressed: Boolean) {
    if (pickingFile) {
      return
    }

    if (previewOpen) {
      callback.openPreview(false, null)

      draft.file = null
      draft.fileName = ""

      if (isExpanded) {
        callback.openFileName(false)
        if (board.spoilers) {
          callback.openSpoiler(show = false, setUnchecked = true)
        }
      }

      previewOpen = false
    } else {
      pickingFile = true
      callback.imagePickDelegate.pick(this, longPressed)
    }
  }

  fun onAuthenticateCalled() {
    if (site.actions().postRequiresAuthentication()) {
      if (!onPrepareToSubmit(true)) {
        return
      }

      switchPage(Page.AUTHENTICATION, useV2NoJsCaptcha = true, autoReply = false)
    }
  }

  fun onSubmitClicked(longClicked: Boolean) {
    if (!onPrepareToSubmit(false)) {
      return
    }

    val chanDescriptor = draft.chanDescriptor
      ?: return

    // only 4chan seems to have the post delay, this is a hack for that
    if (!chanDescriptor.siteDescriptor().is4chan() || longClicked) {
      submitOrAuthenticate()
      return
    }

    if (chanDescriptor.isThreadDescriptor()) {
      val timeLeft = lastReplyRepository.getTimeUntilReply(
        chanDescriptor.boardDescriptor(),
        draft.file != null
      )

      if (timeLeft < 0L) {
        submitOrAuthenticate()
      } else {
        val errorMessage = AndroidUtils.getString(R.string.reply_error_message_timer_reply, timeLeft)
        switchPage(Page.INPUT, { callback.openMessage(errorMessage) })
      }

      return
    }

    val timeLeft = lastReplyRepository.getTimeUntilThread(chanDescriptor.boardDescriptor())
    if (timeLeft < 0L) {
      submitOrAuthenticate()
    } else {
      val errorMessage = AndroidUtils.getString(R.string.reply_error_message_timer_thread, timeLeft)
      switchPage(Page.INPUT, { callback.openMessage(errorMessage) })
    }
  }

  private fun submitOrAuthenticate() {
    if (site.actions().postRequiresAuthentication()) {
      switchPage(Page.AUTHENTICATION)
    } else {
      makeSubmitCall()
    }
  }

  private fun onPrepareToSubmit(isAuthenticateOnly: Boolean): Boolean {
    callback.loadViewsIntoDraft(draft)

    if (!isAuthenticateOnly && draft.file == null && draft.comment.trim().isEmpty()) {
      callback.openMessage(AndroidUtils.getString(R.string.reply_comment_empty))
      return false
    }

    draft.chanDescriptor = chanDescriptor
    draft.spoilerImage = draft.spoilerImage && board.spoilers
    draft.captchaResponse = null
    return true
  }

  override fun onAuthenticationComplete(
    authenticationLayout: AuthenticationLayoutInterface,
    challenge: String?,
    response: String?,
    autoReply: Boolean
  ) {
    draft.captchaChallenge = challenge
    draft.captchaResponse = response

    if (autoReply) {
      makeSubmitCall()
    } else {
      switchPage(Page.INPUT)
    }
  }

  override fun onAuthenticationFailed(error: Throwable) {
    callback.showAuthenticationFailedError(error)
    switchPage(Page.INPUT)
  }

  override fun onFallbackToV1CaptchaView(autoReply: Boolean) {
    callback.onFallbackToV1CaptchaView(autoReply)
  }

  fun onCommentTextChanged(text: CharSequence) {
    val length = text.toString().toByteArray(UTF_8).size
    callback.updateCommentCount(length, board.maxCommentChars, length > board.maxCommentChars)
  }

  fun onSelectionChanged() {
    callback.loadViewsIntoDraft(draft)
    highlightQuotes()
  }

  fun fileNameLongClicked(): Boolean {
    var currentExt = StringUtils.extractFileNameExtension(draft.fileName)

    currentExt = if (currentExt == null) {
      ""
    } else {
      ".$currentExt"
    }

    draft.fileName = System.currentTimeMillis().toString() + currentExt
    callback.loadDraftIntoViews(draft)
    return true
  }

  fun quote(post: Post, withText: Boolean) {
    handleQuote(post, if (withText) post.comment.toString() else null)
  }

  fun quote(post: Post?, text: CharSequence) {
    handleQuote(post, text.toString())
  }

  private fun handleQuote(post: Post?, textQuote: String?) {
    callback.loadViewsIntoDraft(draft)

    val insert = StringBuilder()
    val selectStart = callback.selectionStart

    if (selectStart - 1 >= 0
      && selectStart - 1 < draft.comment.length
      && draft.comment[selectStart - 1] != '\n'
    ) {
      insert
        .append('\n')
    }
    if (post != null && !draft.comment.contains(">>" + post.no)) {
      insert
        .append(">>")
        .append(post.no)
        .append("\n")
    }

    if (textQuote != null) {
      val lines = textQuote.split("\n+").toTypedArray()
      // matches for >>123, >>123 (text), >>>/fit/123
      val quotePattern = Pattern.compile("^>>(>/[a-z0-9]+/)?\\d+.*$")

      for (line in lines) {
        // do not include post no from quoted post
        if (!quotePattern.matcher(line).matches()) {
          insert
            .append(">")
            .append(line)
            .append("\n")
        }
      }
    }

    draft.comment = StringBuilder(draft.comment)
      .insert(selectStart, insert)
      .toString()

    callback.loadDraftIntoViews(draft)
    callback.adjustSelection(selectStart, insert.length)
    highlightQuotes()
  }

  override fun onFilePicked(name: String, file: File) {
    pickingFile = false

    draft.file = file
    draft.fileName = name

    try {
      val exif = ExifInterface(file.absolutePath)
      val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

      if (orientation != ExifInterface.ORIENTATION_UNDEFINED) {
        callback.openMessage(AndroidUtils.getString(R.string.file_has_exif_data))
      }
    } catch (ignored: Exception) {
    }

    showPreview(name, file)
  }

  override fun onFilePickError(canceled: Boolean) {
    pickingFile = false

    if (!canceled) {
      AndroidUtils.showToast(context, R.string.reply_file_open_failed, Toast.LENGTH_LONG)
    }
  }

  private fun closeAll() {
    isExpanded = false
    previewOpen = false
    selectedQuote = -1

    callback.openMessage(null)
    callback.setExpanded(false)
    callback.openSubject(false)
    callback.openFlag(false)
    callback.openCommentQuoteButton(false)
    callback.openCommentSpoilerButton(false)
    callback.openCommentCodeButton(false)
    callback.openCommentEqnButton(false)
    callback.openCommentMathButton(false)
    callback.openCommentSJISButton(false)
    callback.openNameOptions(false)
    callback.openFileName(false)
    callback.openSpoiler(show = false, setUnchecked = true)
    callback.openPreview(false, null)
    callback.openPreviewMessage(false, null)
    callback.destroyCurrentAuthentication()
  }

  private fun makeSubmitCall() {
    launch {
      site.actions().post(draft)
        .collect { postResult ->
          withContext(Dispatchers.Main) {
            when (postResult) {
              is SiteActions.PostResult.PostComplete -> {
                onPostComplete(postResult.replyResponse)
              }
              is SiteActions.PostResult.UploadingProgress -> {
                onUploadingProgress(postResult.percent)
              }
              is SiteActions.PostResult.PostError -> {
                onPostError(postResult.error)
              }
            }
          }
        }

      switchPageSuspend(Page.LOADING)
    }
  }

  private suspend fun onPostComplete(replyResponse: ReplyResponse) {
    when {
      replyResponse.posted -> onPostedSuccessfully(replyResponse)
      replyResponse.requireAuthentication -> switchPage(Page.AUTHENTICATION)
      else -> {
        var errorMessage = AndroidUtils.getString(R.string.reply_error)
        if (replyResponse.errorMessage != null) {
          errorMessage = AndroidUtils.getString(
            R.string.reply_error_message,
            replyResponse.errorMessage
          )
        }

        Logger.e(TAG, "onPostComplete error: $errorMessage")
        switchPage(Page.INPUT, { callback.openMessage(errorMessage) })
      }
    }
  }

  private suspend fun onPostedSuccessfully(replyResponse: ReplyResponse) {
    // if the thread being presented has changed in the time waiting for this call to
    // complete, the loadable field in ReplyPresenter will be incorrect; reconstruct
    // the loadable (local to this method) from the reply response
    val localSite = siteRepository.bySiteDescriptor(replyResponse.siteDescriptor)
    val localBoard = requireNotNull(boardRepository.getFromCode(localSite, replyResponse.boardCode))

    val threadNo = if (replyResponse.threadNo <= 0L) {
      replyResponse.postNo
    } else {
      replyResponse.threadNo
    }

    val newThreadDescriptor = ChanDescriptor.ThreadDescriptor.create(
      localSite!!.name(),
      localBoard.code,
      threadNo
    )

    lastReplyRepository.putLastReply(newThreadDescriptor.boardDescriptor)

    if (chanDescriptor!!.isCatalogDescriptor()) {
      lastReplyRepository.putLastThread(chanDescriptor!!.boardDescriptor())
    }

    if (ChanSettings.postPinThread.get()) {
      bookmarkThread(newThreadDescriptor, threadNo)
    }

    val savedReply = SavedReply.fromBoardNoPassword(
      localBoard,
      replyResponse.postNo,
      replyResponse.password
    )

    databaseManager.runTaskAsync(
      databaseManager.databaseSavedReplyManager.saveReply(savedReply)
    )

    switchPageSuspend(Page.INPUT)

    closeAll()
    highlightQuotes()

    draft = Reply()
    draft.name = draft.name

    replyManager.putReply(newThreadDescriptor, draft)

    callback.loadDraftIntoViews(draft)
    callback.onPosted()

    // special case for new threads, check if we were on the catalog with the nonlocal
    // loadable
    if (bound && chanDescriptor!!.isCatalogDescriptor()) {
      callback.showThread(newThreadDescriptor)
    }
  }

  private fun bookmarkThread(chanDescriptor: ChanDescriptor, threadNo: Long) {
    if (callback.thread?.chanDescriptor?.isThreadDescriptor() == true) {
      // reply
      val thread = callback.thread
      if (thread != null) {
        val op = thread.op
        val title = PostHelper.getTitle(op, chanDescriptor)
        val thumbnail = op.firstImage()?.thumbnailUrl

        bookmarksManager.createBookmark(chanDescriptor.toThreadDescriptor(threadNo), title, thumbnail)
      } else {
        bookmarksManager.createBookmark(chanDescriptor.toThreadDescriptor(threadNo))
      }
    } else {
      // new thread, use the new loadable
      draft.chanDescriptor = chanDescriptor
      val title = PostHelper.getTitle(draft)

      bookmarksManager.createBookmark(
        ChanDescriptor.ThreadDescriptor(chanDescriptor.boardDescriptor(), threadNo),
        title
      )
    }
  }

  private fun onUploadingProgress(percent: Int) {
    // called on a background thread!
    BackgroundUtils.runOnMainThread { callback.onUploadingProgress(percent) }
  }

  private suspend fun onPostError(exception: Throwable?) {
    Logger.e(TAG, "onPostError", exception)
    switchPageSuspend(Page.INPUT)

    var errorMessage = AndroidUtils.getString(R.string.reply_error)
    if (exception != null) {
      val message = exception.message
      if (message != null) {
        errorMessage = AndroidUtils.getString(R.string.reply_error_message, message)
      }
    }

    callback.openMessage(errorMessage)
  }

  suspend fun switchPageSuspend(
    page: Page,
    useV2NoJsCaptcha: Boolean = true,
    autoReply: Boolean = true
  ) {
    return suspendCancellableCoroutine { cancellableContinuation ->
      switchPage(page, { cancellableContinuation.resume(Unit) }, useV2NoJsCaptcha, autoReply)
    }
  }

  @JvmOverloads
  fun switchPage(
    page: Page,
    onSwitched: (() -> Unit)? = null,
    useV2NoJsCaptcha: Boolean = true,
    autoReply: Boolean = true
  ) {
    if (useV2NoJsCaptcha && this.page == page) {
      onSwitched?.invoke()
      return
    }

    this.page = page

    when (page) {
      Page.LOADING,
      Page.INPUT -> {
        callback.setPage(page) { onSwitched?.invoke() }
      }
      Page.AUTHENTICATION -> {
        callback.setPage(Page.AUTHENTICATION) {
          val authentication = site.actions().postAuthenticate()

          // cleanup resources tied to the new captcha layout/presenter
          callback.destroyCurrentAuthentication()

          try {
            // If the user doesn't have WebView installed it will throw an error
            callback.initializeAuthentication(
              site,
              authentication,
              this,
              useV2NoJsCaptcha,
              autoReply
            )
          } catch (error: Throwable) {
            onAuthenticationFailed(error)
          }

          onSwitched?.invoke()
        }
      }
    }
  }

  private fun highlightQuotes() {
    val matcher = QUOTE_PATTERN.matcher(draft.comment)

    // Find all occurrences of >>\d+ with start and end between selectionStart
    var no = -1

    while (matcher.find()) {
      val selectStart = callback.selectionStart

      if (matcher.start() <= selectStart && matcher.end() >= selectStart - 1) {
        val quote = matcher.group().substring(2)
        no = quote.toIntOrNull() ?: -1
      }
    }

    // Allow no = -1 removing the highlight
    if (no != selectedQuote) {
      selectedQuote = no
      callback.highlightPostNo(no)
    }
  }

  private fun showPreview(name: String, file: File?) {
    callback.openPreview(true, file)

    if (isExpanded) {
      callback.openFileName(true)
      if (board.spoilers) {
        callback.openSpoiler(show = true, setUnchecked = false)
      }
    }

    callback.setFileName(name)
    previewOpen = true

    val probablyWebm = "webm" == StringUtils.extractFileNameExtension(name)

    val maxSize = if (probablyWebm) {
      board.maxWebmSize
    } else {
      board.maxFileSize
    }

    //if the max size is undefined for the board, ignore this message
    if (file != null && file.length() > maxSize && maxSize != -1) {
      val fileSize = getReadableFileSize(file.length())
      val stringResId = if (probablyWebm) {
        R.string.reply_webm_too_big
      } else {
        R.string.reply_file_too_big
      }

      callback.openPreviewMessage(
        true,
        AndroidUtils.getString(stringResId, fileSize, getReadableFileSize(maxSize.toLong()))
      )
    } else {
      callback.openPreviewMessage(false, null)
    }
  }

  /**
   * Applies the new file and filename if they have been changed. They may change when user
   * re-encodes the picked image file (they may want to scale it down/remove metadata/change quality etc.)
   */
  fun onImageOptionsApplied(reply: Reply) {
    draft.file = reply.file
    draft.fileName = reply.fileName
    showPreview(draft.fileName, draft.file)
  }

  interface ReplyPresenterCallback {
    val imagePickDelegate: ImagePickDelegate
    val thread: ChanThread?
    val selectionStart: Int

    fun loadViewsIntoDraft(draft: Reply?)
    fun loadDraftIntoViews(draft: Reply?)
    fun adjustSelection(start: Int, amount: Int)
    fun setPage(page: Page, onPageSet: () -> Unit)
    fun initializeAuthentication(
      site: Site?,
      authentication: SiteAuthentication?,
      callback: AuthenticationLayoutCallback?,
      useV2NoJsCaptcha: Boolean,
      autoReply: Boolean
    )

    fun resetAuthentication()
    fun openMessage(message: String?)
    fun onPosted()
    fun setCommentHint(hint: String?)
    fun showCommentCounter(show: Boolean)
    fun setExpanded(expanded: Boolean)
    fun openNameOptions(open: Boolean)
    fun openSubject(open: Boolean)
    fun openFlag(open: Boolean)
    fun openCommentQuoteButton(open: Boolean)
    fun openCommentSpoilerButton(open: Boolean)
    fun openCommentCodeButton(open: Boolean)
    fun openCommentEqnButton(open: Boolean)
    fun openCommentMathButton(open: Boolean)
    fun openCommentSJISButton(open: Boolean)
    fun openFileName(open: Boolean)
    fun setFileName(fileName: String?)
    fun updateCommentCount(count: Int, maxCount: Int, over: Boolean)
    fun openPreview(show: Boolean, previewFile: File?)
    fun openPreviewMessage(show: Boolean, message: String?)
    fun openSpoiler(show: Boolean, setUnchecked: Boolean)
    fun highlightPostNo(no: Int)
    fun showThread(threadDescriptor: ChanDescriptor.ThreadDescriptor)
    fun focusComment()
    fun onUploadingProgress(percent: Int)
    fun onFallbackToV1CaptchaView(autoReply: Boolean)
    fun destroyCurrentAuthentication()
    fun showAuthenticationFailedError(error: Throwable?)
  }

  companion object {
    private const val TAG = "ReplyPresenter"
    private val QUOTE_PATTERN = Pattern.compile(">>\\d+")
    private val UTF_8 = StandardCharsets.UTF_8
  }

}