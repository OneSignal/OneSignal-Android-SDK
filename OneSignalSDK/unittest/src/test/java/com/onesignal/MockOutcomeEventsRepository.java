package com.onesignal;

import org.json.JSONArray;

import java.util.List;

public class MockOutcomeEventsRepository extends OutcomeEventsRepository {

    public MockOutcomeEventsRepository(OutcomeEventsService outcomeEventsService, OneSignalDbHelper dbHelper) {
        super(outcomeEventsService, dbHelper);
    }

    @Override
    public void saveOutcomeEvent(OutcomeEvent event) {
        super.saveOutcomeEvent(event);
    }

    @Override
    public List<OutcomeEvent> getSavedOutcomeEvents() {
        return super.getSavedOutcomeEvents();
    }

    @Override
    void requestMeasureDirectOutcomeEvent(String appId, int deviceType, OutcomeEvent event, OneSignalRestClient.ResponseHandler responseHandler) {
        super.requestMeasureDirectOutcomeEvent(appId, deviceType, event, responseHandler);
    }

    @Override
    void requestMeasureDirectOutcomeEvent(String outcomeId, OutcomeParams outcomeParams, String appId, JSONArray notificationId, int deviceType, OneSignalRestClient.ResponseHandler responseHandler) {
        super.requestMeasureDirectOutcomeEvent(outcomeId, outcomeParams, appId, notificationId, deviceType, responseHandler);
    }

    @Override
    void requestMeasureIndirectOutcomeEvent(String appId, int deviceType, OutcomeEvent event, OneSignalRestClient.ResponseHandler responseHandler) {
        super.requestMeasureIndirectOutcomeEvent(appId, deviceType, event, responseHandler);
    }

    @Override
    void requestMeasureIndirectOutcomeEvent(String outcomeId, OutcomeParams outcomeParams, String appId, JSONArray notificationId, int deviceType, OneSignalRestClient.ResponseHandler responseHandler) {
        super.requestMeasureIndirectOutcomeEvent(outcomeId, outcomeParams, appId, notificationId, deviceType, responseHandler);
    }

    @Override
    void requestMeasureUnattributedOutcomeEvent(String appId, int deviceType, OutcomeEvent event, OneSignalRestClient.ResponseHandler responseHandler) {
        super.requestMeasureUnattributedOutcomeEvent(appId, deviceType, event, responseHandler);
    }

    @Override
    void requestMeasureUnattributedOutcomeEvent(String outcomeId, OutcomeParams outcomeParams, String appId, int deviceType, OneSignalRestClient.ResponseHandler responseHandler) {
        super.requestMeasureUnattributedOutcomeEvent(outcomeId, outcomeParams, appId, deviceType, responseHandler);
    }
}