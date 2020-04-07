package com.onesignal.outcomes;

import com.onesignal.OSLogger;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalApiResponseHandler;
import com.onesignal.outcomes.domain.OutcomeEventsService;
import com.onesignal.outcomes.model.OSOutcomeEvent;
import com.onesignal.outcomes.model.OSOutcomeEventParams;

import org.json.JSONException;
import org.json.JSONObject;

class OSOutcomeEventsV1Repository extends OSOutcomeEventsRepository {

    private static final String DIRECT = "direct";

    OSOutcomeEventsV1Repository(OSLogger logger, OSOutcomeEventsCache outcomeEventsCache, OutcomeEventsService outcomeEventsService) {
        super(logger, outcomeEventsCache, outcomeEventsService);
    }

    @Override
    public void requestMeasureOutcomeEvent(String appId, int deviceType, OSOutcomeEventParams eventParams, OneSignalApiResponseHandler responseHandler) {
        OSOutcomeEvent event = OSOutcomeEvent.fromOutcomeEventParams(eventParams);
        switch (event.getSession()) {
            case DIRECT:
                requestMeasureDirectOutcomeEvent(appId, deviceType, event, responseHandler);
                break;
            case INDIRECT:
                requestMeasureIndirectOutcomeEvent(appId, deviceType, event, responseHandler);
                break;
            case UNATTRIBUTED:
                requestMeasureUnattributedOutcomeEvent(appId, deviceType, event, responseHandler);
                break;
            case DISABLED:
                // In this stage we should't have this case
        }
    }

    private void requestMeasureDirectOutcomeEvent(String appId, int deviceType, OSOutcomeEvent event, OneSignalApiResponseHandler responseHandler) {
        try {
            JSONObject jsonObject = event.toJSONObjectForMeasure();
            jsonObject.put(APP_ID, appId);
            jsonObject.put(DEVICE_TYPE, deviceType);
            jsonObject.put(DIRECT, true);

            outcomeEventsService.sendOutcomeEvent(jsonObject, responseHandler);
        } catch (JSONException e) {
            logger.log(OneSignal.LOG_LEVEL.ERROR, "Generating direct outcome:JSON Failed.", e);
        }
    }

    private void requestMeasureIndirectOutcomeEvent(String appId, int deviceType, OSOutcomeEvent event, OneSignalApiResponseHandler responseHandler) {
        try {
            JSONObject jsonObject = event.toJSONObjectForMeasure();
            jsonObject.put(APP_ID, appId);
            jsonObject.put(DEVICE_TYPE, deviceType);
            jsonObject.put(DIRECT, false);

            outcomeEventsService.sendOutcomeEvent(jsonObject, responseHandler);
        } catch (JSONException e) {
            logger.log(OneSignal.LOG_LEVEL.ERROR, "Generating indirect outcome:JSON Failed.", e);
        }
    }

    private void requestMeasureUnattributedOutcomeEvent(String appId, int deviceType, OSOutcomeEvent event, OneSignalApiResponseHandler responseHandler) {
        try {
            JSONObject jsonObject = event.toJSONObjectForMeasure();
            jsonObject.put(APP_ID, appId);
            jsonObject.put(DEVICE_TYPE, deviceType);

            outcomeEventsService.sendOutcomeEvent(jsonObject, responseHandler);
        } catch (JSONException e) {
            logger.log(OneSignal.LOG_LEVEL.ERROR, "Generating unattributed outcome:JSON Failed.", e);
        }
    }

}
