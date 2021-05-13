package com.github.k1rakishou.chan.core.lib.data.descriptor;


public class ThreadDescriptorNative {
    public final BoardDescriptorNative boardDescriptor;
    public final long threadNo;

    public ThreadDescriptorNative(BoardDescriptorNative boardDescriptor, long threadNo) {
        this.boardDescriptor = boardDescriptor;
        this.threadNo = threadNo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ThreadDescriptorNative that = (ThreadDescriptorNative) o;

        if (threadNo != that.threadNo) return false;
        return boardDescriptor.equals(that.boardDescriptor);
    }

    @Override
    public int hashCode() {
        int result = boardDescriptor.hashCode();
        result = 31 * result + (int) (threadNo ^ (threadNo >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "ThreadDescriptorNative{" +
                "boardDescriptor=" + boardDescriptor +
                ", threadNo=" + threadNo +
                '}';
    }
}
