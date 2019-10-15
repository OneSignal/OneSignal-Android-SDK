package com.onesignal;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;

// Uses modified JobIntentService class that's part of the onesignal package

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class GcmIntentJobService extends JobIntentService {

    public static final String BUNDLE_EXTRA = "Bundle:Parcelable:Extras";
    private static final int JOB_ID = 123890;

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        BundleCompat bundle = BundleCompatFactory.getInstance();
        bundle.setBundle(intent.getExtras().getParcelable(BUNDLE_EXTRA));

        NotificationBundleProcessor.ProcessFromGCMIntentService(this, bundle, null);
    }

    public static void enqueueWork(Context context, Intent intent) {
        enqueueWork(context, GcmIntentJobService.class, JOB_ID, intent, false);
    }
}
