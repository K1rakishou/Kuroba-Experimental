package com.github.k1rakishou.chan.ui.helper;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.manager.ChanThreadManager;
import com.github.k1rakishou.chan.core.manager.PostHideManager;
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter;
import com.github.k1rakishou.chan.ui.controller.RemovedPostsController;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.github.k1rakishou.model.data.descriptor.PostDescriptor;
import com.github.k1rakishou.model.data.post.ChanPost;
import com.github.k1rakishou.model.data.post.ChanPostHide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.Lazy;

public class RemovedPostsHelper {
    private final String TAG = "RemovedPostsHelper";

    @Inject
    Lazy<PostHideManager> postHideManager;
    @Inject
    Lazy<ChanThreadManager> chanThreadManager;

    private Context context;
    private ThreadPresenter presenter;
    private RemovedPostsCallbacks callbacks;
    private @Nullable
    RemovedPostsController controller;

    public RemovedPostsHelper(Context context, ThreadPresenter presenter, RemovedPostsCallbacks callbacks) {
        this.context = context;
        this.presenter = presenter;
        this.callbacks = callbacks;

        AppModuleAndroidUtils.extractActivityComponent(context)
                .inject(this);
    }

    public void showPosts(List<PostDescriptor> threadPosts, ChanDescriptor.ThreadDescriptor threadDescriptor) {
        List<PostDescriptor> removedPosts = getRemovedPosts(threadPosts, threadDescriptor);
        if (removedPosts.isEmpty()) {
            showToast(context, R.string.no_removed_posts_for_current_thread);
            return;
        }

        Collections.sort(removedPosts, (o1, o2) -> Long.compare(o1.getPostNo(), o2.getPostNo()));
        present();

        List<ChanPost> resultPosts = new ArrayList<>();

        chanThreadManager.get().iteratePostsWhile(threadDescriptor, removedPosts, chanPost -> {
            resultPosts.add(chanPost);
            return true;
        });

        if (resultPosts.isEmpty()) {
            return;
        }

        if (controller != null) {
            controller.showRemovePosts(resultPosts);
        }
    }

    private List<PostDescriptor> getRemovedPosts(
            List<PostDescriptor> threadPosts,
            ChanDescriptor.ThreadDescriptor threadDescriptor
    ) {
        List<ChanPostHide> hiddenPosts = postHideManager.get().getHiddenPostsForThread(threadDescriptor);
        List<PostDescriptor> removedPosts = new ArrayList<>();

        @SuppressLint("UseSparseArrays")
        Map<Long, ChanPostHide> fastLookupMap = new HashMap<>();

        for (ChanPostHide postHide : hiddenPosts) {
            fastLookupMap.put(postHide.getPostDescriptor().getPostNo(), postHide);
        }

        for (PostDescriptor post : threadPosts) {
            if (fastLookupMap.containsKey(post.getPostNo())) {
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
