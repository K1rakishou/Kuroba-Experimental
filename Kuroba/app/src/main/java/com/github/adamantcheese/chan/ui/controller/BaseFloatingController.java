package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;

import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.manager.GlobalWindowInsetsManager;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import javax.inject.Inject;

import dev.chrisbanes.insetter.Insetter;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getWindow;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;
import static com.github.adamantcheese.chan.utils.AnimationUtils.animateStatusBar;

public abstract class BaseFloatingController extends Controller {
    private static final int TRANSITION_DURATION = 200;
    private static final int HPADDING = dp(8);
    private static final int VPADDING = dp(16);

    private int statusBarColorPrevious;

    @Inject
    GlobalWindowInsetsManager globalWindowInsetsManager;

    public BaseFloatingController(Context context) {
        super(context);

        inject(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflate(context, getLayoutId());

        statusBarColorPrevious = getWindow(context).getStatusBarColor();
        if (statusBarColorPrevious != 0) {
            animateStatusBar(getWindow(context), true, statusBarColorPrevious, TRANSITION_DURATION);
        }

        Insetter.setOnApplyInsetsListener(view, (view, insets, initialState) -> {
            AndroidUtils.updatePaddings(
                    view,
                    HPADDING + initialState.getPaddings().getLeft() + globalWindowInsetsManager.left(),
                    HPADDING + initialState.getPaddings().getRight() + globalWindowInsetsManager.right(),
                    VPADDING + initialState.getPaddings().getTop() + globalWindowInsetsManager.top(),
                    VPADDING + initialState.getPaddings().getBottom() + globalWindowInsetsManager.bottom()
            );
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (statusBarColorPrevious != 0) {
            animateStatusBar(getWindow(context), false, statusBarColorPrevious, TRANSITION_DURATION);
        }
    }

    protected abstract int getLayoutId();
}
