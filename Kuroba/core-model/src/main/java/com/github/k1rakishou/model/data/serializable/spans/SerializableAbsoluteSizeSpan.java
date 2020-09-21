package com.github.k1rakishou.model.data.serializable.spans;

import com.github.k1rakishou.common.DoNotStrip;
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
