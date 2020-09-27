package com.github.k1rakishou.chan.ui.view;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;

import androidx.recyclerview.widget.RecyclerView;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.settings.ChanSettings;
import com.github.k1rakishou.chan.ui.theme.ChanTheme;

import org.jetbrains.annotations.Nullable;

import static com.github.k1rakishou.chan.utils.AndroidUtils.dp;

/**
 * Helper for attaching a FastScroller with the correct theme colors and default values that
 * make it look like a normal scrollbar.
 */
public class FastScrollerHelper {

    public static FastScroller create(
            int toolbarPaddingTop,
            GlobalWindowInsetsManager globalWindowInsetsManager,
            RecyclerView recyclerView,
            @Nullable
            PostInfoMapItemDecoration postInfoMapItemDecoration,
            ChanTheme currentTheme
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
                globalWindowInsetsManager,
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
                toolbarHeight,
                toolbarPaddingTop
        );
    }

    private static StateListDrawable getThumb(ChanTheme curTheme) {
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(curTheme.getAccentColor()));
        list.addState(new int[]{}, new ColorDrawable(curTheme.getTextSecondaryColor()));
        return list;
    }

    private static StateListDrawable getTrack(ChanTheme curTheme) {
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(curTheme.getTextColorHint()));
        list.addState(new int[]{}, new ColorDrawable(0));
        return list;
    }
}
