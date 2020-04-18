package com.github.adamantcheese.model.data.serializable.spans;

import com.google.gson.annotations.SerializedName;

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
