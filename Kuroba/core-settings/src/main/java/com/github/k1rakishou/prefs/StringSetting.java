package com.github.k1rakishou.prefs;

import com.github.k1rakishou.Setting;
import com.github.k1rakishou.SettingProvider;

public class StringSetting extends Setting<String> {
    private volatile boolean hasCached = false;
    private String cached;

    public StringSetting(SettingProvider settingProvider, String key, String def) {
        super(settingProvider, key, def);
    }

    @Override
    public String get() {
        if (!hasCached) {
            cached = settingProvider.getString(key, def);
            hasCached = true;
        }
        return cached;
    }

    @Override
    public void set(String value) {
        if (!value.equals(get())) {
            settingProvider.putString(key, value);
            cached = value;
            settingState.onNext(value);
        }
    }

    public void setSync(String value) {
        if (!value.equals(get())) {
            settingProvider.putStringSync(key, value);
            cached = value;
            settingState.onNext(value);
        }
    }

    public void setSyncNoCheck(String value) {
        settingProvider.putStringSync(key, value);
        cached = value;
        settingState.onNext(value);
    }

    public void remove() {
        settingProvider.removeSync(key);
        hasCached = false;
        cached = def;
        settingState.onNext(def);
    }

}
