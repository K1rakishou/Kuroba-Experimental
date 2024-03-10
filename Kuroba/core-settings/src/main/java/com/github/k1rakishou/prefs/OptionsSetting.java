package com.github.k1rakishou.prefs;

import com.github.k1rakishou.OptionSettingItem;
import com.github.k1rakishou.Setting;
import com.github.k1rakishou.SettingProvider;

public class OptionsSetting<T extends Enum & OptionSettingItem> extends Setting<T> {
    private volatile boolean hasCached = false;

    private T cached;
    private T[] items;

    public OptionsSetting(SettingProvider settingProvider, String key, Class<T> clazz, T def) {
        super(settingProvider, key, def);

        this.items = clazz.getEnumConstants();
    }

    public T[] getItems() {
        return items;
    }

    @Override
    public T get() {
        if (hasCached) {
            return cached;
        }

        String itemName = settingProvider.getString(key, def.getKey());
        T selectedItem = null;
        for (T item : items) {
            if (item.getKey().equals(itemName)) {
                selectedItem = item;
            }
        }
        if (selectedItem == null) {
            selectedItem = def;
        }

        cached = selectedItem;
        hasCached = true;
        return cached;
    }

    @Override
    public void set(T value) {
        if (!value.equals(get())) {
            settingProvider.putString(key, value.getKey());
            cached = value;
        }
    }

    @Override
    public void setSync(T value) {
        if (!value.equals(get())) {
            settingProvider.putStringSync(key, value.getKey());
            cached = value;
        }
    }
}
