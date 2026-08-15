package com.schwab.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.audit.model.AuditRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

public final class HashUtils {

    public static final String GENESIS_PREVIOUS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private HashUtils() {}

    public static String computeHash(AuditRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("AuditRecord cannot be null");
        }
        return computeHash(
                record.getEventType(),
                record.getActorId(),
                record.getResourceType(),
                record.getResourceId(),
                record.getPayloadMetadataJson(),
                record.getTimestamp(),
                record.getPreviousHash()
        );
    }

    public static String computeHash(String eventType, String actorId, String resourceType,
                                     String resourceId, String payloadMetadataJson, Instant timestamp,
                                     String previousHash) {
        String safeEventType = eventType != null ? eventType : "";
        String safeActorId = actorId != null ? actorId : "";
        String safeResourceType = resourceType != null ? resourceType : "";
        String safeResourceId = resourceId != null ? resourceId : "";
        String safeTimestamp = timestamp != null ? String.valueOf(timestamp.toEpochMilli()) : "";
        String safePreviousHash = previousHash != null ? previousHash : "";
        
        StringBuilder sortedHashes = new StringBuilder();
        if (payloadMetadataJson != null && !payloadMetadataJson.isBlank()) {
            try {
                Map<String, Map<String, String>> metadata = objectMapper.readValue(payloadMetadataJson, new TypeReference<Map<String, Map<String, String>>>() {});
                TreeMap<String, Map<String, String>> sortedMetadata = new TreeMap<>(metadata);
                for (Map.Entry<String, Map<String, String>> entry : sortedMetadata.entrySet()) {
                    sortedHashes.append(entry.getValue().get("hash"));
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Corrupted metadata", e);
            }
        }

        String rawData = safeEventType + safeActorId + safeResourceType + safeResourceId +
                sortedHashes.toString() + safeTimestamp + safePreviousHash;

        return calculateSha256(rawData);
    }

    public static String calculateSha256(String input) {
        if (input == null) input = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in current JVM", e);
        }
    }
}
