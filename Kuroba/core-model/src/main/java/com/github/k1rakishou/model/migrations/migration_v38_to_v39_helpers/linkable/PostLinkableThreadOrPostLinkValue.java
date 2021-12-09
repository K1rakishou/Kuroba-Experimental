package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable;

import android.text.TextUtils;

import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.SerializablePostLinkableType;
import com.google.gson.annotations.SerializedName;

@DoNotStrip
public class PostLinkableThreadOrPostLinkValue
        extends PostLinkableValue {
    @SerializedName("board")
    private String board;
    @SerializedName("thread_id")
    private long threadId;
    @SerializedName("post_id")
    private long postId;

    public PostLinkableThreadOrPostLinkValue(
            SerializablePostLinkableType type,
            String board,
            long threadId,
            long postId
    ) {
        super(type);

        this.board = board;
        this.threadId = threadId;
        this.postId = postId;
    }

    public boolean isValid() {
        return !TextUtils.isEmpty(board) && threadId > 0 && postId > 0;
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
