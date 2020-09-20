package com.github.adamantcheese.model.data.serializable.spans;

import com.github.adamantcheese.common.DoNotStrip;
import com.google.gson.annotations.SerializedName;

@DoNotStrip
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
