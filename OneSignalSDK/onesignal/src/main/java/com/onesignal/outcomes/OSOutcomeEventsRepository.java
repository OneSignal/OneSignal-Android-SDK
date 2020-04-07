package com.onesignal.outcomes;

import com.onesignal.OSLogger;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalApiResponseHandler;
import com.onesignal.influence.model.OSInfluence;
import com.onesignal.outcomes.domain.OutcomeEventsService;
import com.onesignal.outcomes.model.OSOutcomeEventParams;

import java.util.List;
import java.util.Set;

abstract class OSOutcomeEventsRepository implements com.onesignal.outcomes.domain.OSOutcomeEventsRepository {

    static final String APP_ID = "app_id";
    static final String DEVICE_TYPE = "device_type";

    protected final OSLogger logger;
    private final OSOutcomeEventsCache outcomeEventsCache;
    final OutcomeEventsService outcomeEventsService;

    OSOutcomeEventsRepository(OSLogger logger, OSOutcomeEventsCache outcomeEventsCache, OutcomeEventsService outcomeEventsService) {
        this.logger = logger;
        this.outcomeEventsCache = outcomeEventsCache;
        this.outcomeEventsService = outcomeEventsService;
    }

    public abstract void requestMeasureOutcomeEvent(String appId, int deviceType, OSOutcomeEventParams event, OneSignalApiResponseHandler responseHandler);

    public List<OSOutcomeEventParams> getSavedOutcomeEvents() {
        return outcomeEventsCache.getAllEventsToSend();
    }

    public void saveOutcomeEvent(OSOutcomeEventParams event) {
        outcomeEventsCache.saveOutcomeEvent(event);
    }

    public void removeEvent(OSOutcomeEventParams outcomeEvent) {
        outcomeEventsCache.deleteOldOutcomeEvent(outcomeEvent);
    }

    public void saveUniqueOutcomeNotifications(OSOutcomeEventParams eventParams) {
        outcomeEventsCache.saveUniqueOutcomeNotifications(eventParams);
    }

    public List<OSInfluence> getNotCachedUniqueOutcome(String name, List<OSInfluence> influences) {
        List<OSInfluence> influencesNotCached =  outcomeEventsCache.getNotCachedUniqueInfluencesForOutcome(name, influences);
        logger.log(OneSignal.LOG_LEVEL.DEBUG, "OneSignal getNotCachedUniqueOutcome influences: " + influencesNotCached);
        return influencesNotCached;
    }

    @Override
    public Set<String> getUnattributedUniqueOutcomeEventsSent() {
        Set<String> unattributedUniqueOutcomeEvents = outcomeEventsCache.getUnattributedUniqueOutcomeEventsSentByChannel();
        logger.log(OneSignal.LOG_LEVEL.DEBUG, "OneSignal getUnattributedUniqueOutcomeEventsSentByChannel: " + unattributedUniqueOutcomeEvents);
        return unattributedUniqueOutcomeEvents;
    }

    @Override
    public void saveUnattributedUniqueOutcomeEventsSent(Set<String> unattributedUniqueOutcomeEvents) {
        logger.log(OneSignal.LOG_LEVEL.DEBUG, "OneSignal save unattributedUniqueOutcomeEvents: " + unattributedUniqueOutcomeEvents);
        outcomeEventsCache.saveUnattributedUniqueOutcomeEventsSentByChannel(unattributedUniqueOutcomeEvents);
    }
}
