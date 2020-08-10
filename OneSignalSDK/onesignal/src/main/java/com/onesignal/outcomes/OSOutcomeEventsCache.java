package com.onesignal.outcomes;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.onesignal.OSLogger;
import com.onesignal.OSSharedPreferences;
import com.onesignal.OneSignalDb;
import com.onesignal.influence.model.OSInfluence;
import com.onesignal.influence.model.OSInfluenceChannel;
import com.onesignal.influence.model.OSInfluenceType;
import com.onesignal.outcomes.OSOutcomesDbContract.CachedUniqueOutcomeTable;
import com.onesignal.outcomes.OSOutcomesDbContract.OutcomeEventsTable;
import com.onesignal.outcomes.model.OSCachedUniqueOutcome;
import com.onesignal.outcomes.model.OSOutcomeEventParams;
import com.onesignal.outcomes.model.OSOutcomeSource;
import com.onesignal.outcomes.model.OSOutcomeSourceBody;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class OSOutcomeEventsCache {

    private static final String PREFS_OS_UNATTRIBUTED_UNIQUE_OUTCOME_EVENTS_SENT = "PREFS_OS_UNATTRIBUTED_UNIQUE_OUTCOME_EVENTS_SENT";

    private OSLogger logger;
    private OneSignalDb dbHelper;
    private OSSharedPreferences preferences;

    OSOutcomeEventsCache(OSLogger logger, OneSignalDb dbHelper, OSSharedPreferences preferences) {
        this.logger = logger;
        this.dbHelper = dbHelper;
        this.preferences = preferences;
    }

    boolean isOutcomesV2ServiceEnabled() {
        return preferences.getBool(
                preferences.getPreferencesName(),
                preferences.getOutcomesV2KeyName(),
                false);
    }

    Set<String> getUnattributedUniqueOutcomeEventsSentByChannel() {
        return preferences.getStringSet(
                preferences.getPreferencesName(),
                PREFS_OS_UNATTRIBUTED_UNIQUE_OUTCOME_EVENTS_SENT,
                null);
    }

    void saveUnattributedUniqueOutcomeEventsSentByChannel(Set<String> unattributedUniqueOutcomeEvents) {
        preferences.saveStringSet(
                preferences.getPreferencesName(),
                PREFS_OS_UNATTRIBUTED_UNIQUE_OUTCOME_EVENTS_SENT,
                // Post success, store unattributed unique outcome event names
                unattributedUniqueOutcomeEvents);
    }

    /**
     * Delete event from the DB
     */
    @WorkerThread
    synchronized void deleteOldOutcomeEvent(OSOutcomeEventParams event) {
        dbHelper.delete(OutcomeEventsTable.TABLE_NAME,
                OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + " = ?", new String[]{String.valueOf(event.getTimestamp())});
    }

    /**
     * Save an outcome event to send it on the future
     * <p>
     * For offline mode and contingency of errors
     */
    @WorkerThread
    synchronized void saveOutcomeEvent(OSOutcomeEventParams eventParams) {
        JSONArray notificationIds = new JSONArray();
        JSONArray iamIds = new JSONArray();
        OSInfluenceType notificationInfluenceType = OSInfluenceType.UNATTRIBUTED;
        OSInfluenceType iamInfluenceType = OSInfluenceType.UNATTRIBUTED;

        if (eventParams.getOutcomeSource() != null) {
            OSOutcomeSource source = eventParams.getOutcomeSource();
            // Check for direct channels
            if (source.getDirectBody() != null) {
                OSOutcomeSourceBody directBody = source.getDirectBody();

                if (directBody.getNotificationIds() != null && directBody.getNotificationIds().length() > 0) {
                    notificationInfluenceType = OSInfluenceType.DIRECT;
                    notificationIds = source.getDirectBody().getNotificationIds();
                }

                if (directBody.getInAppMessagesIds() != null && directBody.getInAppMessagesIds().length() > 0) {
                    iamInfluenceType = OSInfluenceType.DIRECT;
                    iamIds = source.getDirectBody().getInAppMessagesIds();
                }
            }
            // Check for indirect channels
            if (source.getIndirectBody() != null) {
                OSOutcomeSourceBody indirectBody = source.getIndirectBody();

                if (indirectBody.getNotificationIds() != null && indirectBody.getNotificationIds().length() > 0) {
                    notificationInfluenceType = OSInfluenceType.INDIRECT;
                    notificationIds = source.getIndirectBody().getNotificationIds();
                }

                if (indirectBody.getInAppMessagesIds() != null && indirectBody.getInAppMessagesIds().length() > 0) {
                    iamInfluenceType = OSInfluenceType.INDIRECT;
                    iamIds = source.getIndirectBody().getInAppMessagesIds();
                }
            }
        }
        ContentValues values = new ContentValues();
        // Save influence ids
        values.put(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS, notificationIds.toString());
        values.put(OutcomeEventsTable.COLUMN_NAME_IAM_IDS, iamIds.toString());
        // Save influence types
        values.put(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE, notificationInfluenceType.toString().toLowerCase());
        values.put(OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE, iamInfluenceType.toString().toLowerCase());
        // Save outcome data
        values.put(OutcomeEventsTable.COLUMN_NAME_NAME, eventParams.getOutcomeId());
        values.put(OutcomeEventsTable.COLUMN_NAME_WEIGHT, eventParams.getWeight());
        values.put(OutcomeEventsTable.COLUMN_NAME_TIMESTAMP, eventParams.getTimestamp());

        dbHelper.insert(OutcomeEventsTable.TABLE_NAME, null, values);
    }

    /**
     * Save an outcome event to send it on the future
     * <p>
     * For offline mode and contingency of errors
     */
    @WorkerThread
    synchronized List<OSOutcomeEventParams> getAllEventsToSend() {
        List<OSOutcomeEventParams> events = new ArrayList<>();
        Cursor
                cursor = dbHelper.query(OutcomeEventsTable.TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                null
        );

        if (cursor.moveToFirst()) {
            do {
                // Retrieve influence types
                String notificationInfluenceTypeString = cursor.getString(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE));
                OSInfluenceType notificationInfluenceType = OSInfluenceType.fromString(notificationInfluenceTypeString);
                String iamInfluenceTypeString = cursor.getString(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE));
                OSInfluenceType iamInfluenceType = OSInfluenceType.fromString(iamInfluenceTypeString);

                // Retrieve influence ids
                String notificationIds = cursor.getString(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS));
                notificationIds = notificationIds != null ? notificationIds : "[]";
                String iamIds = cursor.getString(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_IAM_IDS));
                iamIds = iamIds != null ? iamIds : "[]";

                // Retrieve Outcome data
                String name = cursor.getString(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_NAME));
                float weight = cursor.getFloat(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_WEIGHT));
                long timestamp = cursor.getLong(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_TIMESTAMP));

                try {
                    OSOutcomeSourceBody directSourceBody = new OSOutcomeSourceBody();
                    OSOutcomeSourceBody indirectSourceBody = new OSOutcomeSourceBody();
                    OSOutcomeSource source = null;

                    switch (notificationInfluenceType) {
                        case DIRECT:
                            directSourceBody.setNotificationIds(new JSONArray(notificationIds));
                            source = new OSOutcomeSource(directSourceBody, null);
                            break;
                        case INDIRECT:
                            indirectSourceBody.setNotificationIds(new JSONArray(notificationIds));
                            source = new OSOutcomeSource(null, indirectSourceBody);
                            break;
                        case UNATTRIBUTED:
                            // Keep source as null, no source mean unattributed
                            break;
                        case DISABLED:
                            // We should not save disable
                            break;
                    }

                    switch (iamInfluenceType) {
                        case DIRECT:
                            directSourceBody.setInAppMessagesIds(new JSONArray(iamIds));
                            source = source == null ? new OSOutcomeSource(directSourceBody, null) : source.setDirectBody(directSourceBody);
                            break;
                        case INDIRECT:
                            indirectSourceBody.setInAppMessagesIds(new JSONArray(iamIds));
                            source = source == null ? new OSOutcomeSource(null, indirectSourceBody) : source.setIndirectBody(indirectSourceBody);
                            break;
                        case UNATTRIBUTED:
                            // Keep source as null, no source mean unattributed
                            break;
                        case DISABLED:
                            // We should not save disable
                            break;
                    }

                    OSOutcomeEventParams eventParams = new OSOutcomeEventParams(name, source, weight, timestamp);
                    events.add(eventParams);
                } catch (JSONException e) {
                    logger.error("Generating JSONArray from notifications ids outcome:JSON Failed.", e);
                }
            } while (cursor.moveToNext());
        }

        cursor.close();

        return events;
    }

    private void addIdToListFromChannel(List<OSCachedUniqueOutcome> cachedUniqueOutcomes, JSONArray channelIds, OSInfluenceChannel channel) {
        if (channelIds != null) {
            for (int i = 0; i < channelIds.length(); i++) {
                try {
                    String influenceId = channelIds.getString(i);
                    cachedUniqueOutcomes.add(new OSCachedUniqueOutcome(influenceId, channel));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void addIdsToListFromSource(List<OSCachedUniqueOutcome> cachedUniqueOutcomes, OSOutcomeSourceBody sourceBody) {
        if (sourceBody != null) {
            JSONArray iamIds = sourceBody.getInAppMessagesIds();
            JSONArray notificationIds = sourceBody.getNotificationIds();

            addIdToListFromChannel(cachedUniqueOutcomes, iamIds, OSInfluenceChannel.IAM);
            addIdToListFromChannel(cachedUniqueOutcomes, notificationIds, OSInfluenceChannel.NOTIFICATION);
        }
    }

    /**
     * Save a JSONArray of notification ids as separate items with the unique outcome name
     */
    @WorkerThread
    synchronized void saveUniqueOutcomeEventParams(@NonNull OSOutcomeEventParams eventParams) {
        logger.debug("OneSignal saveUniqueOutcomeEventParams: " + eventParams.toString());
        if (eventParams.getOutcomeSource() == null)
            return;

        String outcomeName = eventParams.getOutcomeId();
        List<OSCachedUniqueOutcome> cachedUniqueOutcomes = new ArrayList<>();

        OSOutcomeSourceBody directBody = eventParams.getOutcomeSource().getDirectBody();
        OSOutcomeSourceBody indirectBody = eventParams.getOutcomeSource().getIndirectBody();

        addIdsToListFromSource(cachedUniqueOutcomes, directBody);
        addIdsToListFromSource(cachedUniqueOutcomes, indirectBody);

        for (OSCachedUniqueOutcome uniqueOutcome : cachedUniqueOutcomes) {
            ContentValues values = new ContentValues();

            values.put(CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID, uniqueOutcome.getInfluenceId());
            values.put(CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE, String.valueOf(uniqueOutcome.getChannel()));
            values.put(CachedUniqueOutcomeTable.COLUMN_NAME_NAME, outcomeName);

            dbHelper.insert(CachedUniqueOutcomeTable.TABLE_NAME, null, values);
        }
    }

    /**
     * Create a JSONArray of not cached notification ids from the unique outcome notifications SQL table
     */
    @WorkerThread
    synchronized List<OSInfluence> getNotCachedUniqueInfluencesForOutcome(String name, List<OSInfluence> influences) {
        List<OSInfluence> uniqueInfluences = new ArrayList<>();
        Cursor cursor = null;

        try {
            for (OSInfluence influence : influences) {
                JSONArray availableInfluenceIds = new JSONArray();
                JSONArray influenceIds = influence.getIds();

                if (influenceIds == null)
                    continue;

                for (int i = 0; i < influenceIds.length(); i++) {
                    String channelInfluenceId = influenceIds.getString(i);
                    OSInfluenceChannel channel = influence.getInfluenceChannel();

                    String[] columns = new String[]{};

                    String where = CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID + " = ? AND " +
                            CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE + " = ? AND " +
                            CachedUniqueOutcomeTable.COLUMN_NAME_NAME + " = ?";

                    String[] args = new String[]{channelInfluenceId, String.valueOf(channel), name};

                    cursor = dbHelper.query(
                            CachedUniqueOutcomeTable.TABLE_NAME,
                            columns,
                            where,
                            args,
                            null,
                            null,
                            null,
                            "1"
                    );

                    // Item is not cached, we can use the influence id, add it to the JSONArray
                    if (cursor.getCount() == 0)
                        availableInfluenceIds.put(channelInfluenceId);
                }

                if (availableInfluenceIds.length() > 0) {
                    OSInfluence newInfluence = influence.copy();
                    newInfluence.setIds(availableInfluenceIds);
                    uniqueInfluences.add(newInfluence);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed())
                cursor.close();
        }

        return uniqueInfluences;
    }
}
