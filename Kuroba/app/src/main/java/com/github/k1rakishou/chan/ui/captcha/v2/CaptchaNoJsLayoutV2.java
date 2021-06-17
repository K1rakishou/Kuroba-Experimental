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
package com.github.k1rakishou.chan.ui.captcha.v2;

import static com.github.k1rakishou.chan.core.site.SiteAuthentication.Type.CAPTCHA2_NOJS;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewKt;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.site.SiteAuthentication;
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback;
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutInterface;
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView;
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.chan.utils.BackgroundUtils;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_themes.ThemeEngine;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import kotlin.Unit;

public class CaptchaNoJsLayoutV2
        extends TouchBlockingFrameLayout
        implements AuthenticationLayoutInterface,
        CaptchaNoJsPresenterV2.AuthenticationCallbacks,
        ThemeEngine.ThemeChangesListener {
    private static final String TAG = "CaptchaNoJsLayoutV2";
    private static final long RECAPTCHA_TOKEN_LIVE_TIME = TimeUnit.MINUTES.toMillis(2);

    private ColorizableTextView captchaChallengeTitle;
    private GridView captchaImagesGrid;
    private ColorizableBarButton captchaVerifyButton;

    private CaptchaNoJsV2Adapter adapter;
    private CaptchaNoJsPresenterV2 presenter;
    private AuthenticationLayoutCallback callback;
    @Nullable
    private ConstraintLayout captchaButtonsHolder;
    @Nullable
    private LinearLayout controlsHolder;
    private ColorizableBarButton useOldCaptchaButton;
    private ColorizableBarButton reloadCaptchaButton;

    private boolean isSplitLayoutMode;
    private int prevOrientation = 0;

    @Inject
    CaptchaHolder captchaHolder;
    @Inject
    ProxiedOkHttpClient proxiedOkHttpClient;
    @Inject
    ThemeEngine themeEngine;
    @Inject
    AppConstants appConstants;

    public CaptchaNoJsLayoutV2(@NonNull Context context) {
        super(context, null, 0);

        AppModuleAndroidUtils.extractActivityComponent(getContext())
                .inject(this);

        this.isSplitLayoutMode = ChanSettings.isSplitLayoutMode();
        this.presenter = new CaptchaNoJsPresenterV2(this, proxiedOkHttpClient, appConstants, context);
        this.adapter = new CaptchaNoJsV2Adapter();

        initViewInternal(context);
    }

    private void initViewInternal(@NonNull Context context) {
        int currentOrientation = AppModuleAndroidUtils.getScreenOrientation();
        if (currentOrientation == prevOrientation) {
            return;
        }

        View view;
        removeAllViews();

        if (!isSplitLayoutMode && currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            view = inflate(context, R.layout.layout_captcha_nojs_v2_landscape, this);
        } else {
            view = inflate(context, R.layout.layout_captcha_nojs_v2_portrait, this);
        }

        captchaChallengeTitle = view.findViewById(R.id.captcha_layout_v2_title);
        captchaImagesGrid = view.findViewById(R.id.captcha_layout_v2_images_grid);
        captchaVerifyButton = view.findViewById(R.id.captcha_layout_v2_verify_button);
        useOldCaptchaButton = view.findViewById(R.id.captcha_layout_v2_use_old_captcha_button);
        reloadCaptchaButton = view.findViewById(R.id.captcha_layout_v2_reload_button);
        controlsHolder = view.findViewById(R.id.captcha_layout_v2_controls_holder);
        captchaButtonsHolder = view.findViewById(R.id.captcha_layout_v2_buttons);

        captchaVerifyButton.setOnClickListener(v -> sendVerificationResponse());
        useOldCaptchaButton.setOnClickListener(v -> callback.onFallbackToV1CaptchaView());
        reloadCaptchaButton.setOnClickListener(v -> reset());

        onThemeChanged();
        prevOrientation = currentOrientation;
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
        int primaryColor = themeEngine.getChanTheme().getPrimaryColor();

        captchaChallengeTitle.setBackgroundColor(primaryColor);

        if (captchaButtonsHolder != null) {
            captchaButtonsHolder.setBackgroundColor(primaryColor);
        }

        if (controlsHolder != null) {
            controlsHolder.setBackgroundColor(primaryColor);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        initViewInternal(getContext());
        requestCaptchaInfoInternal(true);
    }

    @Override
    public void initialize(SiteAuthentication authentication, AuthenticationLayoutCallback callback) {
        this.callback = callback;

        if (authentication.type != CAPTCHA2_NOJS) {
            callback.onFallbackToV1CaptchaView();
            return;
        }

        presenter.init(authentication.siteKey, authentication.baseUrl);
    }

    @Override
    public void reset() {
        hardReset();
    }

    @Override
    public void hardReset() {
        requestCaptchaInfoInternal(false);
    }

    private void requestCaptchaInfoInternal(boolean ignoreThrottling) {
        CaptchaNoJsPresenterV2.RequestCaptchaInfoError captchaInfoError =
                presenter.requestCaptchaInfo(ignoreThrottling);

        switch (captchaInfoError) {
            case OK:
            case ALREADY_SHUTDOWN:
                break;
            case HOLD_YOUR_HORSES:
                showToast(
                        getContext(),
                        R.string.captcha_layout_v2_you_are_requesting_captcha_too_fast,
                        Toast.LENGTH_LONG
                );
                break;
            case ALREADY_IN_PROGRESS:
                showToast(
                        getContext(),
                        R.string.captcha_layout_v2_captcha_request_is_already_in_progress,
                        Toast.LENGTH_LONG
                );
                break;
        }
    }

    @Override
    public void onCaptchaInfoParsed(CaptchaInfo captchaInfo) {
        BackgroundUtils.runOnMainThread(() -> {
            ViewKt.doOnPreDraw(this, view -> {
                captchaVerifyButton.setEnabled(true);
                renderCaptchaWindow(captchaInfo);

                return Unit.INSTANCE;
            });
        });
    }

    @Override
    public void onVerificationDone(String verificationToken) {
        BackgroundUtils.runOnMainThread(() -> {
            captchaHolder.addNewToken(verificationToken, RECAPTCHA_TOKEN_LIVE_TIME);

            captchaVerifyButton.setEnabled(true);
            callback.onAuthenticationComplete();
        });
    }

    // Called when we got response from re-captcha but could not parse some part of it
    @Override
    public void onCaptchaInfoParseError(Throwable error) {
        BackgroundUtils.runOnMainThread(() -> {
            Logger.e(TAG, "CaptchaV2 error", error);

            String message = error.getMessage();
            if (!TextUtils.isEmpty(message)) {
                showToast(getContext(), message, Toast.LENGTH_LONG);
            }

            captchaVerifyButton.setEnabled(true);
            callback.onFallbackToV1CaptchaView();
        });
    }

    private void renderCaptchaWindow(CaptchaInfo captchaInfo) {
        try {
            setCaptchaTitle(captchaInfo);
            captchaImagesGrid.setAdapter(adapter);

            int imageSize = Math.min(
                    captchaImagesGrid.getWidth(),
                    captchaImagesGrid.getHeight()
            );

            int columnsCount;
            switch (captchaInfo.getCaptchaType()) {
                case CANONICAL:
                    columnsCount = 3;
                    break;
                case NO_CANONICAL:
                    columnsCount = 2;
                    break;
                default:
                    throw new IllegalStateException("Unknown captcha type");
            }

            imageSize /= columnsCount;
            captchaImagesGrid.setNumColumns(columnsCount);

            adapter.setImageSize(imageSize);
            adapter.setImages(captchaInfo.challengeImages);

            captchaImagesGrid.postInvalidate();
            captchaVerifyButton.setEnabled(true);
        } catch (Throwable error) {
            if (callback != null) {
                callback.onFallbackToV1CaptchaView();
            }
        }
    }

    private void setCaptchaTitle(CaptchaInfo captchaInfo) {
        if (captchaInfo.getCaptchaTitle() == null) {
            return;
        }

        if (!captchaInfo.getCaptchaTitle().hasBold()) {
            captchaChallengeTitle.setText(captchaInfo.getCaptchaTitle().getTitle());
            return;
        }

        SpannableString spannableString = new SpannableString(
                captchaInfo.getCaptchaTitle().getTitle()
        );

        spannableString.setSpan(
                new StyleSpan(Typeface.BOLD),
                captchaInfo.getCaptchaTitle().getBoldStart(),
                captchaInfo.getCaptchaTitle().getBoldEnd(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        captchaChallengeTitle.setText(spannableString);
    }

    private void sendVerificationResponse() {
        List<Integer> selectedIds = adapter.getCheckedImageIds();

        try {
            CaptchaNoJsPresenterV2.VerifyError verifyError = presenter.verify(selectedIds);
            switch (verifyError) {
                case OK:
                    captchaVerifyButton.setEnabled(false);
                    break;
                case NO_IMAGES_SELECTED:
                    showToast(
                            getContext(),
                            R.string.captcha_layout_v2_you_have_to_select_at_least_one_image,
                            Toast.LENGTH_LONG
                    );
                    break;
                case ALREADY_IN_PROGRESS:
                    showToast(
                            getContext(),
                            R.string.captcha_layout_v2_verification_already_in_progress,
                            Toast.LENGTH_LONG
                    );
                    break;
                case ALREADY_SHUTDOWN:
                    // do nothing
                    break;
            }
        } catch (Throwable error) {
            onCaptchaInfoParseError(error);
        }
    }

    @Override
    public void onDestroy() {
        adapter.onDestroy();
        presenter.onDestroy();
    }
}
