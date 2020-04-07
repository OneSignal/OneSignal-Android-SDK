package com.onesignal.outcomes;

import com.onesignal.influence.model.OSInfluenceType;

import org.json.JSONArray;

import java.util.Objects;

public class OSOutcomeEventDB {

    private OSInfluenceType iamInfluenceType;
    private OSInfluenceType notificationInfluenceType;

    private JSONArray iamIds;
    private JSONArray notificationIds;

    private String name;
    private long timestamp;
    private Float weight;

    public OSOutcomeEventDB(OSInfluenceType iamInfluenceType,
                            OSInfluenceType notificationInfluenceType,
                            String iamId, String notificationId,
                            String name, long timestamp, Float weight) {
        this.iamInfluenceType = iamInfluenceType;
        this.notificationInfluenceType = notificationInfluenceType;
        JSONArray iamIds = new JSONArray();
        iamIds.put(iamId);
        this.iamIds = iamIds;
        JSONArray notificationIds = new JSONArray();
        iamIds.put(notificationId);
        this.notificationIds = notificationIds;
        this.name = name;
        this.timestamp = timestamp;
        this.weight = weight;
    }

    public OSOutcomeEventDB(OSInfluenceType iamInfluenceType,
                            OSInfluenceType notificationInfluenceType,
                            JSONArray iamIds, JSONArray notificationIds,
                            String name, long timestamp, Float weight) {
        this.iamInfluenceType = iamInfluenceType;
        this.notificationInfluenceType = notificationInfluenceType;
        this.iamIds = iamIds;
        this.notificationIds = notificationIds;
        this.name = name;
        this.timestamp = timestamp;
        this.weight = weight;
    }

    public OSInfluenceType getIamInfluenceType() {
        return iamInfluenceType;
    }

    public void setIamInfluenceType(OSInfluenceType iamInfluenceType) {
        this.iamInfluenceType = iamInfluenceType;
    }

    public OSInfluenceType getNotificationInfluenceType() {
        return notificationInfluenceType;
    }

    public void setNotificationInfluenceType(OSInfluenceType notificationInfluenceType) {
        this.notificationInfluenceType = notificationInfluenceType;
    }

    public JSONArray getNotificationIds() {
        return notificationIds;
    }

    public void setNotificationIds(JSONArray notificationIds) {
        this.notificationIds = notificationIds;
    }

    public JSONArray getIamIds() {
        return iamIds;
    }

    public void setIamIds(JSONArray iamIds) {
        this.iamIds = iamIds;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Float getWeight() {
        return weight;
    }

    public void setWeight(Float weight) {
        this.weight = weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OSOutcomeEventDB that = (OSOutcomeEventDB) o;
        return timestamp == that.timestamp &&
                iamInfluenceType == that.iamInfluenceType &&
                notificationInfluenceType == that.notificationInfluenceType &&
                Objects.equals(iamIds, that.iamIds) &&
                Objects.equals(notificationIds, that.notificationIds) &&
                Objects.equals(name, that.name) &&
                Objects.equals(weight, that.weight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(iamInfluenceType, notificationInfluenceType, iamIds, notificationIds, name, timestamp, weight);
    }
}
