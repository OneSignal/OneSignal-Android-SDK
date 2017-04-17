/**
 * Modified MIT License
 *
 * Copyright 2016 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import android.provider.BaseColumns;

class OneSignalDbContract {
   OneSignalDbContract() {}

   static abstract class NotificationTable implements BaseColumns {
      public static final String TABLE_NAME = "notification";
      public static final String COLUMN_NAME_NOTIFICATION_ID = "notification_id"; // OneSignal Notification Id
      public static final String COLUMN_NAME_ANDROID_NOTIFICATION_ID = "android_notification_id";
      public static final String COLUMN_NAME_GROUP_ID = "group_id";
      public static final String COLUMN_NAME_COLLAPSE_ID = "collapse_id";
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
      public static final String INDEX_CREATE_COLLAPSE_ID = "CREATE INDEX notification_collapse_id_idx ON notification(collapse_id); ";
      public static final String INDEX_CREATE_CREATED_TIME = "CREATE INDEX notification_created_time_idx ON notification(created_time); ";
   }
}
