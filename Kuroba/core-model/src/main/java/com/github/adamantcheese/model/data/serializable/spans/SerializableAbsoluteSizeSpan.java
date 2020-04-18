package com.github.adamantcheese.model.data.serializable.spans;

import com.google.gson.annotations.SerializedName;

public class SerializableAbsoluteSizeSpan {
    @SerializedName("size")
    private int size;

    public SerializableAbsoluteSizeSpan(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }
}
