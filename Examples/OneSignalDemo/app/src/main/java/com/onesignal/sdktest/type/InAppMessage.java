package com.onesignal.sdktest.type;

public enum InAppMessage {

    TOP_BANNER("Top Banner", "https://firebasestorage.googleapis.com/v0/b/onesignaltest-e7802.appspot.com/o/IN_APP_MESSAGE_ICON%2Ftop_banner_icon.png?alt=media&token=88fe1f2f-01c0-44fb-bb88-c1a4bf00969d"),
    BOTTOM_BANNER("Bottom Banner", "https://firebasestorage.googleapis.com/v0/b/onesignaltest-e7802.appspot.com/o/IN_APP_MESSAGE_ICON%2Fbottom_banner_icon.png?alt=media&token=a8faee09-137d-4049-b41b-0dc3c66b0d8e"),
    CENTER_MODAL("Center Modal", "https://firebasestorage.googleapis.com/v0/b/onesignaltest-e7802.appspot.com/o/IN_APP_MESSAGE_ICON%2Fcenter_modal_icon.png?alt=media&token=c0998b1e-5bab-404a-bfaa-e432ec298bdf"),
    FULL_SCREEN("Full Screen", "https://firebasestorage.googleapis.com/v0/b/onesignaltest-e7802.appspot.com/o/IN_APP_MESSAGE_ICON%2Ffull_screen_icon.png?alt=media&token=db3bb9ea-c61c-4df7-9f1f-6673316a4395");

    private final String title;
    private final String icon;

    InAppMessage(String title, String iconUrl) {
        this.title = title;
        this.icon = iconUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getIconUrl() {
        return icon;
    }

}
