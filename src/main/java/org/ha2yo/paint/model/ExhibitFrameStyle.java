package org.ha2yo.paint.model;

import java.util.Locale;

public enum ExhibitFrameStyle {
    NONE("테두리 없음"),
    FRAME("프레임");

    private final String displayName;

    ExhibitFrameStyle(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public ExhibitFrameStyle next() {
        ExhibitFrameStyle[] styles = values();
        return styles[(ordinal() + 1) % styles.length];
    }

    public static ExhibitFrameStyle fromName(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        if ("WOOD".equals(normalized) || "IRON".equals(normalized)) {
            return FRAME;
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
