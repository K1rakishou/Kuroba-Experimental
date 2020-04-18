package com.github.adamantcheese.chan.core.mapper;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.model.data.serializable.SerializablePost;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class PostMapper {
    private static final String TAG = "PostMapper";

    public static SerializablePost toSerializablePost(Gson gson, Post post) {
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
                post.id,
                post.opId,
                post.capcode,
                post.isSavedReply,
                post.getPostFilter().getFilterHighlightedColor(),
                post.getPostFilter().getFilterStub(),
                post.getPostFilter().getFilterRemove(),
                post.getPostFilter().getFilterWatch(),
                post.getPostFilter().getFilterReplies(),
                post.getPostFilter().getFilterOnlyOP(),
                post.getPostFilter().getFilterSaved(),
                post.getRepliesTo(),
                post.deleted.get(),
                post.getRepliesFrom(),
                post.isSticky(),
                post.isClosed(),
                post.isArchived(),
                post.getReplies(),
                post.getThreadImagesCount(),
                post.getUniqueIps(),
                post.getLastModified(),
                post.getTitle()
        );
    }

    public static List<SerializablePost> toSerializablePostList(Gson gson, List<Post> postList) {
        List<SerializablePost> serializablePostList = new ArrayList<>(postList.size());

        for (Post post : postList) {
            serializablePostList.add(toSerializablePost(gson, post));
        }

        return serializablePostList;
    }

    public static Post fromSerializedPost(
            Gson gson,
            Loadable loadable,
            SerializablePost serializablePost
    ) {
        CharSequence subject = SpannableStringMapper.deserializeSpannableString(
                gson,
                serializablePost.getSubject()
        );
        CharSequence tripcode = SpannableStringMapper.deserializeSpannableString(
                gson,
                serializablePost.getTripcode()
        );

        Post.Builder postBuilder = new Post.Builder().board(loadable.board)
                .id(serializablePost.getNo())
                .op(serializablePost.isOP())
                .name(serializablePost.getName())
                .comment(
                        SpannableStringMapper.deserializeSpannableString(
                                gson,
                                serializablePost.getComment())
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
                .filter(
                        serializablePost.getFilterHighlightedColor(),
                        serializablePost.isFilterStub(),
                        serializablePost.isFilterRemove(),
                        serializablePost.isFilterWatch(),
                        serializablePost.isFilterReplies(),
                        serializablePost.isFilterOnlyOP(),
                        serializablePost.isFilterSaved()
                )
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

        return post;
    }

    public static List<Post> fromSerializedPostList(
            Gson gson,
            Loadable loadable,
            List<SerializablePost> serializablePostList
    ) {
        List<Post> posts = new ArrayList<>(serializablePostList.size());
        Throwable firstException = null;

        for (SerializablePost serializablePost : serializablePostList) {
            try {
                posts.add(fromSerializedPost(gson, loadable, serializablePost));
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
