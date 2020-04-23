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
package com.github.adamantcheese.chan.ui.settings;

import android.view.View;
import android.widget.CompoundButton;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.BooleanSetting;
import com.github.adamantcheese.chan.core.settings.Setting;
import com.github.adamantcheese.chan.ui.controller.settings.SettingsController;
import com.github.adamantcheese.chan.utils.Logger;

import io.reactivex.disposables.Disposable;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class BooleanSettingView
        extends SettingView
        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "BooleanSettingView";

    private SwitchCompat switcher;
    private Setting<Boolean> setting;
    private String description;
    @Nullable
    private Disposable disposable = null;
    private boolean building = true;

    public BooleanSettingView(
            SettingsController controller,
            Setting<Boolean> setting,
            int name,
            int description
    ) {
        this(controller, setting, null, getString(name), getString(description));
    }

    public BooleanSettingView(
            SettingsController controller,
            Setting<Boolean> setting,
            @Nullable BooleanSetting dependsOnSetting,
            int name,
            int description
    ) {
        this(controller, setting, dependsOnSetting, getString(name), getString(description));
    }

    public BooleanSettingView(
            SettingsController controller,
            Setting<Boolean> setting,
            int name,
            String description
    ) {
        this(controller, setting, null, getString(name), description);
    }

    public BooleanSettingView(
            SettingsController controller,
            Setting<Boolean> setting,
            @Nullable BooleanSetting dependsOnSetting,
            int name,
            String description
    ) {
        this(controller, setting, dependsOnSetting, getString(name), description);
    }

    public BooleanSettingView(
            SettingsController settingsController,
            Setting<Boolean> setting,
            String name,
            String description
    ) {
        this(settingsController, setting, null, name, description);
    }

    public BooleanSettingView(
            SettingsController settingsController,
            Setting<Boolean> setting,
            @Nullable BooleanSetting dependsOnSetting,
            String name,
            String description
    ) {
        super(settingsController, name);
        this.setting = setting;
        this.description = description;

        if (dependsOnSetting != null) {
            disposable = dependsOnSetting.listenForChanges()
                    .subscribe((enabled) -> {
                                if (!enabled) {
                                    switcher.setChecked(false);
                                    setting.set(false);
                                }

                                setEnabled(enabled);
                            }, (error) -> {
                                Logger.e(TAG, "Unknown error while listening to parent setting", error);
                            }
                    );
        }
    }

    @Override
    public void setView(View view) {
        super.setView(view);

        view.setOnClickListener(this);

        switcher = view.findViewById(R.id.switcher);
        switcher.setOnCheckedChangeListener(this);
        switcher.setChecked(setting.get());

        building = false;
    }

    @Override
    public String getBottomDescription() {
        return description;
    }

    @Override
    public void setEnabled(boolean enabled) {
        view.setEnabled(enabled);
        view.findViewById(R.id.top).setEnabled(enabled);
        View bottom = view.findViewById(R.id.bottom);
        if (bottom != null) {
            bottom.setEnabled(enabled);
        }
        switcher.setEnabled(enabled);
    }

    @Override
    public void onClick(View v) {
        switcher.toggle();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (!building) {
            setting.set(isChecked);
            settingsController.onPreferenceChange(this);
        }
    }

    @Override
    public void dispose() {
        super.dispose();

        if (disposable != null) {
            disposable.dispose();
            disposable = null;
        }
    }
}
