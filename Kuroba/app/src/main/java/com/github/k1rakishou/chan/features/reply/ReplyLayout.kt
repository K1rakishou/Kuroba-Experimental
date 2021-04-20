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
import android.text.TextUtils
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
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
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach
import com.github.k1rakishou.chan.features.bypass.BypassMode
import com.github.k1rakishou.chan.features.bypass.CookieResult
import com.github.k1rakishou.chan.features.bypass.SiteAntiSpamCheckBypassController
import com.github.k1rakishou.chan.features.reply.ReplyPresenter.ReplyPresenterCallback
import com.github.k1rakishou.chan.features.reply.data.Reply
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder.CaptchaValidationListener
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.helper.RefreshUIMessage
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
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.doIgnoringTextWatcher
import com.github.k1rakishou.chan.utils.setAlphaFast
import com.github.k1rakishou.chan.utils.setEnabledFast
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.findAllChildren
import com.github.k1rakishou.common.isPointInsideView
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.prefs.StringSetting
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import javax.inject.Inject
import kotlin.coroutines.resume

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
  @Inject
  lateinit var staticBoardFlagInfoRepository: StaticBoardFlagInfoRepository
  @Inject
  lateinit var globalViewStateManager: GlobalViewStateManager

  private var threadListLayoutCallbacks: ThreadListLayoutCallbacks? = null
  private var threadListLayoutFilesCallback: ReplyLayoutFilesArea.ThreadListLayoutCallbacks? = null

  private var blockSelectionChange = false
  private var replyLayoutEnabled = true
  private var currentOrientation: Int = Configuration.ORIENTATION_UNDEFINED

  // Reply views:
  private lateinit var replyInputLayout: ViewGroup
  private lateinit var replyInputMessage: MaterialTextView
  private lateinit var replyInputMessageHolder: FrameLayout
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
  private lateinit var commentRevertChangeButton: AppCompatImageView
  private lateinit var captchaButtonContainer: ConstraintLayout
  private lateinit var captchaView: AppCompatImageView
  private lateinit var validCaptchasCount: TextView
  private lateinit var more: ImageView
  private lateinit var submit: ImageView
  private lateinit var moreDropdown: DropdownArrowDrawable
  private lateinit var replyLayoutFilesArea: ReplyLayoutFilesArea

  private var isCounterOverflowed = false

  private val coroutineScope = KurobaCoroutineScope()
  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(coroutineScope)
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
      quoteMenuItem = menu.add(Menu.NONE, R.id.reply_selection_action_quote, 1, R.string.post_quote)

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

  override val chanDescriptor: ChanDescriptor?
    get() = threadListLayoutCallbacks?.getCurrentChanDescriptor()

  override val selectionStart: Int
    get() = comment.selectionStart

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    EventBus.getDefault().register(this)

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
    EventBus.getDefault().unregister(this)

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
    val matchParent = presenter.isExpanded

    setWrappingMode(matchParent)
    threadListLayoutCallbacks?.updateRecyclerViewPaddings()
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onFinishInflate() {
    super.onFinishInflate()

    this.currentOrientation = resources.configuration.orientation

    // Inflate reply input
    replyInputLayout = AppModuleAndroidUtils.inflate(context, R.layout.layout_reply_input, this, false) as ViewGroup
    replyInputMessage = replyInputLayout.findViewById(R.id.reply_input_message)
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
      removeCallbacks(closeMessageRunnable)
      animateReplyInputMessage(appearance = false)

      presenter.executeFloatingReplyMessageClickAction()
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
      rendezvousCoroutineExecutor.post { presenter.onSubmitClicked(longClicked = true) }
      return@setOnLongClickListener true
    }

    moreDropdown = DropdownArrowDrawable(dp(16f), dp(16f), false)
    submit.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_send_white_24dp))
    more.setImageDrawable(moreDropdown)

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

    onThemeChanged()
  }

  fun onDestroy() {
    this.threadListLayoutCallbacks = null
    this.threadListLayoutFilesCallback = null

    comment.cleanup()
    presenter.unbindReplyImages()
    captchaHolder.clearCallbacks()
    cleanup()

    coroutineScope.cancelChildren()
    rendezvousCoroutineExecutor.stop()
    presenter.destroy()
  }

  fun cleanup() {
    presenter.unbindChanDescriptor()
    removeCallbacks(closeMessageRunnable)
  }

  fun onOpen(open: Boolean) {
    presenter.onOpen(open)

    if (open) {
      replyLayoutFilesArea.updateLayoutManager()
      updateCommentButtonsHolderVisibility()

      if (proxyStorage.isDirty()) {
        openMessage(getString(R.string.reply_proxy_list_is_dirty_message), 10000)
      }
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

    captchaButtonContainer.isVisible = site.actions().postRequiresAuthentication()

    captchaHolder.setListener(chanDescriptor, this)
  }

  override suspend fun bindReplyImages(chanDescriptor: ChanDescriptor) {
    replyLayoutFilesArea.onBind(chanDescriptor, threadListLayoutFilesCallback!!, this)
  }

  override fun unbindReplyImages(chanDescriptor: ChanDescriptor) {
    replyLayoutFilesArea.onUnbind()
  }

  override fun presentController(controller: Controller) {
    threadListLayoutCallbacks?.presentController(controller)
  }

  override suspend fun show2chAntiSpamCheckSolverController(): Boolean {
    BackgroundUtils.ensureMainThread()

    val callbacks = threadListLayoutCallbacks
      ?: return false

    return suspendCancellableCoroutine { continuation ->
      val controller = SiteAntiSpamCheckBypassController(
        context = context,
        bypassMode = BypassMode.Bypass2chAntiSpamCheck,
        urlToOpen = Dvach.URL_HANDLER.url!!.toString()
      ) { cookieResult ->
        continuation.resume(cookieResult is CookieResult.CookieValue)
      }

      callbacks.presentController(controller)
      continuation.invokeOnCancellation { controller.stopPresenting() }
    }
  }

  override fun hideKeyboard() {
    AndroidUtils.hideKeyboard(this)
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

    val compactMode = if (!matchParent && globalWindowInsetsManager.isKeyboardOpened) {
      val displayHeight = AndroidUtils.getDisplaySize(context).y

      this.height + globalWindowInsetsManager.keyboardHeight + newPaddingTop > displayHeight
    } else {
      false
    }

    replyLayoutFilesArea.onWrappingModeChanged(matchParent, compactMode)
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
        v === submit -> presenter.onSubmitClicked(longClicked = false)
        v === commentQuoteButton -> insertQuote()
        v === commentSpoilerButton -> insertTags("[spoiler]", "[/spoiler]")
        v === commentCodeButton -> insertTags("[code]", "[/code]")
        v === commentEqnButton -> insertTags("[eqn]", "[/eqn]")
        v === commentMathButton -> insertTags("[math]", "[/math]")
        v === commentSJISButton -> insertTags("[sjis]", "[/sjis]")
        v === commentRevertChangeButton -> presenter.onRevertChangeButtonClicked()
        v === flag -> showFlagSelector(chanDescriptor)
      }
    }
  }

  private fun showFlagSelector(chanDescriptor: ChanDescriptor?) {
    val boardDescriptor = chanDescriptor?.boardDescriptor()
      ?: return

    val countryFlagSetting = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
      ?.getSettingBySettingId<StringSetting>(SiteSetting.SiteSettingId.CountryFlag)
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

        countryFlagSetting.set(flagInfo.flagKey)
        openFlag(flagInfo)
      })

    threadListLayoutCallbacks?.presentController(floatingListMenuController)
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

  override fun setInputPage() {
    setView(replyInputLayout)
    setWrappingMode(presenter.isExpanded)
    threadListLayoutCallbacks?.updateRecyclerViewPaddings()
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

  override fun loadDraftIntoViews(chanDescriptor: ChanDescriptor) {
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
      blockSelectionChange = false
    }
  }

  override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
    if (ev == null) {
      return false
    }

    if (!replyLayoutEnabled && !submit.isPointInsideView(ev.rawX, ev.rawY)) {
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
    captchaView.setEnabledFast(enable)
    validCaptchasCount.setEnabledFast(enable)
    more.setEnabledFast(enable)
    replyLayoutFilesArea.enableOrDisable(enable)

    if (enable) {
      submit.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_send_white_24dp))
    } else {
      submit.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_clear_white_24dp))
    }

    val tintColor = themeEngine.resolveTintColor(themeEngine.chanTheme.isBackColorDark)
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
    removeCallbacks(closeMessageRunnable)

    replyInputMessage.text = message

    if (!TextUtils.isEmpty(message)) {
      animateReplyInputMessage(appearance = true)
      postDelayed(closeMessageRunnable, hideDelayMs.toLong())
    }
  }

  private fun animateReplyInputMessage(appearance: Boolean) {
    val valueAnimator = if (appearance) {
      ValueAnimator.ofFloat(0f, 1f).apply {
        doOnStart {
          replyInputMessageHolder.setAlphaFast(0f)
          replyInputMessageHolder.setVisibilityFast(View.VISIBLE)
        }
      }
    } else {
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
    replyLayoutMessageToast.showToast(context, R.string.reply_success)

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

  override fun highlightPosts(postDescriptors: Set<PostDescriptor>) {
    threadListLayoutCallbacks?.highlightPosts(postDescriptors)
  }

  override fun onSelectionChanged() {
    if (!blockSelectionChange) {
      presenter.onSelectionChanged()
    }
  }

  private fun processBottomNavViewSwipeUpEvents() {
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
        if (child is ReplyInputEditText) {
          child.setOuterOnTouchListener { event ->
            if (!ChanSettings.replyLayoutOpenCloseGestures.get()) {
              return@setOuterOnTouchListener false
            }

            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
              replyLayoutGestureListener.onActionDownOrMove()
            }

            val result = replyLayoutGestureDetector.onTouchEvent(event)

            if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) {
              replyLayoutGestureListener.onActionUpOrCancel()
            }

            return@setOuterOnTouchListener result
          }
        } else {
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
    replyLayoutFilesArea.updateLayoutManager()
    updateWrappingMode()
  }

  override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
    val commentInputState = CommentInputState(
      comment.text.toString(),
      comment.selectionStart,
      comment.selectionEnd
    )

    presenter.updateInitialCommentEditingHistory(commentInputState)
  }

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
    fun highlightPosts(postDescriptors: Set<PostDescriptor>)
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
    private val REPLY_COMMENT_MIN_HEIGHT_TABLET = dp(128f)

    private const val REPLY_LAYOUT_EXPANDED_MAX_LINES = 10
    private const val REPLY_LAYOUT_COLLAPSED_NORMAL_MAX_LINES = 5
  }
}