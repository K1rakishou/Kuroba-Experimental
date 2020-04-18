package com.github.adamantcheese.model.data.serializable.spans;

import com.google.gson.annotations.SerializedName;

public class SerializableBackgroundColorSpan {
    @SerializedName("background_color")
    private int backgroundColor;

    public SerializableBackgroundColorSpan(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }
}
