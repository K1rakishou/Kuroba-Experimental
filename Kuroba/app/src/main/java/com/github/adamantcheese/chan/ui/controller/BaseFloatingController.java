package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;

import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.manager.GlobalWindowInsetsManager;
import com.github.adamantcheese.chan.core.manager.WindowInsetsListener;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;

public abstract class BaseFloatingController extends Controller implements WindowInsetsListener {
    private static final int HPADDING = dp(8);
    private static final int VPADDING = dp(16);

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
        updatePaddings();

        globalWindowInsetsManager.addInsetsUpdatesListener(this);
    }

    @Override
    public void onInsetsChanged() {
        updatePaddings();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        globalWindowInsetsManager.removeInsetsUpdatesListener(this);
    }

    private void updatePaddings() {
        AndroidUtils.updatePaddings(
                view,
                HPADDING + globalWindowInsetsManager.left(),
                HPADDING + globalWindowInsetsManager.right(),
                VPADDING + globalWindowInsetsManager.top(),
                VPADDING + globalWindowInsetsManager.bottom()
        );
    }

    protected abstract int getLayoutId();
}
