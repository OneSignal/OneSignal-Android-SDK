package com.amazon.device.messaging;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/** Test-only stub; mirrors common callbacks in the real ADM base. */
public abstract class ADMMessageHandlerBase extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }

    // Typical callbacks the real base defines; safe no-ops
    protected void onMessage(Context context, Intent intent) {}
    protected void onDeletedMessages(Context context, int total) {}
    protected void onRegistered(Context context, String registrationId) {}
    protected void onUnregistered(Context context, String registrationId) {}
    protected void onRegistrationError(Context context, String errorId) {}
}
