package com.github.adamantcheese.chan.ui.view;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;

import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.theme.Theme;

import org.jetbrains.annotations.Nullable;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;

/**
 * Helper for attaching a FastScroller with the correct theme colors and default values that
 * make it look like a normal scrollbar.
 */
public class FastScrollerHelper {

    public static FastScroller create(
            RecyclerView recyclerView,
            @Nullable
            PostInfoMapItemDecoration postInfoMapItemDecoration,
            Theme currentTheme
    ) {
        Context context = recyclerView.getContext();
        StateListDrawable thumb = getThumb(currentTheme);
        StateListDrawable track = getTrack(currentTheme);

        final int defaultThickness = dp(8);
        final int targetWidth = dp(8);
        final int minimumRange = dp(50);
        final int margin = 0;
        final int thumbMinLength = dp(32);

        int bottomNavBarHeight = 0;
        int toolbarHeight = 0;

        // The toolbar and bottom nav bar are not hideable when using SPLIT mode
        if (ChanSettings.getCurrentLayoutMode() != ChanSettings.LayoutMode.SPLIT) {
            bottomNavBarHeight = (int) context.getResources().getDimension(R.dimen.bottom_nav_view_height);
            toolbarHeight = (int) context.getResources().getDimension(R.dimen.toolbar_height);
        }

        return new FastScroller(
                recyclerView,
                postInfoMapItemDecoration,
                thumb,
                track,
                thumb,
                track,
                defaultThickness,
                minimumRange,
                margin,
                thumbMinLength,
                targetWidth,
                bottomNavBarHeight,
                toolbarHeight
        );
    }

    private static StateListDrawable getThumb(Theme curTheme) {
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(curTheme.accentColor.color));
        list.addState(new int[]{}, new ColorDrawable(curTheme.textSecondary));
        return list;
    }

    private static StateListDrawable getTrack(Theme curTheme) {
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(curTheme.textHint));
        list.addState(new int[]{}, new ColorDrawable(0));
        return list;
    }
}
