package com.amazon.device.messaging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** No-op; Test-only stub to satisfy class linking in unit tests. */
public abstract class ADMMessageReceiver extends BroadcastReceiver {
    protected ADMMessageReceiver() { }
    protected ADMMessageReceiver(Class<? extends ADMMessageHandlerBase> serviceClass) { }
    protected ADMMessageReceiver(Class<? extends ADMMessageHandlerJobBase> serviceClass, int jobId) { }
    protected void registerIntentServiceClass(Class<? extends ADMMessageHandlerBase> serviceClass) { }
    protected void registerJobServiceClass(Class<? extends ADMMessageHandlerJobBase> serviceClass, int jobId) { }
    @Override public void onReceive(Context context, Intent intent) { }
}
