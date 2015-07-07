package com.onesignal;

import android.provider.BaseColumns;

public class OneSignalDbContract {
   public OneSignalDbContract() {}

   public static abstract class NotificationTable implements BaseColumns {
      public static final String TABLE_NAME = "notification";
      public static final String COLUMN_NAME_NOTIFICATION_ID = "notification_id"; // OneSignal Notification Id
      public static final String COLUMN_NAME_ANDROID_NOTIFICATION_ID = "android_notification_id";
      public static final String COLUMN_NAME_GROUP_ID = "group_id";
      public static final String COLUMN_NAME_IS_SUMMARY = "is_summary";
      public static final String COLUMN_NAME_OPENED = "opened";
      public static final String COLUMN_NAME_DISMISSED = "dismissed";
      public static final String COLUMN_NAME_TITLE = "title";
      public static final String COLUMN_NAME_MESSAGE = "message";
      public static final String COLUMN_NAME_CREATED_TIME = "created_time";

      // JSON formatted string of the full GCM bundle
      public static final String COLUMN_NAME_FULL_DATA = "full_data";


      public static final String INDEX_CREATE_NOTIFICATION_ID = "CREATE INDEX notification_notification_id_idx ON notification(notification_id); ";
      public static final String INDEX_CREATE_ANDROID_NOTIFICATION_ID = "CREATE INDEX notification_android_notification_id_idx ON notification(android_notification_id); ";
      public static final String INDEX_CREATE_GROUP_ID = "CREATE INDEX notification_group_id_idx ON notification(group_id); ";
      public static final String INDEX_CREATE_CREATED_TIME = "CREATE INDEX notification_created_time_idx ON notification(created_time); ";
   }
}
