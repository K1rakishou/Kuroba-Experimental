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

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDrawable;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * The navigation properties for a Controller. Controls common properties that parent controllers
 * need to know, such as the title of the controller.
 * <p>
 * This is also used to set up the toolbar menu, see {@link #buildMenu(Context)}.
 */
public class NavigationItem {
    public String title = "";
    public CharSequence subtitle = "";

    public boolean hasBack = true;
    public boolean hasDrawer;
    public boolean swipeable = true;
    public boolean scrollableTitle = false;

    public String searchText;
    public boolean search;

    public boolean selectionMode;
    public String selectionStateText;

    protected ToolbarMenu menu;
    protected ToolbarMiddleMenu middleMenu;
    protected View rightView;

    public boolean hasArrow() {
        return hasBack || search;
    }

    public void setTitle(int resId) {
        title = getString(resId);
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public MenuBuilder buildMenu(Context context) {
        return new MenuBuilder(context, this);
    }

    public void setMiddleMenu(ToolbarMiddleMenu middleMenu) {
        this.middleMenu = middleMenu;
    }

    public void setRightView(View view) {
        rightView = view;
    }

    public ToolbarMenuItem findItem(int id) {
        return menu == null ? null : menu.findItem(id);
    }

    public ToolbarMenuSubItem findSubItem(int id) {
        return menu == null ? null : menu.findSubItem(id);
    }

    public CheckableToolbarMenuSubItem findCheckableSubItem(int id) {
        if (menu == null) {
            return null;
        }

        ToolbarMenuSubItem subItem = menu.findSubItem(id);
        if (subItem instanceof CheckableToolbarMenuSubItem) {
            return (CheckableToolbarMenuSubItem) subItem;
        }

        return null;
    }

    public ToolbarMenuSubItem findNestedSubItem(int id) {
        return menu == null ? null : menu.findNestedSubItem(id);
    }

    public static class MenuBuilder {
        private final NavigationItem navigationItem;
        private final ToolbarMenu menu;
        private final Context context;

        public MenuBuilder(Context context, NavigationItem navigationItem) {
            this.navigationItem = navigationItem;
            this.context = context;
            menu = new ToolbarMenu();
        }

        public MenuBuilder withItem(int drawable, ToolbarMenuItem.ClickCallback clickCallback) {
            return withItem(-1, drawable, clickCallback);
        }

        public MenuBuilder withItem(int id, int drawable, ToolbarMenuItem.ClickCallback clickCallback) {
            return withItem(new ToolbarMenuItem(context, id, drawable, clickCallback));
        }

        public MenuBuilder withItem(int id, Drawable drawable, ToolbarMenuItem.ClickCallback clickCallback) {
            return withItem(new ToolbarMenuItem(context, id, drawable, clickCallback));
        }

        public MenuBuilder withItem(
                int id,
                int drawable,
                ToolbarMenuItem.ClickCallback clickCallback,
                ToolbarMenuItem.ClickCallback longClickCallback
        ) {
            return withItem(new ToolbarMenuItem(context, id, getDrawable(drawable), clickCallback, longClickCallback));
        }

        public MenuBuilder withItem(
                int id,
                Drawable drawable,
                ToolbarMenuItem.ClickCallback clickCallback,
                ToolbarMenuItem.ClickCallback longClickCallback
        ) {
            return withItem(new ToolbarMenuItem(context, id, drawable, clickCallback, longClickCallback));
        }

        public MenuBuilder withItem(ToolbarMenuItem menuItem) {
            menu.addItem(menuItem);
            return this;
        }

        /**
         * Intercepts menu item clicks (except for the Middle menu). Can filter out clicks by
         * returning true;
         * */
        public MenuBuilder withMenuItemClickInterceptor(
                @NonNull ToolbarMenuView.MenuItemClickInterceptor itemClickInterceptor
        ) {
            menu.setMenuItemClickInterceptor(itemClickInterceptor);
            return this;
        }

        public MenuOverflowBuilder withOverflow(NavigationController navigationController) {
            return new MenuOverflowBuilder(this,
                    new ToolbarMenuItem(
                            context,
                            ToolbarMenu.OVERFLOW_ID,
                            R.drawable.ic_more_vert_white_24dp,
                            ToolbarMenuItem::showSubmenu,
                            navigationController,
                            null
                    )
            );
        }

        public MenuOverflowBuilder withOverflow(
                NavigationController navigationController,
                ToolbarMenuItem.ToobarThreedotMenuCallback threedotMenuCallback
        ) {
            return new MenuOverflowBuilder(this,
                    new ToolbarMenuItem(
                            context,
                            ToolbarMenu.OVERFLOW_ID,
                            R.drawable.ic_more_vert_white_24dp,
                            ToolbarMenuItem::showSubmenu,
                            navigationController,
                            threedotMenuCallback
                    ));
        }

        public ToolbarMenu build() {
            navigationItem.menu = menu;
            return menu;
        }
    }

    public static class MenuOverflowBuilder {
        private final MenuBuilder menuBuilder;
        private final ToolbarMenuItem menuItem;

        public MenuOverflowBuilder(MenuBuilder menuBuilder, ToolbarMenuItem menuItem) {
            this.menuBuilder = menuBuilder;
            this.menuItem = menuItem;
        }

        public MenuOverflowBuilder withSubItem(
                int id,
                int text,
                ToolbarMenuSubItem.ClickCallback clickCallback
        ) {
            return withSubItem(id, getString(text), true, clickCallback);
        }

        public MenuOverflowBuilder withSubItem(
                int id,
                int text,
                Function1<ToolbarMenuSubItem, Unit> clickCallback
        ) {
            return withSubItem(id, getString(text), true, clickCallback::invoke);
        }

        public MenuOverflowBuilder withSubItem(
                int id,
                int text,
                boolean visible,
                ToolbarMenuSubItem.ClickCallback clickCallback
        ) {
            return withSubItem(id, getString(text), visible, clickCallback);
        }

        public MenuOverflowBuilder withSubItem(
                int id,
                String text,
                boolean visible,
                ToolbarMenuSubItem.ClickCallback clickCallback
        ) {
            menuItem.addSubItem(new ToolbarMenuSubItem(id, text, clickCallback, visible));

            return this;
        }

        public MenuOverflowBuilder withCheckableSubItem(
                int id,
                int textId,
                boolean visible,
                boolean checked,
                ToolbarMenuSubItem.ClickCallback clickCallback
        ) {
            menuItem.addSubItem(new CheckableToolbarMenuSubItem(id, getString(textId), clickCallback, visible, null, checked));

            return this;
        }

        public MenuOverflowBuilder withCheckableSubItem(
                int id,
                String text,
                boolean visible,
                boolean checked,
                ToolbarMenuSubItem.ClickCallback clickCallback
        ) {
            menuItem.addSubItem(new CheckableToolbarMenuSubItem(id, text, clickCallback, visible, null, checked));

            return this;
        }

        /**
         * Note: this method only supports one level of depth. If you need more you will have to
         * implement it yourself. The reason for that is that at the time of writing this there
         * was no need for more than one level of depth.
         * @see ToolbarMenuItem#showSubmenu()
         *
         * Note2: all menu ids have to be unique. MenuItems without id at all (-1) are not allowed too.
         * Otherwise this will crash in
         * @see ToolbarMenuItem#showSubmenu()
         * */
        public MenuNestedOverflowBuilder withNestedOverflow(
                int id,
                int textId,
                boolean visible
        ) {
            return new MenuNestedOverflowBuilder(this,
                    new ToolbarMenuSubItem(
                            id,
                            textId,
                            null,
                            visible
                    ));
        }

        public MenuOverflowBuilder addNestedItemsTo(
                int ownerMenuItem,
                List<ToolbarMenuSubItem> nestedMenuItems
        ) {
            for (ToolbarMenuSubItem subItem : menuItem.subItems) {
                if (subItem.id == ownerMenuItem) {
                    for (ToolbarMenuSubItem nestedItem : nestedMenuItems) {
                        subItem.addNestedItem(nestedItem);
                    }

                    break;
                }
            }


            return this;
        }

        public MenuBuilder build() {
            return menuBuilder.withItem(menuItem);
        }
    }

    public static class MenuNestedOverflowBuilder {
        private final MenuOverflowBuilder menuOverflowBuilder;
        private final ToolbarMenuSubItem menuSubItem;
        private final List<ToolbarMenuSubItem> nestedMenuItems = new ArrayList<>();

        public MenuNestedOverflowBuilder(
                MenuOverflowBuilder menuOverflowBuilder,
                ToolbarMenuSubItem menuSubItem
        ) {
            this.menuOverflowBuilder = menuOverflowBuilder;
            this.menuSubItem = menuSubItem;
        }

        public MenuNestedOverflowBuilder addNestedItem(
                int itemId,
                int text,
                boolean visible,
                Object value,
                ToolbarMenuSubItem.ClickCallback clickCallback
        ) {
            for (ToolbarMenuSubItem subItem : menuSubItem.moreItems) {
                if (subItem.id == itemId) {
                    throw new IllegalArgumentException("Menu item with id " + itemId + " was already added");
                }
            }

            nestedMenuItems.add(
                    new ToolbarMenuSubItem(
                            itemId,
                            text,
                            clickCallback,
                            visible,
                            value
                    )
            );

            return this;
        }

        public MenuNestedOverflowBuilder addNestedItem(
                int itemId,
                String text,
                boolean visible,
                Object value,
                ToolbarMenuSubItem.ClickCallback clickCallback
        ) {
            for (ToolbarMenuSubItem subItem : menuSubItem.moreItems) {
                if (subItem.id == itemId) {
                    throw new IllegalArgumentException("Menu item with id " + itemId + " was already added");
                }
            }

            nestedMenuItems.add(
                    new ToolbarMenuSubItem(
                            itemId,
                            text,
                            clickCallback,
                            visible,
                            value
                    )
            );

            return this;
        }

        private MenuNestedOverflowBuilder addNestedCheckableItem(
                int itemId,
                int text,
                boolean visible,
                boolean isCurrentlySelected,
                Object value,
                Object groupId,
                ToolbarMenuSubItem.ClickCallback clickCallback
        ) {
            for (ToolbarMenuSubItem subItem : menuSubItem.moreItems) {
                if (subItem.id == itemId) {
                    throw new IllegalArgumentException("Menu item with id " + itemId + " was already added");
                }
            }

            nestedMenuItems.add(
                    new CheckableToolbarMenuSubItem(
                            itemId,
                            getString(text),
                            clickCallback,
                            visible,
                            value,
                            groupId,
                            isCurrentlySelected
                    )
            );

            return this;
        }

        public MenuNestedOverflowBuilder addNestedItem(
                int itemId,
                int text,
                boolean visible,
                Object value,
                Function1<ToolbarMenuSubItem, Unit> clickCallback
        ) {
            return addNestedItem(
                    itemId,
                    text,
                    visible,
                    value,
                    (ToolbarMenuSubItem.ClickCallback) clickCallback::invoke
            );
        }

        public MenuNestedOverflowBuilder addNestedCheckableItem(
                int itemId,
                int text,
                boolean visible,
                boolean isCurrentlySelected,
                Object value,
                Function1<ToolbarMenuSubItem, Unit> clickCallback
        ) {
            return addNestedCheckableItem(
                    itemId,
                    text,
                    visible,
                    isCurrentlySelected,
                    value,
                    null,
                    (ToolbarMenuSubItem.ClickCallback) clickCallback::invoke
            );
        }

        public MenuNestedOverflowBuilder addNestedCheckableItem(
                int itemId,
                int text,
                boolean visible,
                boolean isCurrentlySelected,
                Object value,
                Object groupId,
                Function1<ToolbarMenuSubItem, Unit> clickCallback
        ) {
            return addNestedCheckableItem(
                    itemId,
                    text,
                    visible,
                    isCurrentlySelected,
                    value,
                    groupId,
                    (ToolbarMenuSubItem.ClickCallback) clickCallback::invoke
            );
        }

        public MenuOverflowBuilder build() {
            if (nestedMenuItems.isEmpty()) {
                throw new IllegalStateException("nestedMenuItems is empty");
            }

            return menuOverflowBuilder
                    .withSubItem(menuSubItem.id, menuSubItem.text, menuSubItem.visible, null)
                    .addNestedItemsTo(menuSubItem.id, nestedMenuItems);
        }
    }

    @Override
    public String toString() {
        return "NavigationItem{" +
                "title='" + title + '\'' +
                ", subtitle='" + subtitle + '\'' +
                ", hasBack=" + hasBack +
                ", hasDrawer=" + hasDrawer +
                ", swipeable=" + swipeable +
                ", scrollableTitle=" + scrollableTitle +
                ", searchText='" + searchText + '\'' +
                ", search=" + search +
                ", selectionMode=" + selectionMode +
                ", selectionStateText='" + selectionStateText + '\'' +
                '}';
    }

}
