package com.github.k1rakishou.deprecated.prefs;

import com.github.k1rakishou.deprecated.SettingProvider;

@Deprecated
public class CounterSettingDeprecated extends IntegerSetting {
    public CounterSettingDeprecated(SettingProvider settingProvider, String key) {
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
