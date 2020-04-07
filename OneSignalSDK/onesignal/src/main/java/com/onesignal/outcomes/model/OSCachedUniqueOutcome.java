package com.onesignal.outcomes.model;

import com.onesignal.influence.model.OSInfluenceChannel;

public class OSCachedUniqueOutcome {

    private String influenceId;
    private OSInfluenceChannel channel;

    public OSCachedUniqueOutcome(String influenceId, OSInfluenceChannel channel) {
        this.influenceId = influenceId;
        this.channel = channel;
    }

    public String getInfluenceId() {
        return influenceId;
    }

    public OSInfluenceChannel getChannel() {
        return channel;
    }
}
