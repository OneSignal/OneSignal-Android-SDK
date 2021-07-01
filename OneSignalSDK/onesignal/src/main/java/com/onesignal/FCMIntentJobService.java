package com.onesignal;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import static com.onesignal.NotificationBundleProcessor.processBundleFromReceiver;

/**
 * Uses modified JobIntentService class that's part of the onesignal package
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class FCMIntentJobService extends JobIntentService {

    public static final String BUNDLE_EXTRA = "Bundle:Parcelable:Extras";
    private static final int JOB_ID = 123890;

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null)
            return;

        OneSignal.initWithContext(this);
        processBundleFromReceiver(this, bundle, new NotificationBundleProcessor.ProcessBundleReceiverCallback() {
            @Override
            public void onBundleProcessed(@Nullable NotificationBundleProcessor.ProcessedBundleResult processedResult) {

            }
        });
    }

    public static void enqueueWork(Context context, Intent intent) {
        enqueueWork(context, FCMIntentJobService.class, JOB_ID, intent, false);
    }
}
