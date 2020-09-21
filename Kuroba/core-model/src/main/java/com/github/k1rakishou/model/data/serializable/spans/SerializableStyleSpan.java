package com.github.k1rakishou.model.data.serializable.spans;

import com.github.k1rakishou.common.DoNotStrip;
import com.google.gson.annotations.SerializedName;

@DoNotStrip
public class SerializableStyleSpan {
    @SerializedName("style")
    private int style;

    public SerializableStyleSpan(int style) {
        this.style = style;
    }

    public int getStyle() {
        return style;
    }
}
