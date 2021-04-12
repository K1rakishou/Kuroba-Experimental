package com.github.k1rakishou.core_spannable.serializable.linkable;


import com.github.k1rakishou.core_spannable.serializable.SerializablePostLinkableType;

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
