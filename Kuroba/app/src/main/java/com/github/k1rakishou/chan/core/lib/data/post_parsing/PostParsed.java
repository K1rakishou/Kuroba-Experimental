package com.github.k1rakishou.chan.core.lib.data.post_parsing;

import com.github.k1rakishou.chan.core.lib.data.descriptor.PostDescriptorNative;

public class PostParsed {
    public PostDescriptorNative postDescriptor;
    public ParsedSpannableText postCommentParsed;

    @Override
    public String toString() {
        return "PostParsed{" +
                "postDescriptor=" + postDescriptor +
                ", postCommentParsed=" + postCommentParsed +
                '}';
    }
}
