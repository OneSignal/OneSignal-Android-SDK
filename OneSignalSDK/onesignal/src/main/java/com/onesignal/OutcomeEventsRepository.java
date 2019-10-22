package com.onesignal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

class OutcomeEventsRepository {

    private static final String APP_ID = "app_id";
    private static final String DEVICE_TYPE = "device_type";
    private static final String DIRECT = "direct";

    private final OutcomeEventsService outcomeEventsService;
    private final OneSignalDbHelper dbHelper;

    OutcomeEventsRepository(OneSignalDbHelper dbHelper) {
        this.outcomeEventsService = new OutcomeEventsService();
        this.dbHelper = dbHelper;
    }

    OutcomeEventsRepository(OutcomeEventsService outcomeEventsService, OneSignalDbHelper dbHelper) {
        this.outcomeEventsService = outcomeEventsService;
        this.dbHelper = dbHelper;
    }

    List<OutcomeEvent> getSavedOutcomeEvents() {
        return OutcomeEventsCache.getAllEventsToSend(dbHelper);
    }

    void saveOutcomeEvent(OutcomeEvent event) {
        OutcomeEventsCache.saveOutcomeEvent(event, dbHelper);
    }

    void removeEvent(OutcomeEvent outcomeEvent) {
        OutcomeEventsCache.deleteOldOutcomeEvent(outcomeEvent, dbHelper);
    }

    void requestMeasureDirectOutcomeEvent(String appId, int deviceType, OutcomeEvent event, OneSignalRestClient.ResponseHandler responseHandler) {
        JSONObject jsonObject = event.toJSONObjectForMeasure();
        try {
            jsonObject.put(APP_ID, appId);
            jsonObject.put(DEVICE_TYPE, deviceType);
            jsonObject.put(DIRECT, true);

            outcomeEventsService.sendOutcomeEvent(jsonObject, responseHandler);

        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating direct outcome:JSON Failed.", e);
        }
    }

    void requestMeasureIndirectOutcomeEvent(String appId, int deviceType, OutcomeEvent event, OneSignalRestClient.ResponseHandler responseHandler) {
        JSONObject jsonObject = event.toJSONObjectForMeasure();
        try {
            jsonObject.put(APP_ID, appId);
            jsonObject.put(DEVICE_TYPE, deviceType);
            jsonObject.put(DIRECT, false);

            outcomeEventsService.sendOutcomeEvent(jsonObject, responseHandler);

        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating indirect outcome:JSON Failed.", e);
        }
    }

    void requestMeasureUnattributedOutcomeEvent(String appId, int deviceType, OutcomeEvent event, OneSignalRestClient.ResponseHandler responseHandler) {
        JSONObject jsonObject = event.toJSONObjectForMeasure();
        try {
            jsonObject.put(APP_ID, appId);
            jsonObject.put(DEVICE_TYPE, deviceType);

            outcomeEventsService.sendOutcomeEvent(jsonObject, responseHandler);

        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating unattributed outcome:JSON Failed.", e);
        }
    }

    void saveUniqueOutcomeNotifications(JSONArray notificationIds, String name) {
        OutcomeEventsCache.saveUniqueOutcomeNotifications(notificationIds, name, dbHelper);
    }

    JSONArray getNotCachedUniqueOutcomeNotifications(String name, JSONArray notificationIds) {
        return OutcomeEventsCache.getNotCachedUniqueOutcomeNotifications(name, notificationIds, dbHelper);
    }
}