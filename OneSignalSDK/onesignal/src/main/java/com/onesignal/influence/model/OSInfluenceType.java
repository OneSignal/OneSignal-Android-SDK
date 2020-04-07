package com.onesignal.influence.model;

import android.support.annotation.NonNull;

public enum OSInfluenceType {
    DIRECT,
    INDIRECT,
    UNATTRIBUTED,
    DISABLED,
    ;

    public boolean isDirect() {
        return this.equals(DIRECT);
    }

    public boolean isIndirect() {
        return this.equals(INDIRECT);
    }

    public boolean isAttributed() {
        return this.isDirect() || this.isIndirect();
    }

    public boolean isUnattributed() {
        return this.equals(UNATTRIBUTED);
    }

    public boolean isDisabled() {
        return this.equals(DISABLED);
    }

    public static @NonNull
    OSInfluenceType fromString(String value) {
        if (value == null || value.isEmpty())
            return UNATTRIBUTED;

        for (OSInfluenceType type : OSInfluenceType.values()) {
            if (type.name().equalsIgnoreCase(value))
                return type;
        }
        return UNATTRIBUTED;
    }
}
