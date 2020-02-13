package com.onesignal;

class OSUnprocessedOpenedNotification {

    OneSignal.NotificationHandler notifHandlerType;
    OSNotificationOpenedResult notifOpenedResult;

    OSUnprocessedOpenedNotification(OneSignal.NotificationHandler handlerType, OSNotificationOpenedResult openedResult) {
        this.notifHandlerType = handlerType;
        this.notifOpenedResult = openedResult;
    }
}
