package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SerializableSpannableString {
    @SerializedName("span_info_list")
    private List<SerializableSpanInfo> spanInfoList;
    @SerializedName("text")
    private String text;

    public SerializableSpannableString() {
        this.spanInfoList = new ArrayList<>();
        this.text = "";
    }

    public SerializableSpannableString(List<SerializableSpanInfo> spanInfoList, String text) {
        this.spanInfoList = spanInfoList;
        this.text = text;
    }

    public void addSpanInfo(SerializableSpanInfo spanInfo) {
        spanInfoList.add(spanInfo);
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<SerializableSpanInfo> getSpanInfoList() {
        return spanInfoList;
    }

    public String getText() {
        return text;
    }

    public boolean isEmpty() {
        return spanInfoList.isEmpty() && text.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SerializableSpannableString)) return false;
        SerializableSpannableString that = (SerializableSpannableString) o;
        return Objects.equals(spanInfoList, that.spanInfoList) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spanInfoList, text);
    }

    @Override
    public String toString() {
        return "SerializableSpannableString{" +
                "spanInfoList=" + spanInfoList +
                ", text='" + text + '\'' +
                '}';
    }

}
