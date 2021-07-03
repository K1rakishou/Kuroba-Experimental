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
package com.github.k1rakishou.chan.ui.captcha;

import static android.view.View.MeasureSpec.AT_MOST;
import static com.github.k1rakishou.ChanSettings.LayoutMode.AUTO;
import static com.github.k1rakishou.ChanSettings.LayoutMode.SPLIT;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openLink;
import static com.github.k1rakishou.common.AndroidUtils.getDisplaySize;
import static com.github.k1rakishou.common.AndroidUtils.hideKeyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.core.site.SiteAuthentication;
import com.github.k1rakishou.chan.ui.controller.settings.captcha.JsCaptchaCookiesJar;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.chan.utils.BackgroundUtils;
import com.github.k1rakishou.chan.utils.IOUtils;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor;
import com.google.gson.Gson;

import javax.inject.Inject;

public class CaptchaLayout
        extends WebView
        implements AuthenticationLayoutInterface {
    private static final String TAG = "CaptchaLayout";

    private static final String COOKIE_DOMAIN = "google.com";

    private AuthenticationLayoutCallback callback;
    private boolean loaded = false;
    private String baseUrl;
    private String siteKey;
    private boolean isInvisible;

    @Inject
    CaptchaHolder captchaHolder;
    @Inject
    Gson gson;
    @Inject
    ThemeEngine themeEngine;

    public CaptchaLayout(Context context) {
        super(context);
        init();
    }

    public CaptchaLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CaptchaLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        AppModuleAndroidUtils.extractActivityComponent(getContext())
                .inject(this);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public void initialize(
            SiteDescriptor siteDescriptor,
            SiteAuthentication authentication,
            AuthenticationLayoutCallback callback
    ) {
        this.callback = callback;
        this.siteKey = authentication.siteKey;
        this.baseUrl = authentication.baseUrl;
        this.isInvisible = authentication.type == SiteAuthentication.Type.CAPTCHA2_INVISIBLE;

        requestDisallowInterceptTouchEvent(true);
        hideKeyboard(this);
        getSettings().setJavaScriptEnabled(true);

        JsCaptchaCookiesJar jsCaptchaCookiesJar = getJsCaptchaCookieJar(gson);
        if (jsCaptchaCookiesJar.isValid()) {
            setUpJsCaptchaCookies(jsCaptchaCookiesJar);
        }

        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(@NonNull ConsoleMessage consoleMessage) {
                Logger.d(TAG, consoleMessage.lineNumber() + ":" + consoleMessage.message() + " " + consoleMessage.sourceId());
                return true;
            }
        });

        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (Uri.parse(url).getHost().equals(Uri.parse(CaptchaLayout.this.baseUrl).getHost())) {
                    return false;
                } else {
                    openLink(url);
                    return true;
                }
            }
        });
        setBackgroundColor(0x00000000);
        addJavascriptInterface(new CaptchaInterface(this), "CaptchaCallback");
    }

    @Override
    public void onDestroy() {
    }

    private JsCaptchaCookiesJar getJsCaptchaCookieJar(Gson gson) {
        try {
            return gson.fromJson(ChanSettings.jsCaptchaCookies.get(), JsCaptchaCookiesJar.class);
        } catch (Throwable error) {
            Logger.e(TAG, "Error while trying to deserialize JsCaptchaCookiesJar", error);
            return JsCaptchaCookiesJar.empty();
        }
    }

    private void setUpJsCaptchaCookies(JsCaptchaCookiesJar jsCaptchaCookiesJar) {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(this, true);
        cookieManager.removeAllCookies(null);

        for (String c : jsCaptchaCookiesJar.getCookies()) {
            cookieManager.setCookie(COOKIE_DOMAIN, c);
        }
    }

    public void reset() {
        if (loaded) {
            loadUrl("javascript:grecaptcha.reset()");
        } else {
            hardReset();
        }
    }

    @Override
    public void hardReset() {
        if (isInvisible) {
            loadCaptchaV2Invisible();
        } else {
            loadCaptchaV2Normal();
        }
    }

    private void loadCaptchaV2Invisible() {
        Logger.d(TAG, "loadCaptchaV2Invisible()");

        String html = IOUtils.assetAsString(getContext(), "html/captcha2_invisible.html");
        html = html.replace("__site_key__", siteKey);
        html = html.replace("__theme__", themeEngine.getChanTheme().isLightTheme() ? "light" : "dark");

        loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null);
    }

    private void loadCaptchaV2Normal() {
        Logger.d(TAG, "loadCaptchaV2Normal()");

        String html = IOUtils.assetAsString(getContext(), "html/captcha2.html");
        html = html.replace("__site_key__", siteKey);
        html = html.replace("__theme__", themeEngine.getChanTheme().isLightTheme() ? "light" : "dark");

        Point displaySize = getDisplaySize(getContext());
        boolean isSplitMode = ChanSettings.layoutMode.get() == SPLIT
                || (ChanSettings.layoutMode.get() == AUTO && isTablet());

        measure(
                // 0.35 is from SplitNavigationControllerLayout for the smaller side; measure for the
                // larger of the two sides to find left/right
                MeasureSpec.makeMeasureSpec(isSplitMode ? (int) (displaySize.x * 0.65) : displaySize.x, AT_MOST),
                MeasureSpec.makeMeasureSpec(displaySize.y, AT_MOST)
        );

        // for a 2560 wide screen, partitions in split layout are 896(equal) / 2(divider) / 1662
        // (devicewidth*0.65 - 2(divider)) for some reason, the measurement of THIS view's
        // width is larger than the parent view's width; makes no sense but once onDraw is called,
        // the parent has the correct width, so we use that
        int containerWidth = ((View) getParent()).getMeasuredWidth();

        // if split, smaller side has captcha on the left, larger right; otherwise always on the left
        html = html.replace(
                "__positioning_horizontal__",
                // equal is left, greater is right
                isSplitMode ? (containerWidth == displaySize.x * 0.35 ? "left" : "right") : "left"
        );
        html = html.replace(
                "__positioning_vertical__",
                // split mode should always be on the bottom
                isSplitMode ? "bottom" : (ChanSettings.captchaOnBottom.get() ? "bottom" : "top")
        );

        loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null);
    }

    private void onCaptchaEntered(String challenge, String response) {
        if (TextUtils.isEmpty(response)) {
            reset();
            return;
        }

        captchaHolder.addNewToken(response);
        callback.onAuthenticationComplete();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!loaded) {
            loaded = true;
            hardReset();
        }

        super.onDraw(canvas);
    }

    public static class CaptchaInterface {
        private final CaptchaLayout layout;

        public CaptchaInterface(CaptchaLayout layout) {
            this.layout = layout;
        }

        @JavascriptInterface
        public void onCaptchaEntered(final String response) {
            BackgroundUtils.runOnMainThread(() -> layout.onCaptchaEntered(null, response));
        }

        @JavascriptInterface
        public void onCaptchaEnteredv1(final String challenge, final String response) {
            BackgroundUtils.runOnMainThread(() -> layout.onCaptchaEntered(challenge, response));
        }
    }
}
