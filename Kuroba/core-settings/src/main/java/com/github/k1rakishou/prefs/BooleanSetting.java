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
package com.github.k1rakishou.prefs;

import com.github.k1rakishou.Setting;
import com.github.k1rakishou.SettingProvider;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.processors.BehaviorProcessor;

public class BooleanSetting extends Setting<Boolean> {
    private volatile boolean hasCached = false;
    private BehaviorProcessor<Boolean> settingState = BehaviorProcessor.create();

    public BooleanSetting(SettingProvider settingProvider, String key, Boolean def) {
        super(settingProvider, key, def);

        settingState.onNext(settingProvider.getBoolean(key, def));
    }

    @Override
    public synchronized Boolean get() {
        if (hasCached) {
            return settingState.getValue();
        } else {
            boolean value = settingProvider.getBoolean(key, def);
            settingState.onNext(value);
            hasCached = true;
            return value;
        }
    }

    @Override
    public synchronized void set(Boolean value) {
        if (!value.equals(get())) {
            settingProvider.putBoolean(key, value);
            settingState.onNext(value);
        }
    }

    public synchronized void setSync(Boolean value) {
        if (!value.equals(get())) {
            settingProvider.putBooleanSync(key, value);
            settingState.onNext(value);
        }
    }

    public synchronized boolean toggle() {
        boolean newValue = !get();

        set(newValue);
        return newValue;
    }

    public Flowable<Boolean> listenForChanges() {
        return settingState
                .onBackpressureLatest()
                .hide()
                .observeOn(AndroidSchedulers.mainThread());
    }
}
