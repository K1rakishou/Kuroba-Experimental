package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers;

import com.github.k1rakishou.common.DoNotStrip;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@DoNotStrip
public class SerializablePostLinkableSpan {
    @SerializedName("key")
    private String key;
    @SerializedName("type")
    private int postLinkableType;
    @SerializedName("post_linkable_value_json")
    @Expose(serialize = true, deserialize = false)
    private String postLinkableValueJson;

    public SerializablePostLinkableSpan(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public SerializablePostLinkableType getPostLinkableType() {
        return SerializablePostLinkableType.from(postLinkableType);
    }

    public void setPostLinkableType(int postLinkableType) {
        this.postLinkableType = postLinkableType;
    }

    public String getPostLinkableValueJson() {
        return postLinkableValueJson;
    }

    public void setPostLinkableValueJson(String postLinkableValueJson) {
        this.postLinkableValueJson = postLinkableValueJson;
    }

}
