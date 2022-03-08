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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString;
import static com.github.k1rakishou.common.AndroidUtils.hideKeyboard;
import static com.github.k1rakishou.common.AndroidUtils.requestKeyboardFocus;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableToolbarSearchLayoutEditText;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.chan.utils.KtExtensionsKt;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.google.android.material.textfield.TextInputEditText;

import javax.inject.Inject;

import kotlin.Unit;

public class SearchLayout extends LinearLayout implements ThemeEngine.ThemeChangesListener {

    @Inject
    ThemeEngine themeEngine;

    private TextInputEditText searchView;
    private ImageView clearButton;
    private boolean autoRequestFocus = true;

    @Nullable
    private TextWatcher textWatcher = null;

    public SearchLayout(Context context) {
        super(context);
        init();
    }

    public SearchLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SearchLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        if (!isInEditMode()) {
            AppModuleAndroidUtils.extractActivityComponent(getContext())
                    .inject(this);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        themeEngine.addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        themeEngine.removeListener(this);
    }

    @Override
    public void onThemeChanged() {
        if (clearButton != null) {
            clearButton.getDrawable().setTint(themeEngine.getChanTheme().getTextColorPrimary());
        }
    }

    public void setAutoRequestFocus(boolean request) {
        this.autoRequestFocus = request;
    }

    public void setCallback(final SearchLayoutCallback callback) {
        setCallback(false, false, callback);
    }

    public void setCallback(boolean isToolbar, boolean overrideDoneAction, final SearchLayoutCallback callback) {
        if (isToolbar) {
            searchView = new ColorizableToolbarSearchLayoutEditText(getContext());
        } else {
            searchView = new ColorizableEditText(getContext());
        }

        searchView.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_DONE);
        searchView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        searchView.setHint(getString(R.string.search_hint));
        searchView.setSingleLine(true);
        searchView.setBackgroundResource(0);
        searchView.setPadding(0, 0, 0, 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Hopefully this will fix these crashes:
            // java.lang.RuntimeException:
            //    android.os.TransactionTooLargeException: data parcel size 296380 bytes
            searchView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }

        clearButton = new ImageView(getContext());

        if (textWatcher != null) {
            searchView.removeTextChangedListener(textWatcher);
            textWatcher = null;
        }

        textWatcher = new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearButton.setAlpha(s.length() == 0 ? 0.0f : 1.0f);
                callback.onSearchEntered(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        searchView.addTextChangedListener(textWatcher);
        searchView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(searchView);

                if (overrideDoneAction) {
                    Editable editable = searchView.getText();

                    String input = "";
                    if (editable != null) {
                        input = editable.toString();
                    }

                    callback.onDoneClicked(input);
                } else {
                    callback.onSearchEntered(getText());
                }

                return true;
            }

            return false;
        });
        searchView.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                view.postDelayed(() -> hideKeyboard(view), 100);
            } else if (autoRequestFocus) {
                view.postDelayed(() -> requestKeyboardFocus(view), 100);
            }
        });

        LinearLayout.LayoutParams searchViewParams = new LinearLayout.LayoutParams(0, dp(getContext(), 36), 1);
        searchViewParams.gravity = Gravity.CENTER_VERTICAL;
        addView(searchView, searchViewParams);
        searchView.setFocusable(true);

        if (autoRequestFocus) {
            searchView.requestFocus();
        }

        clearButton.setAlpha(0f);
        clearButton.setImageResource(R.drawable.ic_clear_white_24dp);
        clearButton.setScaleType(ImageView.ScaleType.CENTER);
        clearButton.setOnClickListener(v -> {
            searchView.setText("");
            requestKeyboardFocus(searchView);
        });

        addView(clearButton, dp(getContext(), 48), MATCH_PARENT);

        if (!isInEditMode()) {
            onThemeChanged();
        }
    }

    public void setTextIgnoringWatcher(String searchText) {
        if (textWatcher != null) {
            KtExtensionsKt.doIgnoringTextWatcher(
                    searchView,
                    textWatcher,
                    appCompatEditText -> {
                        appCompatEditText.setText(searchText);
                        return Unit.INSTANCE;
                    });
        } else {
            searchView.setText(searchText);
        }
    }

    public void setText(String searchText) {
        Editable prevEditable = searchView.getText();
        String prevText = null;

        if (prevEditable == null) {
            prevText = "";
        } else {
            prevText = prevEditable.toString();
        }

        searchView.getText().replace(0, prevText.length(), searchText);
    }

    public String getText() {
        return searchView.getText().toString();
    }

    public void setCatalogSearchColors() {
        searchView.setTextColor(Color.WHITE);
        searchView.setHintTextColor(0x88ffffff);
        clearButton.getDrawable().setTintList(null);
    }

    public interface SearchLayoutCallback {
        void onSearchEntered(String entered);

        default void onDoneClicked(String input) {
            // no-op
        }
    }
}
