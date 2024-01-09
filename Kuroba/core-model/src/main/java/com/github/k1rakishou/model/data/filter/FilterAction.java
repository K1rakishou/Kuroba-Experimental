package com.github.k1rakishou.model.data.filter;

public enum FilterAction {
    HIDE(0),
    COLOR(1),
    REMOVE(2),
    WATCH(3),
    AVOID_WATCH(4);

    public final int id;

    FilterAction(int id) {
        this.id = id;
    }

    public static FilterAction forId(int id) {
        return enums[id];
    }

    private static FilterAction[] enums = new FilterAction[FilterAction.values().length];

    public static String filterActionName(FilterAction action) {
        switch (action) {
            case HIDE:
                return "Hide post";
            case COLOR:
                return "Highlight post";
            case REMOVE:
                return "Remove post";
            case WATCH:
                return "Watch post";
            case AVOID_WATCH:
                return "Avoid watching post";
        }

        return "Unknown action (" + action.id + ")";
    }

    static {
        for (FilterAction type : values()) {
            enums[type.id] = type;
        }
    }

}
