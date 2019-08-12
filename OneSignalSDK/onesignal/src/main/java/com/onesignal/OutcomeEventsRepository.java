package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

class OutcomeEventsRepository {

    private final OutcomeEventsService outcomeEventsService;

    OutcomeEventsRepository(OutcomeEventsService outcomeEventsService) {
        this.outcomeEventsService = outcomeEventsService;
    }

    void requestMesureDirectOutcomeEvent(String id, String appId, String notificationId, int deviceType,
                                         OneSignalRestClient.ResponseHandler responseHandler) {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put("app_id", appId)
                    .put("device_type", deviceType)
                    .put("direct", true)
                    .put("notification_id", notificationId);

            outcomeEventsService.sendOutcomeEvent(id, jsonBody, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating direct outcome:JSON Failed.", e);
        }
    }

    void requestMesureIndirectOutcomeEvent(String id, String appId, int deviceType,
                                           OneSignalRestClient.ResponseHandler responseHandler) {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put("app_id", appId)
                    .put("device_type", deviceType)
                    .put("direct", false);

            outcomeEventsService.sendOutcomeEvent(id, jsonBody, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating indirect outcome:JSON Failed.", e);
        }
    }

    void requestMesureUnattributedOutcomeEvent(String id, String appId, int deviceType,
                                               OneSignalRestClient.ResponseHandler responseHandler) {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put("app_id", appId)
                    .put("device_type", deviceType);

            outcomeEventsService.sendOutcomeEvent(id, jsonBody, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating indirect outcome:JSON Failed.", e);
        }
    }

    void requestWeightOutcomeEvent(String id, String appId, String notificationId,
                                   int deviceType, boolean direct, float weight,
                                   OneSignalRestClient.ResponseHandler responseHandler) {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put("app_id", appId)
                    .put("device_type", deviceType)
                    .put("direct", direct)
                    .put("weight", weight);
            if (direct) {
                jsonBody.put("notification_id", notificationId);
            }

            outcomeEventsService.sendOutcomeEvent(id, jsonBody, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating weight outcome:JSON Failed.", e);
        }
    }
}