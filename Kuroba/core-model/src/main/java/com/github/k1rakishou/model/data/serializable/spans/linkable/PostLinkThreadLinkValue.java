package com.github.k1rakishou.model.data.serializable.spans.linkable;

import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.model.data.serializable.spans.SerializablePostLinkableSpan;
import com.google.gson.annotations.SerializedName;

@DoNotStrip
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
