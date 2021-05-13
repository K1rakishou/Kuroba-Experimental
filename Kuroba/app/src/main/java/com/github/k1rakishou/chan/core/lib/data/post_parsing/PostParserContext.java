package com.github.k1rakishou.chan.core.lib.data.post_parsing;

import java.util.Arrays;

/**
 * When updating/renaming or moving this class to other place, don't forget to update the
 * kuroba_ex_native library.
 */
public class PostParserContext {
    public final long[] myRepliesInThread;
    public final long[] threadPosts;

    public PostParserContext(long[] myRepliesInThread, long[] threadPosts) {
        this.myRepliesInThread = myRepliesInThread;
        this.threadPosts = threadPosts;
    }

    @Override
    public String toString() {
        return "PostParserContext{" +
                "myRepliesInThread=" + Arrays.toString(myRepliesInThread) +
                ", threadPosts=" + Arrays.toString(threadPosts) +
                '}';
    }
}
