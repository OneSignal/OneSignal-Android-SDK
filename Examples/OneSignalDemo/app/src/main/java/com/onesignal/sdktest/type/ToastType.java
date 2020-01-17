package com.onesignal.sdktest.type;

import com.onesignal.sdktest.R;

public enum ToastType {

    INFO(R.drawable.ic_information_white_48dp, R.color.colorToastInfo),
    SUCCESS(R.drawable.ic_checkbox_marked_circle_white_48dp, R.color.colorToastSuccess),
    WARNING(R.drawable.ic_alert_white_48dp, R.color.colorToastWarning),
    ERROR(R.drawable.ic_alert_octagon_white_48dp, R.color.colorToastError);

    private int icon;
    private int color;

    ToastType(int icon, int color) {
        this.icon = icon;
        this.color = color;
    }

    public int getIcon() {
        return icon;
    }

    public int getColor() {
        return color;
    }

}