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
import android.view.View;

import androidx.drawerlayout.widget.DrawerLayout;

import com.github.k1rakishou.chan.R;

public class DrawerWidthAdjustingLayout extends DrawerLayout {
    private static final int RIGHT_PADDING = dp(56);
    private static final int DEFAULT_SIZE = RIGHT_PADDING * 8;
    private static final int EDIT_MODE_WIDTH = 300;
    private View drawer;

    public DrawerWidthAdjustingLayout(Context context) {
        super(context);
    }

    public DrawerWidthAdjustingLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DrawerWidthAdjustingLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        if (drawer == null) {
            drawer = findViewById(R.id.drawer_part);
        }

        int width;
        if (!isInEditMode()) {
            width = Math.min(widthSize - RIGHT_PADDING, DEFAULT_SIZE);
        } else {
            width = EDIT_MODE_WIDTH;
        }

        if (drawer.getLayoutParams().width != width) {
            drawer.getLayoutParams().width = width;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
