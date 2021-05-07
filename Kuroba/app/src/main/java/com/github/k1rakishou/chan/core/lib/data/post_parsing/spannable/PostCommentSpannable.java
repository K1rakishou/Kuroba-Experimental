package com.github.k1rakishou.chan.core.lib.data.post_parsing.spannable;

public class PostCommentSpannable {
    public int start;
    public int length;
    public IPostCommentSpannableData spannableData;

    @Override
    public String toString() {
        return "PostCommentSpannable{" +
                "start=" + start +
                ", length=" + length +
                ", spannableData=" + spannableData +
                '}';
    }
}
