package com.github.adamantcheese.model.data.serializable.spans.linkable;

import com.github.adamantcheese.model.data.serializable.spans.SerializablePostLinkableSpan;
import com.google.gson.annotations.SerializedName;

public abstract class PostLinkableValue {
    @SerializedName("post_linkable_value_type")
    protected int type;

    public PostLinkableValue(SerializablePostLinkableSpan.PostLinkableType type) {
        this.type = type.getTypeValue();
    }

    public int getType() {
        return type;
    }
}

