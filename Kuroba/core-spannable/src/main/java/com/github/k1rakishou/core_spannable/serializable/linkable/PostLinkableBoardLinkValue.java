package com.github.k1rakishou.core_spannable.serializable.linkable;

import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.core_spannable.serializable.SerializablePostLinkableType;
import com.google.gson.annotations.SerializedName;

@DoNotStrip
public class PostLinkableBoardLinkValue
        extends PostLinkableValue {
    @SerializedName("board_link")
    private String boardLink;

    public PostLinkableBoardLinkValue(SerializablePostLinkableType type, String boardLink) {
        super(type);

        this.boardLink = boardLink;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public String getBoardLink() {
        return boardLink;
    }
}
