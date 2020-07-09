package com.onesignal.influence;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.onesignal.OSLogger;
import com.onesignal.OSSharedPreferences;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalRemoteParams;
import com.onesignal.influence.model.OSInfluence;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class OSTrackerFactory {

    private ConcurrentHashMap<String, OSChannelTracker> trackers = new ConcurrentHashMap<>();

    private OSInfluenceDataRepository dataRepository;

    public OSTrackerFactory(OSSharedPreferences preferences, OSLogger logger) {
        dataRepository = new OSInfluenceDataRepository(preferences);

        trackers.put(OSInAppMessageTracker.TAG, new OSInAppMessageTracker(dataRepository, logger));
        trackers.put(OSNotificationTracker.TAG, new OSNotificationTracker(dataRepository, logger));
    }

    public void saveInfluenceParams(OneSignalRemoteParams.InfluenceParams influenceParams) {
        dataRepository.saveInfluenceParams(influenceParams);
    }

    public void addSessionData(@NonNull JSONObject jsonObject, List<OSInfluence> influences) {
        for (OSInfluence influence : influences) {
            switch (influence.getInfluenceChannel()) {
                case NOTIFICATION:
                    getNotificationChannelTracker().addSessionData(jsonObject, influence);
                case IAM:
                    // We don't track IAM session data for on_focus call
            }
        }
    }

    public void initFromCache() {
        for (OSChannelTracker tracker : trackers.values()) {
            tracker.initInfluencedTypeFromCache();
        }
    }

    public List<OSInfluence> getInfluences() {
        List<OSInfluence> influences = new ArrayList<>();
        for (OSChannelTracker tracker : trackers.values()) {
            influences.add(tracker.getCurrentSessionInfluence());
        }
        return influences;
    }

    public List<OSInfluence> getSessionInfluences() {
        List<OSInfluence> influences = new ArrayList<>();
        for (OSChannelTracker tracker : trackers.values()) {
            // IAM doesn't influence session calls
            if (tracker instanceof OSInAppMessageTracker)
                continue;
            influences.add(tracker.getCurrentSessionInfluence());
        }
        return influences;
    }

    public OSChannelTracker getIAMChannelTracker() {
        return trackers.get(OSInAppMessageTracker.TAG);
    }

    public OSChannelTracker getNotificationChannelTracker() {
        return trackers.get(OSNotificationTracker.TAG);
    }

    @Nullable
    public OSChannelTracker getChannelByEntryAction(OneSignal.AppEntryAction entryAction) {
        if (entryAction.isNotificationClick())
            return getNotificationChannelTracker();

        return null;
    }

    public List<OSChannelTracker> getChannels() {
        List<OSChannelTracker> channels = new ArrayList<>();

        OSChannelTracker notificationChannel = getNotificationChannelTracker();
        if (notificationChannel != null)
            channels.add(notificationChannel);

        OSChannelTracker iamChannel = getIAMChannelTracker();
        if (iamChannel != null)
            channels.add(iamChannel);

        return channels;
    }

    public List<OSChannelTracker> getChannelsToResetByEntryAction(OneSignal.AppEntryAction entryAction) {
        List<OSChannelTracker> channels = new ArrayList<>();

        // Avoid reset session if application is closed
        if (entryAction.isAppClose())
            return channels;

        // Avoid reset session if app was focused due to a notification click (direct session recently set)
        if (entryAction.isAppOpen()) {
            OSChannelTracker notificationChannel = getNotificationChannelTracker();
            if (notificationChannel != null)
                channels.add(notificationChannel);
        }

        OSChannelTracker iamChannel = getIAMChannelTracker();
        if (iamChannel != null)
            channels.add(iamChannel);

        return channels;
    }

}
