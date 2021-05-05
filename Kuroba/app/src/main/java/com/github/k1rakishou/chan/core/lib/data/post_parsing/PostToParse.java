package com.github.k1rakishou.chan.core.lib.data.post_parsing;

/**
 * When updating/renaming or moving this class to other place, don't forget to update the
 * kuroba_ex_native library.
 * */
public class PostToParse {
    public long postId;
    public String comment;

    public PostToParse(long postId, String comment) {
        this.postId = postId;
        this.comment = comment;
    }
}
