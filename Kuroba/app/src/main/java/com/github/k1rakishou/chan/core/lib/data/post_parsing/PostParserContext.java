package com.github.k1rakishou.chan.core.lib.data.post_parsing;

/**
 * When updating/renaming or moving this class to other place, don't forget to update the
 * kuroba_ex_native library.
 * */
 public class PostParserContext {
    public long threadId;
    public long[] myRepliesInThread;
    public long[] threadPosts;

    public PostParserContext(long threadId, long[] myRepliesInThread, long[] threadPosts) {
        this.threadId = threadId;
        this.myRepliesInThread = myRepliesInThread;
        this.threadPosts = threadPosts;
    }
}
