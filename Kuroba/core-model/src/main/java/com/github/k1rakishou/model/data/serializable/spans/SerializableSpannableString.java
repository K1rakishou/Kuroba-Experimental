package com.github.k1rakishou.model.data.serializable.spans;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SerializableSpannableString {
    @SerializedName("span_info_list")
    private List<SpanInfo> spanInfoList;
    @SerializedName("text")
    private String text;

    public SerializableSpannableString() {
        this.spanInfoList = new ArrayList<>();
        this.text = "";
    }

    public SerializableSpannableString(List<SpanInfo> spanInfoList, String text) {
        this.spanInfoList = spanInfoList;
        this.text = text;
    }

    public void addSpanInfo(SpanInfo spanInfo) {
        spanInfoList.add(spanInfo);
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<SpanInfo> getSpanInfoList() {
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

    public static class SpanInfo {
        @SerializedName("span_start")
        private int spanStart;
        @SerializedName("span_end")
        private int spanEnd;
        @SerializedName("flags")
        private int flags;
        @NonNull
        @SerializedName("span_type")
        private int spanType;
        @SerializedName("span_data")
        @Expose(serialize = true, deserialize = false)
        @Nullable
        private String spanData;

        public SpanInfo(@NonNull SpanType spanType, int spanStart, int spanEnd, int flags) {
            this.spanType = spanType.getSpanTypeValue();
            this.spanStart = spanStart;
            this.spanEnd = spanEnd;
            this.flags = flags;
        }

        public int getSpanStart() {
            return spanStart;
        }

        public int getSpanEnd() {
            return spanEnd;
        }

        public int getFlags() {
            return flags;
        }

        @NonNull
        public int getSpanType() {
            return spanType;
        }

        @Nullable
        public String getSpanData() {
            return spanData;
        }

        public void setSpanData(@Nullable String spanData) {
            this.spanData = spanData;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SpanInfo)) return false;
            SpanInfo spanInfo = (SpanInfo) o;
            return spanStart == spanInfo.spanStart &&
                    spanEnd == spanInfo.spanEnd &&
                    flags == spanInfo.flags &&
                    spanType == spanInfo.spanType &&
                    Objects.equals(spanData, spanInfo.spanData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(spanStart, spanEnd, flags, spanType, spanData);
        }

        @Override
        public String toString() {
            return "SpanInfo{" +
                    "spanStart=" + spanStart +
                    ", spanEnd=" + spanEnd +
                    ", flags=" + flags +
                    ", spanType=" + spanType +
                    ", spanData='" + spanData + '\'' +
                    '}';
        }
    }

    public enum SpanType {
        ForegroundColorSpanHashedType(0),
        BackgroundColorSpanHashedType(1),
        StrikethroughSpanType(2),
        StyleSpanType(3),
        TypefaceSpanType(4),
        AbsoluteSizeSpanHashed(5),
        PostLinkable(6);

        private int spanTypeValue;

        SpanType(int value) {
            this.spanTypeValue = value;
        }

        public int getSpanTypeValue() {
            return spanTypeValue;
        }

        @NonNull
        public static SpanType from(int value) {
            switch (value) {
                case 0:
                    return ForegroundColorSpanHashedType;
                case 1:
                    return BackgroundColorSpanHashedType;
                case 2:
                    return StrikethroughSpanType;
                case 3:
                    return StyleSpanType;
                case 4:
                    return TypefaceSpanType;
                case 5:
                    return AbsoluteSizeSpanHashed;
                case 6:
                    return PostLinkable;
                default:
                    throw new IllegalArgumentException("Not implemented for value = " + value);
            }
        }
    }
}
