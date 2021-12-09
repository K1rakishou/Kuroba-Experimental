package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable;

import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.SerializablePostLinkableType;
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
