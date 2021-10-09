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

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.common.AndroidUtils;
import com.github.k1rakishou.core_themes.ThemeEngine;

import javax.inject.Inject;

public class PostPopupContainer extends LinearLayout implements ThemeEngine.ThemeChangesListener {
    private static final int MAX_WIDTH = dp(800);

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
        setBackgroundColor(themeEngine.getChanTheme().getBackColor());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidth = MAX_WIDTH;
        int minScreenSizeWithPaddings = AndroidUtils.getScreenWidth(getContext());

        if (maxWidth > minScreenSizeWithPaddings) {
            maxWidth = minScreenSizeWithPaddings;
        }

        int parentMaxWidth = MeasureSpec.getSize(widthMeasureSpec);
        if (maxWidth > parentMaxWidth) {
            maxWidth = parentMaxWidth;
        }

        int newWidthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
        super.onMeasure(newWidthSpec, heightMeasureSpec);
    }
}
