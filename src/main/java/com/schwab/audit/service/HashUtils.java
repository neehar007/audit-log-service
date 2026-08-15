package com.schwab.audit.service;

import com.schwab.audit.model.AuditRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Cryptographic utility for SHA-256 calculation and hash generation for audit records.
 */
public final class HashUtils {

    public static final String GENESIS_PREVIOUS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private HashUtils() {
        // Utility class
    }

    /**
     * Computes the SHA-256 hash for an AuditRecord.
     * Concatenates eventType + actorId + resourceType + resourceId + payload + timestamp.toEpochMilli() + previousHash.
     *
     * @param record the audit record to hash
     * @return 64-character lowercase SHA-256 hex string
     */
    public static String computeHash(AuditRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("AuditRecord cannot be null");
        }
        return computeHash(
                record.getEventType(),
                record.getActorId(),
                record.getResourceType(),
                record.getResourceId(),
                record.getPayload(),
                record.getTimestamp(),
                record.getPreviousHash()
        );
    }

    /**
     * Computes the SHA-256 hash for raw audit record parameters.
     * Concatenates eventType + actorId + resourceType + resourceId + payload + timestamp.toEpochMilli() + previousHash.
     *
     * @param eventType    event type
     * @param actorId      actor ID
     * @param resourceType resource type
     * @param resourceId   resource ID
     * @param payload      event payload
     * @param timestamp    server timestamp
     * @param previousHash previous record's hash
     * @return 64-character lowercase SHA-256 hex string
     */
    public static String computeHash(String eventType, String actorId, String resourceType,
                                     String resourceId, String payload, Instant timestamp,
                                     String previousHash) {
        String safeEventType = eventType != null ? eventType : "";
        String safeActorId = actorId != null ? actorId : "";
        String safeResourceType = resourceType != null ? resourceType : "";
        String safeResourceId = resourceId != null ? resourceId : "";
        String safePayload = payload != null ? payload : "";
        String safeTimestamp = timestamp != null ? String.valueOf(timestamp.toEpochMilli()) : "";
        String safePreviousHash = previousHash != null ? previousHash : "";

        String rawData = safeEventType + safeActorId + safeResourceType + safeResourceId +
                safePayload + safeTimestamp + safePreviousHash;

        return calculateSha256(rawData);
    }

    /**
     * Calculates the SHA-256 hash of any input string and returns lowercase hex string.
     *
     * @param input string to hash
     * @return 64-character lowercase hex string
     */
    public static String calculateSha256(String input) {
        if (input == null) {
            input = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in current JVM", e);
        }
    }
}
