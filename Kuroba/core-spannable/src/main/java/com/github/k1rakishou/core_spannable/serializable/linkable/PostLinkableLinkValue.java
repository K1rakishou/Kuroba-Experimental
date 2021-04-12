package com.github.k1rakishou.core_spannable.serializable.linkable;

import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.core_spannable.serializable.SerializablePostLinkableType;
import com.google.gson.annotations.SerializedName;

@DoNotStrip
public class PostLinkableLinkValue extends PostLinkableValue {
    @SerializedName("link")
    private String link;

    public PostLinkableLinkValue(SerializablePostLinkableType type, String link) {
        super(type);

        this.link = link;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public String getLink() {
        return link;
    }
}
