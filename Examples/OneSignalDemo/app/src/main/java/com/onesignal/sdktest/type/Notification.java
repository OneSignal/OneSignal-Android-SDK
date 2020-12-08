package com.onesignal.sdktest.type;

import com.onesignal.sdktest.notification.NotificationData;

public enum Notification {

    GENERAL("General",
            NotificationData.GENERAL_DATA,
            "ic_bell_white_24dp",
            "https://firebasestorage.googleapis.com/v0/b/onesignaltest-e7802.appspot.com/o/NOTIFICATION_ICON%2Fbell.png?alt=media&token=73c2bdd9-355f-42bb-80d7-aead737a9dbc",
            "[]",
            true,
            0),

    GREETING("Greetings",
            NotificationData.GREETING_DATA,
            "ic_human_greeting_white_24dp",
            "https://firebasestorage.googleapis.com/v0/b/onesignaltest-e7802.appspot.com/o/NOTIFICATION_ICON%2Fhuman-greeting.png?alt=media&token=178bd69d-634e-40b3-ac32-b56c88e6cd6a",
            "[]",
            true,
            0),

    PROMOTIONS("Promotions",
            NotificationData.PROMOTION_DATA,
            "ic_brightness_percent_white_24dp",
            "https://firebasestorage.googleapis.com/v0/b/onesignaltest-e7802.appspot.com/o/NOTIFICATION_ICON%2Fbrightness-percent.png?alt=media&token=6a8b4348-ad51-4e3a-97d0-4deb46b1576e",
            "[]",
            true,
            0),

    BREAKING_NEWS("Breaking News",
            NotificationData.BREAKING_NEWS_DATA,
            "ic_newspaper_white_24dp",
            "https://firebasestorage.googleapis.com/v0/b/onesignaltest-e7802.appspot.com/o/NOTIFICATION_ICON%2Fnewspaper.png?alt=media&token=053e419b-14f1-4f0d-a439-bb5b46d1b917",
            "[{'id': 'id1', 'text': 'view', 'icon': ''}, {'id': 'id2', 'text': 'save', 'icon': ''}, {'id': 'id3', 'text': 'share', 'icon': ''}]",
            true,
            0),

    ABANDONED_CART("Abandoned Cart",
            NotificationData.ABANDONED_CART_DATA,
            "ic_cart_white_24dp",
            "https://firebasestorage.googleapis.com/v0/b/onesignaltest-e7802.appspot.com/o/NOTIFICATION_ICON%2Fcart.png?alt=media&token=cf7f4d13-6aa2-4824-9b2f-42e5f33f545b",
            "[]",
            true,
            0),

    NEW_POST("New Post",
            NotificationData.NEW_POST_DATA,
            "ic_image_white_24dp",
            "https://firebasestorage.googleapis.com/v0/b/onesignaltest-e7802.appspot.com/o/NOTIFICATION_ICON%2Fimage.png?alt=media&token=6fb66f31-23de-4c76-a2ff-da40d46ebf15",
            "[]",
            true,
            0),

    RE_ENGAGEMENT("Re-Engagement",
            NotificationData.RE_ENGAGEMENT_DATA,
            "ic_gesture_tap_white_24dp",
            "https://firebasestorage.googleapis.com/v0/b/onesignaltest-e7802.appspot.com/o/NOTIFICATION_ICON%2Fgesture-tap.png?alt=media&token=045ddcb9-f4e5-457e-8577-baa0e264e227",
            "[]",
            true,
            0),

    RATING("Rating",
            NotificationData.RATING_DATA,
            "ic_star_white_24dp",
            "https://firebasestorage.googleapis.com/v0/b/onesignaltest-e7802.appspot.com/o/NOTIFICATION_ICON%2Fstar.png?alt=media&token=da0987e5-a635-488f-9fba-24a1ee5d704a",
            "[]",
            true,
            0);

    private final String title;
    private final String[][] data;
    private final String smallIconRes;
    private final String iconUrl;
    private final String buttons;
    private final boolean shouldShow;
    private int pos;

    Notification(String title, String[][] data, String smallIconRes, String iconUrl, String buttons, boolean shouldShow, int pos) {
        this.title = title;
        this.data = data;
        this.smallIconRes = smallIconRes;
        this.iconUrl = iconUrl;
        this.buttons = buttons;
        this.shouldShow = shouldShow;
        this.pos = pos;
    }

    public String getGroup() {
        return title;
    }

    public String getTitle(int pos) {
        return data[pos][0];
    }

    public String getMessage(int pos) {
        return data[pos][1];
    }

    public String getSmallIconRes() {
        return smallIconRes;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public String getLargeIconUrl(int pos) {
        return data[pos][2];
    }

    public String getBigPictureUrl(int pos) {
        return data[pos][3];
    }

    public String getButtons() {
        return buttons;
    }

    public boolean shouldShow() {
        return shouldShow;
    }

    public int getTemplatePos() {
        if (pos > 2) pos = 0;
        return pos++;
    }
}
