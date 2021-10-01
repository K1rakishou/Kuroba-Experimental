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

public class IntegerSetting extends Setting<Integer> {
    private BehaviorProcessor<Integer> settingState = BehaviorProcessor.create();

    private volatile boolean hasCached = false;
    private Integer cached;

    public IntegerSetting(SettingProvider settingProvider, String key, Integer def) {
        this(settingProvider, key, def, false);
    }

    public IntegerSetting(SettingProvider settingProvider, String key, Integer def, boolean skipInitial) {
        super(settingProvider, key, def);

        if (!skipInitial) {
            settingState.onNext(settingProvider.getInt(key, def));
        }
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

    public Flowable<Integer> listenForChanges() {
        return settingState
                .onBackpressureLatest()
                .hide()
                .observeOn(AndroidSchedulers.mainThread());
    }
}
