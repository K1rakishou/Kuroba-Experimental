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
package com.github.k1rakishou.chan.ui.controller;

import android.content.Context;
import android.widget.FrameLayout;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleNavigationController;
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController;
import com.github.k1rakishou.chan.ui.controller.navigation.SplitNavigationController;

import org.jetbrains.annotations.NotNull;

public class PopupController
        extends BaseFloatingController {
    private FrameLayout container;

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.layout_controller_popup;
    }

    public PopupController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        container = view.findViewById(R.id.inner_container);

        FrameLayout topView = view.findViewById(R.id.outside_area);
        topView.setOnClickListener((v) -> dismiss());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        dismiss();
    }

    public void setChildController(NavigationController childController) {
        addChildController(childController);
        childController.attachToParentView(container);
        childController.onShow();
    }

    public void dismiss() {
        if (presentedByController instanceof DoubleNavigationController) {
            ((SplitNavigationController) presentedByController).popAll();
        }
    }
}
