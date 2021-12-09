package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable;

import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.SerializablePostLinkableType;
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
