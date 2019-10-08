package com.onesignal;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowApplicationPackageManager;

@Implements(className = "android.app.ApplicationPackageManager")
public class OneSignalShadowPackageManager extends ShadowApplicationPackageManager {

    /*
     * Mimics the int configChanges int inside of the ActivityInfo class created from the
     * activity tag attribute
     *
     * android:configChanges="keyboard|keyboardHidden|orientation|screenSize"
     */
    public static int configChanges;

    /*
     * Mimics the Bundle inside of the ApplicationInfo class created from meta-data tags inside the
     * AndroidManifest.xml
     *
     * <meta-data android:name="com.onesignal.NotificationOpened.DEFAULT" android:value="DISABLE" />
     */
    public static Bundle metaData;

    /**
     * Reset all static values (should be called before each test)
     */
    public static void resetStatics() {
        configChanges = 0;
        metaData = new Bundle();
    }

    /**
     * Add a meat data key and String value to the metaData Bundle for placement in the
     * shadowed getApplicationInfo
     */
    public static void addManifestMetaData(String key, Object value) {
        if (value instanceof String) {
            metaData.putString(key, value.toString());
        } else {
            // TODO: We should add any other cases we have for different values here
        }
    }

    @Implementation
    protected ActivityInfo getActivityInfo(ComponentName component, int flags)
            throws PackageManager.NameNotFoundException {
        ActivityInfo activityInfo = super.getActivityInfo(component, flags);

        // Where we replace the ActivityInfo configChanges with our own configChanges to mimic activities with specific flags set
        activityInfo.configChanges = configChanges;
        return activityInfo;
    }

    @Implementation
    protected ApplicationInfo getApplicationInfo(String packageName, int flags)
            throws PackageManager.NameNotFoundException {
        ApplicationInfo applicationInfo = super.getApplicationInfo(packageName, flags);

        // Where we add extra metaData tags into the ApplicationInfo to mimic apps with specific flags set
        applicationInfo.metaData.putAll(metaData);
        return applicationInfo;
    }

}