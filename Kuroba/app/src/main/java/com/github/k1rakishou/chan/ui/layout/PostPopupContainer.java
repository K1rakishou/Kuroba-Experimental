/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.layout;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.common.AndroidUtils;
import com.github.k1rakishou.core_themes.ThemeEngine;

import javax.inject.Inject;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;

public class PostPopupContainer extends LinearLayout implements ThemeEngine.ThemeChangesListener {
    public static final int MAX_WIDTH = dp(800);
    private static final int HORIZ_PADDING = dp(24);
    private int maxWidth = MAX_WIDTH;

    @Inject
    ThemeEngine themeEngine;

    public PostPopupContainer(Context context) {
        super(context);
        init(context);
    }

    public PostPopupContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PostPopupContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        AppModuleAndroidUtils.extractActivityComponent(getContext())
                .inject(this);

        int minScreenSizeWithPaddings = AndroidUtils.getMinScreenSize(context) - HORIZ_PADDING;
        if (maxWidth > minScreenSizeWithPaddings) {
            maxWidth = minScreenSizeWithPaddings;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        themeEngine.addListener(this);

        onThemeChanged();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        themeEngine.removeListener(this);
    }

    @Override
    public void onThemeChanged() {
        setBackgroundColor(themeEngine.chanTheme.getBackColor());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY),
                heightMeasureSpec
        );
    }
}
