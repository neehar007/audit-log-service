package com.schwab.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@Table(name = "audit_records")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "payload_metadata_json", columnDefinition = "TEXT")
    private String payloadMetadataJson;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "previous_hash", nullable = false)
    private String previousHash;

    @Column(name = "hash", nullable = false)
    private String hash;

    public enum ArchiveStatus {
        ACTIVE, ARCHIVED
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ArchiveStatus status = ArchiveStatus.ACTIVE;

    public AuditRecord() {
    }

    public AuditRecord(String eventType, String actorId, String resourceType, String resourceId,
                       String payload, Instant timestamp, String previousHash, String hash) {
        this.eventType = eventType;
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload;
        this.payloadMetadataJson = "{}";
        this.timestamp = timestamp;
        this.previousHash = previousHash;
        this.hash = hash;
    }

    public AuditRecord(Long id, String eventType, String actorId, String resourceType, String resourceId,
                       String payload, Instant timestamp, String previousHash, String hash) {
        this.id = id;
        this.eventType = eventType;
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload;
        this.payloadMetadataJson = "{}";
        this.timestamp = timestamp;
        this.previousHash = previousHash;
        this.hash = hash;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getPayloadMetadataJson() {
        return payloadMetadataJson;
    }

    public void setPayloadMetadataJson(String payloadMetadataJson) {
        this.payloadMetadataJson = payloadMetadataJson;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public ArchiveStatus getStatus() {
        return status;
    }

    public void setStatus(ArchiveStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditRecord that = (AuditRecord) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(eventType, that.eventType) &&
                Objects.equals(actorId, that.actorId) &&
                Objects.equals(resourceType, that.resourceType) &&
                Objects.equals(resourceId, that.resourceId) &&
                Objects.equals(payload, that.payload) &&
                Objects.equals(payloadMetadataJson, that.payloadMetadataJson) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(previousHash, that.previousHash) &&
                Objects.equals(hash, that.hash) &&
                status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, eventType, actorId, resourceType, resourceId, payload, payloadMetadataJson, timestamp, previousHash, hash, status);
    }

    @Override
    public String toString() {
        return "AuditRecord{" +
                "id=" + id +
                ", eventType='" + eventType + '\'' +
                ", actorId='" + actorId + '\'' +
                ", resourceType='" + resourceType + '\'' +
                ", resourceId='" + resourceId + '\'' +
                ", payload='" + payload + '\'' +
                ", payloadMetadataJson='" + payloadMetadataJson + '\'' +
                ", timestamp=" + timestamp +
                ", previousHash='" + previousHash + '\'' +
                ", hash='" + hash + '\'' +
                ", status=" + status +
                '}';
    }
}
