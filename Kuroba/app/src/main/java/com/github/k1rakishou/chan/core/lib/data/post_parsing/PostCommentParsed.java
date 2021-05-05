package com.github.k1rakishou.chan.core.lib.data.post_parsing;

import com.github.k1rakishou.chan.core.lib.data.post_parsing.spannable.PostCommentSpannable;

import java.util.Arrays;

public class PostCommentParsed {
    public String commentTextRaw;
    public String commentTextParsed;
    public PostCommentSpannable[] spannableList;

    @Override
    public String toString() {
        return "PostCommentParsed{" +
                "commentTextRaw='" + commentTextRaw + '\'' +
                ", commentTextParsed='" + commentTextParsed + '\'' +
                ", spannableList=" + Arrays.toString(spannableList) +
                '}';
    }
}
