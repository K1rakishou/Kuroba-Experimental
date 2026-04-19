package com.github.k1rakishou.deprecated.prefs;

import com.github.k1rakishou.deprecated.Setting;
import com.github.k1rakishou.deprecated.SettingProvider;

@Deprecated
public class IntegerSetting extends Setting<Integer> {
    private volatile boolean hasCached = false;
    private Integer cached;

    public IntegerSetting(SettingProvider settingProvider, String key, Integer def) {
        super(settingProvider, key, def);
    }

    @Override
    public Integer get() {
        if (!hasCached) {
            cached = settingProvider.getInt(key, def);
            hasCached = true;
        }

        return cached;
    }

    @Override
    public void set(Integer value) {
        if (!value.equals(get())) {
            settingProvider.putInt(key, value);
            cached = value;
        }
    }

    @Override
    public void setSync(Integer value) {
        if (!value.equals(get())) {
            settingProvider.putIntSync(key, value);
            cached = value;
        }
    }
}
