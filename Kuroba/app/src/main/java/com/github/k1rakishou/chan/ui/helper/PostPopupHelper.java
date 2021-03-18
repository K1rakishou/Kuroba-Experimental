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
package com.github.k1rakishou.chan.ui.helper;

import android.content.Context;

import androidx.annotation.NonNull;

import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.loader.LoaderResult;
import com.github.k1rakishou.chan.core.manager.ChanThreadManager;
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter;
import com.github.k1rakishou.chan.ui.cell.PostCellData;
import com.github.k1rakishou.chan.ui.controller.PostRepliesController;
import com.github.k1rakishou.chan.ui.view.ThumbnailView;
import com.github.k1rakishou.chan.utils.BackgroundUtils;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.github.k1rakishou.model.data.descriptor.PostDescriptor;
import com.github.k1rakishou.model.data.post.ChanPost;
import com.github.k1rakishou.model.data.post.ChanPostImage;
import com.github.k1rakishou.model.data.post.PostIndexed;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kotlin.Unit;

public class PostPopupHelper {
    private final Context context;
    private final ThreadPresenter presenter;
    private final ChanThreadManager chanThreadManager;
    private final PostPopupHelperCallback callback;

    private final List<RepliesData> dataQueue = new ArrayList<>();
    private PostRepliesController presentingController;

    public PostPopupHelper(
            Context context,
            ThreadPresenter presenter,
            ChanThreadManager chanThreadManager,
            PostPopupHelperCallback callback
    ) {
        this.context = context;
        this.presenter = presenter;
        this.chanThreadManager = chanThreadManager;
        this.callback = callback;
    }

    public void showPosts(
            @NonNull ChanDescriptor.ThreadDescriptor threadDescriptor,
            PostCellData.PostAdditionalData postAdditionalData,
            @Nullable PostDescriptor postDescriptor,
            List<ChanPost> posts
    ) {
        RepliesData data = new RepliesData(threadDescriptor, postAdditionalData, postDescriptor, indexPosts(posts));
        dataQueue.add(data);

        if (dataQueue.size() == 1) {
            present();
        }

        if (threadDescriptor == null) {
            throw new IllegalStateException("Thread descriptor cannot be null");
        }

        presentingController.setPostRepliesData(threadDescriptor, data);
    }

    private List<PostIndexed> indexPosts(List<ChanPost> posts) {
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }

        List<PostIndexed> postIndexedList = new ArrayList<>();
        ChanDescriptor.ThreadDescriptor threadDescriptor =
                posts.get(0).getPostDescriptor().threadDescriptor();

        chanThreadManager.iteratePostIndexes(
                threadDescriptor,
                posts,
                ChanPost::getPostDescriptor,
                (chanPost, postIndex) -> {
                    postIndexedList.add(new PostIndexed(chanPost, postIndex));
                    return Unit.INSTANCE;
                }
        );

        return postIndexedList;
    }

    @Nullable
    public RepliesData topOrNull() {
        if (dataQueue.isEmpty()) {
            return null;
        }

        return dataQueue.get(dataQueue.size() - 1);
    }

    public void onPostUpdated(@NotNull PostDescriptor postDescriptor, List<LoaderResult> results) {
        BackgroundUtils.ensureMainThread();
        presentingController.onPostUpdated(postDescriptor, results);
    }

    public void pop() {
        if (dataQueue.size() > 0) {
            dataQueue.remove(dataQueue.size() - 1);
        }

        if (dataQueue.size() > 0) {
            RepliesData repliesData = dataQueue.get(dataQueue.size() - 1);

            if (repliesData.threadDescriptor == null) {
                throw new IllegalStateException("Thread descriptor cannot be null");
            }

            presentingController.setPostRepliesData(
                    repliesData.threadDescriptor,
                    repliesData
            );
        } else {
            dismiss();
        }
    }

    public void popAll() {
        dataQueue.clear();
        dismiss();
    }

    public boolean isOpen() {
        return presentingController != null && presentingController.alive;
    }

    public List<PostDescriptor> getDisplayingPostDescriptors() {
        return presentingController.getPostRepliesData();
    }

    public void scrollTo(int displayPosition, boolean smooth) {
        presentingController.scrollTo(displayPosition);
    }

    public ThumbnailView getThumbnail(ChanPostImage postImage) {
        return presentingController.getThumbnail(postImage);
    }

    public void postClicked(PostDescriptor postDescriptor) {
        popAll();
        presenter.highlightPost(postDescriptor);
        presenter.scrollToPost(postDescriptor, true);
    }

    private void dismiss() {
        PostRepliesController.clearScrollPositionCache();

        if (presentingController != null) {
            presentingController.stopPresenting();
            presentingController = null;
        }
    }

    private void present() {
        if (presentingController == null) {
            presentingController = new PostRepliesController(context, this, presenter);
            callback.presentRepliesController(presentingController);
        }
    }

    public static class RepliesData {
        public final ChanDescriptor.ThreadDescriptor threadDescriptor;
        public final PostCellData.PostAdditionalData postAdditionalData;
        public final List<PostIndexed> posts;
        public final @Nullable PostDescriptor forPostWithDescriptor;

        public RepliesData(
                ChanDescriptor.ThreadDescriptor threadDescriptor,
                PostCellData.PostAdditionalData postAdditionalData,
                @Nullable PostDescriptor postDescriptor,
                List<PostIndexed> posts
        ) {
            this.threadDescriptor = threadDescriptor;
            this.postAdditionalData = postAdditionalData;
            this.forPostWithDescriptor = postDescriptor;
            this.posts = posts;
        }
    }

    public interface PostPopupHelperCallback {
        void presentRepliesController(Controller controller);
    }
}
