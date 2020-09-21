package com.github.k1rakishou.model.data.serializable.spans.linkable;

import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.model.data.serializable.spans.SerializablePostLinkableSpan;
import com.google.gson.annotations.SerializedName;

@DoNotStrip
public class PostLinkableQuoteValue
        extends PostLinkableValue {
    @SerializedName("post_id")
    private long postId;

    public PostLinkableQuoteValue(
            SerializablePostLinkableSpan.PostLinkableType type,
            long postId
    ) {
        super(type);

        this.postId = postId;
    }

    public long getPostId() {
        return postId;
    }
}
