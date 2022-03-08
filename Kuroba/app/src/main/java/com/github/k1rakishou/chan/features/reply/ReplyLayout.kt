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
import android.graphics.Color
import android.os.Build
import android.text.Editable
import android.text.Selection
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.ActionMode
import android.view.GestureDetector
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TextView.BufferType
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.helper.CommentEditingHistory.CommentInputState
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.GlobalViewStateManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.KeyboardStateListener
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter
import com.github.k1rakishou.chan.core.repository.StaticBoardFlagInfoRepository
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach
import com.github.k1rakishou.chan.features.bypass.CookieResult
import com.github.k1rakishou.chan.features.bypass.FirewallType
import com.github.k1rakishou.chan.features.bypass.SiteFirewallBypassController
import com.github.k1rakishou.chan.features.reply.ReplyPresenter.ReplyPresenterCallback
import com.github.k1rakishou.chan.features.reply.data.Reply
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder.CaptchaValidationListener
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.helper.AppSettingsUpdateAppRefreshHelper
import com.github.k1rakishou.chan.ui.layout.ThreadListLayout
import com.github.k1rakishou.chan.ui.theme.DropdownArrowDrawable
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.ui.view.LoadView
import com.github.k1rakishou.chan.ui.view.ReplyInputEditText
import com.github.k1rakishou.chan.ui.view.ReplyInputEditText.SelectionChangedListener
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.widget.CancellableToast
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.doIgnoringTextWatcher
import com.github.k1rakishou.chan.utils.setAlphaFast
import com.github.k1rakishou.chan.utils.setEnabledFast
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.findAllChildren
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.isPointInsideView
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.common.selectionEndSafe
import com.github.k1rakishou.common.selectionStartSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.OptionsSetting
import com.github.k1rakishou.prefs.StringSetting
import com.google.android.material.textview.MaterialTextView
import dagger.Lazy
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject

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
  WindowInsetsListener,
  ThemeChangesListener,
  ReplyLayoutFilesArea.ReplyLayoutCallbacks {

  @Inject
  lateinit var _presenter: Lazy<ReplyPresenter>
  @Inject
  lateinit var _captchaHolder: Lazy<CaptchaHolder>
  @Inject
  lateinit var _siteManager: Lazy<SiteManager>
  @Inject
  lateinit var _boardManager: Lazy<BoardManager>
  @Inject
  lateinit var _globalWindowInsetsManager: Lazy<GlobalWindowInsetsManager>
  @Inject
  lateinit var _proxyStorage: Lazy<ProxyStorage>
  @Inject
  lateinit var _replyManager: Lazy<ReplyManager>
  @Inject
  lateinit var _staticBoardFlagInfoRepository: Lazy<StaticBoardFlagInfoRepository>
  @Inject
  lateinit var _globalViewStateManager: Lazy<GlobalViewStateManager>
  @Inject
  lateinit var _appSettingsUpdateAppRefreshHelper: Lazy<AppSettingsUpdateAppRefreshHelper>
  @Inject
  lateinit var _themeEngine: Lazy<ThemeEngine>

  val presenter: ReplyPresenter
    get() = _presenter.get()
  private val captchaHolder: CaptchaHolder
    get() = _captchaHolder.get()
  private val siteManager: SiteManager
    get() = _siteManager.get()
  private val boardManager: BoardManager
    get() = _boardManager.get()
  private val globalWindowInsetsManager: GlobalWindowInsetsManager
    get() = _globalWindowInsetsManager.get()
  private val proxyStorage: ProxyStorage
    get() = _proxyStorage.get()
  private val replyManager: ReplyManager
    get() = _replyManager.get()
  private val staticBoardFlagInfoRepository: StaticBoardFlagInfoRepository
    get() = _staticBoardFlagInfoRepository.get()
  private val globalViewStateManager: GlobalViewStateManager
    get() = _globalViewStateManager.get()
  private val appSettingsUpdateAppRefreshHelper: AppSettingsUpdateAppRefreshHelper
    get() = _appSettingsUpdateAppRefreshHelper.get()
  private val themeEngine: ThemeEngine
    get() = _themeEngine.get()

  private var threadListLayoutCallbacks: ThreadListLayoutCallbacks? = null
  private var threadListLayoutFilesCallback: ReplyLayoutFilesArea.ThreadListLayoutCallbacks? = null

  private var blockSelectionChange = false
  private var replyLayoutEnabled = true
  private var currentOrientation: Int = Configuration.ORIENTATION_UNDEFINED

  // Reply views:
  private lateinit var replyInputLayout: ViewGroup
  private lateinit var replyInputMessage: MaterialTextView
  private lateinit var replyInputCloseErrorIcon: AppCompatImageView
  private lateinit var replyInputMessageHolder: LinearLayout
  private lateinit var name: ColorizableEditText
  private lateinit var subject: ColorizableEditText
  private lateinit var flag: ColorizableTextView
  private lateinit var options: ColorizableEditText
  private lateinit var nameOptions: LinearLayout
  private lateinit var commentButtonsHolder: LinearLayout
  private lateinit var commentQuoteButton: ColorizableBarButton
  private lateinit var commentSpoilerButton: ColorizableBarButton
  private lateinit var commentCodeButton: ColorizableBarButton
  private lateinit var commentEqnButton: ColorizableBarButton
  private lateinit var commentMathButton: ColorizableBarButton
  private lateinit var commentSJISButton: ColorizableBarButton
  private lateinit var comment: ReplyInputEditText
  private lateinit var commentCounter: TextView
  private lateinit var fileCounter: TextView
  private lateinit var commentRevertChangeButton: AppCompatImageView
  private lateinit var captchaButtonContainer: ConstraintLayout
  private lateinit var captchaView: AppCompatImageView
  private lateinit var validCaptchasCount: TextView
  private lateinit var more: ImageView
  private lateinit var submit: ImageView
  private lateinit var moreDropdown: DropdownArrowDrawable
  private lateinit var replyLayoutFilesArea: ReplyLayoutFilesArea

  private var isMessageCounterOverflowed = false
  private var isFileCounterOverflowed = false
  private val textChangeListeners = mutableListOf<TextWatcher>()

  private val coroutineScope = KurobaCoroutineScope()
  private val debouncingCoroutineExecutor = DebouncingCoroutineExecutor(coroutineScope)
  private val wrappingModeUpdateDebouncer = Debouncer(false)
  private val replyLayoutMessageToast = CancellableToast()

  private val replyLayoutGestureListener = ReplyLayoutGestureListener(
    replyLayout = this,
    onSwipedUp = { presenter.expandOrCollapse(expand = true) },
    onSwipedDown = {
      if (globalWindowInsetsManager.isKeyboardOpened) {
        AndroidUtils.hideKeyboard(comment)
        return@ReplyLayoutGestureListener
      }

      if (!presenter.expandOrCollapse(expand = false)) {
        threadListLayoutCallbacks?.openReply(open = false)
      }
    }
  )

  private val replyLayoutGestureDetector = GestureDetector(
    context,
    replyLayoutGestureListener
  )

  private val closeMessageRunnable = Runnable {
    animateReplyInputMessage(appearance = false)
  }

  private val customSelectionActionCallback = object : ActionMode.Callback {
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
      quoteMenuItem = menu.add(
        Menu.NONE,
        R.id.reply_selection_action_quote,
        1,
        getString(R.string.post_quote)
      )

      // [spoiler] tags
      if (chanBoard.spoilers) {
        spoilerMenuItem = menu.add(
          Menu.NONE,
          R.id.reply_selection_action_spoiler,
          2,
          getString(R.string.reply_comment_button_spoiler)
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
          getString(R.string.reply_comment_button_code)
        )
      }

      // sci [eqn] and [math]
      if (is4chan && boardCode == "sci") {
        eqnMenuItem = otherMods.add(
          Menu.NONE,
          R.id.reply_selection_action_eqn,
          2,
          getString(R.string.reply_comment_button_eqn)
        )
        mathMenuItem = otherMods.add(
          Menu.NONE,
          R.id.reply_selection_action_math,
          3,
          getString(R.string.reply_comment_button_math)
        )
      }

      // jp and vip [sjis]
      if (is4chan && (boardCode == "jp" || boardCode == "vip")) {
        sjisMenuItem = otherMods.add(
          Menu.NONE,
          R.id.reply_selection_action_sjis,
          4,
          getString(R.string.reply_comment_button_sjis)
        )
      }

      return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
      return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
      when {
        item === quoteMenuItem -> processed = insertQuote()
        item === spoilerMenuItem -> processed = insertTags("[spoiler]", "[/spoiler]")
        item === codeMenuItem -> processed = insertTags("[code]", "[/code]")
        item === eqnMenuItem -> processed = insertTags("[eqn]", "[/eqn]")
        item === mathMenuItem -> processed = insertTags("[math]", "[/math]")
        item === sjisMenuItem -> processed = insertTags("[sjis]", "[/sjis]")
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

  override val chanDescriptor: ChanDescriptor?
    get() = threadListLayoutCallbacks?.getCurrentChanDescriptor()

  override val selectionStart: Int
    get() = comment.selectionStartSafe()

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    globalWindowInsetsManager.addKeyboardUpdatesListener(this)
    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    themeEngine.addListener(this)
  }

  override fun onKeyboardStateChanged() {
    if (this.visibility == View.GONE) {
      return
    }

    updateWrappingMode()
  }

  override fun onInsetsChanged() {
    if (this.visibility == View.GONE) {
      return
    }

    updateWrappingMode()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    themeEngine.removeListener(this)
    globalWindowInsetsManager.removeKeyboardUpdatesListener(this)
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  override fun onThemeChanged() {
    val replyInputMessageHolderBackColor = if (themeEngine.chanTheme.isBackColorDark) {
      ThemeEngine.manipulateColor(themeEngine.chanTheme.backColor, 1.2f)
    } else {
      ThemeEngine.manipulateColor(themeEngine.chanTheme.backColor, .8f)
    }
    replyInputMessageHolder.setBackgroundColor(replyInputMessageHolderBackColor)

    commentCounter.setTextColor(themeEngine.chanTheme.textColorSecondary)
    val tintColor = ThemeEngine.resolveDrawableTintColor(themeEngine.chanTheme.isBackColorDark)

    if (commentRevertChangeButton.drawable != null) {
      commentRevertChangeButton.setImageDrawable(
        themeEngine.tintDrawable(commentRevertChangeButton.drawable, tintColor)
      )
    }

    moreDropdown.updateColor(tintColor)

    if (submit.drawable != null) {
      submit.setImageDrawable(themeEngine.tintDrawable(submit.drawable, tintColor))
    }

    if (replyInputCloseErrorIcon.drawable != null) {
      replyInputCloseErrorIcon.setImageDrawable(themeEngine.tintDrawable(replyInputCloseErrorIcon.drawable, tintColor))
    }

    commentCounter.setTextColor(
      if (isMessageCounterOverflowed) {
        themeEngine.chanTheme.errorColor
      } else {
        themeEngine.chanTheme.textColorSecondary
      }
    )

    fileCounter.setTextColor(
      if (isFileCounterOverflowed) {
        themeEngine.chanTheme.errorColor
      } else {
        themeEngine.chanTheme.textColorSecondary
      }
    )

    validCaptchasCount.background = themeEngine.tintDrawable(
      context,
      R.drawable.circle_background,
      0xAA000000.toInt()
    )

    validCaptchasCount.setTextColor(Color.WHITE)

    comment.text?.let { commentText ->
      presenter.updateSpans(commentText)
    }
  }

  private fun updateWrappingMode() {
    val matchParent = presenter.isExpanded

    setWrappingMode(matchParent)
    threadListLayoutCallbacks?.updateRecyclerViewPaddings()
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onFinishInflate() {
    super.onFinishInflate()

    this.currentOrientation = resources.configuration.orientation

    val useReplyInputLandscapeMode =
      currentOrientation == Configuration.ORIENTATION_LANDSCAPE && !ChanSettings.isSplitLayoutMode()

    // Inflate reply input
    replyInputLayout = if (useReplyInputLandscapeMode) {
      // Special type of reply layout for phones when using landscape orientation. Do not use together
      // with SPLIT layout mode.
      AppModuleAndroidUtils.inflate(context, R.layout.layout_reply_input_landscape, this, false) as ViewGroup
    } else {
      AppModuleAndroidUtils.inflate(context, R.layout.layout_reply_input, this, false) as ViewGroup
    }

    replyInputMessage = replyInputLayout.findViewById(R.id.reply_input_message)
    replyInputCloseErrorIcon = replyInputLayout.findViewById(R.id.reply_input_close_error_icon)
    replyInputMessageHolder = replyInputLayout.findViewById(R.id.reply_input_message_holder)
    name = replyInputLayout.findViewById(R.id.name)
    subject = replyInputLayout.findViewById(R.id.subject)
    flag = replyInputLayout.findViewById(R.id.flag)
    options = replyInputLayout.findViewById(R.id.options)
    nameOptions = replyInputLayout.findViewById(R.id.name_options)
    commentButtonsHolder = replyInputLayout.findViewById(R.id.comment_buttons)
    commentQuoteButton = replyInputLayout.findViewById(R.id.comment_quote)
    commentSpoilerButton = replyInputLayout.findViewById(R.id.comment_spoiler)
    commentCodeButton = replyInputLayout.findViewById(R.id.comment_code)
    commentEqnButton = replyInputLayout.findViewById(R.id.comment_eqn)
    commentMathButton = replyInputLayout.findViewById(R.id.comment_math)
    commentSJISButton = replyInputLayout.findViewById(R.id.comment_sjis)
    comment = replyInputLayout.findViewById(R.id.comment)
    commentCounter = replyInputLayout.findViewById(R.id.comment_counter)
    fileCounter = replyInputLayout.findViewById(R.id.file_counter)
    commentRevertChangeButton = replyInputLayout.findViewById(R.id.comment_revert_change_button)
    captchaButtonContainer = replyInputLayout.findViewById(R.id.captcha_button_container)
    validCaptchasCount = replyInputLayout.findViewById(R.id.valid_captchas_count)
    more = replyInputLayout.findViewById(R.id.more)
    submit = replyInputLayout.findViewById(R.id.submit)
    replyLayoutFilesArea = replyInputLayout.findViewById(R.id.reply_layout_files_area)

    passChildMotionEventsToDetectors()

    // Setup reply layout views
    commentQuoteButton.setOnClickListener(this)
    commentSpoilerButton.setOnClickListener(this)
    commentCodeButton.setOnClickListener(this)
    commentMathButton.setOnClickListener(this)
    commentEqnButton.setOnClickListener(this)
    commentSJISButton.setOnClickListener(this)
    flag.setOnClickListener(this)

    replyInputMessage.setOnClickListener {
      onReplyInputErrorMessageClicked()
      presenter.executeFloatingReplyMessageClickAction()
    }

    replyInputCloseErrorIcon.setOnClickListener {
      onReplyInputErrorMessageClicked()
    }

    commentRevertChangeButton.setOnClickListener(this)
    commentRevertChangeButton.setOnLongClickListener {
      presenter.clearCommentChangeHistory()

      replyLayoutMessageToast.showToast(
        context,
        context.getString(R.string.reply_layout_comment_change_history_cleared)
      )
      return@setOnLongClickListener true
    }

    comment.addTextChangedListener(this)
    comment.setSelectionChangedListener(this)
    comment.setPlainTextPaste(true)
    comment.setShowLoadingViewFunc { textId ->
      threadListLayoutFilesCallback?.showLoadingView({}, textId)
    }
    comment.setHideLoadingViewFunc {
      threadListLayoutFilesCallback?.hideLoadingView()
    }

    comment.customSelectionActionModeCallback = customSelectionActionCallback

    AndroidUtils.setBoundlessRoundRippleBackground(more)
    more.setOnClickListener(this)

    captchaView = replyInputLayout.findViewById(R.id.captcha_view)
    AndroidUtils.setBoundlessRoundRippleBackground(captchaView)
    captchaView.setOnClickListener(this)

    AndroidUtils.setBoundlessRoundRippleBackground(submit)
    submit.setOnClickListener(this)
    submit.setOnLongClickListener {
      debouncingCoroutineExecutor.post(250L) { presenter.onSubmitClicked(longClicked = true) }
      return@setOnLongClickListener true
    }

    moreDropdown = DropdownArrowDrawable(dp(16f), dp(16f), false)
    submit.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_send_white_24dp))
    more.setImageDrawable(moreDropdown)

    textChangeListeners += name.addTextChangedListener(afterTextChanged = { presenter.loadViewsIntoDraft() })
    textChangeListeners += subject.addTextChangedListener(afterTextChanged = { presenter.loadViewsIntoDraft() })
    textChangeListeners += flag.addTextChangedListener(afterTextChanged = { presenter.loadViewsIntoDraft() })
    textChangeListeners += options.addTextChangedListener(afterTextChanged = { presenter.loadViewsIntoDraft() })

    setView(replyInputLayout)
    elevation = dp(4f).toFloat()
  }

  fun onCreate(
    replyLayoutCallback: ThreadListLayoutCallbacks,
    threadListLayoutCallbacks: ReplyLayoutFilesArea.ThreadListLayoutCallbacks
  ) {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    this.threadListLayoutCallbacks = replyLayoutCallback
    this.threadListLayoutFilesCallback = threadListLayoutCallbacks

    presenter.create(context, this)
    replyLayoutFilesArea.onCreate()

    coroutineScope.launch {
      globalViewStateManager.listenForBottomNavViewSwipeUpGestures()
        .collect { processBottomNavViewSwipeUpEvents() }
    }

    coroutineScope.launch {
      appSettingsUpdateAppRefreshHelper.settingsUpdatedEvent.collect {
        Logger.d(TAG, "Updating ReplyLayout wrapping mode because app settings were updated")
        setWrappingMode(presenter.isExpanded)
      }
    }

    onThemeChanged()
  }

  fun onDestroy() {
    this.threadListLayoutCallbacks = null
    this.threadListLayoutFilesCallback = null

    textChangeListeners.forEach { textWatcher ->
      name.removeTextChangedListener(textWatcher)
      subject.removeTextChangedListener(textWatcher)
      flag.removeTextChangedListener(textWatcher)
      options.removeTextChangedListener(textWatcher)
    }
    textChangeListeners.clear()

    comment.cleanup()
    presenter.unbindReplyImages()
    captchaHolder.clearCallbacks()
    cleanup()

    coroutineScope.cancelChildren()
    debouncingCoroutineExecutor.stop()
    presenter.destroy()
  }

  fun cleanup() {
    presenter.unbindChanDescriptor()
    removeCallbacks(closeMessageRunnable)
  }

  fun onOpen(open: Boolean) {
    presenter.onOpen(open)

    if (open) {
      replyLayoutFilesArea.updateLayoutManager(presenter.isExpanded)
      updateCommentButtonsHolderVisibility()

      if (proxyStorage.isDirty()) {
        openMessage(getString(R.string.reply_proxy_list_is_dirty_message), 10000)
      }

      updateCaptchaContainerVisibility()
    }

    if (open) {
      comment.isFocusable = true
    } else {
      comment.isFocusable = false
      comment.clearFocus()
    }

    val isCatalogReplyLayout = presenter.isCatalogReplyLayout()
    if (isCatalogReplyLayout != null) {
      val threadControllerType = if (isCatalogReplyLayout) {
        ThreadSlideController.ThreadControllerType.Catalog
      } else {
        ThreadSlideController.ThreadControllerType.Thread
      }

      globalViewStateManager.updateIsReplyLayoutOpened(threadControllerType, open)
    }

    coroutineScope.launch {
      enableOrDisableReplyLayout()
    }
  }

  fun showCaptcha(
    chanDescriptor: ChanDescriptor,
    replyMode: ReplyMode,
    autoReply: Boolean,
    afterPostingAttempt: Boolean,
    onFinished: ((Boolean) -> Unit)? = null
  ) {
    presenter.showCaptcha(chanDescriptor, replyMode, autoReply, afterPostingAttempt, onFinished)
  }

  suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
    if (!presenter.bindChanDescriptor(chanDescriptor)) {
      Logger.d(TAG, "bindLoadable failed to bind $chanDescriptor")
      cleanup()
      return
    }

    comment.minHeight = REPLY_COMMENT_MIN_HEIGHT
    captchaHolder.setListener(chanDescriptor, this)
  }

  override suspend fun bindReplyImages(chanDescriptor: ChanDescriptor) {
    val callback = threadListLayoutFilesCallback
      ?: return

    replyLayoutFilesArea.onBind(chanDescriptor, callback, this)
  }

  override fun unbindReplyImages(chanDescriptor: ChanDescriptor) {
    replyLayoutFilesArea.onUnbind()
  }

  override fun presentController(controller: Controller) {
    threadListLayoutCallbacks?.presentController(controller)
  }

  override suspend fun show2chAntiSpamCheckSolverController(): CookieResult {
    BackgroundUtils.ensureMainThread()

    val callbacks = threadListLayoutCallbacks
      ?: return CookieResult.Canceled

    val antiSpamChallengeEndpoint = (siteManager.bySiteDescriptor(Dvach.SITE_DESCRIPTOR) as? Dvach)
      ?.antiSpamChallengeEndpoint
      ?: return CookieResult.Canceled

    return suspendCancellableCoroutine { continuation ->
      val controller = SiteFirewallBypassController(
        context = context,
        firewallType = FirewallType.DvachAntiSpam,
        urlToOpen = antiSpamChallengeEndpoint,
        onResult = { cookieResult -> continuation.resumeValueSafe(cookieResult) }
      )

      callbacks.presentController(controller)
      continuation.invokeOnCancellation { controller.stopPresenting() }
    }
  }

  override fun hideKeyboard() {
    AndroidUtils.hideKeyboard(this)
  }

  override fun updateCaptchaContainerVisibility() {
    val descriptor = chanDescriptor
      ?: return

    val site = siteManager.bySiteDescriptor(descriptor.siteDescriptor())
      ?: return

    val replyMode = site
      .requireSettingBySettingId<OptionsSetting<ReplyMode>>(SiteSetting.SiteSettingId.LastUsedReplyMode)
      .get()

    val siteDoesNotRequireAuthentication = site.actions().postAuthenticate().type == SiteAuthentication.Type.NONE
    if (siteDoesNotRequireAuthentication) {
      captchaButtonContainer.setVisibilityFast(View.GONE)
      return
    }

    val captchaContainerVisibility = when (replyMode) {
      ReplyMode.Unknown,
      ReplyMode.ReplyModeSolveCaptchaManually,
      ReplyMode.ReplyModeSendWithoutCaptcha,
      ReplyMode.ReplyModeSolveCaptchaAuto -> VISIBLE
      ReplyMode.ReplyModeUsePasscode -> GONE
    }

    captchaButtonContainer.setVisibilityFast(captchaContainerVisibility)
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

    val bottomPadding = globalWindowInsetsManager.bottom()
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
    if (v === commentRevertChangeButton) {
      debouncingCoroutineExecutor.post(16) {
        presenter.onRevertChangeButtonClicked()
      }
    } else {
      debouncingCoroutineExecutor.post(250L) {
        when {
          v === more -> presenter.onMoreClicked()
          v === captchaView -> presenter.onAuthenticateClicked()
          v === submit -> presenter.onSubmitClicked(longClicked = false)
          v === commentQuoteButton -> insertQuote()
          v === commentSpoilerButton -> insertTags("[spoiler]", "[/spoiler]")
          v === commentCodeButton -> insertTags("[code]", "[/code]")
          v === commentEqnButton -> insertTags("[eqn]", "[/eqn]")
          v === commentMathButton -> insertTags("[math]", "[/math]")
          v === commentSJISButton -> insertTags("[sjis]", "[/sjis]")
          v === flag -> showFlagSelector(chanDescriptor)
        }

        if (v === captchaView || v === submit) {
          Selection.removeSelection(comment.text)
        }
      }
    }
  }

  private fun showFlagSelector(chanDescriptor: ChanDescriptor?) {
    val boardDescriptor = chanDescriptor?.boardDescriptor()
      ?: return

    val lastUsedCountryFlagPerBoardSetting = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
      ?.getSettingBySettingId<StringSetting>(SiteSetting.SiteSettingId.LastUsedCountryFlagPerBoard)
      ?: return

    val flagInfoList = staticBoardFlagInfoRepository.getFlagInfoList(boardDescriptor)
    if (flagInfoList.isEmpty()) {
      return
    }

    val lastUsedFlagInfo = staticBoardFlagInfoRepository.getLastUsedFlagInfo(boardDescriptor)
      ?: return

    val floatingListMenuItems = mutableListOf<FloatingListMenuItem>()

    flagInfoList.forEach { flagInfo ->
      floatingListMenuItems += CheckableFloatingListMenuItem(
        key = flagInfo.flagKey,
        name = "${flagInfo.flagKey} (${flagInfo.flagDescription})",
        value = flagInfo,
        isCurrentlySelected = flagInfo.flagKey == lastUsedFlagInfo.flagKey
      )
    }

    val floatingListMenuController = FloatingListMenuController(
      context,
      globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      floatingListMenuItems,
      { floatingListMenuItem ->
        val flagInfo = floatingListMenuItem.value as? StaticBoardFlagInfoRepository.FlagInfo
          ?: return@FloatingListMenuController

        staticBoardFlagInfoRepository.storeLastUsedFlag(
          lastUsedCountryFlagPerBoardSetting,
          flagInfo,
          boardDescriptor.boardCode
        )

        openFlag(flagInfo)
      })

    threadListLayoutCallbacks?.presentController(floatingListMenuController)
  }

  private fun insertQuote(): Boolean {
    val selectionStart = comment.selectionStartSafe()
    val selectionEnd = comment.selectionEndSafe()

    val textLines = comment.text
      ?.subSequence(selectionStart, selectionEnd)
      ?.toString()
      ?.split("\n")
      ?: emptyList()

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
    val selectionStart = comment.selectionStartSafe()
    val selectionEnd = comment.selectionEndSafe()

    val hadSelectedText = selectionStart != selectionEnd

    comment.text?.insert(selectionEnd, after)
    comment.text?.insert(selectionStart, before)

    if (!hadSelectedText) {
      // In case of when the tags are inserted and there is no selected text, the text cursor will be
      // moved between the tags, e.g.:
      // [tag]<cursor>[/tag]
      // Otherwise it will remain at the end of the closing tag, e.g.:
      // [tag]some text[/tag]<cursor>
      val newSelectionCenter = selectionEnd - after.length
      if (newSelectionCenter >= 0) {
        comment.setSelection(newSelectionCenter)
      }
    }

    return true
  }

  override fun setInputPage() {
    setView(replyInputLayout)
    setWrappingMode(presenter.isExpanded)
    threadListLayoutCallbacks?.updateRecyclerViewPaddings()
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
      setText(prevCommentInputState.text, BufferType.EDITABLE)

      if (prevCommentInputState.isSelectionValid()) {
        setSelection(
          prevCommentInputState.selectionStart,
          prevCommentInputState.selectionEnd
        )
      }

      presenter.updateCommentCounter(text)

      comment.text?.let { commentText ->
        presenter.updateSpans(commentText)
      }
    }
  }

  override fun loadDraftIntoViews(chanDescriptor: ChanDescriptor) {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return
    }

    val lastUsedFlagInfo = staticBoardFlagInfoRepository.getLastUsedFlagInfo(chanDescriptor.boardDescriptor())

    replyManager.readReply(chanDescriptor) { reply: Reply ->
      name.setText(reply.postName)
      subject.setText(reply.subject)

      if (lastUsedFlagInfo != null) {
        flag.text = getString(R.string.reply_flag_format, lastUsedFlagInfo.flagKey)
      }

      options.setText(reply.options)

      blockSelectionChange = true
      comment.setText(reply.comment)

      if (reply.comment.isNotEmpty()) {
        comment.setSelection(reply.comment.length)
      }

      presenter.updateCommentCounter(comment.text)
      blockSelectionChange = false
    }
  }

  override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
    if (ev == null) {
      return false
    }

    val isTouchingAllowedButtons = submit.isPointInsideView(ev.rawX, ev.rawY)
      || captchaButtonContainer.isPointInsideView(ev.rawX, ev.rawY)
      || more.isPointInsideView(ev.rawX, ev.rawY)

    if (!replyLayoutEnabled && !isTouchingAllowedButtons) {
      // Intercept touch events for all children (except the submit button) when reply layout is disabled
      return true
    }

    return super.onInterceptTouchEvent(ev)
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    return true
  }

  override suspend fun enableOrDisableReplyLayout() {
    val enable = presenter.isReplyLayoutEnabled()
    replyLayoutEnabled = enable

    replyInputMessage.setEnabledFast(enable)
    name.setEnabledFast(enable)
    subject.setEnabledFast(enable)
    flag.setEnabledFast(enable)
    options.setEnabledFast(enable)
    commentQuoteButton.setEnabledFast(enable)
    commentSpoilerButton.setEnabledFast(enable)
    commentCodeButton.setEnabledFast(enable)
    commentEqnButton.setEnabledFast(enable)
    commentMathButton.setEnabledFast(enable)
    commentSJISButton.setEnabledFast(enable)
    comment.setEnabledFast(enable)
    commentCounter.setEnabledFast(enable)
    commentRevertChangeButton.setEnabledFast(enable)
    replyLayoutFilesArea.enableOrDisable(enable)

    if (enable) {
      submit.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_send_white_24dp))
    } else {
      submit.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_clear_white_24dp))
    }

    val tintColor = ThemeEngine.resolveDrawableTintColor(themeEngine.chanTheme.isBackColorDark)
    submit.setImageDrawable(themeEngine.tintDrawable(submit.drawable, tintColor))
  }

  override fun loadViewsIntoDraft(chanDescriptor: ChanDescriptor) {
    val lastUsedFlagInfo = staticBoardFlagInfoRepository.getLastUsedFlagInfo(chanDescriptor.boardDescriptor())

    replyManager.readReply(chanDescriptor) { reply: Reply ->
      reply.postName = name.text.toString()
      reply.subject = subject.text.toString()
      reply.options = options.text.toString()
      reply.comment = comment.text.toString()

      if (lastUsedFlagInfo != null) {
        reply.flag = lastUsedFlagInfo.flagKey
      }
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

    if (message != null && presenter.floatingReplyMessageHasClickAction) {
      replyInputMessage.text = getString(R.string.reply_error_message_with_action, message)
    } else {
      replyInputMessage.text = message
    }

    if (message.isNotNullNorBlank()) {
      animateReplyInputMessage(appearance = true)
      removeCallbacks(closeMessageRunnable)
      postDelayed(closeMessageRunnable, hideDelayMs.toLong())
    }
  }

  private fun animateReplyInputMessage(appearance: Boolean) {
    val valueAnimator = if (appearance) {
      if (replyInputMessageHolder.visibility == View.VISIBLE) {
        return
      }

      ValueAnimator.ofFloat(0f, 1f).apply {
        doOnStart {
          replyInputMessageHolder.setAlphaFast(0f)
          replyInputMessageHolder.setVisibilityFast(View.VISIBLE)
        }
      }
    } else {
      if (replyInputMessageHolder.visibility == View.GONE) {
        return
      }

      ValueAnimator.ofFloat(1f, 0f).apply {
        doOnEnd {
          replyInputMessageHolder.setVisibilityFast(View.GONE)
          presenter.removeFloatingReplyMessageClickAction()
        }
      }
    }

    valueAnimator.setDuration(200)
    valueAnimator.addUpdateListener { animator ->
      val alpha = animator.animatedValue as Float
      replyInputMessageHolder.alpha = alpha
    }
    valueAnimator.start()
  }

  override fun openOrCloseReply(open: Boolean) {
    threadListLayoutCallbacks?.openReply(open)
  }

  override fun onPosted() {
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

    replyLayoutFilesArea.updateLayoutManager(expanded)
  }

  override fun openNameOptions(open: Boolean) {
    nameOptions.visibility = if (open) VISIBLE else GONE
  }

  override fun openSubject(open: Boolean) {
    subject.visibility = if (open) VISIBLE else GONE
  }

  override fun openFlag(flagInfo: StaticBoardFlagInfoRepository.FlagInfo) {
    flag.visibility = VISIBLE
    flag.text = getString(R.string.reply_flag_format, flagInfo.flagKey)
  }

  override fun hideFlag() {
    flag.visibility = GONE
  }

  override fun openCommentQuoteButton(open: Boolean) {
    commentQuoteButton.visibility = if (open) VISIBLE else GONE
    updateCommentButtonsHolderVisibility()
  }

  override fun openCommentSpoilerButton(open: Boolean) {
    commentSpoilerButton.visibility = if (open) VISIBLE else GONE
    updateCommentButtonsHolderVisibility()
  }

  override fun openCommentCodeButton(open: Boolean) {
    commentCodeButton.visibility = if (open) VISIBLE else GONE
    updateCommentButtonsHolderVisibility()
  }

  override fun openCommentEqnButton(open: Boolean) {
    commentEqnButton.visibility = if (open) VISIBLE else GONE
    updateCommentButtonsHolderVisibility()
  }

  override fun openCommentMathButton(open: Boolean) {
    commentMathButton.visibility = if (open) VISIBLE else GONE
    updateCommentButtonsHolderVisibility()
  }

  override fun openCommentSJISButton(open: Boolean) {
    commentSJISButton.visibility = if (open) VISIBLE else GONE
    updateCommentButtonsHolderVisibility()
  }

  private fun updateCommentButtonsHolderVisibility() {
    if (commentQuoteButton.visibility == View.VISIBLE ||
      commentSpoilerButton.visibility == View.VISIBLE ||
      commentCodeButton.visibility == View.VISIBLE ||
      commentEqnButton.visibility == View.VISIBLE ||
      commentMathButton.visibility == View.VISIBLE ||
      commentSJISButton.visibility == View.VISIBLE
    ) {
      if (commentButtonsHolder.visibility != View.VISIBLE) {
        commentButtonsHolder.visibility = View.VISIBLE
      }

      return
    }

    if (commentButtonsHolder.visibility != View.GONE) {
      commentButtonsHolder.visibility = View.GONE
    }
  }

  private fun onReplyInputErrorMessageClicked() {
    hideReplyInputErrorMessage()
    presenter.onReplyInputErrorMessageClicked()
  }

  override fun hideReplyInputErrorMessage() {
    removeCallbacks(closeMessageRunnable)
    animateReplyInputMessage(appearance = false)
  }

  @SuppressLint("SetTextI18n")
  override fun updateCommentCount(count: Int, maxCount: Int, over: Boolean) {
    isMessageCounterOverflowed = over
    commentCounter.text = "$count/$maxCount"

    val textColor = if (over) {
      themeEngine.chanTheme.errorColor
    } else {
      themeEngine.chanTheme.textColorSecondary
    }

    commentCounter.setTextColor(textColor)
  }

  override fun updateSelectedFilesCounter(selectedCount: Int, maxAllowedCount: Int, totalCount: Int) {
    isFileCounterOverflowed = selectedCount > maxAllowedCount

    if (totalCount <= 0) {
      fileCounter.setVisibilityFast(View.GONE)
      return
    }

    val textColor = if (isFileCounterOverflowed) {
      themeEngine.chanTheme.errorColor
    } else {
      themeEngine.chanTheme.textColorSecondary
    }

    fileCounter.setVisibilityFast(View.VISIBLE)
    fileCounter.text = "${selectedCount}/${maxAllowedCount} ($totalCount)"
    fileCounter.setTextColor(textColor)
  }

  override fun focusComment() {
    //this is a hack to make sure text is selectable
    comment.isEnabled = false
    comment.isEnabled = true
    comment.post { AndroidUtils.requestViewAndKeyboardFocus(comment) }
  }

  override fun highlightPosts(postDescriptors: Set<PostDescriptor>) {
    threadListLayoutCallbacks?.highlightPosts(postDescriptors, blink = false)
  }

  override fun onSelectionChanged() {
    if (!blockSelectionChange) {
      presenter.loadViewsIntoDraft()
      presenter.highlightQuotes()
    }
  }

  private fun processBottomNavViewSwipeUpEvents() {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return
    }

    val isCatalogReplyLayout = presenter.isCatalogReplyLayout()
      ?: return

    val currentFocusedController = threadListLayoutCallbacks?.currentFocusedController()
      ?: ThreadPresenter.CurrentFocusedController.None

    val canOpen = when (currentFocusedController) {
      ThreadPresenter.CurrentFocusedController.Catalog -> isCatalogReplyLayout
      ThreadPresenter.CurrentFocusedController.Thread -> !isCatalogReplyLayout
      ThreadPresenter.CurrentFocusedController.None -> return
    }

    if (!canOpen) {
      return
    }

    threadListLayoutCallbacks?.openReply(open = true)
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun passChildMotionEventsToDetectors() {
    replyInputLayout
      .findAllChildren<View>()
      .forEach { child ->
        if (child === comment) {
          // Do not use the comment
          return@forEach
        }

        child.setOnTouchListener { v, event ->
          if (!ChanSettings.replyLayoutOpenCloseGestures.get()) {
            return@setOnTouchListener false
          }

          if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
            replyLayoutGestureListener.onActionDownOrMove()
          }

          val result = replyLayoutGestureDetector.onTouchEvent(event)

          if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) {
            replyLayoutGestureListener.onActionUpOrCancel()
          }

          return@setOnTouchListener result
        }
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

    updateCommentButtonsHolderVisibility()
    replyLayoutFilesArea.updateLayoutManager(presenter.isExpanded)
    updateWrappingMode()
  }

  override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
    val commentInputState = CommentInputState(
      comment.text.toString(),
      comment.selectionStartSafe(),
      comment.selectionEndSafe()
    )

    presenter.updateInitialCommentEditingHistory(commentInputState)
  }

  override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

  override fun afterTextChanged(editable: Editable) {
    presenter.updateCommentCounter(comment.text)

    val commentInputState = CommentInputState(
      comment.text.toString(),
      comment.selectionStartSafe(),
      comment.selectionEndSafe()
    )

    presenter.updateCommentEditingHistory(commentInputState)
    presenter.updateSpans(editable)
  }

  override fun showThread(threadDescriptor: ThreadDescriptor) {
    threadListLayoutCallbacks?.showThread(threadDescriptor)
  }

  override fun onCaptchaCountChanged(validCaptchaCount: Int) {
    if (validCaptchaCount <= 0) {
      validCaptchasCount.visibility = INVISIBLE
    } else {
      validCaptchasCount.visibility = VISIBLE
      validCaptchasCount.text = validCaptchaCount.toString()
    }
  }

  override fun isReplyLayoutOpened(): Boolean {
    return threadListLayoutCallbacks?.isReplyLayoutOpened() ?: false
  }

  fun onImageOptionsComplete() {
    replyLayoutFilesArea.onImageOptionsComplete()
  }

  interface ThreadListLayoutCallbacks {
    fun isReplyLayoutOpened(): Boolean

    fun currentFocusedController(): ThreadPresenter.CurrentFocusedController
    fun highlightPosts(postDescriptors: Set<PostDescriptor>?, blink: Boolean)
    fun openReply(open: Boolean)
    fun showThread(threadDescriptor: ThreadDescriptor)
    fun requestNewPostLoad()
    fun getCurrentChanDescriptor(): ChanDescriptor?
    fun updateRecyclerViewPaddings()
    fun measureReplyLayout()
    fun presentController(controller: Controller)
  }

  companion object {
    private const val TAG = "ReplyLayout"
    private val REPLY_COMMENT_MIN_HEIGHT = dp(100f)

    private const val REPLY_LAYOUT_EXPANDED_MAX_LINES = 15
    private const val REPLY_LAYOUT_COLLAPSED_NORMAL_MAX_LINES = 5
  }
}