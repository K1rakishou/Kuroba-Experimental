package com.github.k1rakishou.core_spannable.serializable.linkable;

import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.core_spannable.serializable.SerializablePostLinkableType;
import com.google.gson.annotations.SerializedName;

@DoNotStrip
public class PostLinkableQuoteValue
        extends PostLinkableValue {
    @SerializedName("post_id")
    private long postId;

    public PostLinkableQuoteValue(
            SerializablePostLinkableType type,
            long postId
    ) {
        super(type);

        this.postId = postId;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public long getPostId() {
        return postId;
    }
}
