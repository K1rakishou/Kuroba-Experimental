package com.github.k1rakishou.chan.core.lib.data.descriptor;

public class BoardDescriptorNative {
    public final SiteDescriptorNative siteDescriptor;
    public final String boardCode;

    public BoardDescriptorNative(SiteDescriptorNative siteDescriptor, String boardCode) {
        this.siteDescriptor = siteDescriptor;
        this.boardCode = boardCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BoardDescriptorNative that = (BoardDescriptorNative) o;

        if (!siteDescriptor.equals(that.siteDescriptor)) return false;
        return boardCode.equals(that.boardCode);
    }

    @Override
    public int hashCode() {
        int result = siteDescriptor.hashCode();
        result = 31 * result + boardCode.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "BoardDescriptorNative{" +
                "siteDescriptor=" + siteDescriptor +
                ", boardCode='" + boardCode + '\'' +
                '}';
    }
}
