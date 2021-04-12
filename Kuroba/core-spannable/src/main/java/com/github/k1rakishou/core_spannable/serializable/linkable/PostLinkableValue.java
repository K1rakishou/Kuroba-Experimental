package com.github.k1rakishou.core_spannable.serializable.linkable;

import com.github.k1rakishou.core_spannable.serializable.SerializablePostLinkableType;
import com.google.gson.annotations.SerializedName;

public abstract class PostLinkableValue {
    @SerializedName("post_linkable_value_type")
    protected int type;

    public PostLinkableValue(SerializablePostLinkableType type) {
        this.type = type.getTypeValue();
    }

    public abstract boolean isValid();

    public int getType() {
        return type;
    }
}

