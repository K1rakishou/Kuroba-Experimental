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
package com.github.k1rakishou.chan.utils;

import android.os.Handler;
import android.os.Looper;

import androidx.core.os.HandlerCompat;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.core_logger.Logger;

import static com.github.k1rakishou.common.AndroidUtils.getAppContext;

public class BackgroundUtils {
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static boolean isInForeground() {
        return ((Chan) getAppContext()).getApplicationInForeground();
    }

    /**
     * Causes the runnable to be added to the message queue. The runnable will
     * be run on the ui thread.
     */
    public static void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

    public static void runOnMainThread(Runnable runnable, long delay) {
        mainHandler.postDelayed(runnable, delay);
    }

    public static void runOnMainThreadWithToken(long delay, Object token, Runnable runnable) {
        HandlerCompat.postDelayed(mainHandler, runnable, token, delay);
    }

    public static void cancelAllByToken(Object token) {
        mainHandler.removeCallbacksAndMessages(token);
    }

    public static boolean isMainThread() {
        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }

    public static void ensureMainThread() {
        if (isMainThread()) {
            return;
        }

        if (ChanSettings.crashOnSafeThrow.get()) {
            throw new IllegalStateException("Cannot be executed on a background thread!");
        } else {
            Logger.e("BackgroundUtils", "ensureMainThread() expected main thread but " +
                    "got " + Thread.currentThread().getName());
        }
    }

    public static void ensureBackgroundThread() {
        if (!isMainThread()) {
            return;
        }

        if (ChanSettings.crashOnSafeThrow.get()) {
            throw new IllegalStateException("Cannot be executed on the main thread!");
        } else {
            Logger.e("BackgroundUtils", "ensureBackgroundThread() expected background thread " +
                    "but got main");
        }
    }
}
