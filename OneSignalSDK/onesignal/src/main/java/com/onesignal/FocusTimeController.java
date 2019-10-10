package com.onesignal;

import android.content.Context;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
  TODO:1: FocusTimerAccumulator
    We need to two timers for these on_focus types;
      1. Current on_focus
        - Time counted, UNLESS we have an outcome timer tracking time.
        - Min Time - Send network call ONLY if we have 60+ seconds.
      2. Outcome on_focus
        - Time counted when we have an INDIRECT or DIRECT session going.
        - Min Time - Send network call ONLY if we have 60+ seconds.

  TODO:2: FocusTimerController;
    We need a manager to know which timer to add the time to since we only want one active.
    The FocusTimerManager could swap a current **instance** based on a set of rules
      - The only rule for **swapping** will be a session type change
    This will meet the requirements of TODO:1

  TODO:3: FocusTimerAction
    Something like AttributeTime would fire from the FocusTimerAccumulator

  TODO:4: FocusTimerProcessor instead of FocusTimerAccumulator
    This will make the network call but through an OSOnFocusRestPerformer?
*/

class FocusTimeController {

   private static final long MIN_ON_FOCUS_TIME_SEC = 60;

   @NonNull private Context context;
   @Nullable private Long unSentActiveTime = null;
   private long timeFocusedAtMs = SystemClock.elapsedRealtime();

   private static FocusTimeController sInstance;

   private FocusTimeController(@NonNull Context context) {
      this.context = context;
   }

   public static synchronized FocusTimeController getInstance(Context context) {
      if (sInstance == null)
         sInstance = new FocusTimeController(context.getApplicationContext());
      return sInstance;
   }

   void appForegrounded() {
      timeFocusedAtMs = SystemClock.elapsedRealtime();
   }

   void appBackgrounded() {
      syncOnFocusTime();
   }

   private long getTotalUnsentTimeActive() {
      long timeElapsed = (long)(((SystemClock.elapsedRealtime() - timeFocusedAtMs) / 1_000d) + 0.5d);
      timeFocusedAtMs = SystemClock.elapsedRealtime();

      // Time is invalid, count as zero
      if (timeElapsed < 0 || timeElapsed > 86_400)
         return 0;

      long unSentActiveTime = getUnsentActiveTime();
      long totalUnsentTimeActive = unSentActiveTime + timeElapsed;

      saveUnsentActiveTime(totalUnsentTimeActive);
      return totalUnsentTimeActive;
   }

   private boolean isOnFocusNeeded() {
      return
         (
           getTotalUnsentTimeActive() >= MIN_ON_FOCUS_TIME_SEC ||
           OneSignal.isOnFocusNeeded() // TODO: OneSignal.isOnFocusNeeded() should have outcome in the name
         )
         && OneSignal.hasUserId();
   }

   private long getUnsentActiveTime() {
      if (unSentActiveTime == null) {
         // TODO: Could this be getting zero when context hasn't been set yet!
         unSentActiveTime = OneSignalPrefs.getLong(
            OneSignalPrefs.PREFS_ONESIGNAL,
            OneSignalPrefs.PREFS_GT_UNSENT_ACTIVE_TIME,
            0
         );
      }
      OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "getUnsentActiveTime: " + unSentActiveTime);
      return unSentActiveTime;
   }


   private void saveUnsentActiveTime(long time) {
      unSentActiveTime = time;
      OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "saveUnsentActiveTime: " + unSentActiveTime);
      OneSignalPrefs.saveLong(OneSignalPrefs.PREFS_ONESIGNAL,
         OneSignalPrefs.PREFS_GT_UNSENT_ACTIVE_TIME, time);
   }

   // TODO: Can this just be a synchronized?
   //    No, as we could have an out of focus event while a background job running it and
   //    it would cause a thread to wait unnecessary
   @NonNull private final AtomicBoolean runningOnFocusTime = new AtomicBoolean();
   @WorkerThread
   void syncOnFocusTime() {
      if (runningOnFocusTime.get())
         return;

      synchronized (runningOnFocusTime) {
         runningOnFocusTime.set(true);
         if (isOnFocusNeeded())
            sendOnFocus(getUnsentActiveTime());
         runningOnFocusTime.set(false);
      }
   }

   private void sendOnFocusToPlayer(@NonNull String userId, @NonNull JSONObject jsonBody) {
      OneSignalRestClient.ResponseHandler responseHandler = new OneSignalRestClient.ResponseHandler() {
         @Override
         void onFailure(int statusCode, String response, Throwable throwable) {
            OneSignal.logHttpError("sending on_focus Failed", statusCode, throwable, response);
         }

         @Override
         void onSuccess(String response) {
            // TODO: This time is shared between the email + push player and
            //          is cleared no matter which one is successful.

            // TODO: This could be clearing time more time was persisted while the network call was in flight
            saveUnsentActiveTime(0);
         }
      };
      String url = "players/" + userId + "/on_focus";
      OneSignalRestClient.postSync(url, jsonBody, responseHandler);
   }

   private @Nullable JSONObject generateOnFocusPayload(long totalTimeActive) {
      try {
         JSONObject jsonBody = new JSONObject()
            .put("app_id", OneSignal.appId)
            .put("type", 1) // Always 1, where this type means do NOT increase session_count
            .put("state", "ping") // Always ping, other types are not used
            .put("active_time", totalTimeActive)
            .put("device_type", new OSUtils().getDeviceType());
         OneSignal.sessionManager.addSessionNotificationsIds(jsonBody);
         OneSignal.addNetType(jsonBody);
         return jsonBody;
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating on_focus:JSON Failed.", t);
         return null;
      }
   }

   private void sendOnFocus(long totalTimeActive) {
      JSONObject jsonBody = generateOnFocusPayload(totalTimeActive);
      if (jsonBody == null)
         return;

      sendOnFocusToPlayer(OneSignal.getUserId(), jsonBody);
      if (OneSignal.hasEmailId())
         sendOnFocusToPlayer(OneSignal.getEmailId(), jsonBody);

      OneSignal.markOnFocusCalled(); // TODO: OneSignal.markOnFocusCalled() should have outcome in the name
   }

}
