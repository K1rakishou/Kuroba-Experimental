package com.github.k1rakishou.prefs;

import com.github.k1rakishou.Setting;
import com.github.k1rakishou.SettingProvider;

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
            settingState.onNext(value);
        }
    }

    @Override
    public void setSync(Integer value) {
        if (!value.equals(get())) {
            settingProvider.putIntSync(key, value);
            cached = value;
            settingState.onNext(value);
        }
    }
}
