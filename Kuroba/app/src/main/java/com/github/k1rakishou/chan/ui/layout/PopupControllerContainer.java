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
import android.widget.FrameLayout;

import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;

import javax.inject.Inject;


public class PopupControllerContainer extends FrameLayout {

    @Inject
    GlobalWindowInsetsManager globalWindowInsetsManager;

    public PopupControllerContainer(Context context) {
        super(context);
        init(context);
    }

    public PopupControllerContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PopupControllerContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        AppModuleAndroidUtils.extractActivityComponent(context)
                .inject(this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        FrameLayout.LayoutParams child = (LayoutParams) getChildAt(0).getLayoutParams();

        if (globalWindowInsetsManager.isKeyboardOpened()) {
            child.height = heightSize - globalWindowInsetsManager.top();
        } else {
            child.height = heightSize - (globalWindowInsetsManager.top() + globalWindowInsetsManager.bottom());
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
