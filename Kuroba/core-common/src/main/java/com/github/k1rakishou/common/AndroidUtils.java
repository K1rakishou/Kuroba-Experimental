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
package com.github.k1rakishou.common;

import static android.content.Context.AUDIO_SERVICE;
import static android.content.Context.CLIPBOARD_SERVICE;
import static android.content.Context.INPUT_METHOD_SERVICE;
import static android.content.Context.JOB_SCHEDULER_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.job.JobScheduler;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.media.AudioManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AndroidUtils {
    private static final String TAG = "AndroidUtils";
    public static final String CHAN_STATE_PREFS_NAME = "chan_state";
    public static final String MPV_PREFS_NAME = "mpv_prefs";

    @SuppressLint("StaticFieldLeak")
    private static Application application;

    public static void init(Application application) {
        if (AndroidUtils.application == null) {
            AndroidUtils.application = application;
        }
    }

    public static File getAppDir() {
        return application.getFilesDir().getParentFile();
    }

    public static File getFilesDir() {
        return application.getFilesDir();
    }

    public static Context getAppContext() {
        return application;
    }

    public static CharSequence getApplicationLabel() {
        return application.getPackageManager().getApplicationLabel(application.getApplicationInfo());
    }

    public static String getAppFileProvider() {
        return application.getPackageName() + ".fileprovider";
    }

    public static boolean isNotMainProcess() {
        return getProcessType() != AppProcessType.Main;
    }

    private static AppProcessType getProcessType() {
        String currentProcName = "";
        int pid = android.os.Process.myPid();
        ActivityManager manager = (ActivityManager) getAppContext().getSystemService(Context.ACTIVITY_SERVICE);

        List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes == null) {
            return AppProcessType.Main;
        }

        for (ActivityManager.RunningAppProcessInfo processInfo : processes) {
            if (processInfo.pid == pid) {
                currentProcName = processInfo.processName;
                break;
            }
        }

        if (currentProcName.contains(":crashReportProcess")) {
            return AppProcessType.CrashReporting;
        }

        return AppProcessType.Main;
    }

    public static SharedPreferences getAppMainPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(application);
    }

    public static SharedPreferences getAppState() {
        return getAppContext().getSharedPreferences(CHAN_STATE_PREFS_NAME, MODE_PRIVATE);
    }

    public static SharedPreferences getMpvState() {
        return getAppContext().getSharedPreferences(MPV_PREFS_NAME, MODE_PRIVATE);
    }

    public static void requestKeyboardFocus(Dialog dialog, final View view) {
        view.requestFocus();
        dialog.setOnShowListener(dialog1 -> requestKeyboardFocus(view));
    }

    public static void requestKeyboardFocus(final View view) {
        getInputManager().showSoftInput(view, SHOW_IMPLICIT);
    }

    public static void hideKeyboard(View view) {
        if (view != null) {
            getInputManager().hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void requestViewAndKeyboardFocus(View view) {
        view.setFocusable(false);
        view.setFocusableInTouchMode(true);
        if (view.requestFocus()) {
            getInputManager().showSoftInput(view, SHOW_IMPLICIT);
        }
    }

    public static void updatePaddings(View view, int left, int right, int top, int bottom) {
        int newLeft = left;
        if (newLeft < 0) {
            newLeft = view.getPaddingLeft();
        }

        int newRight = right;
        if (newRight < 0) {
            newRight = view.getPaddingRight();
        }

        int newTop = top;
        if (newTop < 0) {
            newTop = view.getPaddingTop();
        }

        int newBottom = bottom;
        if (newBottom < 0) {
            newBottom = view.getPaddingBottom();
        }

        view.setPadding(newLeft, newTop, newRight, newBottom);
    }

    public static void setBoundlessRoundRippleBackground(View view) {
        TypedValue outValue = new TypedValue();
        view.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                outValue,
                true
        );

        view.setBackgroundResource(outValue.resourceId);
    }

    public static void setRippleBackground(View view) {
        TypedValue outValue = new TypedValue();
        view.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground,
                outValue,
                true
        );

        view.setBackgroundResource(outValue.resourceId);
    }

    public static List<View> findViewsById(ViewGroup root, int id) {
        List<View> views = new ArrayList<>();
        int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = root.getChildAt(i);
            if (child instanceof ViewGroup) {
                views.addAll(findViewsById((ViewGroup) child, id));
            }

            if (child.getId() == id) {
                views.add(child);
            }
        }

        return views;
    }

    public static boolean removeFromParentView(View view) {
        if (view.getParent() instanceof ViewGroup && ((ViewGroup) view.getParent()).indexOfChild(view) >= 0) {
            ((ViewGroup) view.getParent()).removeView(view);
            return true;
        } else {
            return false;
        }
    }

    public static Point getDisplaySize(Context context) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = ((Activity) context).getWindowManager();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);

        return new Point(displayMetrics.widthPixels, displayMetrics.heightPixels);
    }

    public static Point getRealDisplaySize(Context context) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = ((Activity) context).getWindowManager();
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);

        return new Point(displayMetrics.widthPixels, displayMetrics.heightPixels);
    }

    /**
     * These two methods get the screen size ignoring the current screen orientation.
     */
    public static int getScreenWidth(Context context) {
        Point displaySize = getDisplaySize(context);
        return displaySize.x;
    }

    public static int getRealMinScreenSize(Context context) {
        Point displaySize = getRealDisplaySize(context);
        return Math.min(displaySize.x, displaySize.y);
    }

    public static int getRealMaxScreenSize(Context context) {
        Point displaySize = getRealDisplaySize(context);
        return Math.max(displaySize.x, displaySize.y);
    }

    public static Window getWindow(Context context) {
        if (context instanceof Activity) {
            return ((Activity) context).getWindow();
        } else {
            return null;
        }
    }

    private static InputMethodManager getInputManager() {
        return (InputMethodManager) application.getSystemService(INPUT_METHOD_SERVICE);
    }

    public static ClipboardManager getClipboardManager() {
        return (ClipboardManager) application.getSystemService(CLIPBOARD_SERVICE);
    }

    public static ActivityManager getActivityManager() {
        return (ActivityManager) application.getSystemService(Context.ACTIVITY_SERVICE);
    }

    public static String getClipboardContent() {
        ClipData primary = getClipboardManager().getPrimaryClip();
        if (primary != null && primary.getItemCount() > 0) {
            CharSequence text = primary.getItemAt(0).getText();
            if (!TextUtils.isEmpty(text)) {
                return primary.getItemAt(0).getText().toString();
            }
        }

        return "";
    }

    public static void setClipboardContent(String label, String content) {
        getClipboardManager().setPrimaryClip(ClipData.newPlainText(label, content));
    }

    public static NotificationManager getNotificationManager() {
        return (NotificationManager) application.getSystemService(NOTIFICATION_SERVICE);
    }

    public static NotificationManagerCompat getNotificationManagerCompat() {
        return NotificationManagerCompat.from(application);
    }

    public static JobScheduler getJobScheduler() {
        return (JobScheduler) application.getSystemService(JOB_SCHEDULER_SERVICE);
    }

    public static AudioManager getAudioManager() {
        return (AudioManager) getAppContext().getSystemService(AUDIO_SERVICE);
    }

    public static int getApiLevel() {
        return Build.VERSION.SDK_INT;
    }

    public static boolean isAndroid11() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    public static boolean isAndroid10() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    public static boolean isAndroidO() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public static boolean isAndroidL_MR1() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1;
    }

    public static boolean isAndroidP() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
    }

    public static boolean isAndroidM() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static boolean isAndroidN() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    public static boolean isAndroidNMR1() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1;
    }

    public enum FlavorType {
        Stable,
        Beta,
        Dev,
        Fdroid
    }

    public enum VerifiedBuildType {
        Debug,
        Release,
        Unknown
    }

    public enum AppProcessType {
        Main,
        CrashReporting
    }
}
