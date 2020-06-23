package com.onesignal.outcomes.domain;

import com.onesignal.OneSignalApiResponseHandler;
import com.onesignal.influence.model.OSInfluence;
import com.onesignal.outcomes.model.OSOutcomeEventParams;

import java.util.List;
import java.util.Set;

public interface OSOutcomeEventsRepository {

    List<OSOutcomeEventParams> getSavedOutcomeEvents();

    void saveOutcomeEvent(OSOutcomeEventParams event);

    void removeEvent(OSOutcomeEventParams outcomeEvent);

    void requestMeasureOutcomeEvent(String appId, int deviceType, OSOutcomeEventParams event, OneSignalApiResponseHandler responseHandler);

    void saveUniqueOutcomeNotifications(OSOutcomeEventParams eventParams);

    List<OSInfluence> getNotCachedUniqueOutcome(String name, List<OSInfluence> influences);

    Set<String> getUnattributedUniqueOutcomeEventsSent();

    void saveUnattributedUniqueOutcomeEventsSent(Set<String> unattributedUniqueOutcomeEvents);
}
