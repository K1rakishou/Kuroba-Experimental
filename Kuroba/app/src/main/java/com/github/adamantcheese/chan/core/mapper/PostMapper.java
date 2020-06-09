package com.github.adamantcheese.chan.core.mapper;

import com.github.adamantcheese.chan.core.manager.PostFilterManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostFilter;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.model.data.serializable.SerializablePost;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class PostMapper {
    private static final String TAG = "PostMapper";

    public static SerializablePost toSerializablePost(
            PostFilterManager postFilterManager,
            Gson gson,
            Post post
    ) {
        return new SerializablePost(
                post.boardId,
                BoardMapper.toSerializableBoard(post.board),
                post.no,
                post.isOP,
                post.name,
                SpannableStringMapper.serializeSpannableString(gson, post.getComment()),
                SpannableStringMapper.serializeSpannableString(gson, post.subject),
                post.time,
                PostImageMapper.toSerializablePostImageList(post.getPostImages()),
                SpannableStringMapper.serializeSpannableString(gson, post.tripcode),
                post.posterId,
                post.opNo,
                post.capcode,
                post.isSavedReply,
                postFilterManager.getFilterHighlightedColor(post.getPostDescriptor()),
                postFilterManager.getFilterStub(post.getPostDescriptor()),
                postFilterManager.getFilterRemove(post.getPostDescriptor()),
                postFilterManager.getFilterWatch(post.getPostDescriptor()),
                postFilterManager.getFilterReplies(post.getPostDescriptor()),
                postFilterManager.getFilterOnlyOP(post.getPostDescriptor()),
                postFilterManager.getFilterSaved(post.getPostDescriptor()),
                post.getRepliesTo(),
                post.deleted.get(),
                post.getRepliesFrom(),
                post.isSticky(),
                post.isClosed(),
                post.isArchived(),
                post.getTotalRepliesCount(),
                post.getThreadImagesCount(),
                post.getUniqueIps(),
                post.getLastModified(),
                post.getTitle()
        );
    }

    public static List<SerializablePost> toSerializablePostList(
            PostFilterManager postFilterManager,
            Gson gson,
            List<Post> postList
    ) {
        List<SerializablePost> serializablePostList = new ArrayList<>(postList.size());

        for (Post post : postList) {
            SerializablePost serializablePost = toSerializablePost(postFilterManager, gson, post);

            serializablePostList.add(serializablePost);
        }

        return serializablePostList;
    }

    public static Post fromSerializedPost(
            Gson gson,
            Loadable loadable,
            PostFilterManager postFilterManager,
            SerializablePost serializablePost,
            Theme currentTheme
    ) {
        CharSequence subject = SpannableStringMapper.deserializeSpannableString(
                gson,
                serializablePost.getSubject(),
                currentTheme
        );
        CharSequence tripcode = SpannableStringMapper.deserializeSpannableString(
                gson,
                serializablePost.getTripcode(),
                currentTheme
        );

        Post.Builder postBuilder = new Post.Builder().board(loadable.board)
                .id(serializablePost.getNo())
                .op(serializablePost.isOP())
                .name(serializablePost.getName())
                .comment(
                        SpannableStringMapper.deserializeSpannableString(
                                gson,
                                serializablePost.getComment(),
                                currentTheme
                        )
                )
                .subject(subject)
                .tripcode(tripcode)
                .setUnixTimestampSeconds(serializablePost.getTime())
                .postImages(
                        PostImageMapper.fromSerializablePostImageList(serializablePost.getImages())
                )
                .opId(serializablePost.getOpId())
                .moderatorCapcode(serializablePost.getCapcode())
                .isSavedReply(serializablePost.isSavedReply())
                .repliesTo(serializablePost.getRepliesTo())
                .sticky(serializablePost.isSticky())
                .archived(serializablePost.isArchived())
                .replies(serializablePost.getReplies())
                .threadImagesCount(serializablePost.getThreadImagesCount())
                .uniqueIps(serializablePost.getUniqueIps())
                .lastModified(serializablePost.getLastModified());

        Post post = postBuilder.build();
        post.setTitle(serializablePost.getTitle());
        post.setRepliesFrom(serializablePost.getRepliesFrom());

        PostFilter postFilter = new PostFilter(
                serializablePost.getFilterHighlightedColor(),
                serializablePost.isFilterStub(),
                serializablePost.isFilterRemove(),
                serializablePost.isFilterWatch(),
                serializablePost.isFilterReplies(),
                serializablePost.isFilterOnlyOP(),
                serializablePost.isFilterSaved()
        );

        postFilterManager.insert(post.getPostDescriptor(), postFilter);

        return post;
    }

    public static List<Post> fromSerializedPostList(
            Gson gson,
            Loadable loadable,
            PostFilterManager postFilterManager,
            List<SerializablePost> serializablePostList,
            Theme currentTheme
    ) {
        List<Post> posts = new ArrayList<>(serializablePostList.size());
        Throwable firstException = null;

        for (SerializablePost serializablePost : serializablePostList) {
            try {
                Post post = fromSerializedPost(
                        gson,
                        loadable,
                        postFilterManager,
                        serializablePost,
                        currentTheme
                );

                posts.add(post);
            } catch (Throwable error) {
                // Skip post if could not deserialize
                if (firstException == null) {
                    // We will report only the first exception because there may be a lot of them
                    // and they may all be the same
                    firstException = error;
                }
            }
        }

        if (firstException != null) {
            Logger.e(TAG, "There were at least one exception thrown while " +
                    "trying to deserialize posts", firstException);
        }

        return posts;
    }
}
