package com.github.k1rakishou.chan.ui.widget.dialog;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.R;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

import com.github.k1rakishou.chan.ui.controller.dialog.KurobaAlertDialogHostControllerCallbacks;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.chan.utils.ViewUtils;
import com.github.k1rakishou.core_themes.ThemeEngine;

import java.lang.ref.WeakReference;

import javax.inject.Inject;

public class KurobaAlertController implements ThemeEngine.ThemeChangesListener {
    private final Context mContext;
    private final View parent;
    final DialogInterface dialogInterface;
    private final KurobaAlertDialogHostControllerCallbacks kurobaAlertDialogHostControllerCallbacks;

    private CharSequence mTitle;
    private CharSequence mMessage;
    private View mView;

    private int mViewLayoutResId;
    private int mViewSpacingLeft;
    private int mViewSpacingTop;
    private int mViewSpacingRight;
    private int mViewSpacingBottom;
    private boolean mViewSpacingSpecified = false;
    private boolean cancelable = true;

    Button mButtonPositive;
    private CharSequence mButtonPositiveText;
    Message mButtonPositiveMessage;

    Button mButtonNegative;
    private CharSequence mButtonNegativeText;
    Message mButtonNegativeMessage;

    Button mButtonNeutral;
    private CharSequence mButtonNeutralText;
    Message mButtonNeutralMessage;

    NestedScrollView mScrollView;

    private TextView mTitleView;
    private TextView mMessageView;
    private View mCustomTitleView;

    private @Nullable LinkMovementMethod customLinkMovementMethod;

    Handler mHandler;

    @Inject
    ThemeEngine themeEngine;

    private final View.OnClickListener mButtonHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final Message m;
            if (v == mButtonPositive && mButtonPositiveMessage != null) {
                m = Message.obtain(mButtonPositiveMessage);
            } else if (v == mButtonNegative && mButtonNegativeMessage != null) {
                m = Message.obtain(mButtonNegativeMessage);
            } else if (v == mButtonNeutral && mButtonNeutralMessage != null) {
                m = Message.obtain(mButtonNeutralMessage);
            } else {
                m = null;
            }

            if (m != null) {
                m.sendToTarget();
            }

            // Post a message so we dismiss after the above handlers are executed
            mHandler.obtainMessage(ButtonHandler.MSG_DISMISS_DIALOG, dialogInterface)
                    .sendToTarget();
        }
    };

    public void dismiss() {
        if (kurobaAlertDialogHostControllerCallbacks != null && cancelable) {
            kurobaAlertDialogHostControllerCallbacks.onDismiss();
        }
    }

    private static final class ButtonHandler extends Handler {
        // Button clicks have Message.what as the BUTTON{1,2,3} constant
        private static final int MSG_DISMISS_DIALOG = 1;

        private WeakReference<DialogInterface> mDialog;

        public ButtonHandler(DialogInterface dialog) {
            mDialog = new WeakReference<>(dialog);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DialogInterface.BUTTON_POSITIVE:
                case DialogInterface.BUTTON_NEGATIVE:
                case DialogInterface.BUTTON_NEUTRAL:
                    ((DialogInterface.OnClickListener) msg.obj).onClick(mDialog.get(), msg.what);
                    break;

                case MSG_DISMISS_DIALOG:
                    ((DialogInterface) msg.obj).dismiss();
            }
        }
    }

    private static boolean shouldCenterSingleButton(Context context) {
        final TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.alertDialogCenterButtons, outValue, true);
        return outValue.data != 0;
    }

    private final View.OnAttachStateChangeListener onAttachStateChangeListener = new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(View v) {
            themeEngine.addListener(KurobaAlertController.this);
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            themeEngine.removeListener(KurobaAlertController.this);
            v.removeOnAttachStateChangeListener(onAttachStateChangeListener);
        }
    };

    public KurobaAlertController(
            Context context,
            DialogInterface di,
            View parent,
            @NonNull KurobaAlertDialogHostControllerCallbacks callbacks
    ) {
        mContext = context;
        dialogInterface = di;
        kurobaAlertDialogHostControllerCallbacks = callbacks;

        AppModuleAndroidUtils.extractActivityComponent(context)
                .inject(this);

        this.parent = parent;
        parent.addOnAttachStateChangeListener(onAttachStateChangeListener);

        mHandler = new ButtonHandler(di);
    }

    public void installContent() {
        setupView();
        onThemeChanged();
    }

    @Override
    public void onThemeChanged() {
        if (mButtonPositive != null) {
            mButtonPositive.setTextColor(themeEngine.getChanTheme().getTextColorPrimary());
            mButtonPositive.invalidate();
        }

        if (mButtonNegative != null) {
            mButtonNegative.setTextColor(themeEngine.getChanTheme().getTextColorPrimary());
            mButtonNegative.invalidate();
        }

        if (mButtonNeutral != null) {
            mButtonNeutral.setTextColor(themeEngine.getChanTheme().getTextColorPrimary());
            mButtonNeutral.invalidate();
        }

        if (mTitleView != null) {
            mTitleView.setTextColor(themeEngine.getChanTheme().getTextColorPrimary());
        }

        if (mMessageView != null) {
            mMessageView.setTextColor(themeEngine.getChanTheme().getTextColorPrimary());
            mMessageView.setLinkTextColor(themeEngine.getChanTheme().getPostLinkColor());
            mMessageView.setTextIsSelectable(true);

            if (customLinkMovementMethod != null) {
                mMessageView.setMovementMethod(customLinkMovementMethod);
            } else {
                mMessageView.setMovementMethod(LinkMovementMethod.getInstance());
            }

            ViewUtils.setEditTextCursorColor(mMessageView, themeEngine.getChanTheme());
            ViewUtils.setHandlesColors(mMessageView, themeEngine.getChanTheme());
        }
    }

    public void setTitle(CharSequence title) {
        mTitle = title;
        if (mTitleView != null) {
            mTitleView.setText(title);
        }
    }

    /**
     * @see AlertDialog.Builder#setCustomTitle(View)
     */
    public void setCustomTitle(View customTitleView) {
        mCustomTitleView = customTitleView;
    }

    public void setMessage(CharSequence message) {
        mMessage = message;
        if (mMessageView != null) {
            mMessageView.setText(message);
        }
    }

    /**
     * Set the view resource to display in the dialog.
     */
    public void setView(int layoutResId) {
        mView = null;
        mViewLayoutResId = layoutResId;
        mViewSpacingSpecified = false;
    }

    public void setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
    }

    /**
     * Set the view to display in the dialog.
     */
    public void setView(View view) {
        mView = view;
        mViewLayoutResId = 0;
        mViewSpacingSpecified = false;
    }

    /**
     * Set the view to display in the dialog along with the spacing around that view
     */
    public void setView(View view, int viewSpacingLeft, int viewSpacingTop, int viewSpacingRight,
                        int viewSpacingBottom) {
        mView = view;
        mViewLayoutResId = 0;
        mViewSpacingSpecified = true;
        mViewSpacingLeft = viewSpacingLeft;
        mViewSpacingTop = viewSpacingTop;
        mViewSpacingRight = viewSpacingRight;
        mViewSpacingBottom = viewSpacingBottom;
    }

    /**
     * Sets an icon, a click listener or a message to be sent when the button is clicked.
     * You only need to pass one of {@code icon}, {@code listener} or {@code msg}.
     *
     * @param whichButton Which button, can be one of
     *                    {@link DialogInterface#BUTTON_POSITIVE},
     *                    {@link DialogInterface#BUTTON_NEGATIVE}, or
     *                    {@link DialogInterface#BUTTON_NEUTRAL}
     * @param text        The text to display in positive button.
     * @param listener    The {@link DialogInterface.OnClickListener} to use.
     * @param msg         The {@link Message} to be sent when clicked.
     */
    public void setButton(
            int whichButton,
            CharSequence text,
            DialogInterface.OnClickListener listener,
            Message msg
    ) {

        if (msg == null && listener != null) {
            msg = mHandler.obtainMessage(whichButton, listener);
        }

        switch (whichButton) {

            case DialogInterface.BUTTON_POSITIVE:
                mButtonPositiveText = text;
                mButtonPositiveMessage = msg;
                break;

            case DialogInterface.BUTTON_NEGATIVE:
                mButtonNegativeText = text;
                mButtonNegativeMessage = msg;
                break;

            case DialogInterface.BUTTON_NEUTRAL:
                mButtonNeutralText = text;
                mButtonNeutralMessage = msg;
                break;

            default:
                throw new IllegalArgumentException("Button does not exist");
        }
    }

    public Button getButton(int whichButton) {
        switch (whichButton) {
            case DialogInterface.BUTTON_POSITIVE:
                return mButtonPositive;
            case DialogInterface.BUTTON_NEGATIVE:
                return mButtonNegative;
            case DialogInterface.BUTTON_NEUTRAL:
                return mButtonNeutral;
            default:
                return null;
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mScrollView != null && mScrollView.executeKeyEvent(event);
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mScrollView != null && mScrollView.executeKeyEvent(event);
    }

    /**
     * Resolves whether a custom or default panel should be used. Removes the
     * default panel if a custom panel should be used. If the resolved panel is
     * a view stub, inflates before returning.
     *
     * @param customPanel  the custom panel
     * @param defaultPanel the default panel
     * @return the panel to use
     */
    @Nullable
    private ViewGroup resolvePanel(@Nullable View customPanel, @Nullable View defaultPanel) {
        if (customPanel == null) {
            // Inflate the default panel, if needed.
            if (defaultPanel instanceof ViewStub) {
                defaultPanel = ((ViewStub) defaultPanel).inflate();
            }

            return (ViewGroup) defaultPanel;
        }

        // Remove the default panel entirely.
        if (defaultPanel != null) {
            final ViewParent parent = defaultPanel.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(defaultPanel);
            }
        }

        // Inflate the custom panel, if needed.
        if (customPanel instanceof ViewStub) {
            customPanel = ((ViewStub) customPanel).inflate();
        }

        return (ViewGroup) customPanel;
    }

    private void setupView() {
        final View parentPanel = parent.findViewById(R.id.parentPanel);
        final View defaultTopPanel = parentPanel.findViewById(R.id.topPanel);
        final View defaultContentPanel = parentPanel.findViewById(R.id.contentPanel);
        final View defaultButtonPanel = parentPanel.findViewById(R.id.buttonPanel);

        // Install custom content before setting up the title or buttons so
        // that we can handle panel overrides.
        final ViewGroup customPanel = (ViewGroup) parentPanel.findViewById(R.id.customPanel);
        setupCustomContent(customPanel);

        final View customTopPanel = customPanel.findViewById(R.id.topPanel);
        final View customContentPanel = customPanel.findViewById(R.id.contentPanel);
        final View customButtonPanel = customPanel.findViewById(R.id.buttonPanel);

        // Resolve the correct panels and remove the defaults, if needed.
        final ViewGroup topPanel = resolvePanel(customTopPanel, defaultTopPanel);
        final ViewGroup contentPanel = resolvePanel(customContentPanel, defaultContentPanel);
        final ViewGroup buttonPanel = resolvePanel(customButtonPanel, defaultButtonPanel);

        setupContent(contentPanel);
        setupButtons(buttonPanel);
        setupTitle(topPanel);

        final boolean hasCustomPanel = customPanel != null
                && customPanel.getVisibility() != View.GONE;
        final boolean hasTopPanel = topPanel != null
                && topPanel.getVisibility() != View.GONE;
        final boolean hasButtonPanel = buttonPanel != null
                && buttonPanel.getVisibility() != View.GONE;

        // Only display the text spacer if we don't have buttons.
        if (!hasButtonPanel) {
            if (contentPanel != null) {
                final View spacer = contentPanel.findViewById(R.id.textSpacerNoButtons);
                if (spacer != null) {
                    spacer.setVisibility(View.VISIBLE);
                }
            }
        }

        if (hasTopPanel) {
            // Only clip scrolling content to padding if we have a title.
            if (mScrollView != null) {
                mScrollView.setClipToPadding(true);
            }

            // Only show the divider if we have a title.
            View divider = null;
            if (mMessage != null) {
                divider = topPanel.findViewById(R.id.titleDividerNoCustom);
            }

            if (divider != null) {
                divider.setVisibility(View.VISIBLE);
            }
        } else {
            if (contentPanel != null) {
                final View spacer = contentPanel.findViewById(R.id.textSpacerNoTitle);
                if (spacer != null) {
                    spacer.setVisibility(View.VISIBLE);
                }
            }
        }

        // Update scroll indicators as needed.
        if (!hasCustomPanel) {
            final View content = mScrollView;
            if (content != null) {
                final int indicators = (hasTopPanel ? ViewCompat.SCROLL_INDICATOR_TOP : 0)
                        | (hasButtonPanel ? ViewCompat.SCROLL_INDICATOR_BOTTOM : 0);
                setScrollIndicators(contentPanel, content, indicators,
                        ViewCompat.SCROLL_INDICATOR_TOP | ViewCompat.SCROLL_INDICATOR_BOTTOM);
            }
        }
    }

    private void setScrollIndicators(ViewGroup contentPanel, View content,
                                     final int indicators, final int mask) {
        // Set up scroll indicators (if present).
        View indicatorUp = parent.findViewById(R.id.scrollIndicatorUp);
        View indicatorDown = parent.findViewById(R.id.scrollIndicatorDown);

        if (Build.VERSION.SDK_INT >= 23) {
            // We're on Marshmallow so can rely on the View APIs
            ViewCompat.setScrollIndicators(content, indicators, mask);
            // We can also remove the compat indicator views
            if (indicatorUp != null) {
                contentPanel.removeView(indicatorUp);
            }
            if (indicatorDown != null) {
                contentPanel.removeView(indicatorDown);
            }
        } else {
            // First, remove the indicator views if we're not set to use them
            if (indicatorUp != null && (indicators & ViewCompat.SCROLL_INDICATOR_TOP) == 0) {
                contentPanel.removeView(indicatorUp);
                indicatorUp = null;
            }
            if (indicatorDown != null && (indicators & ViewCompat.SCROLL_INDICATOR_BOTTOM) == 0) {
                contentPanel.removeView(indicatorDown);
                indicatorDown = null;
            }

            if (indicatorUp != null || indicatorDown != null) {
                final View top = indicatorUp;
                final View bottom = indicatorDown;

                if (mMessage != null) {
                    // We're just showing the ScrollView, set up listener.
                    mScrollView.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
                                @Override
                                public void onScrollChange(NestedScrollView v, int scrollX,
                                                           int scrollY,
                                                           int oldScrollX, int oldScrollY) {
                                    manageScrollIndicators(v, top, bottom);
                                }
                            });
                    // Set up the indicators following layout.
                    mScrollView.post(() -> manageScrollIndicators(mScrollView, top, bottom));
                } else {
                    // We don't have any content to scroll, remove the indicators.
                    if (top != null) {
                        contentPanel.removeView(top);
                    }
                    if (bottom != null) {
                        contentPanel.removeView(bottom);
                    }
                }
            }
        }
    }

    private void setupCustomContent(ViewGroup customPanel) {
        final View customView;
        if (mView != null) {
            customView = mView;
        } else if (mViewLayoutResId != 0) {
            final LayoutInflater inflater = LayoutInflater.from(mContext);
            customView = inflater.inflate(mViewLayoutResId, customPanel, false);
        } else {
            customView = null;
        }

        final boolean hasCustomView = customView != null;
        if (hasCustomView) {
            final FrameLayout custom = (FrameLayout) parent.findViewById(R.id.custom);
            custom.addView(customView, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            if (mViewSpacingSpecified) {
                custom.setPadding(
                        mViewSpacingLeft, mViewSpacingTop, mViewSpacingRight, mViewSpacingBottom);
            }
        } else {
            customPanel.setVisibility(View.GONE);
        }
    }

    private void setupTitle(ViewGroup topPanel) {
        if (mCustomTitleView != null) {
            // Add the custom title view directly to the topPanel layout
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            topPanel.addView(mCustomTitleView, 0, lp);

            // Hide the title template
            View titleTemplate = parent.findViewById(R.id.title_template);
            titleTemplate.setVisibility(View.GONE);
        } else {
            final boolean hasTextTitle = !TextUtils.isEmpty(mTitle);

            if (hasTextTitle) {
                // Display the title if a title is supplied, else hide it.
                mTitleView = (TextView) parent.findViewById(R.id.alertTitle);
                mTitleView.setText(mTitle);
            } else {
                // Hide the title template
                final View titleTemplate = parent.findViewById(R.id.title_template);

                titleTemplate.setVisibility(View.GONE);
                topPanel.setVisibility(View.GONE);
            }
        }

        ImageView iconView = (ImageView) parent.findViewById(android.R.id.icon);
        if (iconView != null) {
            iconView.setVisibility(View.GONE);
        }
    }

    private void setupContent(ViewGroup contentPanel) {
        mScrollView = (NestedScrollView) parent.findViewById(R.id.scrollView);
        mScrollView.setFocusable(false);
        mScrollView.setNestedScrollingEnabled(false);

        // Special case for users that only want to display a String
        mMessageView = (TextView) contentPanel.findViewById(android.R.id.message);
        if (mMessageView == null) {
            return;
        }

        if (mMessage != null) {
            mMessageView.setText(mMessage);
        } else {
            mMessageView.setVisibility(View.GONE);
            mScrollView.removeView(mMessageView);
            contentPanel.setVisibility(View.GONE);
        }
    }

    static void manageScrollIndicators(View v, View upIndicator, View downIndicator) {
        if (upIndicator != null) {
            upIndicator.setVisibility(
                    v.canScrollVertically(-1) ? View.VISIBLE : View.INVISIBLE);
        }
        if (downIndicator != null) {
            downIndicator.setVisibility(
                    v.canScrollVertically(1) ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void setupButtons(ViewGroup buttonPanel) {
        int BIT_BUTTON_POSITIVE = 1;
        int BIT_BUTTON_NEGATIVE = 2;
        int BIT_BUTTON_NEUTRAL = 4;
        int whichButtons = 0;
        mButtonPositive = (Button) buttonPanel.findViewById(android.R.id.button1);
        mButtonPositive.setOnClickListener(mButtonHandler);

        if (TextUtils.isEmpty(mButtonPositiveText)) {
            mButtonPositive.setVisibility(View.GONE);
        } else {
            mButtonPositive.setText(mButtonPositiveText);
            mButtonPositive.setVisibility(View.VISIBLE);
            whichButtons = whichButtons | BIT_BUTTON_POSITIVE;
        }

        mButtonNegative = buttonPanel.findViewById(android.R.id.button2);
        mButtonNegative.setOnClickListener(mButtonHandler);

        if (TextUtils.isEmpty(mButtonNegativeText)) {
            mButtonNegative.setVisibility(View.GONE);
        } else {
            mButtonNegative.setText(mButtonNegativeText);
            mButtonNegative.setVisibility(View.VISIBLE);
            whichButtons = whichButtons | BIT_BUTTON_NEGATIVE;
        }

        mButtonNeutral = (Button) buttonPanel.findViewById(android.R.id.button3);
        mButtonNeutral.setOnClickListener(mButtonHandler);

        if (TextUtils.isEmpty(mButtonNeutralText)) {
            mButtonNeutral.setVisibility(View.GONE);
        } else {
            mButtonNeutral.setText(mButtonNeutralText);
            mButtonNeutral.setVisibility(View.VISIBLE);
            whichButtons = whichButtons | BIT_BUTTON_NEUTRAL;
        }

        if (shouldCenterSingleButton(mContext)) {
            /*
             * If we only have 1 button it should be centered on the layout and
             * expand to fill 50% of the available space.
             */
            if (whichButtons == BIT_BUTTON_POSITIVE) {
                centerButton(mButtonPositive);
            } else if (whichButtons == BIT_BUTTON_NEGATIVE) {
                centerButton(mButtonNegative);
            } else if (whichButtons == BIT_BUTTON_NEUTRAL) {
                centerButton(mButtonNeutral);
            }
        }

        final boolean hasButtons = whichButtons != 0;
        if (!hasButtons) {
            buttonPanel.setVisibility(View.GONE);
        }
    }

    private void setCustomLinkMovementMethod(LinkMovementMethod customLinkMovementMethod) {
        this.customLinkMovementMethod = customLinkMovementMethod;
    }

    private void centerButton(Button button) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) button.getLayoutParams();
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.weight = 0.5f;
        button.setLayoutParams(params);
    }

    public static class AlertParams {
        public final Context mContext;
        public final LayoutInflater mInflater;

        public CharSequence mTitle;
        public View mCustomTitleView;
        public CharSequence mMessage;
        public CharSequence mPositiveButtonText;
        public DialogInterface.OnClickListener mPositiveButtonListener;
        public CharSequence mNegativeButtonText;
        public DialogInterface.OnClickListener mNegativeButtonListener;
        public CharSequence mNeutralButtonText;
        public DialogInterface.OnClickListener mNeutralButtonListener;
        public @Nullable LinkMovementMethod customLinkMovementMethod;
        public boolean mCancelable;
        public int mViewLayoutResId;
        public View mView;
        public int mViewSpacingLeft;
        public int mViewSpacingTop;
        public int mViewSpacingRight;
        public int mViewSpacingBottom;
        public boolean mViewSpacingSpecified = false;

        public AlertParams(Context context) {
            mContext = context;
            mCancelable = true;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public void apply(KurobaAlertController dialog) {
            dialog.setCustomLinkMovementMethod(customLinkMovementMethod);

            if (mCustomTitleView != null) {
                dialog.setCustomTitle(mCustomTitleView);
            } else {
                if (mTitle != null) {
                    dialog.setTitle(mTitle);
                }
            }
            if (mMessage != null) {
                dialog.setMessage(mMessage);
            }
            if (mPositiveButtonText != null) {
                dialog.setButton(
                        DialogInterface.BUTTON_POSITIVE,
                        mPositiveButtonText,
                        mPositiveButtonListener,
                        null);
            }
            if (mNegativeButtonText != null) {
                dialog.setButton(
                        DialogInterface.BUTTON_NEGATIVE,
                        mNegativeButtonText,
                        mNegativeButtonListener,
                        null);
            }
            if (mNeutralButtonText != null) {
                dialog.setButton(
                        DialogInterface.BUTTON_NEUTRAL,
                        mNeutralButtonText,
                        mNeutralButtonListener,
                        null);
            }
            if (mView != null) {
                if (mViewSpacingSpecified) {
                    dialog.setView(mView, mViewSpacingLeft, mViewSpacingTop, mViewSpacingRight,
                            mViewSpacingBottom);
                } else {
                    dialog.setView(mView);
                }
            } else if (mViewLayoutResId != 0) {
                dialog.setView(mViewLayoutResId);
            }

            dialog.setCancelable(mCancelable);
        }
    }

}

