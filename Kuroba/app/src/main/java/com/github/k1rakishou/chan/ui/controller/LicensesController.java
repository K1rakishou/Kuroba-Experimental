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
package com.github.k1rakishou.chan.ui.controller;

import android.content.Context;
import android.graphics.Color;
import android.util.AndroidRuntimeException;
import android.webkit.WebView;
import android.widget.TextView;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;

import org.jetbrains.annotations.NotNull;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate;

public class LicensesController extends Controller {
    private String title;
    private String url;

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    public LicensesController(Context context, String title, String url) {
        super(context);
        this.title = title;
        this.url = url;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.title = title;

        try {
            WebView webView = new WebView(context);
            webView.loadUrl(url);
            webView.setBackgroundColor(Color.WHITE);
            view = webView;
        } catch (Throwable error) {
            String errmsg = "";
            if (error instanceof AndroidRuntimeException && error.getMessage() != null) {
                if (error.getMessage().contains("MissingWebViewPackageException")) {
                    errmsg = getString(R.string.fail_reason_webview_is_not_installed);
                }
            } else {
                errmsg = getString(R.string.fail_reason_some_part_of_webview_not_initialized, error.getMessage());
            }
            view = inflate(context, R.layout.layout_webview_error);
            ((TextView) view.findViewById(R.id.text)).setText(errmsg);
        }
    }
}
