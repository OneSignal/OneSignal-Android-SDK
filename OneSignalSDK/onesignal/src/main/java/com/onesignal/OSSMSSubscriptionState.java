/**
 * Modified MIT License
 * <p>
 * Copyright 2021 OneSignal
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 * <p>
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

public class OSSMSSubscriptionState implements Cloneable {

    private static final String CHANGED_KEY = "changed";
    private static final String SMS_USER_ID = "smsUserId";
    private static final String SMS_NUMBER = "smsNumber";
    private static final String SUBSCRIBED = "isSubscribed";

    private OSObservable<Object, OSSMSSubscriptionState> observable;
    private String smsUserId;
    private String smsNumber;

    OSSMSSubscriptionState(boolean asFrom) {
        observable = new OSObservable<>(CHANGED_KEY, false);

        if (asFrom) {
            smsUserId = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_OS_SMS_ID_LAST, null);
            smsNumber = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_OS_SMS_NUMBER_LAST, null);
        } else {
            smsUserId = OneSignal.getSMSId();
            smsNumber = OneSignalStateSynchronizer.getSMSStateSynchronizer().getRegistrationId();
        }
    }

    void clearSMSAndId() {
        boolean changed = smsUserId != null || smsNumber != null;
        smsUserId = null;
        smsNumber = null;
        if (changed)
            observable.notifyChange(this);
    }

    public String getSmsUserId() {
        return smsUserId;
    }

    void setSMSUserId(@NonNull String id) {
        boolean changed = false;
        if (id == null)
            changed = smsUserId != null;
        else if (!id.equals(smsUserId))
            changed = true;

        smsUserId = id;
        if (changed)
            observable.notifyChange(this);
    }

    public String getSMSNumber() {
        return smsNumber;
    }

    void setSMSNumber(@NonNull String number) {
        boolean changed = !number.equals(smsNumber);
        smsNumber = number;
        if (changed)
            observable.notifyChange(this);
    }

    public boolean isSubscribed() {
        return smsUserId != null && smsNumber != null;
    }

    void persistAsFrom() {
        OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_SMS_ID_LAST, smsUserId);
        OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_SMS_NUMBER_LAST, smsNumber);
    }

    boolean compare(OSSMSSubscriptionState from) {
        return !(smsUserId != null ? smsUserId : "").equals(from.smsUserId != null ? from.smsUserId : "")
                || !(smsNumber != null ? smsNumber : "").equals(from.smsNumber != null ? from.smsNumber : "");
    }

    public OSObservable<Object, OSSMSSubscriptionState> getObservable() {
        return observable;
    }

    protected Object clone() {
        try {
            return super.clone();
        } catch (Throwable t) {
        }
        return null;
    }

    public JSONObject toJSONObject() {
        JSONObject mainObj = new JSONObject();

        try {
            if (smsUserId != null)
                mainObj.put(SMS_USER_ID, smsUserId);
            else
                mainObj.put(SMS_USER_ID, JSONObject.NULL);

            if (smsNumber != null)
                mainObj.put(SMS_NUMBER, smsNumber);
            else
                mainObj.put(SMS_NUMBER, JSONObject.NULL);

            mainObj.put(SUBSCRIBED, isSubscribed());
        } catch (Throwable t) {
            t.printStackTrace();
        }

        return mainObj;
    }

    @Override
    public String toString() {
        return toJSONObject().toString();
    }
}
