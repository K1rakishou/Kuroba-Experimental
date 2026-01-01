package com.github.k1rakishou.chan.ui.helper;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openIntent;
import static com.github.k1rakishou.common.AndroidUtils.getAppContext;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.helper.DialogFactory;

import kotlin.Unit;

public class RuntimePermissionsHelper {
    private static final int RUNTIME_PERMISSION_RESULT_ID = 3;

    private ActivityCompat.OnRequestPermissionsResultCallback callbackActivity;
    private DialogFactory dialogFactory;
    private CallbackHolder pendingCallback;

    public RuntimePermissionsHelper(
            ActivityCompat.OnRequestPermissionsResultCallback callbackActivity,
            DialogFactory dialogFactory
    ) {
        this.callbackActivity = callbackActivity;
        this.dialogFactory = dialogFactory;
    }

    public boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(getAppContext(), permission) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean requestPermission(String permission, Callback callback) {
        if (pendingCallback == null) {
            pendingCallback = new CallbackHolder();
            pendingCallback.callback = callback;
            pendingCallback.permission = permission;

            ActivityCompat.requestPermissions((Activity) callbackActivity,
                    new String[]{permission},
                    RUNTIME_PERMISSION_RESULT_ID
            );

            return true;
        } else {
            return false;
        }
    }

    public void onRequestPermissionsResult(
            int reqCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        if (reqCode == RUNTIME_PERMISSION_RESULT_ID && pendingCallback != null) {
            boolean granted = false;

            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                if (permission.equals(pendingCallback.permission)
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }

            pendingCallback.callback.onRuntimePermissionResult(granted);
            pendingCallback = null;
        }
    }

    public void showPermissionRequiredDialog(
            final Context context,
            String title,
            String message,
            final PermissionRequiredDialogCallback callback
    ) {
        DialogFactory.Builder.newBuilder(context, dialogFactory)
                .withTitle(title)
                .withDescription(message)
                .withNeutralButtonTextId(R.string.permission_app_settings)
                .withOnNeutralButtonClickListener((dialog) -> {
                    callback.retryPermissionRequest();

                    Intent intent = new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + context.getPackageName())
                    );
                    openIntent(intent);

                    return Unit.INSTANCE;
                })
                .withPositiveButtonTextId(R.string.permission_grant)
                .withOnPositiveButtonClickListener((dialog) -> {
                    callback.retryPermissionRequest();
                    return Unit.INSTANCE;
                })
                .withDismissListener(() -> {
                    callback.onDismissed();
                    return Unit.INSTANCE;
                })
                .create();
    }

    public interface PermissionRequiredDialogCallback {
        void retryPermissionRequest();
        void onDismissed();
    }

    private static class CallbackHolder {
        private Callback callback;
        private String permission;
    }

    public interface Callback {
        void onRuntimePermissionResult(boolean granted);
    }
}
