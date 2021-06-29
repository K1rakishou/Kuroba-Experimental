package com.github.k1rakishou.chan.ui.controller;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString;

import android.content.Context;
import android.widget.ProgressBar;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

public class LoadingViewController extends BaseFloatingController {
    private ColorizableTextView loadingControllerTitle;
    private ColorizableTextView loadingControllerMessage;
    private ProgressBar progressBar;

    private String title;
    private boolean indeterminate;
    private boolean backAllowed = false;
    private @Nullable Function0<Unit> cancellationFunc;

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    public LoadingViewController(Context context, boolean indeterminate) {
        super(context);

        this.indeterminate = indeterminate;
        this.title = getString(R.string.doing_heavy_lifting_please_wait);
    }

    public LoadingViewController(Context context, boolean indeterminate, String title) {
        super(context);

        this.indeterminate = indeterminate;
        this.title = title;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        loadingControllerTitle = view.findViewById(R.id.loading_controller_title);
        loadingControllerTitle.setText(title);

        loadingControllerMessage = view.findViewById(R.id.loading_controller_message);
        progressBar = view.findViewById(R.id.progress_bar);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (cancellationFunc != null) {
            cancellationFunc.invoke();
            cancellationFunc = null;
        }
    }

    public void enableBack() {
        backAllowed = true;
    }

    public void enableBack(@NotNull Function0<Unit> cancellationFunc) {
        backAllowed = true;
        this.cancellationFunc = cancellationFunc;
    }

    // Disable the back button for this controller unless otherwise requested by the above
    @Override
    public boolean onBack() {
        if (backAllowed) {
            presentedByController.onBack();
        }

        if (cancellationFunc != null) {
            cancellationFunc.invoke();
            cancellationFunc = null;
        }

        return true;
    }

    /**
     * Shows a progress bar with percentage in the center (cannot be used with indeterminate)
     */
    public void updateProgress(int percent) {
        if (indeterminate) {
            return;
        }

        loadingControllerMessage.setVisibility(VISIBLE);
        progressBar.setVisibility(VISIBLE);
        loadingControllerMessage.setText(String.valueOf(percent > 0 ? percent : "0"));
    }

    /**
     * Hide a progress bar and instead of percentage any text may be shown
     * (cannot be used with indeterminate)
     */
    public void updateWithText(String text) {
        if (indeterminate) {
            return;
        }

        loadingControllerMessage.setVisibility(VISIBLE);
        progressBar.setVisibility(GONE);
        loadingControllerMessage.setText(text);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.controller_loading_view;
    }

}
