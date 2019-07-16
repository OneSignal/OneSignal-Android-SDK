package com.onesignal;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.JobIntentService;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class GcmJobIntentService extends JobIntentService {

    public static final String BUNDLE_EXTRA = "Bundle:Parcelable:Extras";
    private static final int JOB_ID = 123890;

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Parcelable parcelable = intent.getExtras().getParcelable(BUNDLE_EXTRA);
        BundleCompat bundle = parcelable instanceof PersistableBundle && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1?
                new BundleCompatPersistableBundle((PersistableBundle) parcelable) :
                new BundleCompatBundle((Bundle) parcelable);

        NotificationBundleProcessor.ProcessFromGCMIntentService(this, bundle, null);
    }

    public static void enqueueWork(Context context, Intent intent) {
        enqueueWork(context, GcmJobIntentService.class, JOB_ID, intent);
    }
}
