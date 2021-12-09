package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable;

import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.SerializablePostLinkableType;
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

