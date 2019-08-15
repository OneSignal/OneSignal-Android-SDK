package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

class OutcomeEventsRepository {

    private static final String APP_ID = "app_id";
    private static final String DEVICE_TYPE = "device_type";
    private static final String DIRECT = "direct";
    private static final String NOTIFICATION_ID = "notification_id";
    private static final String WEIGHT = "weight";
    private static final String TIME = "time";
    private static final String APP_ACTIVE = "app_active";

    private final OutcomeEventsService outcomeEventsService;

    OutcomeEventsRepository() {
        this.outcomeEventsService = new OutcomeEventsService();
    }

    OutcomeEventsRepository(OutcomeEventsService outcomeEventsService) {
        this.outcomeEventsService = outcomeEventsService;
    }

    void requestMeasureDirectOutcomeEvent(String outcomeId, String appId, String notificationId, int deviceType,
                                          OneSignalRestClient.ResponseHandler responseHandler) {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put(APP_ID, appId)
                    .put(DEVICE_TYPE, deviceType)
                    .put(DIRECT, true)
                    .put(NOTIFICATION_ID, notificationId);

            outcomeEventsService.sendOutcomeEvent(outcomeId, jsonBody, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating direct outcome:JSON Failed.", e);
        }
    }

    void requestMeasureIndirectOutcomeEvent(String outcomeId, String appId, int deviceType,
                                            OneSignalRestClient.ResponseHandler responseHandler) {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put(APP_ID, appId)
                    .put(DEVICE_TYPE, deviceType)
                    .put(DIRECT, false);

            outcomeEventsService.sendOutcomeEvent(outcomeId, jsonBody, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating indirect outcome:JSON Failed.", e);
        }
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