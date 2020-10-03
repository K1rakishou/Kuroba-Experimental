package com.github.k1rakishou.chan.ui.helper;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.manager.PostHideManager;
import com.github.k1rakishou.chan.core.model.Post;
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter;
import com.github.k1rakishou.chan.ui.controller.RemovedPostsController;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.github.k1rakishou.model.data.descriptor.PostDescriptor;
import com.github.k1rakishou.model.data.post.ChanPostHide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import static com.github.k1rakishou.chan.Chan.inject;
import static com.github.k1rakishou.chan.utils.AndroidUtils.showToast;

public class RemovedPostsHelper {
    private final String TAG = "RemovedPostsHelper";

    @Inject
    PostHideManager postHideManager;

    private Context context;
    private ThreadPresenter presenter;
    private RemovedPostsCallbacks callbacks;
    private @Nullable
    RemovedPostsController controller;

    public RemovedPostsHelper(Context context, ThreadPresenter presenter, RemovedPostsCallbacks callbacks) {
        this.context = context;
        this.presenter = presenter;
        this.callbacks = callbacks;

        inject(this);
    }

    public void showPosts(List<Post> threadPosts, ChanDescriptor.ThreadDescriptor threadDescriptor) {
        List<Post> removedPosts = getRemovedPosts(threadPosts, threadDescriptor);

        if (removedPosts.isEmpty()) {
            showToast(context, R.string.no_removed_posts_for_current_thread);
            return;
        }

        Collections.sort(removedPosts, (o1, o2) -> Long.compare(o1.no, o2.no));
        present();

        // controller should not be null here, thus no null check
        controller.showRemovePosts(removedPosts);
    }

    private List<Post> getRemovedPosts(
            List<Post> threadPosts,
            ChanDescriptor.ThreadDescriptor threadDescriptor
    ) {
        List<ChanPostHide> hiddenPosts = postHideManager.getHiddenPostsForThread(threadDescriptor);
        List<Post> removedPosts = new ArrayList<>();

        @SuppressLint("UseSparseArrays")
        Map<Long, ChanPostHide> fastLookupMap = new HashMap<>();

        for (ChanPostHide postHide : hiddenPosts) {
            fastLookupMap.put(postHide.getPostDescriptor().getPostNo(), postHide);
        }

        for (Post post : threadPosts) {
            if (fastLookupMap.containsKey(post.no)) {
                removedPosts.add(post);
            }
        }

        return removedPosts;
    }

    public void pop() {
        dismiss();
    }

    private void present() {
        if (controller == null) {
            controller = new RemovedPostsController(context, this);
            callbacks.presentRemovedPostsController(controller);
        }
    }

    private void dismiss() {
        if (controller != null) {
            controller.stopPresenting();
            controller = null;
        }
    }

    public void onRestoreClicked(List<PostDescriptor> selectedPosts) {
        presenter.onRestoreRemovedPostsClicked(selectedPosts);

        dismiss();
    }

    public interface RemovedPostsCallbacks {
        void presentRemovedPostsController(Controller controller);
    }
}
