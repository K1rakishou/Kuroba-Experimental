package com.github.k1rakishou.chan.ui.view;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;

import androidx.recyclerview.widget.RecyclerView;

import com.github.k1rakishou.chan.ui.theme.ChanTheme;

import org.jetbrains.annotations.Nullable;

import static com.github.k1rakishou.chan.utils.AndroidUtils.dp;

/**
 * Helper for attaching a FastScroller with the correct theme colors and default values that
 * make it look like a normal scrollbar.
 */
public class FastScrollerHelper {

    public static FastScroller create(
            RecyclerView recyclerView,
            @Nullable
            PostInfoMapItemDecoration postInfoMapItemDecoration,
            ChanTheme currentTheme,
            int toolbarPaddingTop
    ) {
        StateListDrawable thumb = getThumb(currentTheme);
        StateListDrawable track = getTrack(currentTheme);

        final int defaultThickness = dp(8);
        final int targetWidth = dp(8);
        final int minimumRange = dp(50);
        final int margin = 0;
        final int thumbMinLength = dp(32);

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
                toolbarPaddingTop
        );
    }

    private static StateListDrawable getThumb(ChanTheme curTheme) {
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(curTheme.getAccentColor()));
        list.addState(new int[]{}, new ColorDrawable(curTheme.getTextColorSecondary()));
        return list;
    }

    private static StateListDrawable getTrack(ChanTheme curTheme) {
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(curTheme.getTextColorHint()));
        list.addState(new int[]{}, new ColorDrawable(0));
        return list;
    }
}
