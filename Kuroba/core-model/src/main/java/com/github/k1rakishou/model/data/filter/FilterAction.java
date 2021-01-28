package com.github.k1rakishou.model.data.filter;

public enum FilterAction {
    HIDE(0),
    COLOR(1),
    REMOVE(2),
    WATCH(3);

    public final int id;

    FilterAction(int id) {
        this.id = id;
    }

    public static FilterAction forId(int id) {
        return enums[id];
    }

    private static FilterAction[] enums = new FilterAction[4];

    static {
        for (FilterAction type : values()) {
            enums[type.id] = type;
        }
    }

}
