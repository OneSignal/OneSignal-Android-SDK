package com.onesignal.sdktest.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;

import com.onesignal.sdktest.R;

public class CustomAlertDialogBuilder extends AlertDialog.Builder {

    /*
     * Click listener
     */
    private DialogInterface.OnClickListener mPositiveButtonListener = null;
    private DialogInterface.OnClickListener mNegativeButtonListener = null;
    private DialogInterface.OnClickListener mNeutralButtonListener = null;
    private DialogInterface.OnDismissListener mOnDismissListener = null;

    /*
     * Buttons text
     */
    private CharSequence mPositiveButtonText = null;
    private CharSequence mNegativeButtonText = null;
    private CharSequence mNeutralButtonText = null;


    /*
     * Buttons
     */
    private Button positiveButton;
    private Button negativeButton;

    private View view;
    private Boolean mCancelOnTouchOutside = null;
    private boolean isCancelable = true;

    public CustomAlertDialogBuilder(Context context, View view) {
        super(context);
        this.view = view;
    }

    public CustomAlertDialogBuilder setOnDismissListener (DialogInterface.OnDismissListener listener) {
        mOnDismissListener = listener;
        return this;
    }

    @Override
    public CustomAlertDialogBuilder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
        mNegativeButtonListener = listener;
        mNegativeButtonText = text;
        return this;
    }

    @Override
    public CustomAlertDialogBuilder setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) {
        mNeutralButtonListener = listener;
        mNeutralButtonText = text;
        return this;
    }

    @Override
    public CustomAlertDialogBuilder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
        mPositiveButtonListener = listener;
        mPositiveButtonText = text;
        return this;
    }

    @Override
    public CustomAlertDialogBuilder setNegativeButton(int textId, DialogInterface.OnClickListener listener) {
        setNegativeButton(getContext().getString(textId), listener);
        return this;
    }

    @Override
    public CustomAlertDialogBuilder setNeutralButton(int textId, DialogInterface.OnClickListener listener) {
        setNeutralButton(getContext().getString(textId), listener);
        return this;
    }

    @Override
    public CustomAlertDialogBuilder setPositiveButton(int textId, DialogInterface.OnClickListener listener) {
        setPositiveButton(getContext().getString(textId), listener);
        return this;
    }

    public CustomAlertDialogBuilder setCanceledOnTouchOutside(boolean cancelOnTouchOutside) {
        mCancelOnTouchOutside = cancelOnTouchOutside;
        return this;
    }

    @Override
    public AlertDialog create() {
        throw new UnsupportedOperationException("CustomAlertDialogBuilder.create(): use show() instead..");
    }

    public Button getPositiveButtonElement() {
        return positiveButton;
    }

    public Button getNegativeButtonElement() {
        return negativeButton;
    }

    public void setIsCancelable(boolean isCancelable) {
        this.isCancelable = isCancelable;
    }

    @Override
    public AlertDialog show() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext(), R.style.WhiteThemeAlertDialog);
        alertDialogBuilder.setView(view);
        alertDialogBuilder.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_BACK:
                        return !isCancelable;
                }
                return false;
            }
        });
        final AlertDialog alertDialog = alertDialogBuilder.create();

        DialogInterface.OnClickListener emptyOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) { }
        };

        // Enable buttons (needed for Android 1.6) - otherwise later getButton() returns null
        if (mPositiveButtonText != null) {
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, mPositiveButtonText, emptyOnClickListener);
        }

        if (mNegativeButtonText != null) {
            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, mNegativeButtonText, emptyOnClickListener);
        }

        if (mNeutralButtonText != null) {
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, mNeutralButtonText, emptyOnClickListener);
        }

        // Set OnDismissListener if available
        if (mOnDismissListener != null) {
            alertDialog.setOnDismissListener(mOnDismissListener);
        }

        if (mCancelOnTouchOutside != null) {
            alertDialog.setCanceledOnTouchOutside(mCancelOnTouchOutside);
        }

        alertDialog.show();

        // Set the OnClickListener directly on the Button object, avoiding the auto-dismiss feature
        // IMPORTANT: this must be after alert.show(), otherwise the button doesn't exist..
        // If the listener are null don't do anything so that they will still dismiss the dialog when clicked
        if (mPositiveButtonListener != null) {
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    mPositiveButtonListener.onClick(alertDialog, AlertDialog.BUTTON_POSITIVE);
                }
            });
        }

        if (mNegativeButtonListener != null) {
            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    mNegativeButtonListener.onClick(alertDialog, AlertDialog.BUTTON_NEGATIVE);
                }
            });
        }

        if (mNeutralButtonListener != null) {
            alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    mNeutralButtonListener.onClick(alertDialog, AlertDialog.BUTTON_NEUTRAL);
                }
            });
        }

        positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        negativeButton = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);

        return alertDialog;
    }

}
