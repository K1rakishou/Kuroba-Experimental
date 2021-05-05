package com.github.k1rakishou.chan.core.lib.data.post_parsing;

/**
 * When updating/renaming or moving this class to other place, don't forget to update the
 * kuroba_ex_native library.
 * */
public class ThreadToParse {
    public PostToParse[] postToParseList;

    public ThreadToParse(PostToParse[] postToParseList) {
        this.postToParseList = postToParseList;
    }
}
