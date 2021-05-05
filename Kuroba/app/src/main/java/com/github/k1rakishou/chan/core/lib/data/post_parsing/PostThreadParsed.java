package com.github.k1rakishou.chan.core.lib.data.post_parsing;

import java.util.Arrays;

public class PostThreadParsed {
    public PostCommentParsed[] postCommentsParsedList;

    @Override
    public String toString() {
        return "PostThreadParsed{" +
                "postCommentsParsedList=" + Arrays.toString(postCommentsParsedList) +
                '}';
    }
}
