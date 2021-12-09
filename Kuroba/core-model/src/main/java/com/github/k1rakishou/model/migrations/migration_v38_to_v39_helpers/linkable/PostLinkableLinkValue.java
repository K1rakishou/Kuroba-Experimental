package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable;

import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.SerializablePostLinkableType;
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
