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

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.Setting;
import com.github.adamantcheese.chan.ui.controller.floating_menu.FloatingListMenuController;
import com.github.adamantcheese.chan.ui.controller.settings.SettingsController;
import com.github.adamantcheese.chan.ui.view.floating_menu.FloatingListMenu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import kotlin.Unit;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class ListSettingView<T>
        extends SettingView
        implements View.OnClickListener {
    public final List<Item> items;
    public int selected;
    private Setting<T> setting;

    public ListSettingView(
            SettingsController settingsController, Setting<T> setting, int name, String[] itemNames, String[] keys
    ) {
        this(settingsController, setting, getString(name), itemNames, keys);
    }

    public ListSettingView(
            SettingsController settingsController, Setting<T> setting, String name, String[] itemNames, String[] keys
    ) {
        super(settingsController, name);

        this.setting = setting;

        items = new ArrayList<>(itemNames.length);
        for (int i = 0; i < itemNames.length; i++) {
            items.add(i, new Item<>(itemNames[i], keys[i]));
        }

        updateSelection();
    }

    public ListSettingView(SettingsController settingsController, Setting<T> setting, int name, Item[] items) {
        this(settingsController, setting, getString(name), items);
    }

    public ListSettingView(SettingsController settingsController, Setting<T> setting, int name, List<Item> items) {
        this(settingsController, setting, getString(name), items);
    }

    public ListSettingView(SettingsController settingsController, Setting<T> setting, String name, Item[] items) {
        this(settingsController, setting, name, Arrays.asList(items));
    }

    public ListSettingView(SettingsController settingsController, Setting<T> setting, String name, List<Item> items) {
        super(settingsController, name);
        this.setting = setting;
        this.items = items;

        updateSelection();
    }

    public String getBottomDescription() {
        return items.get(selected).name;
    }

    public Setting<T> getSetting() {
        return setting;
    }

    @Override
    public void setView(View view) {
        super.setView(view);
        view.setOnClickListener(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        view.setEnabled(enabled);
        view.findViewById(R.id.top).setEnabled(enabled);
        View bottom = view.findViewById(R.id.bottom);
        if (bottom != null) {
            bottom.setEnabled(enabled);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onClick(View v) {
        List<FloatingListMenu.FloatingListMenuItem> menuItems = new ArrayList<>(items.size());
        for (Item item : items) {
            FloatingListMenu.FloatingListMenuItem menuItem = new FloatingListMenu.FloatingListMenuItem(
                    item.key,
                    item.name,
                    item.enabled
            );

            menuItems.add(menuItem);
        }

        FloatingListMenuController floatingListMenuController = new FloatingListMenuController(
                v.getContext(),
                menuItems,
                item -> {
                    T selectedKey = (T) item.getKey();
                    setting.set(selectedKey);
                    updateSelection();
                    settingsController.onPreferenceChange(this);

                    return Unit.INSTANCE;
                }
        );

        settingsController.presentController(floatingListMenuController, true);
    }

    public void updateSelection() {
        T selectedKey = setting.get();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).key.equals(selectedKey)) {
                selected = i;
                break;
            }
        }
    }

    public static class Item<T> {
        public final String name;
        public final T key;
        public boolean enabled;

        public Item(String name, T key) {
            this.name = name;
            this.key = key;
            enabled = true;
        }
    }
}
