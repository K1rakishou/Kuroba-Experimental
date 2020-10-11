package com.github.k1rakishou.model.data.serializable.spans.linkable;

import androidx.annotation.Nullable;

import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.model.data.serializable.spans.SerializablePostLinkableSpan;
import com.google.gson.annotations.SerializedName;

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
            SerializablePostLinkableSpan.PostLinkableType type,
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
