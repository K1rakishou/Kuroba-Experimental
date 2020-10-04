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
package com.github.k1rakishou.chan.core.presenter

import android.content.Context
import android.text.TextUtils
import android.widget.Toast
import androidx.exifinterface.media.ExifInterface
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.manager.*
import com.github.k1rakishou.chan.core.model.ChanThread
import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.repository.LastReplyRepository
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.http.Reply
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback
import com.github.k1rakishou.chan.ui.helper.ImagePickDelegate
import com.github.k1rakishou.chan.ui.helper.ImagePickDelegate.ImagePickCallback
import com.github.k1rakishou.chan.ui.helper.PostHelper
import com.github.k1rakishou.chan.utils.*
import com.github.k1rakishou.chan.utils.PostUtils.getReadableFileSize
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanSavedReply
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class ReplyPresenter @Inject constructor(
  private val context: Context,
  private val replyManager: ReplyManager,
  private val savedReplyManager: SavedReplyManager,
  private val lastReplyRepository: LastReplyRepository,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
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

  private val job = SupervisorJob()
  private lateinit var callback: ReplyPresenterCallback
  private lateinit var draft: Reply
  private lateinit var chanBoard: ChanBoard
  private lateinit var site: Site

  private val highlightQuotesDebouncer = Debouncer(false)

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

  fun bindChanDescriptor(chanDescriptor: ChanDescriptor): Boolean {
    if (this.chanDescriptor != null) {
      unbindChanDescriptor()
    }

    val thisSite = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (thisSite == null) {
      Logger.e(TAG, "bindChanDescriptor couldn't find ${chanDescriptor.siteDescriptor()}")
      return false
    }

    val thisBoard = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
    if (thisBoard == null) {
      Logger.e(TAG, "bindChanDescriptor couldn't find ${chanDescriptor.boardDescriptor()}")
      return false
    }

    this.chanBoard = thisBoard
    this.site = thisSite
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

    callback.loadDraftIntoViews(draft)

    if (thisBoard.maxCommentChars > 0) {
      callback.updateCommentCount(0, thisBoard.maxCommentChars, false)
    }

    callback.setCommentHint(AndroidUtils.getString(stringId))
    callback.showCommentCounter(thisBoard.maxCommentChars > 0)

    if (draft.file != null) {
      showPreview(draft.fileName, draft.file)
    }

    switchPage(Page.INPUT)
    return true
  }

  fun unbindChanDescriptor() {
    bound = false

    job.cancelChildren()

    if (::draft.isInitialized) {
      draft.file = null
      draft.fileName = ""

      callback.loadViewsIntoDraft(draft)
      replyManager.putReply(chanDescriptor, draft)
    }

    closeAll()
  }

  fun isCatalogReplyLayout(): Boolean? {
    if (chanDescriptor == null) {
      return null
    }

    return chanDescriptor is ChanDescriptor.CatalogDescriptor
  }

  fun onOpen(open: Boolean) {
    if (open) {
      callback.focusComment()
    }
  }

  fun onBack(): Boolean {
    return when {
      page == Page.LOADING -> {
        true
      }
      page == Page.AUTHENTICATION -> {
        switchPage(Page.INPUT)
        true
      }
      isExpanded -> {
        onMoreClicked()
        true
      }
      else -> false
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
      if (chanBoard.spoilers) {
        callback.openSpoiler(isExpanded, false)
      }
    }

    val is4chan = chanBoard.boardDescriptor.siteDescriptor.is4chan()
    callback.openCommentQuoteButton(isExpanded)

    if (chanBoard.spoilers) {
      callback.openCommentSpoilerButton(isExpanded)
    }

    if (is4chan && chanBoard.boardCode() == "g") {
      callback.openCommentCodeButton(isExpanded)
    }

    if (is4chan && chanBoard.boardCode() == "sci") {
      callback.openCommentEqnButton(isExpanded)
      callback.openCommentMathButton(isExpanded)
    }

    if (is4chan && (chanBoard.boardCode() == "jp" || chanBoard.boardCode() == "vip")) {
      callback.openCommentSJISButton(isExpanded)
    }

    if (is4chan && chanBoard.boardCode() == "pol") {
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
        if (chanBoard.spoilers) {
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
    if (!site.actions().postRequiresAuthentication()) {
      return
    }

    if (!onPrepareToSubmit(true)) {
      return
    }

    switchPage(Page.AUTHENTICATION, useV2NoJsCaptcha = true, autoReply = false)
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
        switchPage(Page.INPUT)
        callback.openMessage(errorMessage)
      }

      return
    }

    val timeLeft = lastReplyRepository.getTimeUntilThread(chanDescriptor.boardDescriptor())
    if (timeLeft < 0L) {
      submitOrAuthenticate()
    } else {
      val errorMessage = AndroidUtils.getString(R.string.reply_error_message_timer_thread, timeLeft)
      switchPage(Page.INPUT)
      callback.openMessage(errorMessage)
    }
  }

  private fun submitOrAuthenticate() {
    if (site.actions().postRequiresAuthentication()) {
      val token = callback.getTokenOrNull()
      if (token.isNullOrEmpty()) {
        switchPage(Page.AUTHENTICATION)
        return
      }

      onAuthenticationComplete(null, token, true)
      return
    }

    makeSubmitCall()
  }

  private fun onPrepareToSubmit(isAuthenticateOnly: Boolean): Boolean {
    callback.loadViewsIntoDraft(draft)

    if (!isAuthenticateOnly && draft.file == null && draft.comment.trim().isEmpty()) {
      callback.openMessage(AndroidUtils.getString(R.string.reply_comment_empty))
      return false
    }

    draft.chanDescriptor = chanDescriptor
    draft.spoilerImage = draft.spoilerImage && chanBoard.spoilers
    draft.captchaResponse = null
    return true
  }

  override fun onAuthenticationComplete(
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
    if (chanBoard.maxCommentChars < 0) {
      return
    }

    val length = text.toString().toByteArray(UTF_8).size
    callback.updateCommentCount(length, chanBoard.maxCommentChars, length > chanBoard.maxCommentChars)
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
      for (line in lines) {
        // do not include post no from quoted post
        if (!QUOTE_PATTERN_COMPLEX.matcher(line).matches()) {
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

    callback.highlightPostNos(emptySet())
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
      switchPage(Page.LOADING)
      Logger.d(TAG, "makeSubmitCall() chanDescriptor=${draft.chanDescriptor}")

      site.actions().post(draft)
        .catch { error -> onPostError(error) }
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
    }
  }

  private suspend fun onPostComplete(replyResponse: ReplyResponse) {
    when {
      replyResponse.posted -> {
        Logger.d(TAG, "onPostComplete() replyResponse=$replyResponse")
        onPostedSuccessfully(replyResponse)
      }
      replyResponse.requireAuthentication -> {
        Logger.d(TAG, "onPostComplete() requireAuthentication==true replyResponse=$replyResponse")
        switchPage(Page.AUTHENTICATION)
      }
      else -> {
        var errorMessage = AndroidUtils.getString(R.string.reply_error)
        if (replyResponse.errorMessage != null) {
          errorMessage = AndroidUtils.getString(
            R.string.reply_error_message,
            replyResponse.errorMessage
          )
        }

        Logger.e(TAG, "onPostComplete() error: $errorMessage")
        switchPage(Page.INPUT)
        callback.openMessage(errorMessage)
      }
    }
  }

  private suspend fun onPostedSuccessfully(replyResponse: ReplyResponse) {
    val siteDescriptor = replyResponse.siteDescriptor
      ?: return

    // if the thread being presented has changed in the time waiting for this call to
    // complete, the loadable field in ReplyPresenter will be incorrect; reconstruct
    // the loadable (local to this method) from the reply response
    val localSite = siteManager.bySiteDescriptor(siteDescriptor)
      ?: return

    val boardDescriptor = BoardDescriptor(siteDescriptor, replyResponse.boardCode)
    val localBoard = boardManager.byBoardDescriptor(boardDescriptor)
      ?: return

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

    if (chanDescriptor!!.isCatalogDescriptor()) {
      lastReplyRepository.putLastThread(chanDescriptor!!.boardDescriptor())
    }

    if (ChanSettings.postPinThread.get()) {
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

    switchPage(Page.INPUT)

    closeAll()
    highlightQuotes()

    draft = Reply()
    draft.name = draft.name

    replyManager.putReply(newThreadDescriptor, draft)

    callback.loadDraftIntoViews(draft)
    callback.onPosted()

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

  private fun onPostError(exception: Throwable?) {
    Logger.e(TAG, "onPostError", exception)
    switchPage(Page.INPUT)

    var errorMessage = AndroidUtils.getString(R.string.reply_error)
    if (exception != null) {
      val message = exception.message
      if (message != null) {
        errorMessage = AndroidUtils.getString(R.string.reply_error_message, message)
      }
    }

    callback.openMessage(errorMessage)
  }

  @JvmOverloads
  fun switchPage(
    page: Page,
    useV2NoJsCaptcha: Boolean = true,
    autoReply: Boolean = true
  ) {
    if (useV2NoJsCaptcha && this.page == page) {
      return
    }

    this.page = page

    when (page) {
      Page.LOADING,
      Page.INPUT -> callback.setPage(page)
      Page.AUTHENTICATION -> {
        callback.setPage(Page.AUTHENTICATION)

        // cleanup resources tied to the new captcha layout/presenter
        callback.destroyCurrentAuthentication()

        try {
          // If the user doesn't have WebView installed it will throw an error
          callback.initializeAuthentication(
            site,
            site.actions().postAuthenticate(),
            this,
            useV2NoJsCaptcha,
            autoReply
          )
        } catch (error: Throwable) {
          onAuthenticationFailed(error)
        }
      }
    }
  }

  private fun highlightQuotes() {
    highlightQuotesDebouncer.post({
      val matcher = QUOTE_PATTERN.matcher(draft.comment)

      // Find all occurrences of >>\d+ with start and end between selectionStart
      val selectedQuotes = mutableSetOf<Long>()

      while (matcher.find()) {
        val quote = matcher.group().substring(2)
        selectedQuotes += quote.toLongOrNull() ?: continue
      }

      callback.highlightPostNos(selectedQuotes)
    }, 250)
  }

  private fun showPreview(name: String, file: File?) {
    callback.openPreview(true, file)

    if (isExpanded) {
      callback.openFileName(true)
      if (chanBoard.spoilers) {
        callback.openSpoiler(show = true, setUnchecked = false)
      }
    }

    callback.setFileName(name)
    previewOpen = true

    val probablyWebm = "webm" == StringUtils.extractFileNameExtension(name)

    val maxSize = if (probablyWebm) {
      chanBoard.maxWebmSize
    } else {
      chanBoard.maxFileSize
    }

    // if the max size is undefined for the board, ignore this message
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
    fun setPage(page: Page)
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
    fun highlightPostNos(postNos: Set<Long>)
    fun showThread(threadDescriptor: ChanDescriptor.ThreadDescriptor)
    fun focusComment()
    fun onUploadingProgress(percent: Int)
    fun onFallbackToV1CaptchaView(autoReply: Boolean)
    fun destroyCurrentAuthentication()
    fun showAuthenticationFailedError(error: Throwable?)
    fun getTokenOrNull(): String?
  }

  companion object {
    private const val TAG = "ReplyPresenter"
    private val QUOTE_PATTERN = Pattern.compile(">>\\d+")
    // matches for >>123, >>123 (text), >>>/fit/123
    private val QUOTE_PATTERN_COMPLEX = Pattern.compile("^>>(>/[a-z0-9]+/)?\\d+.*$")
    private val UTF_8 = StandardCharsets.UTF_8
  }

}