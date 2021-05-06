package com.github.k1rakishou.chan.core.lib.data.post_parsing;

import java.util.Arrays;

public class ThreadParsed {
    public PostParsed[] postParsedList;

    @Override
    public String toString() {
        return "ThreadParsed{" +
                "postParsedList=" + Arrays.toString(postParsedList) +
                '}';
    }
}
