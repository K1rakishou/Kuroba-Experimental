package com.github.adamantcheese.model.data.serializable.spans.linkable;

import com.github.adamantcheese.model.data.serializable.spans.SerializablePostLinkableSpan;
import com.google.gson.annotations.SerializedName;

public class PostLinkThreadLinkValue
        extends PostLinkableValue {
    @SerializedName("board")
    private String board;
    @SerializedName("thread_id")
    private long threadId;
    @SerializedName("post_id")
    private long postId;

    public PostLinkThreadLinkValue(SerializablePostLinkableSpan.PostLinkableType type, String board, long threadId, long postId) {
        super(type);

        this.board = board;
        this.threadId = threadId;
        this.postId = postId;
    }

    public String getBoard() {
        return board;
    }

    public long getThreadId() {
        return threadId;
    }

    public long getPostId() {
        return postId;
    }
}
