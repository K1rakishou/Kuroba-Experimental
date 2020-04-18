package com.github.adamantcheese.model.data.serializable.spans.linkable;

import com.github.adamantcheese.model.data.serializable.spans.SerializablePostLinkableSpan;
import com.google.gson.annotations.SerializedName;

public class PostLinkableQuoteValue
        extends PostLinkableValue {
    @SerializedName("post_id")
    private int postId;

    public PostLinkableQuoteValue(SerializablePostLinkableSpan.PostLinkableType type, int postId) {
        super(type);

        this.postId = postId;
    }

    public int getPostId() {
        return postId;
    }
}
