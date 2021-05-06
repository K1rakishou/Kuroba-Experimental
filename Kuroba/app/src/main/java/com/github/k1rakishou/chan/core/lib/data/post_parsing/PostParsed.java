package com.github.k1rakishou.chan.core.lib.data.post_parsing;

public class PostParsed {
    public long postId;
    public long postSubId;
    public ParsedSpannableText postCommentParsed;

    @Override
    public String toString() {
        return "PostParsed{" +
                "postId=" + postId +
                ", postSubId=" + postSubId +
                ", postCommentParsed=" + postCommentParsed +
                '}';
    }
}
