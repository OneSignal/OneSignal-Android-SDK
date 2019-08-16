package com.onesignal;

import java.util.List;

public class MockOutcomeEventsRepository extends OutcomeEventsRepository {

    private OneSignalRestClient.ResponseHandler customResponseHandler;

    public MockOutcomeEventsRepository(OutcomeEventsService outcomeEventsService, OneSignalDbHelper dbHelper) {
        super(outcomeEventsService, dbHelper);
    }

    public void setCustomResponseHandler(OneSignalRestClient.ResponseHandler customResponseHandler) {
        this.customResponseHandler = customResponseHandler;
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
    void requestMeasureDirectOutcomeEvent(String outcomeId, String appId, String notificationId, int deviceType, Long timestamp, OneSignalRestClient.ResponseHandler responseHandler) {
        if (customResponseHandler != null)
            super.requestMeasureDirectOutcomeEvent(outcomeId, appId, notificationId, deviceType, timestamp, customResponseHandler);
        else
            super.requestMeasureDirectOutcomeEvent(outcomeId, appId, notificationId, deviceType, timestamp, responseHandler);
    }

    @Override
    void requestMeasureDirectOutcomeEvent(String outcomeId, String appId, String notificationId, int deviceType, OneSignalRestClient.ResponseHandler responseHandler) {
        if (customResponseHandler != null)
            super.requestMeasureDirectOutcomeEvent(outcomeId, appId, notificationId, deviceType, customResponseHandler);
        else
            super.requestMeasureDirectOutcomeEvent(outcomeId, appId, notificationId, deviceType, responseHandler);
    }

    @Override
    void requestMeasureIndirectOutcomeEvent(String outcomeId, String appId, String notificationId, int deviceType, Long timestamp, OneSignalRestClient.ResponseHandler responseHandler) {
        if (customResponseHandler != null)
            super.requestMeasureIndirectOutcomeEvent(outcomeId, appId, notificationId, deviceType, timestamp, customResponseHandler);
        else
            super.requestMeasureIndirectOutcomeEvent(outcomeId, appId, notificationId, deviceType, timestamp, responseHandler);
    }

    @Override
    void requestMeasureIndirectOutcomeEvent(String outcomeId, String appId, String notificationId, int deviceType, OneSignalRestClient.ResponseHandler responseHandler) {
        if (customResponseHandler != null)
            super.requestMeasureIndirectOutcomeEvent(outcomeId, appId, notificationId, deviceType, customResponseHandler);
        else
            super.requestMeasureIndirectOutcomeEvent(outcomeId, appId, notificationId, deviceType, responseHandler);
    }

    @Override
    void requestMeasureUnattributedOutcomeEvent(String outcomeId, String appId, int deviceType, OneSignalRestClient.ResponseHandler responseHandler) {
        if (customResponseHandler != null)
            super.requestMeasureUnattributedOutcomeEvent(outcomeId, appId, deviceType, customResponseHandler);
        else
            super.requestMeasureUnattributedOutcomeEvent(outcomeId, appId, deviceType, responseHandler);
    }
}