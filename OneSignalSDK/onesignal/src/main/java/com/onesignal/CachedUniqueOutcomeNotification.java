package com.onesignal;

class CachedUniqueOutcomeNotification {

    private String notificationId;
    private String name;

    CachedUniqueOutcomeNotification(String notificationId, String name) {
        this.notificationId = notificationId;
        this.name = name;
    }

    String getNotificationId() {
        return notificationId;
    }

    String getName() {
        return name;
    }

}
