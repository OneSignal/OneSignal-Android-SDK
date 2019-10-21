package com.onesignal;

public class CachedUniqueOutcomeNotification {

    private String notificationId;
    private String name;

    public CachedUniqueOutcomeNotification(String notificationId, String name) {
        this.notificationId = notificationId;
        this.name = name;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getName() {
        return name;
    }

}
