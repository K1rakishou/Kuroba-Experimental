package com.github.k1rakishou.prefs;

import com.github.k1rakishou.Setting;
import com.github.k1rakishou.SettingProvider;

public class BooleanSetting extends Setting<Boolean> {
    private volatile boolean hasCached = false;
    private Boolean cached;

    public BooleanSetting(SettingProvider settingProvider, String key, Boolean def) {
        super(settingProvider, key, def);
    }

    @Override
    public synchronized Boolean get() {
        if (!hasCached) {
            cached = settingProvider.getBoolean(key, def);
            hasCached = true;
        }

        return cached;
    }

    @Override
    public synchronized void set(Boolean value) {
        if (!value.equals(get())) {
            cached = value;
            settingProvider.putBoolean(key, value);
            settingState.onNext(value);
        }
    }

    public synchronized void setSync(Boolean value) {
        if (!value.equals(get())) {
            cached = value;
            settingProvider.putBooleanSync(key, value);
            settingState.onNext(value);
        }
    }

    public synchronized boolean toggle() {
        boolean newValue = !get();

        set(newValue);
        return newValue;
    }

}
