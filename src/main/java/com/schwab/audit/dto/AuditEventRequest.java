package com.schwab.audit.dto;

import com.schwab.audit.model.AuditRecord;
import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

/**
 * DTO representing an incoming request to create a new audit log event.
 */
public class AuditEventRequest {

    @NotBlank(message = "eventType is required")
    private String eventType;

    @NotBlank(message = "actorId is required")
    private String actorId;

    @NotBlank(message = "resourceType is required")
    private String resourceType;

    @NotBlank(message = "resourceId is required")
    private String resourceId;

    @NotBlank(message = "payload is required")
    private String payload;

    public AuditEventRequest() {
    }

    public AuditEventRequest(String eventType, String actorId, String resourceType, String resourceId, String payload) {
        this.eventType = eventType;
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    /**
     * Maps this DTO to an AuditRecord domain entity.
     *
     * @return a new unpersisted AuditRecord instance populated with request data
     */
    public AuditRecord toAuditRecord() {
        AuditRecord record = new AuditRecord();
        record.setEventType(this.eventType);
        record.setActorId(this.actorId);
        record.setResourceType(this.resourceType);
        record.setResourceId(this.resourceId);
        record.setPayload(this.payload);
        return record;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEventRequest that = (AuditEventRequest) o;
        return Objects.equals(eventType, that.eventType) &&
                Objects.equals(actorId, that.actorId) &&
                Objects.equals(resourceType, that.resourceType) &&
                Objects.equals(resourceId, that.resourceId) &&
                Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, actorId, resourceType, resourceId, payload);
    }

    @Override
    public String toString() {
        return "AuditEventRequest{" +
                "eventType='" + eventType + '\'' +
                ", actorId='" + actorId + '\'' +
                ", resourceType='" + resourceType + '\'' +
                ", resourceId='" + resourceId + '\'' +
                ", payload='" + payload + '\'' +
                '}';
    }
}
