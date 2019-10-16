package com.onesignal;

public class OneSignalOutcome {

    /**
     * Predefined params available
     */
    public static class Param {

        public static final String LEVEL = "level";
        public static final String DESTINATION = "destination";
        public static final String ORIGIN = "origin";
        public static final String QUANTITY = "quantity";
        public static final String VALUE = "value";

        protected Param() {
        }
    }

    /**
     * Predefined events available
     */
    public static class Event {

        public static final String APP_OPEN = "app_open";
        public static final String LOGIN = "login";
        public static final String SIGN_UP = "sign_up";
        public static final String NOTIFICATION_CLICK = "notification_click";
        public static final String SESSIONS = "sessions";
        public static final String SESSION_DURATION = "session_duration";
        public static final String ADDED_TO_CART = "added_to_cart";
        public static final String ADDED_FRIEND = "added_friend";

        protected Event() {
        }
    }
}
