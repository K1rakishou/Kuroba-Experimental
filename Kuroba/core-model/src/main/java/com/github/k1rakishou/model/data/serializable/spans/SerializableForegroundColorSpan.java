package com.github.k1rakishou.model.data.serializable.spans;

import com.github.k1rakishou.common.DoNotStrip;
import com.google.gson.annotations.SerializedName;

@DoNotStrip
public class SerializableForegroundColorSpan {
    @SerializedName("foreground_color")
    private int foregroundColor;

    public SerializableForegroundColorSpan(int foregroundColor) {
        this.foregroundColor = foregroundColor;
    }

    public int getForegroundColor() {
        return foregroundColor;
    }
}
