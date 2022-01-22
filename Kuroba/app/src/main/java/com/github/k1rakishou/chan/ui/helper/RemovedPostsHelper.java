package com.github.k1rakishou.chan.ui.helper;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast;

import android.content.Context;

import androidx.annotation.NonNull;
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
        Map<PostDescriptor, ChanPostHide> hiddenPostMap = getHiddenPostsForThread(threadDescriptor);
        List<PostDescriptor> hiddenOrRemovedPosts = getRemovedPosts(hiddenPostMap, threadPosts);

        if (hiddenOrRemovedPosts.isEmpty()) {
            showToast(context, R.string.no_removed_posts_for_current_thread);
            return;
        }

        Collections.sort(hiddenOrRemovedPosts, (o1, o2) -> Long.compare(o1.getPostNo(), o2.getPostNo()));
        present();

        List<HiddenOrRemovedPost> resultPosts = new ArrayList<>();

        chanThreadManager.get().iteratePostsWhile(threadDescriptor, hiddenOrRemovedPosts, chanPost -> {
            ChanPostHide chanPostHide = hiddenPostMap.get(chanPost.getPostDescriptor());
            if (chanPostHide != null) {
                resultPosts.add(new HiddenOrRemovedPost(chanPost, chanPostHide));
            }

            return true;
        });

        if (resultPosts.isEmpty()) {
            return;
        }

        if (controller != null) {
            controller.showRemovePosts(resultPosts);
        }
    }

    @NonNull
    private Map<PostDescriptor, ChanPostHide> getHiddenPostsForThread(ChanDescriptor.ThreadDescriptor threadDescriptor) {
        List<ChanPostHide> chanPostHides = postHideManager.get().getHiddenPostsForThread(threadDescriptor);
        Map<PostDescriptor, ChanPostHide> resultMap = new HashMap<>(chanPostHides.size());

        for (ChanPostHide chanPostHide : chanPostHides) {
            resultMap.put(chanPostHide.getPostDescriptor(), chanPostHide);
        }

        return resultMap;
    }

    private List<PostDescriptor> getRemovedPosts(
            Map<PostDescriptor, ChanPostHide> hiddenPostMap,
            List<PostDescriptor> threadPosts
    ) {
        List<PostDescriptor> removedPosts = new ArrayList<>();

        for (PostDescriptor post : threadPosts) {
            if (hiddenPostMap.containsKey(post)) {
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

    public class HiddenOrRemovedPost {
        public ChanPost chanPost;
        public ChanPostHide chanPostHide;

        public HiddenOrRemovedPost(ChanPost chanPost, ChanPostHide chanPostHide) {
            this.chanPost = chanPost;
            this.chanPostHide = chanPostHide;
        }
    }
}
