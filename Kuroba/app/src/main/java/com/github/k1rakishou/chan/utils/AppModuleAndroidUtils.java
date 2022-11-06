package com.github.k1rakishou.chan.utils;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static com.github.k1rakishou.common.AndroidUtils.VerifiedBuildType.Debug;
import static com.github.k1rakishou.common.AndroidUtils.VerifiedBuildType.Release;
import static com.github.k1rakishou.common.AndroidUtils.VerifiedBuildType.Unknown;
import static com.github.k1rakishou.common.AndroidUtils.getAppContext;
import static com.github.k1rakishou.common.AndroidUtils.isAndroidP;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.StatFs;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.BuildConfig;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.activity.SharingActivity;
import com.github.k1rakishou.chan.activity.StartActivity;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerActivity;
import com.github.k1rakishou.chan.ui.widget.CancellableToast;
import com.github.k1rakishou.common.AndroidUtils;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor;
import com.github.k1rakishou.persist_state.PersistableChanState;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AppModuleAndroidUtils {
    private static final String TAG = "AppModuleAndroidUtils";
    private static final CancellableToast cancellableToast = new CancellableToast();

    @SuppressLint("StaticFieldLeak")
    private static Application application;

    public static final String SITE_PREFS_FILE_PREFIX = "site_preferences_";

    public static void init(Application application) {
        if (AppModuleAndroidUtils.application == null) {
            AppModuleAndroidUtils.application = application;
        }
    }

    public static boolean checkDontKeepActivitiesSettingEnabledForWarningDialog(Context context) {
        if (PersistableChanState.dontKeepActivitiesWarningShown.get()) {
          return false;
        }

        boolean settingEnabled = Settings.Global.getInt(
                context.getContentResolver(),
                Settings.Global.ALWAYS_FINISH_ACTIVITIES,
                0
        ) == 1;

        if (settingEnabled) {
            PersistableChanState.dontKeepActivitiesWarningShown.set(true);
        }

        return settingEnabled;
    }

    public static AndroidUtils.VerifiedBuildType getVerifiedBuildType() {
        try {
            @SuppressLint("PackageManagerGetSignatures")
            Signature sig = getApplicationSignature();

            String signatureHexString =
                    HashingUtil.byteArrayHashSha256HexString(sig.toByteArray()).toUpperCase();

            boolean isOfficialRelease = BuildConfig.RELEASE_SIGNATURE.equals(signatureHexString);
            if (isOfficialRelease) {
                return Release;
            }

            boolean isOfficialBeta = BuildConfig.DEBUG_SIGNATURE.equals(signatureHexString);
            if (isOfficialBeta) {
                return Debug;
            }

            return Unknown;
        } catch (Throwable error) {
            Logger.e(TAG, "getVerifiedBuildType() error: " + KotlinExtensionsKt.errorMessageOrClassName(error));
            return Unknown;
        }
    }

    private static Signature getApplicationSignature() throws PackageManager.NameNotFoundException {
        @SuppressLint("PackageManagerGetSignatures")
        Signature sig;

        if (isAndroidP()) {
            sig = application.getPackageManager().getPackageInfo(
                    application.getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES
            ).signingInfo.getApkContentsSigners()[0];
        } else {
            sig = application.getPackageManager().getPackageInfo(
                    application.getPackageName(),
                    PackageManager.GET_SIGNATURES
            ).signatures[0];
        }
        return sig;
    }

    public static void printApplicationSignatureHash() throws PackageManager.NameNotFoundException {
        @SuppressLint("PackageManagerGetSignatures")
        Signature sig = getApplicationSignature();

        String signatureHexString =
                HashingUtil.byteArrayHashSha256HexString(sig.toByteArray()).toUpperCase();

        Logger.d(TAG, "Signature hash: " + signatureHexString);
    }

    public static boolean isStableBuild() {
        return getFlavorType() == AndroidUtils.FlavorType.Stable;
    }

    public static boolean isDevBuild() {
        return getFlavorType() == AndroidUtils.FlavorType.Dev;
    }

    public static boolean isBetaBuild() {
        return getFlavorType() == AndroidUtils.FlavorType.Beta;
    }

    public static boolean isFdroidBuild() {
        return getFlavorType() == AndroidUtils.FlavorType.Fdroid;
    }

    @SuppressWarnings("ConstantConditions")
    public static AndroidUtils.FlavorType getFlavorType() {
        switch (BuildConfig.FLAVOR_TYPE) {
            case 0:
                return AndroidUtils.FlavorType.Stable;
            case 1:
                return AndroidUtils.FlavorType.Beta;
            case 2:
                return AndroidUtils.FlavorType.Dev;
            case 3:
                return AndroidUtils.FlavorType.Fdroid;
            default:
                throw new RuntimeException("Unknown flavor type " + BuildConfig.FLAVOR_TYPE);
        }
    }

    /**
     * Tries to open an app that can open the specified URL.<br>
     * If this app will open the link then show a chooser to the user without this app.<br>
     * Else allow the default logic to run with startActivity.
     *
     * @param link url to open
     */
    public static void openLink(String link) {
        if (TextUtils.isEmpty(link)) {
            Logger.d(TAG, "openLink() link is empty");
            showToast(application, getString(R.string.open_link_failed_url, link), Toast.LENGTH_LONG);
            return;
        }

        PackageManager pm = application.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));

        ComponentName resolvedActivity = intent.resolveActivity(pm);
        if (resolvedActivity == null) {
            Logger.d(TAG, "openLink() resolvedActivity == null");

            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                KtExtensionsKt.startActivitySafe(application, intent);
            } catch (Throwable e) {
                Logger.e(TAG, "openLink() application.startActivity() error, intent = " + intent, e);

                String message = getString(R.string.open_link_failed_url_additional_info, link, e.getMessage());
                Toast.makeText(application, message, Toast.LENGTH_SHORT).show();
            }

            return;
        }

        boolean thisAppIsDefault = resolvedActivity.getPackageName()
                .equals(application.getPackageName());

        if (!thisAppIsDefault) {
            Logger.d(TAG, "openLink() thisAppIsDefault == false");
            openIntent(intent);
            return;
        }

        // Get all intents that match, and filter out this app
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        List<Intent> filteredIntents = new ArrayList<>(resolveInfos.size());

        for (ResolveInfo info : resolveInfos) {
            if (!info.activityInfo.packageName.equals(application.getPackageName())) {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                i.setPackage(info.activityInfo.packageName);
                filteredIntents.add(i);
            }
        }

        if (filteredIntents.size() <= 0) {
            Logger.d(TAG, "openLink() filteredIntents.size() <= 0");
            String message = getString(
                    R.string.open_link_failed_url_additional_info,
                    link,
                    "filteredIntents count <= 0"
            );

            showToast(application, message, Toast.LENGTH_LONG);
            return;
        }

        if (filteredIntents.size() == 1) {
            Logger.d(TAG, "openLink() filteredIntents.size() == 1");
            openIntent(filteredIntents.get(0));

            return;
        }

        // Create a chooser for the last app in the list, and add the rest with
        // EXTRA_INITIAL_INTENTS that get placed above
        Intent chooser = Intent.createChooser(
                filteredIntents.remove(filteredIntents.size() - 1),
                null
        );

        chooser.putExtra(
                Intent.EXTRA_INITIAL_INTENTS,
                filteredIntents.toArray(new Intent[0])
        );

        Logger.d(TAG, "openLink() success");
        openIntent(chooser);
    }

    public static void shareLink(String link) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, link);
        Intent chooser = Intent.createChooser(intent, getString(R.string.action_share));
        openIntent(chooser);
    }

    public static void openIntent(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            KtExtensionsKt.startActivitySafe(application, intent);
        } catch (Throwable e) {
            Logger.e(TAG, "openIntent() application.startActivity() error, intent = " + intent, e);

            String message = getString(R.string.open_intent_failed, intent.toString());
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show();
            return;
        }

        Logger.d(TAG, "openIntent() success");
    }

    public static int getAttrColor(Context context, int attr) {
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(new int[]{attr});
        int color = typedArray.getColor(0, 0);
        typedArray.recycle();
        return color;
    }

    public static Drawable getAttrDrawable(Context context, int attr) {
        TypedArray typedArray = context.obtainStyledAttributes(new int[]{attr});
        Drawable drawable = typedArray.getDrawable(0);
        typedArray.recycle();
        return drawable;
    }

    public static View inflate(Context context, int resId, ViewGroup root) {
        return LayoutInflater.from(context).inflate(resId, root);
    }

    public static View inflate(Context context, int resId, ViewGroup root, boolean attachToRoot) {
        return LayoutInflater.from(context).inflate(resId, root, attachToRoot);
    }

    public static ViewGroup inflate(Context context, int resId) {
        return (ViewGroup) LayoutInflater.from(context).inflate(resId, null);
    }

    public static Resources getRes() {
        return application.getResources();
    }

    public static int dp(float dp) {
        return (int) (dp * getRes().getDisplayMetrics().density);
    }

    public static int pxToDp(float px) {
        return (int) (px / getRes().getDisplayMetrics().density);
    }

    public static int pxToDp(int px) {
        return (int) (px / getRes().getDisplayMetrics().density);
    }

    public static int dp(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    public static int sp(int sp) {
        return sp((float) sp);
    }

    public static int sp(float sp) {
        return (int) (sp * getRes().getDisplayMetrics().scaledDensity);
    }

    public static String getString(int res) {
        try {
            return getRes().getString(res);
        } catch (Exception e) {
            return null;
        }
    }

    public static String getString(int res, Object... formatArgs) {
        return getRes().getString(res, formatArgs);
    }

    public static String getQuantityString(int res, int quantity) {
        return getRes().getQuantityString(res, quantity);
    }

    public static String getQuantityString(int res, int quantity, Object... formatArgs) {
        return getRes().getQuantityString(res, quantity, formatArgs);
    }

    public static Drawable getDrawable(@DrawableRes int res) {
        return ContextCompat.getDrawable(getAppContext(), res);
    }

    public static boolean isTablet() {
        return getRes().getBoolean(R.bool.is_tablet);
    }

    public static int getDimen(int dimen) {
        return getRes().getDimensionPixelSize(dimen);
    }

    public static boolean shouldLoadForNetworkType(ChanSettings.NetworkContentAutoLoadMode networkType) {
        if (networkType == ChanSettings.NetworkContentAutoLoadMode.NONE) {
            return false;
        } else if (networkType == ChanSettings.NetworkContentAutoLoadMode.WIFI) {
            return isConnected(ConnectivityManager.TYPE_WIFI);
        } else {
            return networkType == ChanSettings.NetworkContentAutoLoadMode.ALL;
        }
    }

    public static boolean isConnected(int type) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(type);
        return networkInfo != null && networkInfo.isConnected();
    }

    public static int getScreenOrientation() {
        int screenOrientation = getAppContext().getResources().getConfiguration().orientation;
        if (screenOrientation != ORIENTATION_LANDSCAPE && screenOrientation != ORIENTATION_PORTRAIT) {
            throw new IllegalStateException("Illegal screen orientation value! value = " + screenOrientation);
        }

        return screenOrientation;
    }

    /**
     * Change to ConnectivityManager#registerDefaultNetworkCallback when minSdk == 24, basically never
     */
    public static String getNetworkClass(@NonNull ConnectivityManager connectivityManager) {
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) {
            return "No connected"; // not connected
        }

        if (info.getType() == ConnectivityManager.TYPE_WIFI) {
            return "WIFI";
        }

        if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
            int networkType = info.getSubtype();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:     // api< 8: replace by 11
                case TelephonyManager.NETWORK_TYPE_GSM:      // api<25: replace by 16
                    return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:   // api< 9: replace by 12
                case TelephonyManager.NETWORK_TYPE_EHRPD:    // api<11: replace by 14
                case TelephonyManager.NETWORK_TYPE_HSPAP:    // api<13: replace by 15
                case TelephonyManager.NETWORK_TYPE_TD_SCDMA: // api<25: replace by 17
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE:      // api<11: replace by 13
                case TelephonyManager.NETWORK_TYPE_IWLAN:    // api<25: replace by 18
                case 19: // LTE_CA
                    return "4G";
                case TelephonyManager.NETWORK_TYPE_NR:       // api<29: replace by 20
                    return "5G";
            }
        }

        return "Unknown";
    }

    public static long getAvailableSpaceInBytes(File file) {
        StatFs stat = new StatFs(file.getPath());

        return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
    }

    /**
     * Waits for a measure. Calls callback immediately if the view width and height are more than 0.
     * Otherwise it registers an onpredrawlistener.
     * <b>Warning: the view you give must be attached to the view root!</b>
     */
    public static void waitForMeasure(final View view, final OnMeasuredCallback callback) {
        if (view.getWindowToken() == null) {
            // If you call getViewTreeObserver on a view when it's not attached to a window will
            // result in the creation of a temporarily viewtreeobserver.
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    waitForLayoutInternal(true, view.getViewTreeObserver(), view, callback);
                    view.removeOnAttachStateChangeListener(this);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    view.removeOnAttachStateChangeListener(this);
                }
            });
            return;
        }

        waitForLayoutInternal(true, view.getViewTreeObserver(), view, callback);
    }

    /**
     * Always registers an onpredrawlistener.
     * <b>Warning: the view you give must be attached to the view root!</b>
     */
    public static void waitForLayout(final View view, final OnMeasuredCallback callback) {
        if (view.getWindowToken() == null) {
            // See comment above
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    waitForLayoutInternal(true, view.getViewTreeObserver(), view, callback);
                    view.removeOnAttachStateChangeListener(this);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    view.removeOnAttachStateChangeListener(this);
                }
            });
            return;
        }

        waitForLayoutInternal(false, view.getViewTreeObserver(), view, callback);
    }

    /**
     * Always registers an onpredrawlistener. The given ViewTreeObserver will be used.
     */
    public static void waitForLayout(
            final ViewTreeObserver viewTreeObserver,
            final View view,
            final OnMeasuredCallback callback
    ) {
        waitForLayoutInternal(false, viewTreeObserver, view, callback);
    }

    private static void waitForLayoutInternal(
            boolean returnIfNotZero,
            final ViewTreeObserver viewTreeObserver,
            final View view,
            final OnMeasuredCallback callback
    ) {
        int width = view.getWidth();
        int height = view.getHeight();

        if (returnIfNotZero && width > 0 && height > 0) {
            callback.onMeasured(view);
            return;
        }

        viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            private ViewTreeObserver usingViewTreeObserver = viewTreeObserver;

            @Override
            public boolean onPreDraw() {
                if (usingViewTreeObserver != view.getViewTreeObserver()) {
                    Logger.e(TAG, "view.getViewTreeObserver() is another viewtreeobserver! " +
                            "replacing with the new one");

                    usingViewTreeObserver = view.getViewTreeObserver();
                }

                if (usingViewTreeObserver.isAlive()) {
                    usingViewTreeObserver.removeOnPreDrawListener(this);
                } else {
                    Logger.e(TAG, "ViewTreeObserver not alive, could not remove onPreDrawListener! " +
                            "This will probably not end well");
                }

                boolean ret;
                try {
                    ret = callback.onMeasured(view);
                } catch (Exception e) {
                    Logger.e(TAG, "Exception in onMeasured", e);
                    throw e;
                }

                if (!ret) {
                    Logger.d(TAG, "waitForLayout requested a re-layout by returning false");
                }

                return ret;
            }
        });
    }

    public static void showToast(Context context, String message) {
        showToast(context, message, Toast.LENGTH_SHORT);
    }

    public static void showToast(Context context, int resId, int duration) {
        showToast(context, getString(resId), duration);
    }

    public static void showToast(Context context, int resId) {
        showToast(context, getString(resId));
    }

    public static void showToast(Context context, String message, int duration) {
        if (BackgroundUtils.isMainThread()) {
            cancellableToast.showToast(context.getApplicationContext(), message, duration);
            return;
        }

        BackgroundUtils.runOnMainThread(() -> {
            cancellableToast.showToast(context.getApplicationContext(), message, duration);
        });
    }

    public static void cancelLastToast() {
        cancellableToast.cancel();
    }

    public static SharedPreferences getPreferencesForSite(SiteDescriptor siteDescriptor) {
        String preferencesFileName = SITE_PREFS_FILE_PREFIX + siteDescriptor.getSiteName();

        return application.getSharedPreferences(
                preferencesFileName,
                Context.MODE_PRIVATE
        );
    }

    public static ActivityComponent extractActivityComponent(Context context) {
        if (context instanceof StartActivity) {
            return ((StartActivity) context).getActivityComponent();
        } else if (context instanceof SharingActivity) {
            return ((SharingActivity) context).getActivityComponent();
        } else if (context instanceof MediaViewerActivity) {
            return ((MediaViewerActivity) context).getActivityComponent();
        } else if (context instanceof ContextWrapper) {
            Context baseContext = ((ContextWrapper) context).getBaseContext();
            if (baseContext != null) {
                return extractActivityComponent(baseContext);
            }
        }

        throw new IllegalStateException("Unknown context wrapper " + context.getClass().getName());
    }

    public interface OnMeasuredCallback {
        /**
         * Called when the layout is done.
         *
         * @param view same view as the argument.
         * @return true to continue with rendering, false to cancel and redo the layout.
         */
        boolean onMeasured(View view);
    }

}
