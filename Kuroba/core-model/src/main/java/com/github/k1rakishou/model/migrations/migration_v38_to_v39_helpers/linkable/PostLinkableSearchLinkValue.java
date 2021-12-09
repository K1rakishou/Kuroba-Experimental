package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.linkable;

import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.SerializablePostLinkableType;
import com.google.gson.annotations.SerializedName;

@DoNotStrip
public class PostLinkableSearchLinkValue extends PostLinkableValue {
    @SerializedName("board")
    private String board;
    @SerializedName("search")
    private String search;

    public PostLinkableSearchLinkValue(SerializablePostLinkableType type, String board, String search) {
        super(type);

        this.board = board;
        this.search = search;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public String getBoard() {
        return board;
    }

    public String getSearch() {
        return search;
    }
}
