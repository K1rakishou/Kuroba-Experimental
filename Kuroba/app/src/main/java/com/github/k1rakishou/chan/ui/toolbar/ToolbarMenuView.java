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
package com.github.k1rakishou.chan.ui.toolbar;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.github.k1rakishou.common.AndroidUtils;

/**
 * The container view for the list of ToolbarMenuItems, a list of ImageViews.
 */
public class ToolbarMenuView extends LinearLayout {
    private ToolbarMenu menu;

    public ToolbarMenuView(Context context) {
        this(context, null);
    }

    public ToolbarMenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ToolbarMenuView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(6f), 0, dp(3f), 0);
    }

    public void attach(ToolbarMenu menu) {
        this.menu = menu;

        setupMenuViews();
    }

    public void detach() {
        if (menu != null) {
            for (ToolbarMenuItem item : menu.items) {
                item.detach();
            }
        }
    }

    private void setupMenuViews() {
        removeAllViews();

        for (ToolbarMenuItem item : menu.items) {
            ImageView imageView = new ImageView(getContext());

            imageView.setOnClickListener(v -> {
                MenuItemClickInterceptor menuItemClickInterceptor = menu.getInterceptor();

                if (menuItemClickInterceptor != null && menuItemClickInterceptor.intercept(item)) {
                    return;
                }

                item.performClick();
            });

            imageView.setOnLongClickListener(v -> item.performLongClick());

            imageView.setFocusable(true);
            imageView.setScaleType(ImageView.ScaleType.CENTER);

            imageView.setVisibility(item.visible ? VISIBLE : GONE);

            imageView.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
            imageView.setPadding(dp(10f), 0, dp(10f), 0);

            imageView.setImageDrawable(item.drawable);
            AndroidUtils.setBoundlessRoundRippleBackground(imageView);

            addView(imageView);

            item.attach(imageView);
        }
    }

    public interface MenuItemClickInterceptor {
        /**
         * Return true to consume the click and to not call the original toolbar menu item click
         * handler. Return false to continue with the original click handler.
         * */
        boolean intercept(ToolbarMenuItem toolbarMenuItem);
    }
}
