package com.github.k1rakishou.chan.core.lib.data.descriptor;

import com.github.k1rakishou.model.data.descriptor.PostDescriptor;

public class PostDescriptorNative {
    public final ThreadDescriptorNative threadDescriptor;
    public final long postNo;
    public final long postSubNo;

    public PostDescriptorNative(ThreadDescriptorNative threadDescriptor, long postNo, long postSubNo) {
        this.threadDescriptor = threadDescriptor;
        this.postNo = postNo;
        this.postSubNo = postSubNo;
    }

    public PostDescriptor toPostDescriptor() {
        return PostDescriptor.create(
                threadDescriptor.boardDescriptor.siteDescriptor.siteName,
                threadDescriptor.boardDescriptor.boardCode,
                threadDescriptor.threadNo,
                postNo,
                postSubNo
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PostDescriptorNative that = (PostDescriptorNative) o;

        if (postNo != that.postNo) return false;
        if (postSubNo != that.postSubNo) return false;
        return threadDescriptor.equals(that.threadDescriptor);
    }

    @Override
    public int hashCode() {
        int result = threadDescriptor.hashCode();
        result = 31 * result + (int) (postNo ^ (postNo >>> 32));
        result = 31 * result + (int) (postSubNo ^ (postSubNo >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "PostDescriptorNative{" +
                "threadDescriptor=" + threadDescriptor +
                ", postNo=" + postNo +
                ", postSubNo=" + postSubNo +
                '}';
    }
}
