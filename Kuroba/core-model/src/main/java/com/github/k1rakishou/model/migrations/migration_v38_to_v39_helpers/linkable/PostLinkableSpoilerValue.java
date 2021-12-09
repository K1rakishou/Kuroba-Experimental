package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable;


import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.SerializablePostLinkableType;

public class PostLinkableSpoilerValue
        extends PostLinkableValue {

    public PostLinkableSpoilerValue(SerializablePostLinkableType type) {
        super(type);
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
