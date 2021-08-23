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

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.ISODateTimeFormat;

public class Logger {
    private static String tagPrefix;
    private static boolean isCurrentBuildDev;
    public static final String DI_TAG = "Dependency Injection";

    private static final DateTimeFormatter LOG_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .append(ISODateTimeFormat.hourMinuteSecondMillis())
            .toFormatter();

    public static void init(String prefix, boolean isDevBuild) {
        tagPrefix = prefix;
        isCurrentBuildDev = isDevBuild;
    }

    private static String getTime() {
        if (isCurrentBuildDev) {
            return "";
        }

        return "(" + LOG_TIME_FORMATTER.print(DateTime.now()) + ") ";
    }

    //region VERBOSE
    public static void v(String tag, String message) {
        if (canLog()) {
            Log.v(getTime() + tagPrefix + tag, message);
        }
    }

    public static void v(String tag, String message, Throwable throwable) {
        if (canLog()) {
            Log.v(getTime() + tagPrefix + tag, message, throwable);
        }
    }
    //endregion VERBOSE

    //region DEBUG
    public static void d(String tag, String message) {
        if (canLog()) {
            Log.d(getTime() + tagPrefix + tag, message);
        }
    }

    public static void d(String tag, String message, Throwable throwable) {
        if (canLog()) {
            Log.d(getTime() + tagPrefix + tag, message, throwable);
        }
    }
    //endregion DEBUG

    //region INFO
    public static void i(String tag, String message) {
        Log.i(getTime() + tagPrefix + tag, message);
    }

    public static void i(String tag, String message, Throwable throwable) {
        Log.i(getTime() + tagPrefix + tag, message, throwable);
    }

    //endregion INFO

    //region WARN
    public static void w(String tag, String message) {
        Log.w(getTime() + tagPrefix + tag, message);
    }

    public static void w(String tag, String message, Throwable throwable) {
        Log.w(getTime() + tagPrefix + tag, message, throwable);
    }

    //endregion WARN

    //region ERROR
    public static void e(String tag, String message) {
        Log.e(getTime() + tagPrefix + tag, message);
    }

    public static void e(String tag, String message, Throwable throwable) {
        Log.e(getTime() + tagPrefix + tag, message, throwable);
    }
    //endregion ERROR

    //region WTF
    public static void wtf(String tag, String message) {
        Log.wtf(getTime() + tagPrefix + tag, message);
    }

    public static void wtf(String tag, String message, Throwable throwable) {
        Log.wtf(getTime() + tagPrefix + tag, message, throwable);
    }
    //endregion WTF

    //region TEST
    public static void test(String message) {
        if (canLog()) {
            Log.i(getTime() + tagPrefix + "test", message);
        }
    }

    public static void test(String message, Throwable throwable) {
        if (canLog()) {
            Log.i(getTime() + tagPrefix + "test", message, throwable);
        }
    }
    //endregion TEST

    public static void deps(String message) {
        if (!isCurrentBuildDev) {
            return;
        }

        String tag = DI_TAG + " (" + Thread.currentThread().getName() + ":" + Thread.currentThread().getId() + ")";
        Logger.d(tag, message);
    }

    private static boolean canLog() {
        return true;
    }
}
