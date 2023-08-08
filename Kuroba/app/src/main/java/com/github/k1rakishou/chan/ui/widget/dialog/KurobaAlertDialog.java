package com.github.k1rakishou.chan.ui.widget.dialog;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Message;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.StringRes;

import com.github.k1rakishou.chan.ui.controller.dialog.KurobaAlertDialogHostControllerCallbacks;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class KurobaAlertDialog implements DialogInterface {
    final KurobaAlertController mAlert;

    protected KurobaAlertDialog(
            @NonNull Context context,
            @NonNull ViewGroup parentView,
            @NonNull KurobaAlertDialogHostControllerCallbacks callbacks
    ) {
        mAlert = new KurobaAlertController(
                context,
                this,
                parentView,
                callbacks
        );
    }

    public Button getButton(int whichButton) {
        return mAlert.getButton(whichButton);
    }

    public void setTitle(CharSequence title) {
        mAlert.setTitle(title);
    }

    public void setCustomTitle(View customTitleView) {
        mAlert.setCustomTitle(customTitleView);
    }

    public void setMessage(CharSequence message) {
        mAlert.setMessage(message);
    }

    public void setView(View view) {
        mAlert.setView(view);
    }

    public void setView(View view, int viewSpacingLeft, int viewSpacingTop, int viewSpacingRight,
                        int viewSpacingBottom) {
        mAlert.setView(view, viewSpacingLeft, viewSpacingTop, viewSpacingRight, viewSpacingBottom);
    }

    public void setButton(int whichButton, CharSequence text, Message msg) {
        mAlert.setButton(whichButton, text, null, msg);
    }

    public void setButton(int whichButton, CharSequence text, OnClickListener listener) {
        mAlert.setButton(whichButton, text, listener, null);
    }

    @Override
    public void cancel() {
        mAlert.dismiss();
    }

    @Override
    public void dismiss() {
        mAlert.dismiss();
    }

    public static class Builder {
        private final KurobaAlertController.AlertParams P;

        public Builder(@NonNull Context context) {
            P = new KurobaAlertController.AlertParams(context);
        }

        /**
         * Returns a {@link Context} with the appropriate theme for dialogs created by this Builder.
         * Applications should use this Context for obtaining LayoutInflaters for inflating views
         * that will be used in the resulting dialogs, as it will cause views to be inflated with
         * the correct theme.
         *
         * @return A Context for built Dialogs.
         */
        @NonNull
        public Context getContext() {
            return P.mContext;
        }

        public KurobaAlertDialog.Builder setTitle(@StringRes int titleId) {
            P.mTitle = P.mContext.getText(titleId);
            return this;
        }

        /**
         * Set the title displayed in the {@link Dialog}.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public KurobaAlertDialog.Builder setTitle(@Nullable CharSequence title) {
            P.mTitle = title;
            return this;
        }

        /**
         * Set the title using the custom view {@code customTitleView}.
         * <p>
         * The methods {@link #setTitle(int)} should
         * be sufficient for most titles, but this is provided if the title
         * needs more customization. Using this will replace the title and icon
         * set via the other methods.
         * <p>
         * <strong>Note:</strong> To ensure consistent styling, the custom view
         * should be inflated or constructed using the alert dialog's themed
         * context obtained via {@link #getContext()}.
         *
         * @param customTitleView the custom view to use as the title
         * @return this Builder object to allow for chaining of calls to set
         *         methods
         */
        public KurobaAlertDialog.Builder setCustomTitle(@Nullable View customTitleView) {
            P.mCustomTitleView = customTitleView;
            return this;
        }

        /**
         * Set the message to display using the given resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public KurobaAlertDialog.Builder setMessage(@StringRes int messageId) {
            P.mMessage = P.mContext.getText(messageId);
            return this;
        }

        /**
         * Set the message to display.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public KurobaAlertDialog.Builder setMessage(@Nullable CharSequence message) {
            P.mMessage = message;
            return this;
        }

        /**
         * Set a listener to be invoked when the positive button of the dialog is pressed.
         * @param textId The resource id of the text to display in the positive button
         * @param listener The {@link DialogInterface.OnClickListener} to use.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public KurobaAlertDialog.Builder setPositiveButton(@StringRes int textId, final OnClickListener listener) {
            P.mPositiveButtonText = P.mContext.getText(textId);
            P.mPositiveButtonListener = listener;
            return this;
        }


        @NotNull
        public KurobaAlertDialog.Builder setCustomLinkMovementMethod(@Nullable LinkMovementMethod customLinkMovementMethod) {
            P.customLinkMovementMethod = customLinkMovementMethod;
            return this;
        }

        /**
         * Set a listener to be invoked when the positive button of the dialog is pressed.
         * @param text The text to display in the positive button
         * @param listener The {@link DialogInterface.OnClickListener} to use.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public KurobaAlertDialog.Builder setPositiveButton(CharSequence text, final OnClickListener listener) {
            P.mPositiveButtonText = text;
            P.mPositiveButtonListener = listener;
            return this;
        }

        /**
         * Set a listener to be invoked when the negative button of the dialog is pressed.
         * @param textId The resource id of the text to display in the negative button
         * @param listener The {@link DialogInterface.OnClickListener} to use.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public KurobaAlertDialog.Builder setNegativeButton(@StringRes int textId, final OnClickListener listener) {
            P.mNegativeButtonText = P.mContext.getText(textId);
            P.mNegativeButtonListener = listener;
            return this;
        }

        /**
         * Set a listener to be invoked when the negative button of the dialog is pressed.
         * @param text The text to display in the negative button
         * @param listener The {@link DialogInterface.OnClickListener} to use.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public KurobaAlertDialog.Builder setNegativeButton(CharSequence text, final OnClickListener listener) {
            P.mNegativeButtonText = text;
            P.mNegativeButtonListener = listener;
            return this;
        }

        /**
         * Set a listener to be invoked when the neutral button of the dialog is pressed.
         * @param textId The resource id of the text to display in the neutral button
         * @param listener The {@link DialogInterface.OnClickListener} to use.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public KurobaAlertDialog.Builder setNeutralButton(@StringRes int textId, final OnClickListener listener) {
            P.mNeutralButtonText = P.mContext.getText(textId);
            P.mNeutralButtonListener = listener;
            return this;
        }

        /**
         * Set a listener to be invoked when the neutral button of the dialog is pressed.
         * @param text The text to display in the neutral button
         * @param listener The {@link DialogInterface.OnClickListener} to use.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public KurobaAlertDialog.Builder setNeutralButton(CharSequence text, final OnClickListener listener) {
            P.mNeutralButtonText = text;
            P.mNeutralButtonListener = listener;
            return this;
        }

        /**
         * Sets whether the dialog is cancelable or not.  Default is true.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public KurobaAlertDialog.Builder setCancelable(boolean cancelable) {
            P.mCancelable = cancelable;
            return this;
        }

        /**
         * Set a custom view resource to be the contents of the Dialog. The
         * resource will be inflated, adding all top-level views to the screen.
         *
         * @param layoutResId Resource ID to be inflated.
         * @return this Builder object to allow for chaining of calls to set
         *         methods
         */
        public KurobaAlertDialog.Builder setView(int layoutResId) {
            P.mView = null;
            P.mViewLayoutResId = layoutResId;
            P.mViewSpacingSpecified = false;
            return this;
        }

        /**
         * Sets a custom view to be the contents of the alert dialog.
         * <p>
         * When using a pre-Holo theme, if the supplied view is an instance of
         * a {@link ListView} then the light background will be used.
         * <p>
         * <strong>Note:</strong> To ensure consistent styling, the custom view
         * should be inflated or constructed using the alert dialog's themed
         * context obtained via {@link #getContext()}.
         *
         * @param view the view to use as the contents of the alert dialog
         * @return this Builder object to allow for chaining of calls to set
         *         methods
         */
        public KurobaAlertDialog.Builder setView(View view) {
            P.mView = view;
            P.mViewLayoutResId = 0;
            P.mViewSpacingSpecified = false;
            return this;
        }

        /**
         * Set a custom view to be the contents of the Dialog, specifying the
         * spacing to appear around that view. If the supplied view is an
         * instance of a {@link ListView} the light background will be used.
         *
         * @param view              The view to use as the contents of the Dialog.
         * @param viewSpacingLeft   Spacing between the left edge of the view and
         *                          the dialog frame
         * @param viewSpacingTop    Spacing between the top edge of the view and
         *                          the dialog frame
         * @param viewSpacingRight  Spacing between the right edge of the view
         *                          and the dialog frame
         * @param viewSpacingBottom Spacing between the bottom edge of the view
         *                          and the dialog frame
         * @return This Builder object to allow for chaining of calls to set
         * methods
         *
         *
         * This is currently hidden because it seems like people should just
         * be able to put padding around the view.
         * @hide
         * @deprecated This method has been deprecated.
         */
        @RestrictTo(LIBRARY_GROUP_PREFIX)
        @Deprecated
        public KurobaAlertDialog.Builder setView(View view, int viewSpacingLeft, int viewSpacingTop,
                                                 int viewSpacingRight, int viewSpacingBottom) {
            P.mView = view;
            P.mViewLayoutResId = 0;
            P.mViewSpacingSpecified = true;
            P.mViewSpacingLeft = viewSpacingLeft;
            P.mViewSpacingTop = viewSpacingTop;
            P.mViewSpacingRight = viewSpacingRight;
            P.mViewSpacingBottom = viewSpacingBottom;
            return this;
        }

        @NonNull
        public KurobaAlertDialog create(
                @NonNull ViewGroup parent,
                @NonNull KurobaAlertDialogHostControllerCallbacks callbacks,
                @NonNull AlertDialogHandle alertDialogHandle
        ) {
            // We can't use Dialog's 3-arg constructor with the createThemeContextWrapper param,
            // so we always have to re-set the theme
            final KurobaAlertDialog dialog = new KurobaAlertDialog(P.mContext, parent, callbacks);
            P.apply(dialog.mAlert);
            dialog.mAlert.installContent();
            alertDialogHandle.setDialog(dialog);

            if (alertDialogHandle.isAlreadyDismissed()) {
                dialog.dismiss();
            }

            return dialog;
        }
    }

    public interface AlertDialogHandle {
        @Retention(RetentionPolicy.SOURCE)
        @IntDef({DialogInterface.BUTTON_POSITIVE,
                DialogInterface.BUTTON_NEGATIVE,
                DialogInterface.BUTTON_NEUTRAL})
        public @interface ButtonId {}

        public boolean isAlreadyDismissed();

        public void setDialog(KurobaAlertDialog dialog);
        public void dismiss();

        @Nullable
        public Button getButton(@ButtonId int buttonId);
    }

}
