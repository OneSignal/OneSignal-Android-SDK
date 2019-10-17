package com.onesignal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

// TODO: Clean up duplicated requestMeasure*OutcomeEvent methods.
//       There are 2 of each with different params, there should only be one of each.
//         - Possibility just one depending on how it is cleaned up.
class OutcomeEventsRepository {

    private static final String APP_ID = "app_id";
    private static final String DEVICE_TYPE = "device_type";
    private static final String DIRECT = "direct";
    private static final String NOTIFICATION_IDS = "notification_ids";
    private static final String OUTCOME_ID = "id";

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
        JSONObject jsonObject = event.toJSONObjectWithNotification();
        try {
            jsonObject.put(APP_ID, appId);
            jsonObject.put(DEVICE_TYPE, deviceType);
            jsonObject.put(DIRECT, true);
            outcomeEventsService.sendOutcomeEvent(jsonObject, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating direct outcome:JSON Failed.", e);
        }
    }

    void requestMeasureDirectOutcomeEvent(String outcomeId, OutcomeParams outcomeParams, String appId, JSONArray notificationIds, int deviceType,
                                          OneSignalRestClient.ResponseHandler responseHandler) {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put(APP_ID, appId)
                    .put(DEVICE_TYPE, deviceType)
                    .put(DIRECT, true)
                    .put(OUTCOME_ID, outcomeId)
                    .put(NOTIFICATION_IDS, notificationIds);

            if (outcomeParams != null)
                outcomeParams.addParamsToJson(jsonBody);

            outcomeEventsService.sendOutcomeEvent(jsonBody, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating direct outcome:JSON Failed.", e);
        }
    }

    void requestMeasureIndirectOutcomeEvent(String appId, int deviceType, OutcomeEvent event, OneSignalRestClient.ResponseHandler responseHandler) {
        JSONObject jsonObject = event.toJSONObjectWithNotification();
        try {
            jsonObject.put(APP_ID, appId);
            jsonObject.put(DEVICE_TYPE, deviceType);
            jsonObject.put(DIRECT, false);
            outcomeEventsService.sendOutcomeEvent(jsonObject, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating indirect outcome:JSON Failed.", e);
        }
    }

    void requestMeasureIndirectOutcomeEvent(String outcomeId, OutcomeParams outcomeParams, String appId, JSONArray notificationIds, int deviceType,
                                            OneSignalRestClient.ResponseHandler responseHandler) {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put(APP_ID, appId)
                    .put(DEVICE_TYPE, deviceType)
                    .put(DIRECT, false)
                    .put(OUTCOME_ID, outcomeId)
                    .put(NOTIFICATION_IDS, notificationIds);

            if (outcomeParams != null)
                outcomeParams.addParamsToJson(jsonBody);

            outcomeEventsService.sendOutcomeEvent(jsonBody, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating indirect outcome:JSON Failed.", e);
        }
    }

    void requestMeasureUnattributedOutcomeEvent(String appId, int deviceType, OutcomeEvent event, OneSignalRestClient.ResponseHandler responseHandler) {
        JSONObject jsonObject = event.toJSONObject();
        try {
            jsonObject.put(APP_ID, appId);
            jsonObject.put(DEVICE_TYPE, deviceType);
            outcomeEventsService.sendOutcomeEvent(jsonObject, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating unattributed outcome:JSON Failed.", e);
        }
    }

    void requestMeasureUnattributedOutcomeEvent(String outcomeId, OutcomeParams outcomeParams, String appId, int deviceType,
                                                OneSignalRestClient.ResponseHandler responseHandler) {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put(APP_ID, appId)
                    .put(OUTCOME_ID, outcomeId)
                    .put(DEVICE_TYPE, deviceType);

            if (outcomeParams != null)
                outcomeParams.addParamsToJson(jsonBody);

            outcomeEventsService.sendOutcomeEvent(jsonBody, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating unattributed outcome:JSON Failed.", e);
        }
    }
}