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

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.AndroidRuntimeException
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.helper.CommentEditingHistory.CommentInputState
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.KeyboardStateListener
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.features.reply.ReplyPresenter.ReplyPresenterCallback
import com.github.k1rakishou.chan.features.reply.data.Reply
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutInterface
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder.CaptchaValidationListener
import com.github.k1rakishou.chan.ui.captcha.CaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.GenericWebViewAuthenticationLayout
import com.github.k1rakishou.chan.ui.captcha.LegacyCaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.v1.CaptchaNojsLayoutV1
import com.github.k1rakishou.chan.ui.captcha.v2.CaptchaNoJsLayoutV2
import com.github.k1rakishou.chan.ui.helper.RefreshUIMessage
import com.github.k1rakishou.chan.ui.layout.ThreadListLayout
import com.github.k1rakishou.chan.ui.theme.DropdownArrowDrawable
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.ui.view.LoadView
import com.github.k1rakishou.chan.ui.view.SelectionListeningEditText
import com.github.k1rakishou.chan.ui.view.SelectionListeningEditText.SelectionChangedListener
import com.github.k1rakishou.chan.ui.widget.CancellableToast
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.doIgnoringTextWatcher
import com.github.k1rakishou.chan.utils.setAlphaFast
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import javax.inject.Inject

// TODO(KurobaEx): When catalog reply is opened and we open any thread via "tabs" the opened thread
//  will be glitched, it won't have the bottomNavBar because we have a replyLayout opened.
class ReplyLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : LoadView(context, attrs, defStyle),
  View.OnClickListener,
  ReplyPresenterCallback,
  TextWatcher,
  SelectionChangedListener,
  CaptchaValidationListener,
  KeyboardStateListener,
  ThemeChangesListener,
  ReplyLayoutFilesArea.ReplyLayoutCallbacks {

  @Inject
  lateinit var presenter: ReplyPresenter
  @Inject
  lateinit var captchaHolder: CaptchaHolder
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var proxyStorage: ProxyStorage
  @Inject
  lateinit var replyManager: ReplyManager

  private var threadListLayoutCallbacks: ThreadListLayoutCallbacks? = null
  private var threadListLayoutFilesCallback: ReplyLayoutFilesArea.ThreadListLayoutCallbacks? = null

  private var authenticationLayout: AuthenticationLayoutInterface? = null
  private var blockSelectionChange = false
  private var currentOrientation: Int = Configuration.ORIENTATION_UNDEFINED

  // Progress view (when sending request to the server)
  private lateinit var progressLayout: View
  private lateinit var currentProgress: ColorizableTextView
  private lateinit var currentFile: ColorizableTextView

  // Reply views:
  private lateinit var replyInputLayout: View
  private lateinit var name: ColorizableEditText
  private lateinit var subject: ColorizableEditText
  private lateinit var flag: ColorizableEditText
  private lateinit var options: ColorizableEditText
  private lateinit var nameOptions: LinearLayout
  private lateinit var commentQuoteButton: ColorizableBarButton
  private lateinit var commentSpoilerButton: ColorizableBarButton
  private lateinit var commentCodeButton: ColorizableBarButton
  private lateinit var commentEqnButton: ColorizableBarButton
  private lateinit var commentMathButton: ColorizableBarButton
  private lateinit var commentSJISButton: ColorizableBarButton
  private lateinit var comment: SelectionListeningEditText
  private lateinit var commentCounter: TextView
  private lateinit var commentRevertChangeButton: AppCompatImageView
  private lateinit var captchaButtonContainer: ConstraintLayout
  private lateinit var captchaView: AppCompatImageView
  private lateinit var validCaptchasCount: TextView
  private lateinit var more: ImageView
  private lateinit var submit: ImageView
  private lateinit var moreDropdown: DropdownArrowDrawable
  private lateinit var replyLayoutFilesArea: ReplyLayoutFilesArea

  private var isCounterOverflowed = false

  // Captcha views:
  private lateinit var captchaContainer: FrameLayout
  private lateinit var captchaHardReset: ImageView

  private val coroutineScope = KurobaCoroutineScope()
  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(coroutineScope)
  private val wrappingModeUpdateDebouncer = Debouncer(false)
  private val replyLayoutMessageToast = CancellableToast()

  override val chanDescriptor: ChanDescriptor?
    get() = threadListLayoutCallbacks?.getCurrentChanDescriptor()

  override val selectionStart: Int
    get() = comment.selectionStart

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    EventBus.getDefault().register(this)
    globalWindowInsetsManager.addKeyboardUpdatesListener(this)
    themeEngine.addListener(this)
  }

  override fun onKeyboardStateChanged() {
    updateWrappingMode()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    themeEngine.removeListener(this)
    EventBus.getDefault().unregister(this)
    globalWindowInsetsManager.removeKeyboardUpdatesListener(this)
  }

  override fun onThemeChanged() {
    commentCounter.setTextColor(themeEngine.chanTheme.textColorSecondary)
    val tintColor = themeEngine.resolveTintColor(themeEngine.chanTheme.isBackColorDark)

    if (commentRevertChangeButton.drawable != null) {
      commentRevertChangeButton.setImageDrawable(
        themeEngine.tintDrawable(commentRevertChangeButton.drawable, tintColor)
      )
    }

    moreDropdown.updateColor(tintColor)

    if (submit.drawable != null) {
      submit.setImageDrawable(themeEngine.tintDrawable(submit.drawable, tintColor))
    }

    val textColor = if (isCounterOverflowed) {
      themeEngine.chanTheme.errorColor
    } else {
      themeEngine.chanTheme.textColorSecondary
    }

    commentCounter.setTextColor(textColor)

    validCaptchasCount.background = themeEngine.tintDrawable(
      context,
      R.drawable.circle_background,
      0xAA000000.toInt()
    )

    validCaptchasCount.setTextColor(Color.WHITE)
  }

  private fun updateWrappingMode() {
    val page = presenter.page

    val matchParent = when {
      page === ReplyPresenter.Page.INPUT -> presenter.isExpanded
      page === ReplyPresenter.Page.LOADING -> false
      page === ReplyPresenter.Page.AUTHENTICATION -> true
      else -> throw IllegalStateException("Unknown Page: $page")
    }

    setWrappingMode(matchParent)
    threadListLayoutCallbacks?.updateRecyclerViewPaddings()
  }

  override fun onFinishInflate() {
    super.onFinishInflate()

    if (!isInEditMode) {
      AppModuleAndroidUtils.extractStartActivityComponent(context)
        .inject(this)
    }

    this.currentOrientation = resources.configuration.orientation

    // Inflate reply input
    replyInputLayout = AppModuleAndroidUtils.inflate(context, R.layout.layout_reply_input, this, false)
    name = replyInputLayout.findViewById(R.id.name)
    subject = replyInputLayout.findViewById(R.id.subject)
    flag = replyInputLayout.findViewById(R.id.flag)
    options = replyInputLayout.findViewById(R.id.options)
    nameOptions = replyInputLayout.findViewById(R.id.name_options)
    commentQuoteButton = replyInputLayout.findViewById(R.id.comment_quote)
    commentSpoilerButton = replyInputLayout.findViewById(R.id.comment_spoiler)
    commentCodeButton = replyInputLayout.findViewById(R.id.comment_code)
    commentEqnButton = replyInputLayout.findViewById(R.id.comment_eqn)
    commentMathButton = replyInputLayout.findViewById(R.id.comment_math)
    commentSJISButton = replyInputLayout.findViewById(R.id.comment_sjis)
    comment = replyInputLayout.findViewById(R.id.comment)
    commentCounter = replyInputLayout.findViewById(R.id.comment_counter)
    commentRevertChangeButton = replyInputLayout.findViewById(R.id.comment_revert_change_button)
    captchaButtonContainer = replyInputLayout.findViewById(R.id.captcha_button_container)
    validCaptchasCount = replyInputLayout.findViewById(R.id.valid_captchas_count)
    more = replyInputLayout.findViewById(R.id.more)
    submit = replyInputLayout.findViewById(R.id.submit)
    replyLayoutFilesArea = replyInputLayout.findViewById(R.id.reply_layout_files_area)

    progressLayout = AppModuleAndroidUtils.inflate(context, R.layout.layout_reply_progress, this, false)
    currentProgress = progressLayout.findViewById(R.id.current_progress)
    currentFile = progressLayout.findViewById(R.id.current_file)

    // Setup reply layout views
    commentQuoteButton.setOnClickListener(this)
    commentSpoilerButton.setOnClickListener(this)
    commentCodeButton.setOnClickListener(this)
    commentMathButton.setOnClickListener(this)
    commentEqnButton.setOnClickListener(this)
    commentSJISButton.setOnClickListener(this)
    commentRevertChangeButton.setOnClickListener(this)

    comment.addTextChangedListener(this)
    comment.setSelectionChangedListener(this)
    comment.setOnFocusChangeListener({ _, focused: Boolean ->
      if (!focused) {
        AndroidUtils.hideKeyboard(comment)
      }
    })

    comment.setPlainTextPaste(true)
    setupCommentContextMenu()

    AndroidUtils.setBoundlessRoundRippleBackground(more)
    more.setOnClickListener(this)

    captchaView = replyInputLayout.findViewById(R.id.captcha_view)
    AndroidUtils.setBoundlessRoundRippleBackground(captchaView)
    captchaView.setOnClickListener(this)

    AndroidUtils.setBoundlessRoundRippleBackground(submit)
    submit.setOnClickListener(this)
    submit.setOnLongClickListener({
      presenter.onSubmitClicked(true)
      true
    })

    // Inflate captcha layout
    captchaContainer = AppModuleAndroidUtils.inflate(
      context,
      R.layout.layout_reply_captcha,
      this,
      false
    ) as FrameLayout

    captchaHardReset = captchaContainer.findViewById(R.id.reset)

    // Setup captcha layout views
    captchaContainer.layoutParams = LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    AndroidUtils.setBoundlessRoundRippleBackground(captchaHardReset)
    captchaHardReset.setOnClickListener(this)
    moreDropdown = DropdownArrowDrawable(
      dp(16f),
      dp(16f),
      false
    )

    submit.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_send_white_24dp))
    more.setImageDrawable(moreDropdown)

    captchaHardReset.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_refresh_white_24dp))
    setView(replyInputLayout)
    elevation = dp(4f).toFloat()

    presenter.create(this)
    replyLayoutFilesArea.onCreate()

    onThemeChanged()
  }

  fun onCreate(
    replyLayoutCallback: ThreadListLayoutCallbacks,
    threadListLayoutCallbacks: ReplyLayoutFilesArea.ThreadListLayoutCallbacks
  ) {
    this.threadListLayoutCallbacks = replyLayoutCallback
    this.threadListLayoutFilesCallback = threadListLayoutCallbacks
  }

  fun onOpen(open: Boolean) {
    presenter.onOpen(open)

    if (open) {
      replyLayoutFilesArea.updateLayoutManager()
    }

    if (open && proxyStorage.isDirty()) {
      openMessage(getString(R.string.reply_proxy_list_is_dirty_message), 10000)
    }
  }

  suspend fun bindLoadable(chanDescriptor: ChanDescriptor) {
    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (site == null) {
      Logger.e(TAG, "bindLoadable couldn't find site " + chanDescriptor.siteDescriptor())
      return
    }

    if (!presenter.bindChanDescriptor(chanDescriptor)) {
      Logger.d(TAG, "bindLoadable failed to bind $chanDescriptor")
      cleanup()
      return
    }

    if (isTablet()) {
      comment.minHeight = REPLY_COMMENT_MIN_HEIGHT_TABLET
    } else {
      comment.minHeight = REPLY_COMMENT_MIN_HEIGHT
    }

    if (!site.actions().postRequiresAuthentication()) {
      captchaButtonContainer.visibility = GONE
    } else {
      captchaButtonContainer.visibility = VISIBLE
    }

    captchaHolder.setListener(chanDescriptor, this)
  }

  override suspend fun bindReplyImages(chanDescriptor: ChanDescriptor) {
    replyLayoutFilesArea.onBind(chanDescriptor, threadListLayoutFilesCallback!!, this)
  }

  override fun unbindReplyImages(chanDescriptor: ChanDescriptor) {
    replyLayoutFilesArea.onUnbind()
  }

  override fun filesAreaOnBackPressed(): Boolean {
    return replyLayoutFilesArea.onBackPressed()
  }

  override fun requestWrappingModeUpdate() {
    BackgroundUtils.ensureMainThread()

    wrappingModeUpdateDebouncer.post({ updateWrappingMode() }, 250L)
  }

  override fun disableSendButton() {
    BackgroundUtils.ensureMainThread()

    if (!submit.isEnabled) {
      return
    }

    submit.isEnabled = false
    submit.isClickable = false
    submit.isFocusable = false
    submit.setAlphaFast(.4f)
  }

  override fun enableSendButton() {
    BackgroundUtils.ensureMainThread()

    if (submit.isEnabled) {
      return
    }

    submit.isEnabled = true
    submit.isClickable = true
    submit.isFocusable = true
    submit.setAlphaFast(1f)
  }

  override fun showReplyLayoutMessage(message: String, duration: Int) {
    openMessage(message, duration)
  }

  fun onDestroy() {
    this.threadListLayoutCallbacks = null
    this.threadListLayoutFilesCallback = null

    presenter.unbindReplyImages()
    captchaHolder.clearCallbacks()
    coroutineScope.cancelChildren()
    rendezvousCoroutineExecutor.stop()

    cleanup()
  }

  fun cleanup() {
    presenter.unbindChanDescriptor()
  }

  fun onBack(): Boolean {
    return presenter.onBack()
  }

  private fun setWrappingMode(matchParent: Boolean) {
    val prevLayoutParams = layoutParams as LayoutParams
    val newLayoutParams = LayoutParams((layoutParams as LayoutParams))

    newLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
    newLayoutParams.height = if (matchParent) {
      ViewGroup.LayoutParams.MATCH_PARENT
    } else {
      ViewGroup.LayoutParams.WRAP_CONTENT
    }

    if (matchParent) {
      newLayoutParams.gravity = Gravity.TOP
    } else {
      newLayoutParams.gravity = Gravity.BOTTOM
    }

    var bottomPadding = 0
    if (!globalWindowInsetsManager.isKeyboardOpened) {
      bottomPadding = globalWindowInsetsManager.bottom()
    }

    val newPaddingTop = (parent as ThreadListLayout).toolbarHeight()

    val needUpdateLayoutParams = needUpdateLayoutParams(
      prevLayoutParams = prevLayoutParams,
      newLayoutParams = newLayoutParams,
      bottomPadding = bottomPadding,
      matchParent = matchParent,
      paddingTop = newPaddingTop
    )

    if (needUpdateLayoutParams) {
      if (matchParent) {
        setPadding(0, newPaddingTop, 0, bottomPadding)
      } else {
        setPadding(0, 0, 0, bottomPadding)
      }

      layoutParams = newLayoutParams
    }

    replyLayoutFilesArea.onWrappingModeChanged(matchParent)
  }

  private fun needUpdateLayoutParams(
    prevLayoutParams: LayoutParams,
    newLayoutParams: LayoutParams,
    bottomPadding: Int,
    matchParent: Boolean,
    paddingTop: Int
  ): Boolean {
    return prevLayoutParams.width != newLayoutParams.width
      || prevLayoutParams.height != newLayoutParams.height
      || prevLayoutParams.gravity != newLayoutParams.gravity
      || paddingBottom != bottomPadding
      || !matchParent
      || getPaddingTop() != paddingTop
  }

  override fun onClick(v: View) {
    rendezvousCoroutineExecutor.post {
      when {
        v === more -> presenter.onMoreClicked()
        v === captchaView -> presenter.onAuthenticateCalled()
        v === submit -> presenter.onSubmitClicked(false)
        v === captchaHardReset -> authenticationLayout?.hardReset()
        v === commentQuoteButton -> insertQuote()
        v === commentSpoilerButton -> insertTags("[spoiler]", "[/spoiler]")
        v === commentCodeButton -> insertTags("[code]", "[/code]")
        v === commentEqnButton -> insertTags("[eqn]", "[/eqn]")
        v === commentMathButton -> insertTags("[math]", "[/math]")
        v === commentSJISButton -> insertTags("[sjis]", "[/sjis]")
        v === commentRevertChangeButton -> presenter.onRevertChangeButtonClicked()
      }
    }
  }

  private fun insertQuote(): Boolean {
    val selectionStart = comment.selectionStart
    val selectionEnd = comment.selectionEnd

    val textLines = comment.text
      ?.subSequence(selectionStart, selectionEnd)
      ?.toString()
      ?.split("\n".toRegex())
      ?.toTypedArray()
      ?: emptyArray()

    val rebuilder = StringBuilder()
    for (i in textLines.indices) {
      rebuilder.append(">").append(textLines[i])
      if (i != textLines.size - 1) {
        rebuilder.append("\n")
      }
    }

    comment.text?.replace(selectionStart, selectionEnd, rebuilder.toString())
    return true
  }

  private fun insertTags(before: String, after: String): Boolean {
    val selectionStart = comment.selectionStart
    comment.text?.insert(comment.selectionEnd, after)
    comment.text?.insert(selectionStart, before)

    return true
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    return true
  }

  override fun initializeAuthentication(
    site: Site,
    authentication: SiteAuthentication,
    callback: AuthenticationLayoutCallback,
    useV2NoJsCaptcha: Boolean,
    autoReply: Boolean
  ) {
    if (authenticationLayout == null) {
      authenticationLayout = createAuthenticationLayout(authentication, useV2NoJsCaptcha)
      captchaContainer.addView(authenticationLayout as View?, 0)
    }

    authenticationLayout!!.initialize(site, callback, autoReply)
    authenticationLayout!!.reset()
  }

  private fun createAuthenticationLayout(
    authentication: SiteAuthentication,
    useV2NoJsCaptcha: Boolean
  ): AuthenticationLayoutInterface {
    when (authentication.type) {
      SiteAuthentication.Type.CAPTCHA1 -> {
        return AppModuleAndroidUtils.inflate(
          context,
          R.layout.layout_captcha_legacy,
          captchaContainer,
          false
        ) as LegacyCaptchaLayout
      }
      SiteAuthentication.Type.CAPTCHA2 -> {
        return CaptchaLayout(context)
      }
      SiteAuthentication.Type.CAPTCHA2_NOJS -> {
        val authenticationLayoutInterface = if (useV2NoJsCaptcha) {
          // new captcha window without webview
          CaptchaNoJsLayoutV2(context)
        } else {
          // default webview-based captcha view
          CaptchaNojsLayoutV1(context)
        }

        val resetButton = captchaContainer.findViewById<ImageView>(R.id.reset)
        if (resetButton != null) {
          if (useV2NoJsCaptcha) {
            // we don't need the default reset button because we have our own
            resetButton.visibility = GONE
          } else {
            // restore the button's visibility when using old v1 captcha view
            resetButton.visibility = VISIBLE
          }
        }

        return authenticationLayoutInterface
      }
      SiteAuthentication.Type.GENERIC_WEBVIEW -> {
        val view = GenericWebViewAuthenticationLayout(context)
        val params =
          LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view.layoutParams = params
        return view
      }
      SiteAuthentication.Type.NONE -> {
        throw IllegalArgumentException("${authentication.type} is not supposed to be used here")
      }
      else -> throw IllegalArgumentException("Unknown authentication.type=${authentication.type}")
    }
  }

  override fun setPage(page: ReplyPresenter.Page) {
    Logger.d(TAG, "Switching to page " + page.name)

    when (page) {
      ReplyPresenter.Page.LOADING -> {
        setView(progressLayout)
        setWrappingMode(false)

        //reset progress to 0 upon uploading start
        currentProgress.visibility = INVISIBLE
        destroyCurrentAuthentication()
        threadListLayoutCallbacks?.updateRecyclerViewPaddings()
      }
      ReplyPresenter.Page.INPUT -> {
        setView(replyInputLayout)
        setWrappingMode(presenter.isExpanded)
        destroyCurrentAuthentication()
        threadListLayoutCallbacks?.updateRecyclerViewPaddings()
      }
      ReplyPresenter.Page.AUTHENTICATION -> {
        AndroidUtils.hideKeyboard(this)
        setView(captchaContainer, false)
        setWrappingMode(true)
        captchaContainer.requestFocus(FOCUS_DOWN)
        threadListLayoutCallbacks?.updateRecyclerViewPaddings()
      }
    }
  }

  override fun resetAuthentication() {
    authenticationLayout?.reset()
  }

  override fun destroyCurrentAuthentication() {
    if (authenticationLayout == null) {
      return
    }

    // cleanup resources when switching from the new to the old captcha view
    authenticationLayout?.onDestroy()
    captchaContainer.removeView(authenticationLayout as View?)
    authenticationLayout = null
  }

  override fun showAuthenticationFailedError(error: Throwable) {
    val message = getString(R.string.could_not_initialized_captcha, getReason(error))
    AppModuleAndroidUtils.showToast(context, message, Toast.LENGTH_LONG)
  }

  override fun getTokenOrNull(): String? {
    return captchaHolder.token
  }

  override fun updateRevertChangeButtonVisibility(isBufferEmpty: Boolean) {
    if (isBufferEmpty) {
      commentRevertChangeButton.visibility = GONE
    } else {
      commentRevertChangeButton.visibility = VISIBLE
    }
  }

  override fun restoreComment(prevCommentInputState: CommentInputState) {
    comment.doIgnoringTextWatcher(this) {
      setText(prevCommentInputState.text)
      setSelection(
        prevCommentInputState.selectionStart,
        prevCommentInputState.selectionEnd
      )

      presenter.updateCommentCounter(text)
    }
  }

  private fun getReason(error: Throwable): String {
    if (error is AndroidRuntimeException && error.message != null) {
      if (error.message?.contains("MissingWebViewPackageException") == true) {
        return getString(R.string.fail_reason_webview_is_not_installed)
      }

      // Fallthrough
    } else if (error is Resources.NotFoundException) {
      return getString(
        R.string.fail_reason_some_part_of_webview_not_initialized,
        error.message
      )
    }

    if (error.message != null) {
      return String.format("%s: %s", error.javaClass.simpleName, error.message)
    }

    return error.javaClass.simpleName
  }

  override fun loadDraftIntoViews(chanDescriptor: ChanDescriptor) {
    replyManager.readReply(chanDescriptor) { reply: Reply ->
      name.setText(reply.postName)
      subject.setText(reply.subject)
      flag.setText(reply.flag)
      options.setText(reply.options)

      blockSelectionChange = true
      comment.setText(reply.comment)
      blockSelectionChange = false
    }
  }

  override fun loadViewsIntoDraft(chanDescriptor: ChanDescriptor) {
    replyManager.readReply(chanDescriptor) { reply: Reply ->
      reply.postName = name.text.toString()
      reply.subject = subject.text.toString()
      reply.flag = flag.text.toString()
      reply.options = options.text.toString()
      reply.comment = comment.text.toString()
    }
  }

  override fun adjustSelection(start: Int, amount: Int) {
    try {
      comment.setSelection(start + amount)
    } catch (e: Exception) {
      // set selection to the end if it fails for any reason
      comment.setSelection(comment.text?.length ?: 0)
    }
  }

  override fun openMessage(message: String?) {
    openMessage(message, 5000)
  }

  override fun openMessage(message: String?, hideDelayMs: Int) {
    require(hideDelayMs > 0) { "Bad hideDelayMs: $hideDelayMs" }

    if (message.isNullOrEmpty()) {
      return
    }

    replyLayoutMessageToast.showToast(context, message, hideDelayMs)
  }

  override fun onPosted() {
    AppModuleAndroidUtils.showToast(context, R.string.reply_success)

    threadListLayoutCallbacks?.openReply(false)
    threadListLayoutCallbacks?.requestNewPostLoad()
  }

  override fun setCommentHint(hint: String?) {
    comment.hint = hint
  }

  override fun showCommentCounter(show: Boolean) {
    commentCounter.visibility = if (show) {
      VISIBLE
    } else {
      GONE
    }
  }

  @Subscribe
  fun onEvent(message: RefreshUIMessage?) {
    setWrappingMode(presenter.isExpanded)
  }

  override fun setExpanded(expanded: Boolean, isCleaningUp: Boolean) {
    setWrappingMode(expanded)

    comment.maxLines = if (expanded) {
      REPLY_LAYOUT_EXPANDED_MAX_LINES
    } else {
      REPLY_LAYOUT_COLLAPSED_NORMAL_MAX_LINES
    }

    val startRotation = 1f
    val endRotation = 0f

    val animator = ValueAnimator.ofFloat(
      if (expanded) startRotation else endRotation,
      if (expanded) endRotation else startRotation
    )

    animator.interpolator = DecelerateInterpolator(2f)
    animator.duration = 400
    animator.addUpdateListener { animation ->
      moreDropdown.setRotation(animation.animatedValue as Float)
    }

    if (!isCleaningUp && !expanded) {
      // Update the recycler view's paddings after the animation has ended to make sure it has
      // proper paddings
      animator.doOnEnd { threadListLayoutCallbacks?.updateRecyclerViewPaddings() }
    }

    more.setImageDrawable(moreDropdown)
    animator.start()
  }

  override fun openNameOptions(open: Boolean) {
    nameOptions.visibility = if (open) VISIBLE else GONE
  }

  override fun openSubject(open: Boolean) {
    subject.visibility = if (open) VISIBLE else GONE
  }

  override fun openFlag(open: Boolean) {
    flag.visibility = if (open) VISIBLE else GONE
  }

  override fun openCommentQuoteButton(open: Boolean) {
    commentQuoteButton.visibility = if (open) VISIBLE else GONE
  }

  override fun openCommentSpoilerButton(open: Boolean) {
    commentSpoilerButton.visibility = if (open) VISIBLE else GONE
  }

  override fun openCommentCodeButton(open: Boolean) {
    commentCodeButton.visibility = if (open) VISIBLE else GONE
  }

  override fun openCommentEqnButton(open: Boolean) {
    commentEqnButton.visibility = if (open) VISIBLE else GONE
  }

  override fun openCommentMathButton(open: Boolean) {
    commentMathButton.visibility = if (open) VISIBLE else GONE
  }

  override fun openCommentSJISButton(open: Boolean) {
    commentSJISButton.visibility = if (open) VISIBLE else GONE
  }

  @SuppressLint("SetTextI18n")
  override fun updateCommentCount(count: Int, maxCount: Int, over: Boolean) {
    isCounterOverflowed = over
    commentCounter.text = "$count/$maxCount"

    val textColor = if (over) {
      themeEngine.chanTheme.errorColor
    } else {
      themeEngine.chanTheme.textColorSecondary
    }

    commentCounter.setTextColor(textColor)
  }

  override fun focusComment() {
    //this is a hack to make sure text is selectable
    comment.isEnabled = false
    comment.isEnabled = true
    comment.post { AndroidUtils.requestViewAndKeyboardFocus(comment) }
  }

  override fun onFallbackToV1CaptchaView(autoReply: Boolean) {
    // fallback to v1 captcha window
    presenter.switchPage(ReplyPresenter.Page.AUTHENTICATION, false, autoReply)
  }

  override fun highlightPostNos(postNos: Set<Long>) {
    threadListLayoutCallbacks?.highlightPostNos(postNos)
  }

  override fun onSelectionChanged() {
    if (!blockSelectionChange) {
      presenter.onSelectionChanged()
    }
  }

  private fun setupCommentContextMenu() {
    comment.customSelectionActionModeCallback = object : ActionMode.Callback {
      private var quoteMenuItem: MenuItem? = null
      private var spoilerMenuItem: MenuItem? = null
      private var codeMenuItem: MenuItem? = null
      private var mathMenuItem: MenuItem? = null
      private var eqnMenuItem: MenuItem? = null
      private var sjisMenuItem: MenuItem? = null
      private var processed = false

      override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val chanDescriptor = threadListLayoutCallbacks?.getCurrentChanDescriptor()
          ?: return true

        val chanBoard = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
          ?: return true

        val is4chan = chanDescriptor.siteDescriptor().is4chan()
        val boardCode = chanDescriptor.boardCode()

        // menu item cleanup, these aren't needed for this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          if (menu.size() > 0) {
            menu.removeItem(android.R.id.shareText)
          }
        }

        // setup standard items
        // >greentext
        quoteMenuItem =
          menu.add(Menu.NONE, R.id.reply_selection_action_quote, 1, R.string.post_quote)

        // [spoiler] tags
        if (chanBoard.spoilers) {
          spoilerMenuItem = menu.add(
            Menu.NONE,
            R.id.reply_selection_action_spoiler,
            2,
            R.string.reply_comment_button_spoiler
          )
        }

        // setup specific items in a submenu
        val otherMods = menu.addSubMenu("Modify")

        // g [code]
        if (is4chan && boardCode == "g") {
          codeMenuItem = otherMods.add(
            Menu.NONE,
            R.id.reply_selection_action_code,
            1,
            R.string.reply_comment_button_code
          )
        }

        // sci [eqn] and [math]
        if (is4chan && boardCode == "sci") {
          eqnMenuItem = otherMods.add(
            Menu.NONE,
            R.id.reply_selection_action_eqn,
            2,
            R.string.reply_comment_button_eqn
          )
          mathMenuItem = otherMods.add(
            Menu.NONE,
            R.id.reply_selection_action_math,
            3,
            R.string.reply_comment_button_math
          )
        }

        // jp and vip [sjis]
        if (is4chan && (boardCode == "jp" || boardCode == "vip")) {
          sjisMenuItem = otherMods.add(
            Menu.NONE,
            R.id.reply_selection_action_sjis,
            4,
            R.string.reply_comment_button_sjis
          )
        }
        return true
      }

      override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        return true
      }

      override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when {
          item === quoteMenuItem -> {
            processed = insertQuote()
          }
          item === spoilerMenuItem -> {
            processed = insertTags("[spoiler]", "[/spoiler]")
          }
          item === codeMenuItem -> {
            processed = insertTags("[code]", "[/code]")
          }
          item === eqnMenuItem -> {
            processed = insertTags("[eqn]", "[/eqn]")
          }
          item === mathMenuItem -> {
            processed = insertTags("[math]", "[/math]")
          }
          item === sjisMenuItem -> {
            processed = insertTags("[sjis]", "[/sjis]")
          }
        }

        if (processed) {
          mode.finish()
          processed = false
          return true
        }

        return false
      }

      override fun onDestroyActionMode(mode: ActionMode) {}
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration?) {
    super.onConfigurationChanged(newConfig)

    val newOrientation = newConfig?.orientation
      ?: return

    if (newOrientation == currentOrientation) {
      return
    }

    currentOrientation = newOrientation
    replyLayoutFilesArea.updateLayoutManager()
  }

  override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
  override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

  override fun afterTextChanged(s: Editable) {
    presenter.updateCommentCounter(comment.text)

    val commentInputState = CommentInputState(
      comment.text.toString(),
      comment.selectionStart,
      comment.selectionEnd
    )

    presenter.updateCommentEditingHistory(commentInputState)
  }

  override fun showThread(threadDescriptor: ThreadDescriptor) {
    threadListLayoutCallbacks?.showThread(threadDescriptor)
  }

  override fun onUploadingProgress(fileIndex: Int, totalFiles: Int, percent: Int) {
    if (!::currentProgress.isInitialized || !::currentFile.isInitialized) {
      return
    }

    if (percent in 0..99) {
      currentProgress.visibility = VISIBLE
      currentFile.visibility = VISIBLE
    }

    currentFile.text = context.getString(R.string.upload_file_x_out_of_y, fileIndex, totalFiles)
    currentProgress.text = percent.toString()

    if (fileIndex >= totalFiles && percent >= 100) {
      currentProgress.visibility = View.INVISIBLE
      currentFile.visibility = INVISIBLE
    }
  }

  override fun onCaptchaCountChanged(validCaptchaCount: Int) {
    if (validCaptchaCount <= 0) {
      validCaptchasCount.visibility = INVISIBLE
    } else {
      validCaptchasCount.visibility = VISIBLE
      validCaptchasCount.text = validCaptchaCount.toString()
    }
  }

  interface ThreadListLayoutCallbacks {
    fun highlightPostNos(postNos: Set<Long>)
    fun openReply(open: Boolean)
    fun showThread(threadDescriptor: ThreadDescriptor)
    fun requestNewPostLoad()
    fun getCurrentChanDescriptor(): ChanDescriptor?
    fun showImageReencodingWindow(supportsReencode: Boolean)
    fun updateRecyclerViewPaddings()
    fun measureReplyLayout()
  }

  companion object {
    private const val TAG = "ReplyLayout"
    private val REPLY_COMMENT_MIN_HEIGHT = dp(100f)
    private val REPLY_COMMENT_MIN_HEIGHT_TABLET = dp(128f)

    private const val REPLY_LAYOUT_EXPANDED_MAX_LINES = 10
    private const val REPLY_LAYOUT_COLLAPSED_NORMAL_MAX_LINES = 5
  }
}