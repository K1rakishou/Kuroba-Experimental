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

import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter;
import com.github.k1rakishou.chan.ui.controller.PostRepliesController;
import com.github.k1rakishou.chan.ui.view.ThumbnailView;
import com.github.k1rakishou.model.data.descriptor.PostDescriptor;
import com.github.k1rakishou.model.data.post.ChanPost;
import com.github.k1rakishou.model.data.post.ChanPostImage;

import java.util.ArrayList;
import java.util.List;

public class PostPopupHelper {
    private Context context;
    private ThreadPresenter presenter;
    private final PostPopupHelperCallback callback;

    private final List<RepliesData> dataQueue = new ArrayList<>();
    private PostRepliesController presentingController;

    public PostPopupHelper(Context context, ThreadPresenter presenter, PostPopupHelperCallback callback) {
        this.context = context;
        this.presenter = presenter;
        this.callback = callback;
    }

    public void showPosts(ChanPost forPost, List<ChanPost> posts) {
        RepliesData data = new RepliesData(forPost, posts);
        dataQueue.add(data);

        if (dataQueue.size() == 1) {
            present();
        }

        if (presenter.getCurrentChanDescriptor() == null) {
            throw new IllegalStateException("Thread loadable cannot be null");
        }

        presentingController.setPostRepliesData(presenter.getCurrentChanDescriptor(), data);
    }

    public void pop() {
        if (dataQueue.size() > 0) {
            dataQueue.remove(dataQueue.size() - 1);
        }

        if (dataQueue.size() > 0) {
            if (presenter.getCurrentChanDescriptor() == null) {
                throw new IllegalStateException("Thread loadable cannot be null");
            }

            presentingController.setPostRepliesData(
                    presenter.getCurrentChanDescriptor(),
                    dataQueue.get(dataQueue.size() - 1)
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
        public List<ChanPost> posts;
        public ChanPost forPost;

        public RepliesData(ChanPost forPost, List<ChanPost> posts) {
            this.forPost = forPost;
            this.posts = posts;
        }
    }

    public interface PostPopupHelperCallback {
        void presentRepliesController(Controller controller);
    }
}
