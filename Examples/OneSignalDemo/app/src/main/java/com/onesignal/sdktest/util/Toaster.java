package com.onesignal.sdktest.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.CardView;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.onesignal.sdktest.R;
import com.onesignal.sdktest.constant.Text;
import com.onesignal.sdktest.type.ToastType;

public class Toaster {

    private Context context;
    private Font font;

    public Toaster(Context context) {
        this.context = context;

        font = new Font(context);
    }

    public void makeToast(String bread) {
        Toast.makeText(context, bread, Toast.LENGTH_SHORT).show();
    }

    public void makeLongToast(String bread) {
        Toast.makeText(context, bread, Toast.LENGTH_LONG).show();
    }

    public void makeCustomViewToast(String bread, ToastType toastType) {
        View toastView = ((Activity) context).getLayoutInflater().inflate(R.layout.toaster_toast_card_layout, null, false);
        CardView toastCardView = toastView.findViewById(R.id.toaster_toast_card_view);
        ImageView toastIcon = toastView.findViewById(R.id.toaster_toast_image_view);
        TextView toastTextView = toastView.findViewById(R.id.toaster_toast_text_view);

        int color = context.getResources().getColor(toastType.getColor());
        toastCardView.setCardBackgroundColor(color);

        toastTextView.setTypeface(font.saralaBold);
        toastTextView.setText(bread);

        Drawable icon = context.getResources().getDrawable(toastType.getIcon());
        toastIcon.setImageDrawable(icon);

        Toast toast = Toast.makeText(context, Text.EMPTY, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, (int) (InterfaceUtil.getScreenHeight(context) * 0.25f));
        toast.setView(toastView);
        toast.show();
    }

}