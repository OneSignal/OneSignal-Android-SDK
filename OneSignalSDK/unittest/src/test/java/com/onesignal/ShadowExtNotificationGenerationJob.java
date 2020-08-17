package com.onesignal;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

@Implements(OSNotificationGenerationJob.ExtNotificationGenerationJob.class)
public class ShadowExtNotificationGenerationJob {

    @RealObject
    private OSNotificationGenerationJob.ExtNotificationGenerationJob generationJob;

    @Implementation
    public void completeJob(boolean bubble) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                generationJob.complete(true);
            }
        }, "OS_EXT_NOTIFICATION_GENERATION_JOB").start();
    }
}
