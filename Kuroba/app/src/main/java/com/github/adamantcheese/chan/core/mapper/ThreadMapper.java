package com.github.adamantcheese.chan.core.mapper;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.manager.PostPreloadedInfoHolder;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.SerializableThread;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.Logger;
import com.google.gson.Gson;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ThreadMapper {
    private static final String TAG = "ThreadMapper";
    private static final Comparator<Post> POST_COMPARATOR = (p1, p2) -> Long.compare(p1.no, p2.no);

    public static SerializableThread toSerializableThread(Gson gson, List<Post> posts) {
        return new SerializableThread(PostMapper.toSerializablePostList(gson, posts));
    }

    @Nullable
    public static ChanThread fromSerializedThread(
            Gson gson,
            Loadable loadable,
            SerializableThread serializableThread,
            Theme currentTheme
    ) {
        List<Post> posts = PostMapper.fromSerializedPostList(
                gson,
                loadable,
                serializableThread.getPostList(),
                currentTheme
        );

        if (posts.isEmpty()) {
            Logger.w(TAG, "PostMapper.fromSerializedPostList returned empty list");
            return null;
        }

        Collections.sort(posts, POST_COMPARATOR);

        PostPreloadedInfoHolder postPreloadedInfoHolder = new PostPreloadedInfoHolder();
        postPreloadedInfoHolder.preloadPostsInfo(posts);

        ChanThread chanThread = new ChanThread(loadable, posts);
        chanThread.setPostPreloadedInfoHolder(postPreloadedInfoHolder);
        chanThread.setArchived(true);

        return chanThread;
    }
}
