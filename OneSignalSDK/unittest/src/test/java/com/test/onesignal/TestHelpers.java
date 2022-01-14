package com.test.onesignal;

import android.app.AlarmManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.onesignal.MockOSTimeImpl;
import com.onesignal.OneSignalDb;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.OneSignalPackagePrivateHelper.OSTestInAppMessageInternal;
import com.onesignal.OneSignalPackagePrivateHelper.TestOneSignalPrefs;
import com.onesignal.OneSignalShadowPackageManager;
import com.onesignal.OSOutcomeEvent;
import com.onesignal.ShadowBadgeCountUpdater;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowDynamicTimer;
import com.onesignal.ShadowFCMBroadcastReceiver;
import com.onesignal.ShadowFirebaseAnalytics;
import com.onesignal.ShadowFusedLocationApiWrapper;
import com.onesignal.ShadowGenerateNotification;
import com.onesignal.ShadowGoogleApiClientCompatProxy;
import com.onesignal.ShadowHMSFusedLocationProviderClient;
import com.onesignal.ShadowHmsInstanceId;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowNotificationReceivedEvent;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOSWebView;
import com.onesignal.ShadowOneSignalDbHelper;
import com.onesignal.ShadowOneSignalNotificationManager;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowOneSignalRestClientWithMockConnection;
import com.onesignal.ShadowPushRegistratorADM;
import com.onesignal.ShadowPushRegistratorFCM;
import com.onesignal.ShadowPushRegistratorHMS;
import com.onesignal.ShadowTimeoutHandler;
import com.onesignal.StaticResetHelper;
import com.onesignal.SyncJobService;
import com.onesignal.influence.domain.OSInfluenceType;
import com.onesignal.outcomes.data.MockOSCachedUniqueOutcomeTable;
import com.onesignal.outcomes.data.MockOSOutcomeEventsTable;
import com.onesignal.outcomes.OSOutcomeEventDB;
import com.onesignal.outcomes.domain.OSCachedUniqueOutcomeName;

import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONException;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.util.Scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_OSTaskController_ShutdownNow;
import static junit.framework.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

public class TestHelpers {

   private static final String TAG = TestHelpers.class.getCanonicalName();
   private static final long SIX_MONTHS_TIME_SECONDS = 6 * 30 * 24 * 60 * 60;

   static Exception lastException;

   static void beforeTestInitAndCleanup() throws Exception {
      TestOneSignalPrefs.initializePool();
      if (!ranBeforeTestSuite)
         return;

      setupTestWorkManager(ApplicationProvider.getApplicationContext());

      resetAlarmManager();

      resetSystemClock();

      stopAllOSThreads();

      StaticResetHelper.restSetStaticFields();

      ShadowOneSignalRestClient.resetStatics();

      ShadowPushRegistratorFCM.resetStatics();
      ShadowPushRegistratorADM.resetStatics();
      ShadowHmsInstanceId.resetStatics();
      ShadowPushRegistratorHMS.resetStatics();

      ShadowNotificationManagerCompat.enabled = true;

      ShadowCustomTabsClient.resetStatics();
      ShadowFCMBroadcastReceiver.resetStatics();

      ShadowFusedLocationApiWrapper.resetStatics();
      ShadowHMSFusedLocationProviderClient.resetStatics();

      ShadowFirebaseAnalytics.resetStatics();

      ShadowGoogleApiClientCompatProxy.restSetStaticFields();
      ShadowOneSignalDbHelper.restSetStaticFields();
      ShadowOneSignalRestClientWithMockConnection.resetStatics();

      ShadowOSWebView.resetStatics();

      ShadowDynamicTimer.resetStatics();

      OneSignalShadowPackageManager.resetStatics();

      ShadowOSUtils.resetStatics();
      ShadowTimeoutHandler.resetStatics();
      ShadowGenerateNotification.resetStatics();
      ShadowNotificationReceivedEvent.resetStatics();
      ShadowOneSignalNotificationManager.resetStatics();
      ShadowBadgeCountUpdater.resetStatics();

      lastException = null;
   }

   public static void afterTestCleanup() throws Exception {
      try {
         OneSignal_OSTaskController_ShutdownNow();
         stopAllOSThreads();
      } catch (Exception e) {
         e.printStackTrace();
      }

      stopJobs();

      if (lastException != null)
         throw lastException;
   }

   static void stopAllOSThreads() {
      boolean joinedAThread;
      do {
         joinedAThread = false;
         Set<Thread> threadSet = Thread.getAllStackTraces().keySet();

         for (Thread thread : threadSet) {
            if (thread.getName().startsWith("OS_")) {
               thread.interrupt();
               joinedAThread = true;
            }
         }
      } while (joinedAThread);
   }

   static void flushBufferedSharedPrefs() {
      TestOneSignalPrefs.WritePrefHandlerThread handlerThread = TestOneSignalPrefs.prefsHandler;

      if (handlerThread.getLooper() == null)
         return;
      Scheduler scheduler = shadowOf(handlerThread.getLooper()).getScheduler();
      while (scheduler.runOneTask());
   }

   // Join all OS_ threads
   //   Returns true if we had to join any threads
   static boolean runOSThreads() throws Exception {
      boolean createdNewThread = false;
      boolean joinedAThread;
      do {
         joinedAThread = false;
         Set<Thread> threadSet = Thread.getAllStackTraces().keySet();

         for (Thread thread : threadSet) {
            if (!thread.getName().startsWith("OS_"))
               continue;
            if (ShadowOneSignalRestClient.isAFrozenThread(thread))
               continue;

            thread.join(0, 1);

            if (lastException != null)
               throw lastException;
            joinedAThread = createdNewThread = true;
         }
      } while (joinedAThread);

      return createdNewThread;
   }

   static Thread getThreadByName(String threadName) {
      for (Thread t : Thread.getAllStackTraces().keySet()) {
         if (t.getName().equals(threadName))
            return t;
      }
      return null;
   }

   // Run any OneSignal background threads including any pending runnables
   public static void threadAndTaskWait() throws Exception {
      shadowOf(RuntimeEnvironment.application).getForegroundThreadScheduler().runOneTask();
      // Runs Runnables posted by calling View.post() which are run on the main thread.
      Robolectric.getForegroundThreadScheduler().runOneTask();

      boolean createdNewThread;
      do {
         // We run a 2nd time if we did not find any threads to ensure we don't skip any
         createdNewThread = runOSThreads() || runOSThreads();

         boolean advancedRunnables = OneSignalPackagePrivateHelper.runAllNetworkRunnables();

         if (advancedRunnables)
            createdNewThread = true;
      } while (createdNewThread);

      if (lastException != null)
         throw lastException;
   }

   private static boolean ranBeforeTestSuite;
   public static void beforeTestSuite() throws Exception {
      if (ranBeforeTestSuite)
         return;

      StaticResetHelper.load();

      beforeTestInitAndCleanup();

      System.out.println("beforeTestSuite!!!!!!");

      // Setup process global exception handler to catch any silent exceptions on background threads
      Thread.setDefaultUncaughtExceptionHandler(
         new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
               lastException = new Exception(e);
            }
         }
      );
      ranBeforeTestSuite = true;
   }

   static void fastColdRestartApp() throws Exception {
      stopAllOSThreads();
      flushBufferedSharedPrefs();
      StaticResetHelper.restSetStaticFields();
      Log.d(TAG, "************ FAST COLD RESTART FINISHED! ************");
   }

   static void restartAppAndElapseTimeToNextSession(MockOSTimeImpl time) throws Exception {
      stopAllOSThreads();
      flushBufferedSharedPrefs();
      StaticResetHelper.restSetStaticFields();
      time.advanceSystemAndElapsedTimeBy(31);
      Log.d(TAG, "************ restartAppAndElapseTimeToNextSession finished ************");
   }

   static ArrayList<HashMap<String, Object>> getAllNotificationRecords(OneSignalDb db) {
      Cursor cursor = db.query(
         OneSignalPackagePrivateHelper.NotificationTable.TABLE_NAME,
         null,
         null,
         null,
         null, // group by
         null, // filter by row groups
         null, // sort order, new to old
         null // limit
      );

      ArrayList<HashMap<String, Object>> mapList = new ArrayList<>();
      while (cursor.moveToNext()) {
         HashMap<String, Object> map = new HashMap<>();
         for(int i = 0; i < cursor.getColumnCount(); i++) {
            int type = cursor.getType(i);
            String key = cursor.getColumnName(i);

             if (type == Cursor.FIELD_TYPE_INTEGER)
                map.put(key, cursor.getLong(i));
             else if (type == Cursor.FIELD_TYPE_FLOAT)
                map.put(key, cursor.getFloat(i));
             else
                map.put(key, cursor.getString(i));
         }
         mapList.add(map);
      }

      cursor.close();

      return mapList;
   }

   static List<OSOutcomeEvent>  getAllOutcomesRecordsDBv5(OneSignalDb db) { ;
      Cursor cursor = db.query(
              MockOSOutcomeEventsTable.TABLE_NAME,
              null,
              null,
              null,
              null, // group by
              null, // filter by row groups
              null, // sort order, new to old
              null // limit
      );

      List<OSOutcomeEvent> events = new ArrayList<>();
      if (cursor.moveToFirst()) {
         do {
            String notificationIds = cursor.getString(cursor.getColumnIndex(MockOSOutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS));
            String name = cursor.getString(cursor.getColumnIndex(MockOSOutcomeEventsTable.COLUMN_NAME_NAME));
            String sessionString = cursor.getString(cursor.getColumnIndex(MockOSOutcomeEventsTable.COLUMN_NAME_SESSION));
            OSInfluenceType session = OSInfluenceType.fromString(sessionString);
            long timestamp = cursor.getLong(cursor.getColumnIndex(MockOSOutcomeEventsTable.COLUMN_NAME_TIMESTAMP));
            float weight = cursor.getFloat(cursor.getColumnIndex(MockOSOutcomeEventsTable.COLUMN_NAME_WEIGHT));

            try {
               OSOutcomeEvent event = new OSOutcomeEvent(session, new JSONArray(notificationIds), name, timestamp, weight);
               events.add(event);

            } catch (JSONException e) {
               e.printStackTrace();
            }
         } while (cursor.moveToNext());
      }

      cursor.close();

      return events;
   }

   static List<OSOutcomeEventDB> getAllOutcomesRecords(OneSignalDb db) {
      Cursor cursor = db.query(
              MockOSOutcomeEventsTable.TABLE_NAME,
              null,
              null,
              null,
              null, // group by
              null, // filter by row groups
              null, // sort order, new to old
              null // limit
      );

      List<OSOutcomeEventDB> events = new ArrayList<>();
      if (cursor.moveToFirst()) {
         do {
            String name = cursor.getString(cursor.getColumnIndex(MockOSOutcomeEventsTable.COLUMN_NAME_NAME));
            String iamIds = cursor.getString(cursor.getColumnIndex(MockOSOutcomeEventsTable.COLUMN_NAME_IAM_IDS));
            String iamInfluenceTypeString = cursor.getString(cursor.getColumnIndex(MockOSOutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE));
            String notificationIds = cursor.getString(cursor.getColumnIndex(MockOSOutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS));
            String notificationInfluenceTypeString = cursor.getString(cursor.getColumnIndex(MockOSOutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE));
            OSInfluenceType iamInfluenceType = OSInfluenceType.fromString(iamInfluenceTypeString);
            OSInfluenceType notificationInfluenceType = OSInfluenceType.fromString(notificationInfluenceTypeString);

            long timestamp = cursor.getLong(cursor.getColumnIndex(MockOSOutcomeEventsTable.COLUMN_NAME_TIMESTAMP));
            float weight = cursor.getFloat(cursor.getColumnIndex(MockOSOutcomeEventsTable.COLUMN_NAME_WEIGHT));

            try {
               OSOutcomeEventDB event = new OSOutcomeEventDB(iamInfluenceType, notificationInfluenceType,
                       new JSONArray(iamIds != null ? iamIds : "[]"), new JSONArray(notificationIds != null ? notificationIds : "[]"),
                       name, timestamp, weight);
               events.add(event);

            } catch (JSONException e) {
               e.printStackTrace();
            }
         } while (cursor.moveToNext());
      }

      cursor.close();

      return events;
   }

   static ArrayList<OSCachedUniqueOutcomeName> getAllUniqueOutcomeNotificationRecordsDBv5(OneSignalDb db) {
      Cursor cursor = db.query(
              MockOSCachedUniqueOutcomeTable.TABLE_NAME_V1,
              null,
              null,
              null,
              null, // group by
              null, // filter by row groups
              null, // sort order, new to old
              null // limit
      );

      ArrayList<OSCachedUniqueOutcomeName> cachedUniqueOutcomes = new ArrayList<>();
      if (cursor.moveToFirst()) {
         do {
            String name = cursor.getString(cursor.getColumnIndex(MockOSCachedUniqueOutcomeTable.COLUMN_NAME_NAME));
            String influenceId = cursor.getString(cursor.getColumnIndex(MockOSCachedUniqueOutcomeTable.COLUMN_NAME_NOTIFICATION_ID));

            OSCachedUniqueOutcomeName uniqueOutcome = new OSCachedUniqueOutcomeName(name, influenceId);
            cachedUniqueOutcomes.add(uniqueOutcome);

         } while (cursor.moveToNext());
      }

      cursor.close();

      return cachedUniqueOutcomes;
   }

   static ArrayList<OSCachedUniqueOutcomeName> getAllUniqueOutcomeNotificationRecordsDB(OneSignalDb db) {
      Cursor cursor = db.query(
              MockOSCachedUniqueOutcomeTable.TABLE_NAME_V2,
              null,
              null,
              null,
              null, // group by
              null, // filter by row groups
              null, // sort order, new to old
              null // limit
      );

      ArrayList<OSCachedUniqueOutcomeName> cachedUniqueOutcomes = new ArrayList<>();
      if (cursor.moveToFirst()) {
         do {
            String name = cursor.getString(cursor.getColumnIndex(MockOSCachedUniqueOutcomeTable.COLUMN_NAME_NAME));
            String influenceId = cursor.getString(cursor.getColumnIndex(MockOSCachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID));
            String channelType = cursor.getString(cursor.getColumnIndex(MockOSCachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE));

            OSCachedUniqueOutcomeName uniqueOutcome = new OSCachedUniqueOutcomeName(name, influenceId, channelType);
            cachedUniqueOutcomes.add(uniqueOutcome);

         } while (cursor.moveToNext());
      }

      cursor.close();

      return cachedUniqueOutcomes;
   }

   synchronized static void saveIAM(OSTestInAppMessageInternal inAppMessage, OneSignalDb db) {
      ContentValues values = new ContentValues();
      values.put(OneSignalPackagePrivateHelper.InAppMessageTable.COLUMN_NAME_MESSAGE_ID, inAppMessage.getMessageId());
      values.put(OneSignalPackagePrivateHelper.InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY, inAppMessage.getRedisplayStats().getDisplayQuantity());
      values.put(OneSignalPackagePrivateHelper.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY, inAppMessage.getRedisplayStats().getLastDisplayTime());
      values.put(OneSignalPackagePrivateHelper.InAppMessageTable.COLUMN_CLICK_IDS, inAppMessage.getClickedClickIds().toString());
      values.put(OneSignalPackagePrivateHelper.InAppMessageTable.COLUMN_DISPLAYED_IN_SESSION, inAppMessage.isDisplayedInSession());

      db.insert(OneSignalPackagePrivateHelper.InAppMessageTable.TABLE_NAME, null, values);
   }

   synchronized static List<OSTestInAppMessageInternal> getAllInAppMessages(OneSignalDb db) throws JSONException {
      Cursor cursor = db.query(
              OneSignalPackagePrivateHelper.InAppMessageTable.TABLE_NAME,
              null,
              null,
              null,
              null,
              null,
              null
      );

      List<OSTestInAppMessageInternal> iams = new ArrayList<>();
      if (cursor.moveToFirst())
         do {
            String messageId = cursor.getString(cursor.getColumnIndex(OneSignalPackagePrivateHelper.InAppMessageTable.COLUMN_NAME_MESSAGE_ID));
            String clickIds = cursor.getString(cursor.getColumnIndex(OneSignalPackagePrivateHelper.InAppMessageTable.COLUMN_CLICK_IDS));
            int displayQuantity = cursor.getInt(cursor.getColumnIndex(OneSignalPackagePrivateHelper.InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY));
            long lastDisplay = cursor.getLong(cursor.getColumnIndex(OneSignalPackagePrivateHelper.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY));
            boolean displayed = cursor.getInt(cursor.getColumnIndex(OneSignalPackagePrivateHelper.InAppMessageTable.COLUMN_DISPLAYED_IN_SESSION)) == 1;

            JSONArray clickIdsArray = new JSONArray(clickIds);
            Set<String> clickIdsSet = new HashSet<>();

            for (int i = 0; i < clickIdsArray.length(); i++) {
               clickIdsSet.add(clickIdsArray.getString(i));
            }

            OSTestInAppMessageInternal inAppMessage = new OSTestInAppMessageInternal(messageId, displayQuantity, lastDisplay, displayed, clickIdsSet);
            iams.add(inAppMessage);
         } while (cursor.moveToNext());

      cursor.close();

      return iams;
   }

   static void setupTestWorkManager(Context context) {
      final Configuration config = new Configuration.Builder()
              .setMinimumLoggingLevel(Log.DEBUG)
              .setExecutor(new SynchronousExecutor())
              .build();
      WorkManagerTestInitHelper.initializeTestWorkManager(context, config);
   }

   private static void resetAlarmManager() {
      AlarmManager alarmManager = (AlarmManager) ApplicationProvider.getApplicationContext()
              .getSystemService(Context.ALARM_SERVICE);
      ShadowAlarmManager shadowAlarmManager = shadowOf(alarmManager);
      shadowAlarmManager.getScheduledAlarms().clear();
   }

   static void resetSystemClock() {
      SystemClock.setCurrentTimeMillis(System.currentTimeMillis());
   }

   public static void assertMainThread() {
      if (!Looper.getMainLooper().getThread().equals(Thread.currentThread()))
         Assert.fail("assertMainThread - Not running on main thread when expected to!");
   }

   static void stopJobs() throws Exception {
      JobScheduler jobScheduler =
              (JobScheduler) ApplicationProvider.getApplicationContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);
      if (jobScheduler != null) {
         List<JobInfo> jobs = jobScheduler.getAllPendingJobs();
         for (JobInfo jobInfo : jobs) {
            stopJob(jobInfo);
            threadAndTaskWait();
         }
      }
   }
   public static @Nullable JobInfo getNextJob() {
      return getJob(0);
   }

   private static @Nullable JobInfo getJob(int index) {
      JobScheduler jobScheduler =
              (JobScheduler)ApplicationProvider.getApplicationContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);
      List<JobInfo> jobs = jobScheduler.getAllPendingJobs();
      if (jobs.size() == 0 || jobs.size() <= index)
         return null;
      else
         return jobs.get(index);
   }

   public static void runNextJob() {
      try {
         Class jobClass = Class.forName(getNextJob().getService().getClassName());
         runJob(jobClass);
      } catch (ClassNotFoundException e) {
         e.printStackTrace();
      }
   }

   public static void runJob(Class jobClass) {
      JobService jobService = (JobService) Robolectric.buildService(jobClass).create().get();
      jobService.onStartJob(null);
   }

   public static void stopJob(JobInfo jobInfo) {
      try {
         Class jobClass = Class.forName(jobInfo.getService().getClassName());
         JobService jobService = (JobService) Robolectric.buildService(jobClass).create().get();
         jobService.jobFinished(null, false);
      } catch (ClassNotFoundException e) {
         e.printStackTrace();
      }
   }

   public static void assertNumberOfServicesAvailable(int quantity) {
      JobScheduler jobScheduler =
              (JobScheduler)ApplicationProvider.getApplicationContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);
      List<JobInfo> jobs = jobScheduler.getAllPendingJobs();
      assertEquals(quantity, jobs.size());
   }

   public static void assertAndRunNextJob(Class jobClass) {
      assertNumberOfServicesAvailable(1);
      assertNextJob(jobClass);
      runNextJob();
   }

   public static void assertAndRunJobAtIndex(Class jobClass, int index) {
      assertNextJob(jobClass, index);
      runJob(jobClass);
   }

   public static void assertNextJob(Class jobClass) {
      assertNextJob(jobClass, 0);
   }

   public static void assertNextJob(Class jobClass, int index) {
      assertEquals(jobClass.getName(), getJob(index).getService().getClassName());
   }

   public static void pauseActivity(ActivityController activityController) throws Exception {
      activityController.pause();
      threadAndTaskWait();
   }

   public static void assertAndRunSyncService() throws Exception {
      // There should be a SyncJobService service scheduled
      assertNumberOfServicesAvailable(1);
      // A sync job should have been schedule, lets run it to ensure on_focus is called.
      TestHelpers.assertAndRunJobAtIndex(SyncJobService.class, 0);
      threadAndTaskWait();
   }

   /**
    * Add the correct manifest meta-data key and value regarding the NotificationServiceExtension to the
    *    mocked OneSignalShadowPackageManager metaData Bundle
    */
   public static void startRemoteNotificationReceivedHandlerService(String servicePath) {
      OneSignalShadowPackageManager.addManifestMetaData("com.onesignal.NotificationServiceExtension", servicePath);
   }
}
