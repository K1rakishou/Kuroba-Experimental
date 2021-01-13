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
package com.github.k1rakishou.core_logger;

import android.util.Log;

public class Logger {
    private static String tagPrefix;

    public static void init(String prefix) {
        tagPrefix = prefix;
    }

    //region VERBOSE
    public static void v(String tag, String message) {
        if (canLog()) {
            Log.v(tagPrefix + tag, message);
        }
    }

    public static void v(String tag, String message, Throwable throwable) {
        if (canLog()) {
            Log.v(tagPrefix + tag, message, throwable);
        }
    }
    //endregion VERBOSE

    //region DEBUG
    public static void d(String tag, String message) {
        if (canLog()) {
            Log.d(tagPrefix + tag, message);
        }
    }

    public static void d(String tag, String message, Throwable throwable) {
        if (canLog()) {
            Log.d(tagPrefix + tag, message, throwable);
        }
    }
    //endregion DEBUG

    //region INFO
    public static void i(String tag, String message) {
        Log.i(tagPrefix + tag, message);
    }

    public static void i(String tag, String message, Throwable throwable) {
        Log.i(tagPrefix + tag, message, throwable);
    }

    //endregion INFO

    //region WARN
    public static void w(String tag, String message) {
        Log.w(tagPrefix + tag, message);
    }

    public static void w(String tag, String message, Throwable throwable) {
        Log.w(tagPrefix + tag, message, throwable);
    }

    //endregion WARN

    //region ERROR
    public static void e(String tag, String message) {
        Log.e(tagPrefix + tag, message);
    }

    public static void e(String tag, String message, Throwable throwable) {
        Log.e(tagPrefix + tag, message, throwable);
    }
    //endregion ERROR

    //region WTF
    public static void wtf(String tag, String message) {
        Log.wtf(tagPrefix + tag, message);
    }

    public static void wtf(String tag, String message, Throwable throwable) {
        Log.wtf(tagPrefix + tag, message, throwable);
    }
    //endregion WTF

    //region TEST
    public static void test(String message) {
        if (canLog()) {
            Log.i(tagPrefix + "test", message);
        }
    }

    public static void test(String message, Throwable throwable) {
        if (canLog()) {
            Log.i(tagPrefix + "test", message, throwable);
        }
    }
    //endregion TEST

    private static boolean canLog() {
        return true;
    }
}
