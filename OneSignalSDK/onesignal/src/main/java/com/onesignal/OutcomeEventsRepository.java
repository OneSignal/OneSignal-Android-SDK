package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

class OutcomeEventsRepository {

    private static final String APP_ID = "app_id";
    private static final String DEVICE_TYPE = "device_type";
    private static final String DIRECT = "direct";
    private static final String NOTIFICATION_ID = "notification_id";
    private static final String WEIGHT = "weight";
    private static final String TIME = "time";

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

    void removeEvents(List<OutcomeEvent> outcomeEvents) {
        OutcomeEventsCache.deleteOldOutcomeEvents(outcomeEvents, dbHelper);
    }

    void requestMeasureDirectOutcomeEvent(String outcomeId, String appId, String notificationId, int deviceType, Long timestamp,
                                          OneSignalRestClient.ResponseHandler responseHandler) {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put(APP_ID, appId)
                    .put(DEVICE_TYPE, deviceType)
                    .put(DIRECT, true)
                    .put(NOTIFICATION_ID, notificationId);

            if (timestamp != null)
                jsonBody.put(TIME, timestamp);

            outcomeEventsService.sendOutcomeEvent(outcomeId, jsonBody, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating direct outcome:JSON Failed.", e);
        }
    }

    void requestMeasureDirectOutcomeEvent(String outcomeId, String appId, String notificationId, int deviceType,
                                          OneSignalRestClient.ResponseHandler responseHandler) {
        requestMeasureDirectOutcomeEvent(outcomeId, appId, notificationId, deviceType, null, responseHandler);
    }

    void requestMeasureIndirectOutcomeEvent(String outcomeId, String appId, String notificationId, int deviceType, Long timestamp,
                                            OneSignalRestClient.ResponseHandler responseHandler) {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put(APP_ID, appId)
                    .put(DEVICE_TYPE, deviceType)
                    .put(DIRECT, false)
                    .put(NOTIFICATION_ID, notificationId);

            if (timestamp != null)
                jsonBody.put(TIME, timestamp);

            outcomeEventsService.sendOutcomeEvent(outcomeId, jsonBody, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating indirect outcome:JSON Failed.", e);
        }
    }

    void requestMeasureIndirectOutcomeEvent(String outcomeId, String appId, String notificationId, int deviceType,
                                            OneSignalRestClient.ResponseHandler responseHandler) {
        requestMeasureIndirectOutcomeEvent(outcomeId, appId, notificationId, deviceType, null, responseHandler);
    }

    void requestMeasureUnattributedOutcomeEvent(String outcomeId, String appId, int deviceType,
                                                OneSignalRestClient.ResponseHandler responseHandler) {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put(APP_ID, appId)
                    .put(DEVICE_TYPE, deviceType);

            outcomeEventsService.sendOutcomeEvent(outcomeId, jsonBody, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating indirect outcome:JSON Failed.", e);
        }
    }

    void requestWeightOutcomeEvent(String outcomeId, String appId, String notificationId,
                                   int deviceType, boolean direct, float weight,
                                   OneSignalRestClient.ResponseHandler responseHandler) {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put(APP_ID, appId)
                    .put(DEVICE_TYPE, deviceType)
                    .put(DIRECT, direct)
                    .put(WEIGHT, weight);
            if (direct) {
                jsonBody.put(NOTIFICATION_ID, notificationId);
            }

            outcomeEventsService.sendOutcomeEvent(outcomeId, jsonBody, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating weight outcome:JSON Failed.", e);
        }
    }
}