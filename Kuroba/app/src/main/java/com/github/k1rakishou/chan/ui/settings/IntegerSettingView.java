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
package com.github.k1rakishou.chan.ui.settings;

import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import com.github.k1rakishou.Setting;
import com.github.k1rakishou.chan.core.helper.DialogFactory;
import com.github.k1rakishou.chan.ui.controller.settings.SettingsController;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;

import javax.inject.Inject;

import kotlin.Unit;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.k1rakishou.common.AndroidUtils.dp;
import static com.github.k1rakishou.common.AndroidUtils.getString;

/**
 * Created by Zetsubou on 02.07.2015
 */
public class IntegerSettingView
        extends SettingView
        implements View.OnClickListener {

    @Inject
    DialogFactory dialogFactory;

    private final Setting<Integer> setting;
    private final String dialogTitle;

    public IntegerSettingView(SettingsController controller, Setting<Integer> setting, int name, int dialogTitle) {
        this(controller, setting, getString(name), getString(dialogTitle));
    }

    public IntegerSettingView(
            SettingsController settingsController,
            Setting<Integer> setting,
            String name,
            String dialogTitle
    ) {
        super(settingsController, name);

        AppModuleAndroidUtils.extractStartActivityComponent(settingsController.context)
                .inject(this);

        this.setting = setting;
        this.dialogTitle = dialogTitle;
    }

    @Override
    public void setView(View view) {
        super.setView(view);
        view.setOnClickListener(this);
    }

    @Override
    public String getBottomDescription() {
        return setting.get() != null ? setting.get().toString() : null;
    }

    @Override
    public void onClick(View v) {
        LinearLayout container = new LinearLayout(v.getContext());
        container.setPadding(dp(24), dp(8), dp(24), 0);

        final ColorizableEditText editText = new ColorizableEditText(v.getContext());
        editText.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN);
        editText.setText(setting.get().toString());
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setSingleLine(true);
        editText.setSelection(editText.getText().length());
        container.addView(editText, MATCH_PARENT, WRAP_CONTENT);

        AlertDialog dialog = DialogFactory.Builder.newBuilder(v.getContext(), dialogFactory)
                .withCustomView(container)
                .withTitle(dialogTitle)
                .withOnPositiveButtonClickListener((dialog1) -> {
                    try {
                        setting.set(Integer.parseInt(editText.getText().toString()));
                    } catch (Exception e) {
                        setting.set(setting.getDefault());
                    }

                    settingsController.onPreferenceChange(IntegerSettingView.this);
                    return Unit.INSTANCE;
                })
                .create();

        if (dialog != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }
}