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
package com.github.adamantcheese.chan.ui.cell;

import com.github.adamantcheese.chan.core.manager.PostPreloadedInfoHolder;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest;
import com.github.adamantcheese.chan.ui.controller.FloatingListMenuController;
import com.github.adamantcheese.chan.ui.text.span.PostLinkable;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.ui.view.floating_menu.FloatingListMenu;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PostCellInterface {
    void setPost(
            Loadable loadable,
            Post post,
            PostCellCallback callback,
            PostPreloadedInfoHolder postPreloadedInfoHolder,
            boolean inPopup,
            boolean highlighted,
            boolean selected,
            long markedNo,
            boolean showDivider,
            ChanSettings.PostViewMode postViewMode,
            boolean compact,
            Theme theme
    );

    /**
     * @param isActuallyRecycling is only true when the view holder that is getting passed into the
     *                            RecyclerView's onViewRecycled is being recycled because it's
     *                            offscreen and not because we called notifyItemChanged.
     * */
    void onPostRecycled(boolean isActuallyRecycling);
    Post getPost();
    ThumbnailView getThumbnailView(PostImage postImage);

    interface PostCellCallback {
        @Nullable Loadable getLoadable();
        // Only used in PostCell and CardPostCell, no need to use in stubs
        void onPostBind(Post post);
        // Only used in PostCell and CardPostCell, no need to use in stubs
        void onPostUnbind(Post post, boolean isActuallyRecycling);
        void onPostClicked(Post post);
        void onPostDoubleClicked(Post post);
        void onThumbnailClicked(PostImage image, ThumbnailView thumbnail);
        void onShowPostReplies(Post post);
        void onPopulatePostOptions(Post post, List<FloatingListMenu.FloatingListMenuItem> menu);
        void onPostOptionClicked(Post post, Object id, boolean inPopup);
        void onPostLinkableClicked(Post post, PostLinkable linkable);
        void onPostNoClicked(Post post);
        void onPostSelectionQuoted(Post post, CharSequence quoted);
        @Nullable Chan4PagesRequest.BoardPage getPage(Post op);
        boolean hasAlreadySeenPost(Post post);
        void presentController(FloatingListMenuController floatingListMenuController, boolean animate);
    }
}
