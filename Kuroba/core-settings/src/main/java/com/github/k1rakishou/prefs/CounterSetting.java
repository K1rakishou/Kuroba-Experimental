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

import com.github.k1rakishou.SettingProvider;

public class CounterSetting
        extends IntegerSetting {
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
