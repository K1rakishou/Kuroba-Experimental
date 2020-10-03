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
package com.github.k1rakishou.chan.ui.controller.settings;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.StartActivity;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.ui.helper.RefreshUIMessage;
import com.github.k1rakishou.chan.ui.settings.IntegerSettingView;
import com.github.k1rakishou.chan.ui.settings.LinkSettingView;
import com.github.k1rakishou.chan.ui.settings.ListSettingView;
import com.github.k1rakishou.chan.ui.settings.SettingView;
import com.github.k1rakishou.chan.ui.settings.SettingsGroup;
import com.github.k1rakishou.chan.ui.settings.StringSettingView;
import com.github.k1rakishou.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.CompositeDisposable;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.k1rakishou.chan.Chan.inject;
import static com.github.k1rakishou.chan.utils.AndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AndroidUtils.findViewsById;
import static com.github.k1rakishou.chan.utils.AndroidUtils.isTablet;
import static com.github.k1rakishou.chan.utils.AndroidUtils.postToEventBus;
import static com.github.k1rakishou.chan.utils.AndroidUtils.updatePaddings;
import static com.github.k1rakishou.chan.utils.AndroidUtils.waitForLayout;

public class SettingsController
        extends Controller
        implements AndroidUtils.OnMeasuredCallback {
    private final String TAG = "SettingsController";

    protected LinearLayout content;
    private List<SettingsGroup> groups = new ArrayList<>();
    private List<SettingView> requiresUiRefresh = new ArrayList<>();
    // Very user unfriendly.
    private List<SettingView> requiresRestart = new ArrayList<>();
    protected CompositeDisposable compositeDisposable = new CompositeDisposable();
    private boolean needRestart = false;

    public SettingsController(Context context) {
        super(context);

        inject(this);
    }

    @Override
    public void onShow() {
        super.onShow();

        waitForLayout(view, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        disposeAndClearSettings();
        compositeDisposable.clear();

        if (needRestart) {
            ((StartActivity) context).restartApp();
        }
    }

    protected void disposeAndClearSettings() {
        for (SettingsGroup settingsGroup : groups) {
            for (SettingView settingView : settingsGroup.settingViews) {
                settingView.dispose();
            }
        }

        for (SettingView settingView : requiresUiRefresh) {
            settingView.dispose();
        }

        for (SettingView settingView : requiresRestart) {
            settingView.dispose();
        }

        groups.clear();
        requiresUiRefresh.clear();
        requiresRestart.clear();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        waitForLayout(view, this);
    }

    @Override
    public boolean onMeasured(View view) {
        setMargins();
        return false;
    }

    public void onPreferenceChange(SettingView item) {
        if ((item instanceof ListSettingView) || (item instanceof StringSettingView)
                || (item instanceof IntegerSettingView) || (item instanceof LinkSettingView)) {
            setDescriptionText(item.view, item.getTopDescription(), item.getBottomDescription());
        }

        if (requiresUiRefresh.contains(item)) {
            postToEventBus(new RefreshUIMessage("SettingsController refresh"));
        } else if (requiresRestart.contains(item)) {
            needRestart = true;
        }
    }

    private void setMargins() {
        int margin = 0;
        if (isTablet()) {
            margin = (int) (view.getWidth() * 0.1);
        }

        int itemMargin = 0;
        if (isTablet()) {
            itemMargin = dp(16);
        }

        List<View> items = findViewsById(content, R.id.preference_item);
        for (View item : items) {
            updatePaddings(item, itemMargin, itemMargin, -1, -1);
        }
    }

    private void setDescriptionText(View view, String topText, String bottomText) {
        ((TextView) view.findViewById(R.id.top)).setText(topText);

        final TextView bottom = view.findViewById(R.id.bottom);
        if (bottom != null) {
            bottom.setText(bottomText);
            bottom.setVisibility(bottomText == null ? GONE : VISIBLE);
        }
    }
}
