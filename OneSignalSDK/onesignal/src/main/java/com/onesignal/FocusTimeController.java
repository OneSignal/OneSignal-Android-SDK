package com.onesignal;

import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entry points that could cause on_focus to fire:
 * 1. OSSessionManager.session changed (onSessionEnded) - Send any attributed session time
 * 2. App is foregrounded (appForegrounded) - Set start focused time
 * 3. App is backgrounded (appBackgrounded) - Kick off job to sync when session ends
 */
class FocusTimeController {
   // Only present if app is currently in focus.
   @Nullable private Long timeFocusedAtMs;

   private static FocusTimeController sInstance;

   private List<FocusTimeProcessorBase> focusTimeProcessors =
      Arrays.asList(new FocusTimeProcessorUnattributed(), new FocusTimeProcessorAttributed());

   private enum FocusEventType {
      BACKGROUND,
      END_SESSION
   }

   private FocusTimeController() { }
   public static synchronized FocusTimeController getInstance() {
      if (sInstance == null)
         sInstance = new FocusTimeController();
      return sInstance;
   }

   void appForegrounded() {
      timeFocusedAtMs = SystemClock.elapsedRealtime();
   }

   void appBackgrounded() {
      giveProcessorsValidFocusTime(OneSignal.getSessionManager().getSessionResult(), FocusEventType.BACKGROUND);
      timeFocusedAtMs = null;
   }

   void onSessionEnded(@NonNull OSSessionManager.SessionResult lastSessionResult) {
      final FocusEventType focusEventType = FocusEventType.END_SESSION;
      boolean hadValidTime = giveProcessorsValidFocusTime(lastSessionResult, focusEventType);

      // If there is no in focus time to be added we just need to send the time from the last session that just ended.
      if (!hadValidTime) {
         for (FocusTimeProcessorBase focusTimeProcessor : focusTimeProcessors)
            focusTimeProcessor.sendUnsentTimeNow(focusEventType);
      }
   }

   void doBlockingBackgroundSyncOfUnsentTime() {
      if (OneSignal.isForeground())
         return;

      for (FocusTimeProcessorBase focusTimeProcessor : focusTimeProcessors)
         focusTimeProcessor.syncUnsentTimeFromSyncJob();
   }

   private boolean giveProcessorsValidFocusTime(@NonNull OSSessionManager.SessionResult lastSessionResult, @NonNull FocusEventType focusType) {
      Long timeElapsed = getTimeFocusedElapsed();
      if (timeElapsed == null)
        return false;

      for (FocusTimeProcessorBase focusTimeProcessor : focusTimeProcessors)
         focusTimeProcessor.addTime(timeElapsed, lastSessionResult.session, focusType);
      return true;
   }

   // Get time past since app was put into focus.
   // Will be null if time is invalid or 0
   private @Nullable Long getTimeFocusedElapsed() {
      // timeFocusedAtMs is cleared when the app goes into the background so we don't have a focus time
      if (timeFocusedAtMs == null)
         return null;

      long timeElapsed = (long)(((SystemClock.elapsedRealtime() - timeFocusedAtMs) / 1_000d) + 0.5d);

      // Time is invalid if below 1 or over a day
      if (timeElapsed < 1 || timeElapsed > 86_400)
         return null;
      return timeElapsed;
   }

   private static class FocusTimeProcessorUnattributed extends FocusTimeProcessorBase {
      FocusTimeProcessorUnattributed() {
         MIN_ON_FOCUS_TIME_SEC = 60;
         PREF_KEY_FOR_UNSENT_TIME = OneSignalPrefs.PREFS_GT_UNSENT_ACTIVE_TIME;
      }

      protected boolean timeTypeApplies(@NonNull OSSessionManager.Session session) {
         return session.isUnattributed() || session.isDisabled();
      }

      protected void sendTime(@NonNull FocusEventType focusType) {
         // We only need to send unattributed focus time when the app goes out of focus.
         if (focusType.equals(FocusEventType.END_SESSION))
            return;

         syncUnsentTimeOnBackgroundEvent();
      }
   }

   private static class FocusTimeProcessorAttributed extends FocusTimeProcessorBase {
      FocusTimeProcessorAttributed() {
         MIN_ON_FOCUS_TIME_SEC = 1;
         PREF_KEY_FOR_UNSENT_TIME = OneSignalPrefs.PREFS_OS_UNSENT_ATTRIBUTED_ACTIVE_TIME;
      }

      protected boolean timeTypeApplies(@NonNull OSSessionManager.Session session) {
         return session.isAttributed();
      }

      protected void additionalFieldsToAddToOnFocusPayload(@NonNull JSONObject jsonBody) {
         OneSignal.getSessionManager().addSessionNotificationsIds(jsonBody);
      }

      protected void sendTime(@NonNull FocusEventType focusType) {
         if (focusType.equals(FocusEventType.END_SESSION))
            syncOnFocusTime();
         else
            OneSignalSyncServiceUtils.scheduleSyncTask(OneSignal.appContext);
      }
   }

   private static abstract class FocusTimeProcessorBase {
      // These values are set by child classes that inherit this base class
      protected long MIN_ON_FOCUS_TIME_SEC;
      protected @NonNull String PREF_KEY_FOR_UNSENT_TIME;

      protected abstract boolean timeTypeApplies(@NonNull OSSessionManager.Session session);
      protected abstract void sendTime(@NonNull FocusEventType focusType);

      @Nullable private Long unsentActiveTime = null;

      private long getUnsentActiveTime() {
         if (unsentActiveTime == null) {
            unsentActiveTime = OneSignalPrefs.getLong(
               OneSignalPrefs.PREFS_ONESIGNAL,
               PREF_KEY_FOR_UNSENT_TIME,
               0
            );
         }
         OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, this.getClass().getSimpleName() + ":getUnsentActiveTime: " + unsentActiveTime);
         return unsentActiveTime;
      }

      private void saveUnsentActiveTime(long time) {
         unsentActiveTime = time;
         OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, this.getClass().getSimpleName() + ":saveUnsentActiveTime: " + unsentActiveTime);
         OneSignalPrefs.saveLong(
            OneSignalPrefs.PREFS_ONESIGNAL,
            PREF_KEY_FOR_UNSENT_TIME,
            time
         );
      }

      private void addTime(long time, @NonNull OSSessionManager.Session session, @NonNull FocusEventType focusType) {
         if (!timeTypeApplies(session))
            return;

         long totalTime = getUnsentActiveTime() + time;
         saveUnsentActiveTime(totalTime);
         sendUnsentTimeNow(focusType);
      }

      private void sendUnsentTimeNow(FocusEventType focusType) {
         if (!OneSignal.hasUserId())
            return;

         sendTime(focusType);
      }

      private boolean hasMinSyncTime() {
         return getUnsentActiveTime() >= MIN_ON_FOCUS_TIME_SEC;
      }

      protected void syncUnsentTimeOnBackgroundEvent() {
         if (!hasMinSyncTime())
            return;
         // Schedule this sync in case app is killed before completing
         OneSignalSyncServiceUtils.scheduleSyncTask(OneSignal.appContext);
         syncOnFocusTime();
      }

      private void syncUnsentTimeFromSyncJob() {
         if (hasMinSyncTime())
            syncOnFocusTime();
      }

      @NonNull private final AtomicBoolean runningOnFocusTime = new AtomicBoolean();
      @WorkerThread
      protected void syncOnFocusTime() {
         if (runningOnFocusTime.get())
            return;

         synchronized (runningOnFocusTime) {
            runningOnFocusTime.set(true);
            if (hasMinSyncTime())
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
               // TODO: PRE-EXISTING: This time is shared between the email + push player and
               //          is cleared no matter which one is successful.
               // TODO: PRE-EXISTING: This could be clearing time more then was persisted while the network call was in flight
               saveUnsentActiveTime(0);
            }
         };
         String url = "players/" + userId + "/on_focus";
         OneSignalRestClient.postSync(url, jsonBody, responseHandler);
      }

      // Override Optional
      protected void additionalFieldsToAddToOnFocusPayload(@NonNull JSONObject jsonBody) { }

      private @NonNull JSONObject generateOnFocusPayload(long totalTimeActive) throws JSONException {
         JSONObject jsonBody = new JSONObject()
            .put("app_id", OneSignal.getSavedAppId())
            .put("type", 1) // Always 1, where this type means do NOT increase session_count
            .put("state", "ping") // Always ping, other types are not used
            .put("active_time", totalTimeActive)
            .put("device_type", new OSUtils().getDeviceType());
         OneSignal.addNetType(jsonBody);
         return jsonBody;
      }

      private void sendOnFocus(long totalTimeActive) {
         try {
            JSONObject jsonBody = generateOnFocusPayload(totalTimeActive);
            additionalFieldsToAddToOnFocusPayload(jsonBody);
            sendOnFocusToPlayer(OneSignal.getUserId(), jsonBody);

            // For email we omit additionalFieldsToAddToOnFocusPayload as we don't want to add
            //   outcome fields which would double report the session time
            if (OneSignal.hasEmailId())
               sendOnFocusToPlayer(OneSignal.getEmailId(), generateOnFocusPayload(totalTimeActive));
         }
         catch (JSONException t) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating on_focus:JSON Failed.", t);
         }
      }
   }
}
