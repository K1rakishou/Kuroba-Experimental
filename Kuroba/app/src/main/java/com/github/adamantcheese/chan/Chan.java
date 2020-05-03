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
package com.github.adamantcheese.chan;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.cache.downloader.FileCacheException;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.di.AppModule;
import com.github.adamantcheese.chan.core.di.DatabaseModule;
import com.github.adamantcheese.chan.core.di.ExecutorsModule;
import com.github.adamantcheese.chan.core.di.GsonModule;
import com.github.adamantcheese.chan.core.di.LoaderModule;
import com.github.adamantcheese.chan.core.di.ManagerModule;
import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.di.RepositoryModule;
import com.github.adamantcheese.chan.core.di.RoomDatabaseModule;
import com.github.adamantcheese.chan.core.di.SiteModule;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.core.manager.ReportManager;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager;
import com.github.adamantcheese.chan.core.net.DnsSelector;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.SiteService;
import com.github.adamantcheese.chan.ui.service.LastPageNotification;
import com.github.adamantcheese.chan.ui.service.SavingNotification;
import com.github.adamantcheese.chan.ui.service.WatchNotification;
import com.github.adamantcheese.chan.ui.settings.SettingNotificationType;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.common.AppConstants;
import com.github.adamantcheese.model.DatabaseModuleInjector;
import com.github.adamantcheese.model.di.ModelMainComponent;
import com.github.k1rakishou.feather2.Feather;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import okhttp3.Dns;
import okhttp3.Protocol;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getBuildType;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;
import static java.lang.Thread.currentThread;

public class Chan
        extends Application
        implements Application.ActivityLifecycleCallbacks, CoroutineScope {
    private static final String TAG = "Chan";
    private int activityForegroundCounter = 0;

    @Inject
    DatabaseManager databaseManager;
    @Inject
    SiteService siteService;
    @Inject
    BoardManager boardManager;
    @Inject
    ReportManager reportManager;
    @Inject
    SettingsNotificationManager settingsNotificationManager;

    @NotNull
    @Override
    public CoroutineContext getCoroutineContext() {
        return Dispatchers.getMain();
    }

    private static Feather feather;

    /**
     * Only ever use this method in cases when there is no other way around it (like when normal
     * injection will create a circular dependency)
     * TODO(dependency-cycles): get rid of this method once all dependency cycles are resolved
     * */
    public static <T> T instance(Class<T> tClass) {
        return feather.instance(tClass);
    }

    public static <T> T inject(T instance) {
        feather.injectFields(instance);
        return instance;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        AndroidUtils.init(this);

        // spit out the build hash to the log
        AndroidUtils.getBuildType();

        // remove this if you need to debug some sort of event bus issue
        EventBus.builder().logNoSubscriberMessages(false).installDefaultEventBus();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        long start = System.currentTimeMillis();
        onCreateInternal();

        long diff = System.currentTimeMillis() - start;
        Logger.d(TAG, "Application initialization took " + diff + "ms");
    }

    private void onCreateInternal() {
        registerActivityLifecycleCallbacks(this);
        System.setProperty("kotlinx.coroutines.debug", BuildConfig.DEBUG ? "on" : "off");

        AppConstants appConstants = new AppConstants(getApplicationContext());
        logAppConstants(appConstants);

        WatchNotification.setupChannel();
        SavingNotification.setupChannel();
        LastPageNotification.setupChannel();

        Dns okHttpDns = getOkHttpDns();
        OkHttpProtocols okHttpProtocols = getOkHttpProtocols();

        ModelMainComponent modelMainComponent = DatabaseModuleInjector.build(
                this,
                okHttpDns,
                okHttpProtocols.protocols,
                Logger.TAG_PREFIX,
                ChanSettings.verboseLogs.get(),
                appConstants,
                this
        );

        feather = Feather.with(
                new AppModule(this, this, okHttpDns, okHttpProtocols, appConstants),
                new ExecutorsModule(),
                new DatabaseModule(),
                // TODO: change to a normal dagger implementation when we get rid of Feather
                new RoomDatabaseModule(modelMainComponent),
                new NetModule(),
                new GsonModule(),
                new RepositoryModule(),
                new SiteModule(),
                new LoaderModule(),
                new ManagerModule()
        );
        feather.injectFields(this);

        siteService.initialize();
        boardManager.initialize();
        databaseManager.initializeAndTrim();

        //create these classes here even if they aren't explicitly used, so they do their background startup tasks
        //and so that they're available for feather later on for archives/filter watch waking
        feather.instance(FilterWatchManager.class);

        RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof UndeliverableException) {
                e = e.getCause();
            }

            if (e == null) {
                return;
            }

            if (e instanceof IOException) {
                // fine, irrelevant network problem or API that throws on cancellation
                return;
            }
            if (e instanceof InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return;
            }
            if (e instanceof RuntimeException && e.getCause() instanceof InterruptedException) {
                // fine, DB synchronous call (via runTask) was interrupted when a reactive stream
                // was disposed of.
                return;
            }
            if (e instanceof FileCacheException.CancellationException
                    || e instanceof FileCacheException.FileNotFoundOnTheServerException) {
                // fine, sometimes they get through all the checks but it doesn't really matter
                return;
            }
            if ((e instanceof NullPointerException) || (e instanceof IllegalArgumentException)) {
                // that's likely a bug in the application
                currentThread().getUncaughtExceptionHandler().uncaughtException(currentThread(), e);
                return;
            }
            if (e instanceof IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                currentThread().getUncaughtExceptionHandler().uncaughtException(currentThread(), e);
                return;
            }

            onUnhandledException(e, exceptionToString(true, e));
            Logger.e("APP", "RxJava undeliverable exception", e);

            // Do not exit the app here! Most of the time an exception that comes here is not a
            // fatal one. We only want to log and report them to analyze later. The app should be
            // able to continue running after that.
        });

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            //if there's any uncaught crash stuff, just dump them to the log and exit immediately
            String errorText = exceptionToString(false, e);

            Logger.e("UNCAUGHT", errorText);
            Logger.e("UNCAUGHT", "------------------------------");
            Logger.e("UNCAUGHT", "END OF CURRENT RUNTIME MESSAGES");
            Logger.e("UNCAUGHT", "------------------------------");
            Logger.e("UNCAUGHT", "Android API Level: " + Build.VERSION.SDK_INT);
            Logger.e("UNCAUGHT", "App Version: " + BuildConfig.VERSION_NAME);
            Logger.e("UNCAUGHT", "Development Build: " + getBuildType().name());
            Logger.e("UNCAUGHT", "Phone Model: " + Build.MANUFACTURER + " " + Build.MODEL);

            onUnhandledException(e, errorText);
            System.exit(999);
        });

        if (ChanSettings.collectCrashLogs.get()) {
            if (reportManager.hasCrashLogs()) {
                settingsNotificationManager.notify(SettingNotificationType.CrashLog);
            }
        }
    }

    private void logAppConstants(AppConstants appConstants) {
        Logger.d("APP", "maxPostsCountInPostsCache = " + appConstants.getMaxPostsCountInPostsCache());
    }

    private Dns getOkHttpDns() {
        if (ChanSettings.okHttpAllowIpv6.get()) {
            Logger.d(AppModule.DI_TAG, "Using DnsSelector.Mode.SYSTEM");
            return new DnsSelector(DnsSelector.Mode.SYSTEM);
        }

        Logger.d(AppModule.DI_TAG, "Using DnsSelector.Mode.IPV4_ONLY");
        return new DnsSelector(DnsSelector.Mode.IPV4_ONLY);
    }

    @NonNull
    private OkHttpProtocols getOkHttpProtocols() {
        if (ChanSettings.okHttpAllowHttp2.get()) {
            Logger.d(AppModule.DI_TAG, "Using HTTP_2 and HTTP_1_1");
            return new OkHttpProtocols(
                    Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1)
            );
        }

        Logger.d(AppModule.DI_TAG, "Using HTTP_1_1");
        return new OkHttpProtocols(
                Collections.singletonList(Protocol.HTTP_1_1)
        );
    }

    private boolean isEmulator() {
        return Build.MODEL.contains("google_sdk") || Build.MODEL.contains("Emulator") || Build.MODEL.contains(
                "Android SDK");
    }

    private String exceptionToString(boolean isCalledFromRxJavaHandler, Throwable e) {
        try (StringWriter sw = new StringWriter()) {
            try (PrintWriter pw = new PrintWriter(sw)) {
                e.printStackTrace(pw);
                String stackTrace = sw.toString();

                if (isCalledFromRxJavaHandler) {
                    return "Called from RxJava onError handler.\n" + stackTrace;
                }

                return "Called from unhandled exception handler.\n" + stackTrace;
            }
        } catch (IOException ex) {
            throw new RuntimeException("Error while trying to convert exception to string!", ex);
        }
    }

    private void onUnhandledException(Throwable exception, String error) {
        //don't upload debug crashes

        if ("Debug crash".equals(exception.getMessage())) {
            return;
        }

        if (isEmulator()) {
            return;
        }

        if (ChanSettings.collectCrashLogs.get()) {
            reportManager.storeCrashLog(exception.getMessage(), error);
        }
    }

    private void activityEnteredForeground() {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter++;

        if (getApplicationInForeground() != lastForeground) {
            postToEventBus(new ForegroundChangedMessage(getApplicationInForeground()));
        }
    }

    private void activityEnteredBackground() {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter--;
        if (activityForegroundCounter < 0) {
            Logger.wtf("ChanApplication", "activityForegroundCounter below 0");
        }

        if (getApplicationInForeground() != lastForeground) {
            postToEventBus(new ForegroundChangedMessage(getApplicationInForeground()));
        }
    }

    public boolean getApplicationInForeground() {
        return activityForegroundCounter > 0;
    }

    public static class ForegroundChangedMessage {
        public boolean inForeground;

        public ForegroundChangedMessage(boolean inForeground) {
            this.inForeground = inForeground;
        }
    }

    //region Empty Methods
    @SuppressWarnings("EmptyMethod")
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
        activityEnteredForeground();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onActivityResumed(Activity activity) {
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
        activityEnteredBackground();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onActivityDestroyed(Activity activity) {
    }
    //endregion Empty Methods

    public static class OkHttpProtocols {
        public final List<Protocol> protocols;

        public OkHttpProtocols(List<Protocol> okHttpProtocols) {
            this.protocols = okHttpProtocols;
        }
    }
}
