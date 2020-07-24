package com.onesignal;

import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.onesignal.influence.OSChannelTracker;
import com.onesignal.influence.OSTrackerFactory;
import com.onesignal.influence.model.OSInfluence;
import com.onesignal.influence.model.OSInfluenceType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager in charge to check what type of session is active
 * <p>
 * Types of sessions
 * - Direct: the session occurred due to a push
 * - Indirect: the session occurred on a time frame less than 24hrs
 * - Unattributed: the session was not influenced nor was on the time frame os a push
 */
public class OSSessionManager {

    private static final String OS_END_CURRENT_SESSION = "OS_END_CURRENT_SESSION";

    public interface SessionListener {
        // Fire with the last OSInfluence that just ended.
        void onSessionEnding(@NonNull List<OSInfluence> lastInfluences);
    }

    protected OSTrackerFactory trackerFactory;
    private SessionListener sessionListener;
    private OSLogger logger;

    public OSSessionManager(@NonNull SessionListener sessionListener, OSTrackerFactory trackerFactory, OSLogger logger) {
        this.sessionListener = sessionListener;
        this.trackerFactory = trackerFactory;
        this.logger = logger;
    }

    void initSessionFromCache() {
        logger.debug("OneSignal SessionManager initSessionFromCache");
        trackerFactory.initFromCache();
    }

    void addSessionIds(@NonNull JSONObject jsonObject, List<OSInfluence> endingInfluences) {
        logger.debug("OneSignal SessionManager addSessionData with influences: " + endingInfluences.toString());
        trackerFactory.addSessionData(jsonObject, endingInfluences);
        logger.debug("OneSignal SessionManager addSessionIds on jsonObject: " + jsonObject);
    }

    void restartSessionIfNeeded(OneSignal.AppEntryAction entryAction) {
        List<OSChannelTracker> channelTrackers = trackerFactory.getChannelsToResetByEntryAction(entryAction);
        List<OSInfluence> updatedInfluences = new ArrayList<>();

        logger.debug("OneSignal SessionManager restartSessionIfNeeded with entryAction: " + entryAction + "\n channelTrackers: " + channelTrackers.toString());
        for (OSChannelTracker channelTracker : channelTrackers) {
            JSONArray lastIds = channelTracker.getLastReceivedIds();
            logger.debug("OneSignal SessionManager restartSessionIfNeeded lastIds: " + lastIds);

            OSInfluence influence = channelTracker.getCurrentSessionInfluence();
            boolean updated;
            if (lastIds.length() > 0)
                updated = setSession(channelTracker, OSInfluenceType.INDIRECT, null, lastIds);
            else
                updated = setSession(channelTracker, OSInfluenceType.UNATTRIBUTED, null, null);

            if (updated)
                updatedInfluences.add(influence);
        }

        sendSessionEndingWithInfluences(updatedInfluences);
    }

    void onInAppMessageReceived(@NonNull String messageId) {
        logger.debug("OneSignal SessionManager onInAppMessageReceived messageId: " + messageId);
        OSChannelTracker inAppMessageTracker = trackerFactory.getIAMChannelTracker();
        inAppMessageTracker.saveLastId(messageId);
        inAppMessageTracker.resetAndInitInfluence();
    }

    void onDirectInfluenceFromIAMClick(@NonNull String messageId) {
        logger.debug("OneSignal SessionManager onDirectInfluenceFromIAMClick messageId: " + messageId);
        OSChannelTracker inAppMessageTracker = trackerFactory.getIAMChannelTracker();
        // We don't care about ending the session duration because IAM doesn't influence a session
        setSession(inAppMessageTracker, OSInfluenceType.DIRECT, messageId, null);
    }

    void onDirectInfluenceFromIAMClickFinished() {
        logger.debug("OneSignal SessionManager onDirectInfluenceFromIAMClickFinished");
        OSChannelTracker inAppMessageTracker = trackerFactory.getIAMChannelTracker();
        inAppMessageTracker.resetAndInitInfluence();
    }

    void onNotificationReceived(@Nullable String notificationId) {
        logger.debug("OneSignal SessionManager onNotificationReceived notificationId: " + notificationId);
        if (notificationId == null || notificationId.isEmpty())
            return;
        OSChannelTracker notificationTracker = trackerFactory.getNotificationChannelTracker();
        notificationTracker.saveLastId(notificationId);
    }

    void onDirectInfluenceFromNotificationOpen(OneSignal.AppEntryAction entryAction, @Nullable String notificationId) {
        logger.debug("OneSignal SessionManager onDirectInfluenceFromNotificationOpen notificationId: " + notificationId);
        if (notificationId == null || notificationId.isEmpty())
            return;

        attemptSessionUpgrade(entryAction, notificationId);
    }

    // Get the current session based on state + if outcomes features are enabled.
    @NonNull
    List<OSInfluence> getInfluences() {
        return trackerFactory.getInfluences();
    }

    @NonNull
    List<OSInfluence> getSessionInfluences() {
        return trackerFactory.getSessionInfluences();
    }

    /**
     * Attempt to override the current session before the 30 second session minimum
     * This should only be done in a upward direction:
     * * UNATTRIBUTED can become INDIRECT or DIRECT
     * * INDIRECT can become DIRECT
     * * DIRECT can become DIRECT
     */
    void attemptSessionUpgrade(OneSignal.AppEntryAction entryAction) {
        attemptSessionUpgrade(entryAction, null);
    }

    private void attemptSessionUpgrade(OneSignal.AppEntryAction entryAction, @Nullable String directId) {
        logger.debug("OneSignal SessionManager attemptSessionUpgrade with entryAction: " + entryAction);
        OSChannelTracker channelTrackerByAction = trackerFactory.getChannelByEntryAction(entryAction);
        List<OSChannelTracker> channelTrackersToReset = trackerFactory.getChannelsToResetByEntryAction(entryAction);
        List<OSInfluence> influencesToEnd = new ArrayList<>();
        OSInfluence lastInfluence = null;

        // We will try to override any session with DIRECT
        boolean updated = false;
        if (channelTrackerByAction != null) {
            lastInfluence = channelTrackerByAction.getCurrentSessionInfluence();
            updated = setSession(channelTrackerByAction,
                    OSInfluenceType.DIRECT,
                    directId == null ? channelTrackerByAction.getDirectId() : directId,
                    null);
        }

        if (updated) {
            logger.debug("OneSignal SessionManager attemptSessionUpgrade channel updated, search for ending direct influences on channels: " + channelTrackersToReset);
            influencesToEnd.add(lastInfluence);
            // Only one session influence channel can be DIRECT at the same time
            // Reset other DIRECT channels, they will init an INDIRECT influence
            // In that way we finish the session duration time for the last influenced session
            for (OSChannelTracker tracker : channelTrackersToReset) {
                if (tracker.getInfluenceType().isDirect()) {
                    influencesToEnd.add(tracker.getCurrentSessionInfluence());
                    tracker.resetAndInitInfluence();
                }
            }
        }
        logger.debug("OneSignal SessionManager attemptSessionUpgrade try UNATTRIBUTED to INDIRECT upgrade");
        // We will try to override the UNATTRIBUTED session with INDIRECT
        for (OSChannelTracker channelTracker : channelTrackersToReset) {
            if (channelTracker.getInfluenceType().isUnattributed()) {
                JSONArray lastIds = channelTracker.getLastReceivedIds();
                // There are new ids for attribution and the application was open again without resetting session
                if (lastIds.length() > 0 && !entryAction.isAppClose()) {
                    // Save influence to ended it later if needed
                    // This influence will be unattributed
                    OSInfluence influence = channelTracker.getCurrentSessionInfluence();
                    updated = setSession(channelTracker, OSInfluenceType.INDIRECT, null, lastIds);
                    // Changed from UNATTRIBUTED to INDIRECT
                    if (updated)
                        influencesToEnd.add(influence);
                }
            }
        }

        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Trackers after update attempt: " + trackerFactory.getChannels().toString());
        sendSessionEndingWithInfluences(influencesToEnd);
    }

    // Call when the session for the app changes, caches the state, and broadcasts the session that just ended
    private boolean setSession(@NonNull OSChannelTracker channelTracker,
                               @NonNull OSInfluenceType influenceType,
                               @Nullable String directNotificationId,
                               @Nullable JSONArray indirectNotificationIds) {
        if (!willChangeSession(channelTracker, influenceType, directNotificationId, indirectNotificationIds))
            return false;

        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG,
                "OSChannelTracker changed: " + channelTracker.getIdTag() +
                        "\nfrom:\n" +
                        "influenceType: " + channelTracker.getInfluenceType() +
                        ", directNotificationId: " + channelTracker.getDirectId() +
                        ", indirectNotificationIds: " + channelTracker.getIndirectIds() +
                        "\nto:\n" +
                        "influenceType: " + influenceType +
                        ", directNotificationId: " + directNotificationId +
                        ", indirectNotificationIds: " + indirectNotificationIds);

        channelTracker.setInfluenceType(influenceType);
        channelTracker.setDirectId(directNotificationId);
        channelTracker.setIndirectIds(indirectNotificationIds);
        channelTracker.cacheState();

        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Trackers changed to: " + trackerFactory.getChannels().toString());
        // Session changed
        return true;
    }

    private boolean willChangeSession(@NonNull OSChannelTracker channelTracker,
                                      @NonNull OSInfluenceType influenceType,
                                      @Nullable String directNotificationId,
                                      @Nullable JSONArray indirectNotificationIds) {
        if (!influenceType.equals(channelTracker.getInfluenceType()))
            return true;

        OSInfluenceType channelInfluenceType = channelTracker.getInfluenceType();
        // Allow updating a direct session to a new direct when a new notification is clicked
        if (channelInfluenceType.isDirect() &&
                channelTracker.getDirectId() != null &&
                !channelTracker.getDirectId().equals(directNotificationId)) {
            return true;
        }

        // Allow updating an indirect session to a new indirect when a new notification is received
        return channelInfluenceType.isIndirect() &&
                channelTracker.getIndirectIds() != null &&
                channelTracker.getIndirectIds().length() > 0 &&
                !JSONUtils.compareJSONArrays(channelTracker.getIndirectIds(), indirectNotificationIds);
    }

    private void sendSessionEndingWithInfluences(final List<OSInfluence> endingInfluences) {
        logger.debug("OneSignal SessionManager sendSessionEndingWithInfluences with influences: " + endingInfluences);
        // Only end session if there are influences available to end
        if (endingInfluences.size() > 0) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    sessionListener.onSessionEnding(endingInfluences);
                }
            }, OS_END_CURRENT_SESSION).start();
        }
    }
}
