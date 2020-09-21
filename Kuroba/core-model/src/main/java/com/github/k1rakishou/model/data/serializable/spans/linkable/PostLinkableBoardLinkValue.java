package com.github.k1rakishou.model.data.serializable.spans.linkable;

import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.model.data.serializable.spans.SerializablePostLinkableSpan;
import com.google.gson.annotations.SerializedName;

@DoNotStrip
public class PostLinkableBoardLinkValue
        extends PostLinkableValue {
    @SerializedName("board_link")
    private String boardLink;

    public PostLinkableBoardLinkValue(SerializablePostLinkableSpan.PostLinkableType type, String boardLink) {
        super(type);

        this.boardLink = boardLink;
    }

    public String getBoardLink() {
        return boardLink;
    }
}
