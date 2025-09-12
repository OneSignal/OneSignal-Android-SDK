package com.amazon.device.messaging;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Test-only stub for job-based handler. */
public abstract class ADMMessageHandlerJobBase extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }
}
