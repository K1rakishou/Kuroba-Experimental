package com.github.k1rakishou.chan.core.mpv;

// Wrapper for native library

/**
 * Taken from https://github.com/mpv-android/mpv-android
 * <p>
 * DO NOT RENAME!
 * DO NOT MOVE!
 * NATIVE LIBRARIES DEPEND ON THE CLASS PACKAGE!
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.core_logger.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DoNotStrip
@SuppressWarnings("unused")
public class MPVLib {
    private static final String TAG = "MPVLib";

    private static boolean mpvCreated = false;

    // When updating the player code or anything related to it update jin/main.cpp:player_version
    // variable as well as the MPVLib.SUPPORTED_MPV_PLAYER_VERSION
    public static final int SUPPORTED_MPV_PLAYER_VERSION = 3;

    /**
     * Libraries are sorted by the order of dependency.
     * The libraries that come first are those that have no dependencies.
     * The libraries that come last are those that depend on other libraries and will throw
     * UnsatisfiedLinkError if are attempted to get loaded before all the dependencies are loaded.
     *
     * !!!!! DO NOT CHANGE THE ORDER !!!!!!!
     * */
    public static final List<String> LIBS = Arrays.asList(
            "libavutil.so",
            "libswresample.so",
            "libavcodec.so",
            "libavformat.so",
            "libavdevice.so",
            "libswscale.so",
            "libpostproc.so",
            "libavfilter.so",
            "libc++_shared.so",
            "libmpv.so",
            "libplayer.so"
    );
    /** !!!!! DO NOT CHANGE THE ORDER !!!!!!! */

    @Nullable
    private static Throwable lastError = null;
    private static boolean librariesLoaded = false;

    @SuppressWarnings("TryWithIdenticalCatches")
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    public static void tryLoadLibraries(File mpvNativeLibsDir) {
        if (lastError != null || librariesLoaded) {
            return;
        }

        try {
            for (String lib : LIBS) {
                File libFile = new File(mpvNativeLibsDir, lib);
                Logger.d(TAG, "loadLibraries() loading " + libFile.getPath());

                System.load(libFile.getPath());
            }

            if (playerVersion() != SUPPORTED_MPV_PLAYER_VERSION) {
                lastError = new MismatchedVersionException(playerVersion(), SUPPORTED_MPV_PLAYER_VERSION);
                return;
            }

            librariesLoaded = true;
        } catch (UnsatisfiedLinkError error) {
            lastError = error;
            Logger.e(TAG, "loadLibraries() error: " + KotlinExtensionsKt.errorMessageOrClassName(error));
        } catch (LinkageError error) {
            lastError = error;
            Logger.e(TAG, "loadLibraries() error: " + KotlinExtensionsKt.errorMessageOrClassName(error));
        } catch (Throwable error) {
            lastError = error;
            Logger.e(TAG, "loadLibraries() error: " + KotlinExtensionsKt.errorMessageOrClassName(error));
        }
    }

    public static boolean librariesAreLoaded() {
        return librariesLoaded && lastError == null;
    }

    @Nullable
    public static Throwable getLastError() {
        return lastError;
    }

    public static boolean isCreated() {
        return mpvCreated;
    }

    public static void mpvCreate(Context appctx) {
        if (mpvCreated) {
            return;
        }

        create(appctx);
        mpvCreated = true;
    }

    public static void mpvInit() {
        init();
    }

    public static void mpvDestroy() {
        if (!mpvCreated) {
            return;
        }

        destroy();
        mpvCreated = false;
    }

    @Nullable
    public static Integer mpvPlayerVersion() {
        return playerVersion();
    }

    public static void mpvAttachSurface(Surface surface) {
        if (!mpvCreated) {
            return;
        }

        attachSurface(surface);
    }

    public static void mpvDetachSurface() {
        if (!mpvCreated) {
            return;
        }

        detachSurface();
    }

    public static void mpvCommand(@NonNull String[] cmd) {
        if (!mpvCreated) {
            return;
        }

        command(cmd);
    }

    @Nullable
    public static Bitmap mpvGrabThumbnail(int dimension) {
        if (!mpvCreated) {
            return null;
        }

        return grabThumbnail(dimension);
    }

    public static void mpvSetOptionString(@NonNull String name, @NonNull String value) {
        if (!mpvCreated) {
            return;
        }

        setOptionString(name, value);
    }

    @Nullable
    public static Integer mpvGetPropertyInt(@NonNull String property) {
        if (!mpvCreated) {
            return null;
        }

        return getPropertyInt(property);
    }

    public static void mpvSetPropertyInt(@NonNull String property, @NonNull Integer value) {
        if (!mpvCreated) {
            return;
        }

        setPropertyInt(property, value);
    }

    @Nullable
    public static Double mpvGetPropertyDouble(@NonNull String property) {
        if (!mpvCreated) {
            return null;
        }

        return getPropertyDouble(property);
    }

    public static void mpvSetPropertyDouble(@NonNull String property, @NonNull Double value) {
        if (!mpvCreated) {
            return;
        }

        setPropertyDouble(property, value);
    }

    @Nullable
    public static Boolean mpvGetPropertyBoolean(@NonNull String property) {
        if (!mpvCreated) {
            return null;
        }

        return getPropertyBoolean(property);
    }

    public static void mpvSetPropertyBoolean(@NonNull String property, @NonNull Boolean value) {
        if (!mpvCreated) {
            return;
        }

        setPropertyBoolean(property, value);
    }

    @Nullable
    public static String mpvGetPropertyString(@NonNull String property) {
        if (!mpvCreated) {
            return null;
        }

        return getPropertyString(property);
    }

    public static void mpvSetPropertyString(@NonNull String property, @NonNull String value) {
        if (!mpvCreated) {
            return;
        }

        setPropertyString(property, value);
    }

    private static native void create(Context appctx);

    private static native void init();

    private static native void destroy();

    private static native Integer playerVersion();

    private static native void attachSurface(Surface surface);

    private static native void detachSurface();

    private static native void command(@NonNull String[] cmd);

    private static native Bitmap grabThumbnail(int dimension);

    private static native int setOptionString(@NonNull String name, @NonNull String value);

    private static native Integer getPropertyInt(@NonNull String property);

    private static native void setPropertyInt(@NonNull String property, @NonNull Integer value);

    private static native Double getPropertyDouble(@NonNull String property);

    private static native void setPropertyDouble(@NonNull String property, @NonNull Double value);

    private static native Boolean getPropertyBoolean(@NonNull String property);

    private static native void setPropertyBoolean(@NonNull String property, @NonNull Boolean value);

    private static native String getPropertyString(@NonNull String property);

    private static native void setPropertyString(@NonNull String property, @NonNull String value);

    public static native void observeProperty(@NonNull String property, int format);

    private static final List<EventObserver> observers = new ArrayList<>();

    public static void addObserver(EventObserver o) {
        synchronized (observers) {
            observers.add(o);
        }
    }

    public static void removeObserver(EventObserver o) {
        synchronized (observers) {
            observers.remove(o);
        }
    }

    public static void eventProperty(String property, long value) {
        synchronized (observers) {
            for (EventObserver o : observers)
                o.eventProperty(property, value);
        }
    }

    public static void eventProperty(String property, boolean value) {
        synchronized (observers) {
            for (EventObserver o : observers)
                o.eventProperty(property, value);
        }
    }

    public static void eventProperty(String property, String value) {
        synchronized (observers) {
            for (EventObserver o : observers)
                o.eventProperty(property, value);
        }
    }

    public static void eventProperty(String property) {
        synchronized (observers) {
            for (EventObserver o : observers)
                o.eventProperty(property);
        }
    }

    public static void event(int eventId) {
        synchronized (observers) {
            for (EventObserver o : observers)
                o.event(eventId);
        }
    }

    private static final List<LogObserver> log_observers = new ArrayList<>();

    public static void addLogObserver(LogObserver o) {
        synchronized (log_observers) {
            log_observers.add(o);
        }
    }

    public static void removeLogObserver(LogObserver o) {
        synchronized (log_observers) {
            log_observers.remove(o);
        }
    }

    public static void logMessage(String prefix, int level, String text) {
        synchronized (log_observers) {
            for (LogObserver o : log_observers)
                o.logMessage(prefix, level, text);
        }
    }

    public static boolean checkLibrariesInstalled(Context context, File mpvNativeLibrariesDir) {
        Map<String, Boolean> checkedLibsMap = getInstalledLibraries(context, mpvNativeLibrariesDir);
        boolean allLibsExist = true;

        for (Map.Entry<String, Boolean> stringBooleanEntry : checkedLibsMap.entrySet()) {
            Boolean exists = stringBooleanEntry.getValue();

            if (!exists) {
                allLibsExist = false;
                break;
            }
        }

        return allLibsExist;
    }

    @NonNull
    public static Map<String, Boolean> getInstalledLibraries(Context context, File mpvNativeLibrariesDir) {
        Map<String, Boolean> checkedLibsMap = new HashMap<String, Boolean>();

        for (String libToCheck : LIBS) {
            checkedLibsMap.put(libToCheck, false);
        }

        File[] libsDirFiles = mpvNativeLibrariesDir.listFiles();
        if (libsDirFiles == null || libsDirFiles.length <= 0) {
            return checkedLibsMap;
        }

        for (File libsDirFile : libsDirFiles) {
            if (checkedLibsMap.containsKey(libsDirFile.getName())) {
                if (libsDirFile.exists() && libsDirFile.canRead() && libsDirFile.length() > 0L) {
                    checkedLibsMap.put(libsDirFile.getName(), true);
                }
            }
        }

        return checkedLibsMap;
    }

    @DoNotStrip
    public static class MismatchedVersionException extends Exception {

        public MismatchedVersionException(int playerVersion, int supportedVersion) {
            super("Mismatched libplayer.so and currently supported versions! libplayer.so version: " +
                    playerVersion + ", supported version: " + supportedVersion +
                    ". You need to install the correct libplayer.so version which is: " + supportedVersion);
        }
    }

    @DoNotStrip
    public interface EventObserver {
        void eventProperty(@NonNull String property);

        void eventProperty(@NonNull String property, long value);

        void eventProperty(@NonNull String property, boolean value);

        void eventProperty(@NonNull String property, @NonNull String value);

        void event(int eventId);
    }

    @DoNotStrip
    public interface LogObserver {
        void logMessage(@NonNull String prefix, int level, @NonNull String text);
    }

    @DoNotStrip
    public static class mpvFormat {
        public static final int MPV_FORMAT_NONE = 0;
        public static final int MPV_FORMAT_STRING = 1;
        public static final int MPV_FORMAT_OSD_STRING = 2;
        public static final int MPV_FORMAT_FLAG = 3;
        public static final int MPV_FORMAT_INT64 = 4;
        public static final int MPV_FORMAT_DOUBLE = 5;
        public static final int MPV_FORMAT_NODE = 6;
        public static final int MPV_FORMAT_NODE_ARRAY = 7;
        public static final int MPV_FORMAT_NODE_MAP = 8;
        public static final int MPV_FORMAT_BYTE_ARRAY = 9;
    }

    @DoNotStrip
    public static class mpvEventId {
        public static final int MPV_EVENT_NONE = 0;
        public static final int MPV_EVENT_SHUTDOWN = 1;
        public static final int MPV_EVENT_LOG_MESSAGE = 2;
        public static final int MPV_EVENT_GET_PROPERTY_REPLY = 3;
        public static final int MPV_EVENT_SET_PROPERTY_REPLY = 4;
        public static final int MPV_EVENT_COMMAND_REPLY = 5;
        public static final int MPV_EVENT_START_FILE = 6;
        public static final int MPV_EVENT_END_FILE = 7;
        public static final int MPV_EVENT_FILE_LOADED = 8;
        public static final @Deprecated int MPV_EVENT_IDLE = 11;
        public static final @Deprecated int MPV_EVENT_TICK = 14;
        public static final int MPV_EVENT_CLIENT_MESSAGE = 16;
        public static final int MPV_EVENT_VIDEO_RECONFIG = 17;
        public static final int MPV_EVENT_AUDIO_RECONFIG = 18;
        public static final int MPV_EVENT_SEEK = 20;
        public static final int MPV_EVENT_PLAYBACK_RESTART = 21;
        public static final int MPV_EVENT_PROPERTY_CHANGE = 22;
        public static final int MPV_EVENT_QUEUE_OVERFLOW = 24;
        public static final int MPV_EVENT_HOOK = 25;
    }

    @DoNotStrip
    public static class mpvLogLevel {
        public static final int MPV_LOG_LEVEL_NONE = 0;
        public static final int MPV_LOG_LEVEL_FATAL = 10;
        public static final int MPV_LOG_LEVEL_ERROR = 20;
        public static final int MPV_LOG_LEVEL_WARN = 30;
        public static final int MPV_LOG_LEVEL_INFO = 40;
        public static final int MPV_LOG_LEVEL_V = 50;
        public static final int MPV_LOG_LEVEL_DEBUG = 60;
        public static final int MPV_LOG_LEVEL_TRACE = 70;
    }
}
