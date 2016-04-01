package com.onesignal;

import android.database.sqlite.SQLiteDatabase;

import org.robolectric.annotation.Implements;

@Implements(BadgeCountUpdater.class)
public class ShadowBadgeCountUpdater {

   public static int lastCount = 0;

   private static void updateCount(int count) {
      lastCount = count;
   }
}
