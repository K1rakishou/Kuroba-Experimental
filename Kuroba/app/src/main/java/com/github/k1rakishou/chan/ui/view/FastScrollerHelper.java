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
    private static final int MINIMUM_RANGE = dp(50);
    private static final int THUMB_MIN_LENGTH = dp(48);

    public static FastScroller create(
            FastScroller.FastScrollerControllerType fastScrollerControllerType,
            RecyclerView recyclerView,
            @Nullable
            PostInfoMapItemDecoration postInfoMapItemDecoration
    ) {
        recyclerView.setVerticalScrollBarEnabled(false);

        return new FastScroller(
                fastScrollerControllerType,
                recyclerView,
                postInfoMapItemDecoration,
                FAST_SCROLLER_WIDTH,
                MINIMUM_RANGE,
                THUMB_MIN_LENGTH
        );
    }
}
