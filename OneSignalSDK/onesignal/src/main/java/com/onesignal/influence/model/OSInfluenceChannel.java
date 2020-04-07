package com.onesignal.influence.model;

import android.support.annotation.NonNull;

public enum OSInfluenceChannel {
    IAM("iam"),
    NOTIFICATION("notification"),
    ;

    private final String name;

    OSInfluenceChannel(String name) {
        this.name = name;
    }

    public boolean equalsName(String otherName) {
        return name.equals(otherName);
    }

    public String toString() {
        return this.name;
    }

    public static @NonNull
    OSInfluenceChannel fromString(String value) {
        if (value == null || value.isEmpty())
            return NOTIFICATION;

        for (OSInfluenceChannel type : OSInfluenceChannel.values()) {
            if (type.equalsName(value))
                return type;
        }
        return NOTIFICATION;
    }
}