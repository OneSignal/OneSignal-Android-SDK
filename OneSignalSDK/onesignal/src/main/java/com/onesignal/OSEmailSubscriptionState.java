/**
 * Modified MIT License
 *
 * Copyright 2018 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import androidx.annotation.NonNull;

import org.json.JSONObject;

public class OSEmailSubscriptionState implements Cloneable {

    private static final String CHANGED_KEY = "changed";

    private OSObservable<Object, OSEmailSubscriptionState> observable;
    private String emailUserId;
    private String emailAddress;

    OSEmailSubscriptionState(boolean asFrom) {
        observable = new OSObservable<>(CHANGED_KEY, false);

        if (asFrom) {
            emailUserId = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_ONESIGNAL_EMAIL_ID_LAST, null);
            emailAddress = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_ONESIGNAL_EMAIL_ADDRESS_LAST, null);
        } else {
            emailUserId = OneSignal.getEmailId();
            emailAddress = OneSignalStateSynchronizer.getEmailStateSynchronizer().getRegistrationId();
        }
    }

    void clearEmailAndId() {
        boolean changed = emailUserId != null || emailAddress != null;
        emailUserId = null;
        emailAddress = null;
        if (changed)
            observable.notifyChange(this);
    }

    void setEmailUserId(@NonNull String id) {
        boolean changed = false;
        if (id == null)
            changed = emailUserId != null;
        else if (!id.equals(emailUserId))
            changed = true;

        emailUserId = id;
        if (changed)
            observable.notifyChange(this);
    }

    public String getEmailUserId() {
        return emailUserId;
    }

    void setEmailAddress(@NonNull String email) {
        boolean changed = !email.equals(emailAddress);
        emailAddress = email;
        if (changed)
            observable.notifyChange(this);
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public boolean isSubscribed() {
        return emailUserId != null && emailAddress != null;
    }

    void persistAsFrom() {
        OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_ONESIGNAL_EMAIL_ID_LAST, emailUserId);
        OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_ONESIGNAL_EMAIL_ADDRESS_LAST, emailAddress);
    }

    boolean compare(OSEmailSubscriptionState from) {
        return    !(emailUserId != null ? emailUserId : "").equals(from.emailUserId != null ? from.emailUserId : "")
               || !(emailAddress != null ? emailAddress : "").equals(from.emailAddress != null ? from.emailAddress : "");
    }

    public OSObservable<Object, OSEmailSubscriptionState> getObservable() {
        return observable;
    }

    protected Object clone() {
        try {
            return super.clone();
        } catch (Throwable t) {}
        return null;
    }

    public JSONObject toJSONObject() {
        JSONObject mainObj = new JSONObject();

        try {
            if (emailUserId != null)
                mainObj.put("emailUserId", emailUserId);
            else
                mainObj.put("emailUserId", JSONObject.NULL);

            if (emailAddress != null)
                mainObj.put("emailAddress", emailAddress);
            else
                mainObj.put("emailAddress", JSONObject.NULL);

            mainObj.put("isSubscribed", isSubscribed());
        }
        catch(Throwable t) {
            t.printStackTrace();
        }

        return mainObj;
    }

    @Override
    public String toString() {
        return toJSONObject().toString();
    }
}
