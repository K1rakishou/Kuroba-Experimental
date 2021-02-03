package com.github.k1rakishou.chan.ui.view;

import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.Nullable;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;

/**
 * Helper for attaching a FastScroller with the correct theme colors and default values that
 * make it look like a normal scrollbar.
 */
public class FastScrollerHelper {
    public static final int FAST_SCROLLER_WIDTH = dp(10);

    public static FastScroller create(
            RecyclerView recyclerView,
            @Nullable
            PostInfoMapItemDecoration postInfoMapItemDecoration,
            int toolbarPaddingTop
    ) {
        final int minimumRange = dp(50);
        final int margin = 0;
        final int thumbMinLength = dp(32);

        return new FastScroller(
                recyclerView,
                postInfoMapItemDecoration,
                FAST_SCROLLER_WIDTH,
                minimumRange,
                margin,
                thumbMinLength,
                toolbarPaddingTop
        );
    }
}
