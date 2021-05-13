package com.github.k1rakishou.chan.core.lib.data.post_parsing;

/**
 * When updating/renaming or moving this class to other place, don't forget to update the
 * kuroba_ex_native library.
 * */
public class PostToParse {
    public final String siteName;
    public final String boardCode;
    public final long threadId;
    public final long postId;
    public final long postSubId;
    public final String comment;

    public PostToParse(String siteName, String boardCode, long threadId, long postId, long postSubId, String comment) {
        this.siteName = siteName;
        this.boardCode = boardCode;
        this.threadId = threadId;
        this.postId = postId;
        this.postSubId = postSubId;
        this.comment = comment;
    }

    @Override
    public String toString() {
        return "PostToParse{" +
                "siteName='" + siteName + '\'' +
                ", boardCode='" + boardCode + '\'' +
                ", threadId=" + threadId +
                ", postId=" + postId +
                ", postSubId=" + postSubId +
                ", comment='" + comment + '\'' +
                '}';
    }
}
