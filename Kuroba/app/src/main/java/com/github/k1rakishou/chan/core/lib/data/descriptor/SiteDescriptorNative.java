package com.github.k1rakishou.chan.core.lib.data.descriptor;

import java.util.Objects;

public class SiteDescriptorNative {
    public final String siteName;

    public SiteDescriptorNative(String siteName) {
        this.siteName = siteName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SiteDescriptorNative that = (SiteDescriptorNative) o;

        return Objects.equals(siteName, that.siteName);
    }

    @Override
    public int hashCode() {
        return siteName != null ? siteName.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "SiteDescriptorNative{" +
                "siteName='" + siteName + '\'' +
                '}';
    }
}
