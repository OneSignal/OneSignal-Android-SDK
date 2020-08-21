package com.onesignal;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Uses modified JobIntentService class that's part of the onesignal package
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class FCMIntentJobService extends JobIntentService {

    public static final String BUNDLE_EXTRA = "Bundle:Parcelable:Extras";
    private static final int JOB_ID = 123890;

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        BundleCompat bundle = BundleCompatFactory.getInstance();
        bundle.setBundle(intent.getExtras().getParcelable(BUNDLE_EXTRA));

        NotificationBundleProcessor.processFromFCMIntentService(this, bundle, null);
    }

    public static void enqueueWork(Context context, Intent intent) {
        enqueueWork(context, FCMIntentJobService.class, JOB_ID, intent, false);
    }
}
