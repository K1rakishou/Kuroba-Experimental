package com.github.k1rakishou.prefs;

import com.github.k1rakishou.Setting;
import com.github.k1rakishou.SettingProvider;

public class LongSetting extends Setting<Long> {
    private volatile boolean hasCached = false;
    private Long cached;

    public LongSetting(SettingProvider settingProvider, String key, Long def) {
        super(settingProvider, key, def);
    }

    @Override
    public Long get() {
        if (!hasCached) {
            cached = settingProvider.getLong(key, def);
            hasCached = true;
        }

        return cached;
    }

    @Override
    public void set(Long value) {
        if (!value.equals(get())) {
            settingProvider.putLong(key, value);
            cached = value;
            settingState.onNext(value);
        }
    }

    public void setSync(Long value) {
        if (!value.equals(get())) {
            settingProvider.putLongSync(key, value);
            cached = value;
            settingState.onNext(value);
        }
    }

}
