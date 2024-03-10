package com.github.k1rakishou.prefs;

import com.github.k1rakishou.SettingProvider;

public class CounterSetting extends IntegerSetting {
    public CounterSetting(SettingProvider settingProvider, String key) {
        super(settingProvider, key, 0);
    }

    public synchronized int increase() {
        set(get() + 1);
        return get();
    }

    public void reset() {
        set(getDefault());
    }
}
