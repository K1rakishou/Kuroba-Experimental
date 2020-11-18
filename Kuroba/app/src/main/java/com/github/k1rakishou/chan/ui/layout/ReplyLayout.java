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
package com.github.k1rakishou.chan.ui.layout;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AndroidRuntimeException;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.StartActivity;
import com.github.k1rakishou.chan.core.helper.CommentEditingHistory;
import com.github.k1rakishou.chan.core.helper.ProxyStorage;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.manager.KeyboardStateListener;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.presenter.ReplyPresenter;
import com.github.k1rakishou.chan.core.site.Site;
import com.github.k1rakishou.chan.core.site.SiteAuthentication;
import com.github.k1rakishou.chan.core.site.http.Reply;
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback;
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutInterface;
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder;
import com.github.k1rakishou.chan.ui.captcha.CaptchaLayout;
import com.github.k1rakishou.chan.ui.captcha.GenericWebViewAuthenticationLayout;
import com.github.k1rakishou.chan.ui.captcha.LegacyCaptchaLayout;
import com.github.k1rakishou.chan.ui.captcha.v1.CaptchaNojsLayoutV1;
import com.github.k1rakishou.chan.ui.captcha.v2.CaptchaNoJsLayoutV2;
import com.github.k1rakishou.chan.ui.helper.HintPopup;
import com.github.k1rakishou.chan.ui.helper.ImagePickDelegate;
import com.github.k1rakishou.chan.ui.helper.RefreshUIMessage;
import com.github.k1rakishou.chan.ui.theme.DropdownArrowDrawable;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCheckBox;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView;
import com.github.k1rakishou.chan.ui.view.LoadView;
import com.github.k1rakishou.chan.ui.view.SelectionListeningEditText;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.chan.utils.ImageDecoder;
import com.github.k1rakishou.chan.utils.KtExtensionsKt;
import com.github.k1rakishou.common.AndroidUtils;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.model.data.board.ChanBoard;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Set;

import javax.inject.Inject;

import kotlin.Unit;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast;
import static com.github.k1rakishou.common.AndroidUtils.dp;
import static com.github.k1rakishou.common.AndroidUtils.getString;
import static com.github.k1rakishou.common.AndroidUtils.hideKeyboard;
import static com.github.k1rakishou.common.AndroidUtils.requestViewAndKeyboardFocus;
import static com.github.k1rakishou.core_themes.ThemeEngine.isDarkColor;

// TODO(KurobaEx): When catalog reply is opened and we open any thread via "tabs" the opened thread
//  will be glitched, it won't have the bottomNavBar because we have a replyLayout opened.
public class ReplyLayout extends LoadView implements View.OnClickListener,
        ReplyPresenter.ReplyPresenterCallback,
        TextWatcher,
        ImageDecoder.ImageDecoderCallback,
        SelectionListeningEditText.SelectionChangedListener,
        CaptchaHolder.CaptchaValidationListener,
        KeyboardStateListener,
        ThemeEngine.ThemeChangesListener {
    private static final String TAG = "ReplyLayout";
    private static final int ATTACH_IMAGE_BY_URL_HINT_OFFSET_X = dp(64f);

    @Inject
    ReplyPresenter presenter;
    @Inject
    CaptchaHolder captchaHolder;
    @Inject
    ThemeEngine themeEngine;
    @Inject
    SiteManager siteManager;
    @Inject
    BoardManager boardManager;
    @Inject
    GlobalWindowInsetsManager globalWindowInsetsManager;
    @Inject
    ProxyStorage proxyStorage;

    private ReplyLayoutCallback callback;
    private AuthenticationLayoutInterface authenticationLayout;

    private boolean blockSelectionChange = false;

    // Progress view (when sending request to the server)
    private View progressLayout;
    private ColorizableTextView currentProgress;

    // Reply views:
    private View replyLayoutTopDivider;
    private View replyInputLayout;
    private TextView message;
    private ColorizableEditText name;
    private ColorizableEditText subject;
    private ColorizableEditText flag;
    private ColorizableEditText options;
    private ColorizableEditText fileName;
    private LinearLayout nameOptions;
    private ColorizableBarButton commentQuoteButton;
    private ColorizableBarButton commentSpoilerButton;
    private ColorizableBarButton commentCodeButton;
    private ColorizableBarButton commentEqnButton;
    private ColorizableBarButton commentMathButton;
    private ColorizableBarButton commentSJISButton;
    private SelectionListeningEditText comment;
    private TextView commentCounter;
    private AppCompatImageView commentRevertChangeButton;
    private ColorizableCheckBox spoiler;
    private LinearLayout previewHolder;
    private ImageView preview;
    private ColorizableTextView previewMessage;
    private ImageView attach;
    private ConstraintLayout captcha;
    private TextView validCaptchasCount;
    private ImageView more;
    private ImageView submit;
    private DropdownArrowDrawable moreDropdown;
    @Nullable
    private HintPopup hintPopup = null;
    private boolean isCounterOverflowed = false;

    // Captcha views:
    private FrameLayout captchaContainer;
    private ImageView captchaHardReset;

    private Runnable closeMessageRunnable = new Runnable() {
        @Override
        public void run() {
            message.setVisibility(GONE);
        }
    };

    public ReplyLayout(Context context) {
        this(context, null);
    }

    public ReplyLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ReplyLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        EventBus.getDefault().register(this);
        globalWindowInsetsManager.addKeyboardUpdatesListener(this);
        themeEngine.addListener(this);
    }

    @Override
    public void onKeyboardStateChanged() {
        updateWrappingMode();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        dismissHint();

        themeEngine.removeListener(this);
        EventBus.getDefault().unregister(this);
        globalWindowInsetsManager.removeKeyboardUpdatesListener(this);
    }

    @Override
    public void onThemeChanged() {
        commentCounter.setTextColor(themeEngine.getChanTheme().getTextColorSecondary());
        boolean isDarkColor = isDarkColor(themeEngine.chanTheme.getBackColor());

        if (attach.getDrawable() != null) {
            attach.setImageDrawable(themeEngine.tintDrawable(attach.getDrawable(), isDarkColor));
        }

        replyLayoutTopDivider.setBackgroundColor(
                ThemeEngine.updateAlphaForColor(
                        themeEngine.getChanTheme().getTextColorHint(),
                        (int) (0.4f * 255f)
                )
        );

        moreDropdown.updateColor(themeEngine.resolveTintColor(isDarkColor));

        if (submit.getDrawable() != null) {
            submit.setImageDrawable(themeEngine.tintDrawable(submit.getDrawable(), isDarkColor));
        }

        int textColor = isCounterOverflowed
                ? themeEngine.getChanTheme().getErrorColor()
                : themeEngine.getChanTheme().getTextColorSecondary();

        commentCounter.setTextColor(textColor);
    }

    private void updateWrappingMode() {
        ReplyPresenter.Page page = presenter.getPage();
        boolean matchParent;

        if (page == ReplyPresenter.Page.INPUT) {
            matchParent = presenter.isExpanded();
        } else if (page == ReplyPresenter.Page.LOADING) {
            matchParent = false;
        } else if (page == ReplyPresenter.Page.AUTHENTICATION) {
            matchParent = true;
        } else {
            throw new IllegalStateException("Unknown Page: " + page);
        }

        setWrappingMode(matchParent);
        callback.updatePadding();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (!isInEditMode()) {
            AppModuleAndroidUtils.extractStartActivityComponent(getContext())
                    .inject(this);
        }

        // Inflate reply input
        replyInputLayout = AndroidUtils.inflate(getContext(), R.layout.layout_reply_input, this, false);
        replyLayoutTopDivider = replyInputLayout.findViewById(R.id.comment_top_divider);
        message = replyInputLayout.findViewById(R.id.message);
        name = replyInputLayout.findViewById(R.id.name);
        subject = replyInputLayout.findViewById(R.id.subject);
        flag = replyInputLayout.findViewById(R.id.flag);
        options = replyInputLayout.findViewById(R.id.options);
        fileName = replyInputLayout.findViewById(R.id.file_name);
        nameOptions = replyInputLayout.findViewById(R.id.name_options);
        commentQuoteButton = replyInputLayout.findViewById(R.id.comment_quote);
        commentSpoilerButton = replyInputLayout.findViewById(R.id.comment_spoiler);
        commentCodeButton = replyInputLayout.findViewById(R.id.comment_code);
        commentEqnButton = replyInputLayout.findViewById(R.id.comment_eqn);
        commentMathButton = replyInputLayout.findViewById(R.id.comment_math);
        commentSJISButton = replyInputLayout.findViewById(R.id.comment_sjis);
        comment = replyInputLayout.findViewById(R.id.comment);
        commentCounter = replyInputLayout.findViewById(R.id.comment_counter);
        commentRevertChangeButton = replyInputLayout.findViewById(R.id.comment_revert_change_button);
        spoiler = replyInputLayout.findViewById(R.id.spoiler);
        preview = replyInputLayout.findViewById(R.id.preview);
        previewHolder = replyInputLayout.findViewById(R.id.preview_holder);
        previewMessage = replyInputLayout.findViewById(R.id.preview_message);
        attach = replyInputLayout.findViewById(R.id.attach);
        captcha = replyInputLayout.findViewById(R.id.captcha_container);
        validCaptchasCount = replyInputLayout.findViewById(R.id.valid_captchas_count);
        more = replyInputLayout.findViewById(R.id.more);
        submit = replyInputLayout.findViewById(R.id.submit);

        progressLayout = AndroidUtils.inflate(getContext(), R.layout.layout_reply_progress, this, false);
        currentProgress = progressLayout.findViewById(R.id.current_progress);

        // Setup reply layout views
        fileName.setOnLongClickListener(v -> presenter.fileNameLongClicked());
        commentQuoteButton.setOnClickListener(this);
        commentSpoilerButton.setOnClickListener(this);
        commentCodeButton.setOnClickListener(this);
        commentMathButton.setOnClickListener(this);
        commentEqnButton.setOnClickListener(this);
        commentSJISButton.setOnClickListener(this);
        commentRevertChangeButton.setOnClickListener(this);

        comment.addTextChangedListener(this);
        comment.setSelectionChangedListener(this);
        comment.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                hideKeyboard(comment);
            }
        });
        comment.setPlainTextPaste(true);
        setupCommentContextMenu();

        previewHolder.setOnClickListener(this);

        AndroidUtils.setBoundlessRoundRippleBackground(more);
        more.setOnClickListener(this);

        AndroidUtils.setBoundlessRoundRippleBackground(attach);
        attach.setOnClickListener(this);
        attach.setOnLongClickListener(v -> {
            presenter.onAttachClicked(true);
            return true;
        });
        attach.setClickable(true);
        attach.setFocusable(true);

        ImageView captchaImage = replyInputLayout.findViewById(R.id.captcha);
        AndroidUtils.setBoundlessRoundRippleBackground(captchaImage);
        captcha.setOnClickListener(this);

        AndroidUtils.setBoundlessRoundRippleBackground(submit);
        submit.setOnClickListener(this);
        submit.setOnLongClickListener(v -> {
            presenter.onSubmitClicked(true);
            return true;
        });

        // Inflate captcha layout
        captchaContainer = (FrameLayout) AndroidUtils.inflate(
                getContext(),
                R.layout.layout_reply_captcha,
                this,
                false
        );

        captchaHardReset = captchaContainer.findViewById(R.id.reset);

        // Setup captcha layout views
        captchaContainer.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        AndroidUtils.setBoundlessRoundRippleBackground(captchaHardReset);
        captchaHardReset.setOnClickListener(this);

        moreDropdown = new DropdownArrowDrawable(
                dp(16),
                dp(16),
                false
        );

        attach.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_image_white_24dp));
        submit.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_send_white_24dp));

        more.setImageDrawable(moreDropdown);
        captchaHardReset.setImageDrawable(
                ContextCompat.getDrawable(getContext(), R.drawable.ic_refresh_white_24dp)
        );

        setView(replyInputLayout);
        setElevation(dp(4));

        // Presenter
        presenter.create(this);
        onThemeChanged();
    }

    public void setCallback(ReplyLayoutCallback callback) {
        this.callback = callback;
    }

    public ReplyPresenter getPresenter() {
        return presenter;
    }

    public void onOpen(boolean open) {
        presenter.onOpen(open);

        if (open && ChanSettings.replyOpenCounter.increase() == 1) {
            showUrlImagePasteHint();
        } else {
            dismissHint();
        }

        if (open && proxyStorage.isDirty()) {
            openMessage(getString(R.string.reply_proxy_list_is_dirty_message), 10000);
        }
    }

    private void showUrlImagePasteHint() {
        postDelayed(() -> {
            dismissHint();

            hintPopup = HintPopup.show(
                    getContext(),
                    attach,
                    AndroidUtils.getString(R.string.reply_attach_long_click_hint),
                    ATTACH_IMAGE_BY_URL_HINT_OFFSET_X,
                    0
            );
        }, 600);
    }

    private void dismissHint() {
        if (hintPopup != null) {
            hintPopup.dismiss();
            hintPopup = null;
        }
    }

    public void bindLoadable(ChanDescriptor chanDescriptor) {
        Site site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor());
        if (site == null) {
            Logger.e(TAG, "bindLoadable couldn't find site " + chanDescriptor.siteDescriptor());
            return;
        }

        if (!presenter.bindChanDescriptor(chanDescriptor)) {
            Logger.e(TAG, "bindLoadable failed to bind " + chanDescriptor);
            cleanup();
            return;
        }

        if (site.actions().postRequiresAuthentication()) {
            comment.setMinHeight(dp(144));
        } else {
            captcha.setVisibility(GONE);
        }

        captchaHolder.setListener(chanDescriptor, this);
    }

    public void clearCaptchaHolderCallbacks() {
        captchaHolder.clearCallbacks();
    }

    public void cleanup() {
        presenter.unbindChanDescriptor();
        removeCallbacks(closeMessageRunnable);
    }

    public boolean onBack() {
        return presenter.onBack();
    }

    private void setWrappingMode(boolean matchParent) {
        LayoutParams prevLayoutParams = (LayoutParams) getLayoutParams();
        LayoutParams newLayoutParams = new LayoutParams((LayoutParams) getLayoutParams());
        newLayoutParams.width = MATCH_PARENT;
        newLayoutParams.height = matchParent ? MATCH_PARENT : WRAP_CONTENT;

        if (matchParent) {
            newLayoutParams.gravity = Gravity.TOP;
        } else {
            newLayoutParams.gravity = Gravity.BOTTOM;
        }

        int bottomPadding = 0;
        if (!globalWindowInsetsManager.isKeyboardOpened()) {
            bottomPadding = globalWindowInsetsManager.bottom();
        }

        int paddingTop = ((ThreadListLayout) getParent()).toolbarHeight();

        if (prevLayoutParams.width == newLayoutParams.width
                && prevLayoutParams.height == newLayoutParams.height
                && prevLayoutParams.gravity == newLayoutParams.gravity
                && getPaddingBottom() == bottomPadding
                && (matchParent && getPaddingTop() == paddingTop)) {
            return;
        }

        if (matchParent) {
            setPadding(0, paddingTop, 0, bottomPadding);
        } else {
            setPadding(0, 0, 0, bottomPadding);
        }

        setLayoutParams(newLayoutParams);
    }

    @Override
    public void onClick(View v) {
        if (v == more) {
            presenter.onMoreClicked();
        } else if (v == attach) {
            presenter.onAttachClicked(false);
        } else if (v == captcha) {
            presenter.onAuthenticateCalled();
        } else if (v == submit) {
            presenter.onSubmitClicked(false);
        } else if (v == previewHolder) {
            // prevent immediately removing the file
            attach.setClickable(false);
            callback.showImageReencodingWindow(presenter.isAttachedFileSupportedForReencoding());
        } else if (v == captchaHardReset) {
            if (authenticationLayout != null) {
                authenticationLayout.hardReset();
            }
        } else if (v == commentQuoteButton) {
            insertQuote();
        } else if (v == commentSpoilerButton) {
            insertTags("[spoiler]", "[/spoiler]");
        } else if (v == commentCodeButton) {
            insertTags("[code]", "[/code]");
        } else if (v == commentEqnButton) {
            insertTags("[eqn]", "[/eqn]");
        } else if (v == commentMathButton) {
            insertTags("[math]", "[/math]");
        } else if (v == commentSJISButton) {
            insertTags("[sjis]", "[/sjis]");
        } else if (v == commentRevertChangeButton) {
            presenter.onRevertChangeButtonClicked();
        }
    }

    @SuppressWarnings("ConstantConditions")
    private boolean insertQuote() {
        int selectionStart = comment.getSelectionStart();
        int selectionEnd = comment.getSelectionEnd();

        String[] textLines = comment.getText()
                .subSequence(selectionStart, selectionEnd)
                .toString()
                .split("\n");

        StringBuilder rebuilder = new StringBuilder();

        for (int i = 0; i < textLines.length; i++) {
            rebuilder.append(">").append(textLines[i]);

            if (i != textLines.length - 1) {
                rebuilder.append("\n");
            }
        }

        comment.getText().replace(selectionStart, selectionEnd, rebuilder.toString());
        return true;
    }

    @SuppressWarnings("ConstantConditions")
    private boolean insertTags(String before, String after) {
        int selectionStart = comment.getSelectionStart();

        comment.getText().insert(comment.getSelectionEnd(), after);
        comment.getText().insert(selectionStart, before);
        return true;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public void initializeAuthentication(
            Site site,
            SiteAuthentication authentication,
            AuthenticationLayoutCallback callback,
            boolean useV2NoJsCaptcha,
            boolean autoReply
    ) {
        if (authenticationLayout == null) {
            authenticationLayout = createAuthenticationLayout(authentication, useV2NoJsCaptcha);
            captchaContainer.addView((View) authenticationLayout, 0);
        }

        authenticationLayout.initialize(site, callback, autoReply);
        authenticationLayout.reset();
    }

    private AuthenticationLayoutInterface createAuthenticationLayout(
            SiteAuthentication authentication,
            boolean useV2NoJsCaptcha
    ) {
        switch (authentication.type) {
            case CAPTCHA1:
                return (LegacyCaptchaLayout) AndroidUtils.inflate(getContext(),
                        R.layout.layout_captcha_legacy,
                        captchaContainer,
                        false
                );
            case CAPTCHA2:
                return new CaptchaLayout(getContext());
            case CAPTCHA2_NOJS:
                AuthenticationLayoutInterface authenticationLayoutInterface;

                if (useV2NoJsCaptcha) {
                    // new captcha window without webview
                    authenticationLayoutInterface = new CaptchaNoJsLayoutV2(getContext());
                } else {
                    // default webview-based captcha view
                    authenticationLayoutInterface = new CaptchaNojsLayoutV1(getContext());
                }

                ImageView resetButton = captchaContainer.findViewById(R.id.reset);
                if (resetButton != null) {
                    if (useV2NoJsCaptcha) {
                        // we don't need the default reset button because we have our own
                        resetButton.setVisibility(GONE);
                    } else {
                        // restore the button's visibility when using old v1 captcha view
                        resetButton.setVisibility(VISIBLE);
                    }
                }

                return authenticationLayoutInterface;
            case GENERIC_WEBVIEW:
                GenericWebViewAuthenticationLayout view =
                        new GenericWebViewAuthenticationLayout(getContext());

                LayoutParams params = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
                view.setLayoutParams(params);

                return view;
            case NONE:
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public void setPage(@NotNull ReplyPresenter.Page page) {
        Logger.d(TAG, "Switching to page " + page.name());

        switch (page) {
            case LOADING:
                setView(progressLayout);

                setWrappingMode(false);

                //reset progress to 0 upon uploading start
                currentProgress.setVisibility(INVISIBLE);
                destroyCurrentAuthentication();
                callback.updatePadding();

                break;
            case INPUT:
                setView(replyInputLayout);

                setWrappingMode(presenter.isExpanded());
                destroyCurrentAuthentication();
                callback.updatePadding();

                break;
            case AUTHENTICATION:
                AndroidUtils.hideKeyboard(this);
                setView(captchaContainer);

                setWrappingMode(true);
                captchaContainer.requestFocus(View.FOCUS_DOWN);
                callback.updatePadding();

                break;
        }
    }

    @Override
    public void resetAuthentication() {
        authenticationLayout.reset();
    }

    @Override
    public void destroyCurrentAuthentication() {
        if (authenticationLayout == null) {
            return;
        }

        // cleanup resources when switching from the new to the old captcha view
        authenticationLayout.onDestroy();
        captchaContainer.removeView((View) authenticationLayout);
        authenticationLayout = null;
    }

    @Override
    public void showAuthenticationFailedError(Throwable error) {
        String message = getString(R.string.could_not_initialized_captcha, getReason(error));
        showToast(getContext(), message, Toast.LENGTH_LONG);
    }

    @Nullable
    @Override
    public String getTokenOrNull() {
        return captchaHolder.getToken();
    }

    @Override
    public void updateRevertChangeButtonVisibility(boolean isBufferEmpty) {
        if (isBufferEmpty) {
            commentRevertChangeButton.setVisibility(View.GONE);
        } else {
            commentRevertChangeButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void restoreComment(@NotNull CommentEditingHistory.CommentInputState prevCommentInputState) {
        KtExtensionsKt.doIgnoringTextWatcher(comment, this, appCompatEditText -> {
            appCompatEditText.setText(prevCommentInputState.getText());

            appCompatEditText.setSelection(
                    prevCommentInputState.getSelectionStart(),
                    prevCommentInputState.getSelectionEnd()
            );

            presenter.updateCommentCounter(appCompatEditText.getText());
            return Unit.INSTANCE;
        });
    }

    private String getReason(Throwable error) {
        if (error instanceof AndroidRuntimeException && error.getMessage() != null) {
            if (error.getMessage().contains("MissingWebViewPackageException")) {
                return getString(R.string.fail_reason_webview_is_not_installed);
            }

            // Fallthrough
        } else if (error instanceof Resources.NotFoundException) {
            return getString(R.string.fail_reason_some_part_of_webview_not_initialized, error.getMessage());
        }

        if (error.getMessage() != null) {
            return String.format("%s: %s", error.getClass().getSimpleName(), error.getMessage());
        }

        return error.getClass().getSimpleName();
    }

    @Override
    public void loadDraftIntoViews(Reply draft) {
        name.setText(draft.name);
        subject.setText(draft.subject);
        flag.setText(draft.flag);
        options.setText(draft.options);
        blockSelectionChange = true;
        comment.setText(draft.comment);
        blockSelectionChange = false;
        fileName.setText(draft.fileName);
        spoiler.setChecked(draft.spoilerImage);
    }

    @Override
    public void loadViewsIntoDraft(Reply draft) {
        draft.name = name.getText().toString();
        draft.subject = subject.getText().toString();
        draft.flag = flag.getText().toString();
        draft.options = options.getText().toString();
        draft.comment = comment.getText().toString();
        draft.fileName = fileName.getText().toString();
        draft.spoilerImage = spoiler.isChecked();
    }

    @Override
    public int getSelectionStart() {
        return comment.getSelectionStart();
    }

    @Override
    public void adjustSelection(int start, int amount) {
        try {
            comment.setSelection(start + amount);
        } catch (Exception e) {
            // set selection to the end if it fails for any reason
            comment.setSelection(comment.getText().length());
        }
    }

    @Override
    public void openMessage(String text) {
        openMessage(text, 5000);
    }

    @Override
    public void openMessage(String text, int hideDelayMs) {
        if (hideDelayMs <= 0) {
            throw new IllegalArgumentException("Bad hideDelayMs: " + hideDelayMs);
        }

        if (text == null) {
            text = "";
        }

        removeCallbacks(closeMessageRunnable);
        message.setText(text);
        message.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);

        if (!TextUtils.isEmpty(text)) {
            postDelayed(closeMessageRunnable, hideDelayMs);
        }
    }

    @Override
    public void onPosted() {
        showToast(getContext(), R.string.reply_success);
        callback.openReply(false);
        callback.requestNewPostLoad();
    }

    @Override
    public void setCommentHint(String hint) {
        comment.setHint(hint);
    }

    @Override
    public void showCommentCounter(boolean show) {
        commentCounter.setVisibility(show ? VISIBLE : GONE);
    }

    @Subscribe
    public void onEvent(RefreshUIMessage message) {
        setWrappingMode(presenter.isExpanded());
    }

    @Override
    public void setExpanded(boolean expanded) {
        setWrappingMode(expanded);

        comment.setMaxLines(expanded ? 500 : 6);
        previewHolder.setLayoutParams(
                new LinearLayout.LayoutParams(MATCH_PARENT, expanded ? dp(150) : dp(100))
        );

        float startRotation = 1f;
        float endRotation = 0f;

        ValueAnimator animator = ValueAnimator.ofFloat(
                expanded ? startRotation : endRotation,
                expanded ? endRotation : startRotation
        );

        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.setDuration(400);
        animator.addUpdateListener(animation ->
                moreDropdown.setRotation((float) animation.getAnimatedValue()));

        more.setImageDrawable(moreDropdown);
        animator.start();

        if (expanded) {
            replyLayoutTopDivider.setVisibility(View.INVISIBLE);
        } else {
            replyLayoutTopDivider.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void openNameOptions(boolean open) {
        nameOptions.setVisibility(open ? VISIBLE : GONE);
    }

    @Override
    public void openSubject(boolean open) {
        subject.setVisibility(open ? VISIBLE : GONE);
    }

    @Override
    public void openFlag(boolean open) {
        flag.setVisibility(open ? VISIBLE : GONE);
    }

    @Override
    public void openCommentQuoteButton(boolean open) {
        commentQuoteButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentSpoilerButton(boolean open) {
        commentSpoilerButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentCodeButton(boolean open) {
        commentCodeButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentEqnButton(boolean open) {
        commentEqnButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentMathButton(boolean open) {
        commentMathButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentSJISButton(boolean open) {
        commentSJISButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openFileName(boolean open) {
        fileName.setVisibility(open ? VISIBLE : GONE);
    }

    @Override
    public void setFileName(String name) {
        fileName.setText(name);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void updateCommentCount(int count, int maxCount, boolean over) {
        this.isCounterOverflowed = over;
        commentCounter.setText(count + "/" + maxCount);

        int textColor = over
                ? themeEngine.getChanTheme().getErrorColor()
                : themeEngine.getChanTheme().getTextColorSecondary();

        commentCounter.setTextColor(textColor);
    }

    public void focusComment() {
        //this is a hack to make sure text is selectable
        comment.setEnabled(false);
        comment.setEnabled(true);
        comment.post(() -> requestViewAndKeyboardFocus(comment));
    }

    @Override
    public void onFallbackToV1CaptchaView(boolean autoReply) {
        // fallback to v1 captcha window
        presenter.switchPage(ReplyPresenter.Page.AUTHENTICATION, false, autoReply);
    }

    @Override
    public void openPreview(boolean show, File previewFile) {
        previewHolder.setClickable(false);
        boolean isDarkColor = isDarkColor(themeEngine.chanTheme.getBackColor());

        if (show) {
            ImageDecoder.decodeFileOnBackgroundThread(
                    previewFile,
                    dp(400),
                    dp(300),
                    this
            );

            attach.setImageDrawable(
                    themeEngine.getDrawableTinted(
                            getContext(),
                            R.drawable.ic_clear_white_24dp,
                            isDarkColor
                    )
            );
        } else {
            spoiler.setVisibility(GONE);
            previewHolder.setVisibility(GONE);
            previewMessage.setVisibility(GONE);
            callback.updatePadding();

            attach.setImageDrawable(
                    themeEngine.getDrawableTinted(
                            getContext(),
                            R.drawable.ic_image_white_24dp,
                            isDarkColor
                    )
            );
        }

        // the delay is taken from LayoutTransition, as this class is set to automatically animate
        // layout changes only allow the preview to be clicked if it is fully visible
        postDelayed(() -> previewHolder.setClickable(true), 300);
    }

    @Override
    public void openPreviewMessage(boolean show, String message) {
        previewMessage.setVisibility(show ? VISIBLE : GONE);
        previewMessage.setText(message);
    }

    @Override
    public void openSpoiler(boolean show, boolean setUnchecked) {
        spoiler.setVisibility(show ? VISIBLE : GONE);

        if (setUnchecked) {
            spoiler.setChecked(false);
        }
    }

    @Override
    public void onImageBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            preview.setImageBitmap(bitmap);
            previewHolder.setVisibility(VISIBLE);
            callback.updatePadding();

            showReencodeImageHint();
        } else {
            openPreviewMessage(true, getString(R.string.reply_no_preview));
        }
    }

    @Override
    public void highlightPostNos(Set<Long> postNos) {
        callback.highlightPostNos(postNos);
    }

    @Override
    public void onSelectionChanged() {
        if (!blockSelectionChange) {
            presenter.onSelectionChanged();
        }
    }

    private void setupCommentContextMenu() {
        comment.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            private MenuItem quoteMenuItem;
            private MenuItem spoilerMenuItem;
            private MenuItem codeMenuItem;
            private MenuItem mathMenuItem;
            private MenuItem eqnMenuItem;
            private MenuItem sjisMenuItem;
            private boolean processed;

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                ChanDescriptor chanDescriptor = callback.getCurrentChanDescriptor();
                if (chanDescriptor == null) {
                    return true;
                }

                ChanBoard board = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor());
                if (board == null) {
                    return true;
                }

                boolean is4chan = chanDescriptor.siteDescriptor().is4chan();
                String boardCode = chanDescriptor.boardCode();

                // menu item cleanup, these aren't needed for this
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (menu.size() > 0) {
                        menu.removeItem(android.R.id.shareText);
                    }
                }

                // setup standard items
                // >greentext
                quoteMenuItem = menu.add(Menu.NONE, R.id.reply_selection_action_quote, 1, R.string.post_quote);

                // [spoiler] tags
                if (board.getSpoilers()) {
                    spoilerMenuItem = menu.add(Menu.NONE,
                            R.id.reply_selection_action_spoiler,
                            2,
                            R.string.reply_comment_button_spoiler
                    );
                }

                // setup specific items in a submenu
                SubMenu otherMods = menu.addSubMenu("Modify");
                // g [code]
                if (is4chan && boardCode.equals("g")) {
                    codeMenuItem = otherMods.add(Menu.NONE,
                            R.id.reply_selection_action_code,
                            1,
                            R.string.reply_comment_button_code
                    );
                }

                // sci [eqn] and [math]
                if (is4chan && boardCode.equals("sci")) {
                    eqnMenuItem = otherMods.add(Menu.NONE,
                            R.id.reply_selection_action_eqn,
                            2,
                            R.string.reply_comment_button_eqn
                    );

                    mathMenuItem = otherMods.add(
                            Menu.NONE,
                            R.id.reply_selection_action_math,
                            3,
                            R.string.reply_comment_button_math
                    );
                }

                // jp and vip [sjis]
                if (is4chan && (boardCode.equals("jp") || boardCode.equals("vip"))) {
                    sjisMenuItem = otherMods.add(
                            Menu.NONE,
                            R.id.reply_selection_action_sjis,
                            4,
                            R.string.reply_comment_button_sjis
                    );
                }

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item == quoteMenuItem) {
                    processed = insertQuote();
                } else if (item == spoilerMenuItem) {
                    processed = insertTags("[spoiler]", "[/spoiler]");
                } else if (item == codeMenuItem) {
                    processed = insertTags("[code]", "[/code]");
                } else if (item == eqnMenuItem) {
                    processed = insertTags("[eqn]", "[/eqn]");
                } else if (item == mathMenuItem) {
                    processed = insertTags("[math]", "[/math]");
                } else if (item == sjisMenuItem) {
                    processed = insertTags("[sjis]", "[/sjis]");
                }

                if (processed) {
                    mode.finish();
                    processed = false;
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        });
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        presenter.updateCommentCounter(comment.getText());

        CommentEditingHistory.CommentInputState commentInputState = new CommentEditingHistory.CommentInputState(
                comment.getText().toString(),
                comment.getSelectionStart(),
                comment.getSelectionEnd()
        );

        presenter.updateCommentEditingHistory(commentInputState);
    }

    @Override
    public void showThread(@NotNull ChanDescriptor.ThreadDescriptor threadDescriptor) {
        callback.showThread(threadDescriptor);
    }

    @Override
    public ImagePickDelegate getImagePickDelegate() {
        return ((StartActivity) getContext()).getImagePickDelegate();
    }

    @Nullable
    @Override
    public ChanDescriptor getChanDescriptor() {
        return callback.getCurrentChanDescriptor();
    }

    public void onImageOptionsApplied(Reply reply, boolean filenameRemoved) {
        if (filenameRemoved) {
            // update edit field with new filename
            fileName.setText(reply.fileName);
        } else {
            // update reply with existing filename (may have been changed by user)
            reply.fileName = fileName.getText().toString();
        }

        presenter.onImageOptionsApplied(reply);
    }

    public void onImageOptionsComplete() {
        // reencode windows gone, allow the file to be removed
        attach.setClickable(true);
    }

    private void showReencodeImageHint() {
        if (ChanSettings.reencodeHintShown.get()) {
            return;
        }

        String message = getString(R.string.click_image_for_extra_options);
        dismissHint();

        hintPopup = HintPopup.show(getContext(), preview, message, dp(-32), dp(16));
        hintPopup.wiggle();

        ChanSettings.reencodeHintShown.set(true);
    }

    @Override
    public void onUploadingProgress(int percent) {
        if (currentProgress != null) {
            if (percent >= 0) {
                currentProgress.setVisibility(VISIBLE);
            }

            currentProgress.setText(String.valueOf(percent));
        }
    }

    @Override
    public void onCaptchaCountChanged(int validCaptchaCount) {
        if (validCaptchaCount <= 0) {
            validCaptchasCount.setVisibility(INVISIBLE);
        } else {
            validCaptchasCount.setVisibility(VISIBLE);
            validCaptchasCount.setText(String.valueOf(validCaptchaCount));
        }
    }

    public interface ReplyLayoutCallback {
        void highlightPostNos(Set<Long> postNos);

        void openReply(boolean open);

        void showThread(ChanDescriptor.ThreadDescriptor threadDescriptor);

        void requestNewPostLoad();

        @Nullable
        ChanDescriptor getCurrentChanDescriptor();

        void showImageReencodingWindow(boolean supportsReencode);

        void updatePadding();
    }
}
