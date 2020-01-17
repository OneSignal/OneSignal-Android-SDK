package com.onesignal.sdktest.util;

import android.support.design.widget.TextInputLayout;
import android.util.Patterns;

import com.onesignal.sdktest.constant.Text;

public class ProfileUtil {

    public enum FieldType {

        APP_ID("App Id"),
        EMAIL("Email"),
        EXTERNAL_USER_ID("External User Id"),

        TAG("Tags"),
        TRIGGER("Triggers");

        private final String title;

        FieldType(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    private static boolean isAppIdValid(TextInputLayout appIdTextInputLayout) {
        appIdTextInputLayout.setErrorEnabled(false);
        if (appIdTextInputLayout.getEditText() != null) {
            String appId = appIdTextInputLayout.getEditText().getText().toString().trim();
            if (appId.isEmpty()) {
                appIdTextInputLayout.setError(Text.APP_ID_IS_REQUIRED);
                return false;
            }
            if (appId.length() != 36) {
                appIdTextInputLayout.setError(Text.INVALID_APP_ID);
                return false;
            }
        } else {
            appIdTextInputLayout.setError(Text.ERROR);
            return false;
        }
        return true;
    }

    public static boolean isEmailValid(TextInputLayout emailTextInputLayout) {
        emailTextInputLayout.setErrorEnabled(false);
        if (emailTextInputLayout.getEditText() != null) {
            String email = emailTextInputLayout.getEditText().getText().toString().trim();
            if (email.isEmpty()) {
                emailTextInputLayout.setError(Text.EMAIL_IS_REQUIRED);
                return false;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailTextInputLayout.setError(Text.INVALID_EMAIL);
                return false;
            }
        } else {
            emailTextInputLayout.setError(Text.ERROR);
            return false;
        }
        return true;
    }

    private static boolean isExternalUserIdValid(TextInputLayout externalUserIdTextInputLayout) {
        externalUserIdTextInputLayout.setErrorEnabled(false);
        if (externalUserIdTextInputLayout.getEditText() != null) {
            String externalUserId = externalUserIdTextInputLayout.getEditText().getText().toString().trim();
            if (externalUserId.isEmpty()) {
                externalUserIdTextInputLayout.setError(Text.EXTERNAL_USER_ID_IS_REQUIRED);
                return false;
            }
        } else {
            externalUserIdTextInputLayout.setError(Text.ERROR);
            return false;
        }
        return true;
    }

    private static boolean isKeyValid(TextInputLayout keyTextInputLayout) {
        keyTextInputLayout.setErrorEnabled(false);
        if (keyTextInputLayout.getEditText() != null) {
            String key = keyTextInputLayout.getEditText().getText().toString().trim();
            if (key.isEmpty()) {
                keyTextInputLayout.setError(Text.KEY_IS_REQUIRED);
                return false;
            }
        } else {
            keyTextInputLayout.setError(Text.ERROR);
            return false;
        }
        return true;
    }

    static boolean isContentValid(FieldType field, TextInputLayout alertDialogTextInputLayout) {
        switch (field) {
            case APP_ID:
                return isAppIdValid(alertDialogTextInputLayout);
            case EMAIL:
                return isEmailValid(alertDialogTextInputLayout);
            case EXTERNAL_USER_ID:
                return isExternalUserIdValid(alertDialogTextInputLayout);

            case TAG:
            case TRIGGER:
                return isKeyValid(alertDialogTextInputLayout);
        }
        return false;
    }
}
