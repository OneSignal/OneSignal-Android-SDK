package com.onesignal.sdktest.type;

public enum OutcomeEvent {

    OUTCOME("Normal Outcome"),
    UNIQUE_OUTCOME("Unique Outcome"),
    OUTCOME_WITH_VALUE("Outcome with Value");

    String title;

    OutcomeEvent(String title) {
        this.title = title;
    }

    public static OutcomeEvent enumFromTitleString(String title) {
        for (OutcomeEvent outcomeEvent : OutcomeEvent.values()) {
            if (title.equals(outcomeEvent.getTitle()))
                return outcomeEvent;
        }

        return null;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public String toString() {
        return getTitle();
    }
}
