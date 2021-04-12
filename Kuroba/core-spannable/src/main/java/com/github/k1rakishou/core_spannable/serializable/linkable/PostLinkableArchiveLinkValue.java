package com.github.k1rakishou.core_spannable.serializable.linkable;

import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.core_spannable.serializable.SerializablePostLinkableType;
import com.google.gson.annotations.SerializedName;

import org.jetbrains.annotations.Nullable;

@DoNotStrip
public class PostLinkableArchiveLinkValue extends PostLinkableValue {
    @SerializedName("archive_domain")
    String archiveDomain;
    @SerializedName("board_code")
    String boardCode;
    @SerializedName("thread_no")
    long threadNo;
    @SerializedName("post_no")
    Long postNo;

    public PostLinkableArchiveLinkValue(
            SerializablePostLinkableType type,
            String archiveDomain,
            String boardCode,
            long threadNo,
            @Nullable Long postNo
    ) {
        super(type);

        this.archiveDomain = archiveDomain;
        this.boardCode = boardCode;
        this.threadNo = threadNo;
        this.postNo = postNo;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public String getArchiveDomain() {
        return archiveDomain;
    }

    public String getBoardCode() {
        return boardCode;
    }

    public long getThreadNo() {
        return threadNo;
    }

    public Long getPostNo() {
        return postNo;
    }
}
