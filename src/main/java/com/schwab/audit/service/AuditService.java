package com.schwab.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.audit.dto.ChainVerificationResult;
import com.schwab.audit.model.AuditRecord;
import com.schwab.audit.repository.AuditRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class AuditService {

    public static final String GENESIS_PREVIOUS_HASH = HashUtils.GENESIS_PREVIOUS_HASH;

    private final AuditRecordRepository auditRecordRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public synchronized AuditRecord saveRecord(AuditRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("AuditRecord cannot be null");
        }

        try {
            if (record.getPayload() != null && !record.getPayload().isBlank()) {
                Map<String, Object> payloadMap = objectMapper.readValue(record.getPayload(), new TypeReference<Map<String, Object>>() {});
                Map<String, Map<String, String>> metadata = new HashMap<>();
                for (Map.Entry<String, Object> entry : payloadMap.entrySet()) {
                    String key = entry.getKey();
                    Object valObj = entry.getValue();
                    String value = valObj == null ? "null" : (valObj instanceof String ? (String) valObj : (valObj instanceof Number || valObj instanceof Boolean ? valObj.toString() : objectMapper.writeValueAsString(valObj)));
                    String nonce = UUID.randomUUID().toString();
                    String hash = HashUtils.calculateSha256(key + value + nonce);
                    Map<String, String> metaEntry = new HashMap<>();
                    metaEntry.put("nonce", nonce);
                    metaEntry.put("hash", hash);
                    metadata.put(key, metaEntry);
                }
                record.setPayloadMetadataJson(objectMapper.writeValueAsString(metadata));
            } else {
                record.setPayloadMetadataJson("{}");
            }
        } catch (Exception e) {
            record.setPayloadMetadataJson("{}");
        }

        Optional<AuditRecord> lastRecordOpt = auditRecordRepository.findTopByOrderByIdDesc();
        if (lastRecordOpt.isPresent()) {
            record.setPreviousHash(lastRecordOpt.get().getHash());
        } else {
            record.setPreviousHash(GENESIS_PREVIOUS_HASH);
        }

        record.setTimestamp(Instant.now());
        record.setHash(HashUtils.computeHash(record));

        return auditRecordRepository.save(record);
    }

    @Transactional(readOnly = true)
    public Page<AuditRecord> getRecords(String actorId, String resourceType, String resourceId,
                                        String eventType, Instant from, Instant to, Pageable pageable) {
        Specification<AuditRecord> spec = Specification.where(null);
        if (actorId != null && !actorId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId));
        }
        if (resourceType != null && !resourceType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("resourceType"), resourceType));
        }
        if (resourceId != null && !resourceId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("resourceId"), resourceId));
        }
        if (eventType != null && !eventType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("eventType"), eventType));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), to));
        }
        return auditRecordRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditRecord> getRecords(Pageable pageable) {
        return auditRecordRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public ChainVerificationResult verifyChain() {
        List<AuditRecord> records = auditRecordRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        if (records.isEmpty()) {
            return ChainVerificationResult.intact();
        }

        String expectedPreviousHash = GENESIS_PREVIOUS_HASH;

        for (AuditRecord record : records) {
            if (!Objects.equals(record.getPreviousHash(), expectedPreviousHash)) {
                return ChainVerificationResult.broken(record.getId(), ChainVerificationResult.VIOLATION_PREVIOUS_HASH_MISMATCH);
            }

            String calculatedHash = HashUtils.computeHash(record);
            if (!Objects.equals(record.getHash(), calculatedHash)) {
                return ChainVerificationResult.broken(record.getId(), ChainVerificationResult.VIOLATION_HASH_MISMATCH);
            }
            
            try {
                if (record.getPayload() != null && !record.getPayload().isBlank() && record.getPayloadMetadataJson() != null && !record.getPayloadMetadataJson().isBlank()) {
                    Map<String, Object> payloadMap = objectMapper.readValue(record.getPayload(), new TypeReference<Map<String, Object>>() {});
                    Map<String, Map<String, String>> metadata = objectMapper.readValue(record.getPayloadMetadataJson(), new TypeReference<Map<String, Map<String, String>>>() {});
                    
                    for (Map.Entry<String, Object> entry : payloadMap.entrySet()) {
                        String key = entry.getKey();
                        Object valObj = entry.getValue();
                        String value = valObj == null ? "null" : (valObj instanceof String ? (String) valObj : (valObj instanceof Number || valObj instanceof Boolean ? valObj.toString() : objectMapper.writeValueAsString(valObj)));
                        
                        if (!"***REDACTED***".equals(value)) {
                            if (metadata.containsKey(key)) {
                                String nonce = metadata.get(key).get("nonce");
                                String expectedFieldHash = metadata.get(key).get("hash");
                                String calculatedFieldHash = HashUtils.calculateSha256(key + value + nonce);
                                if (!calculatedFieldHash.equals(expectedFieldHash)) {
                                    return ChainVerificationResult.broken(record.getId(), ChainVerificationResult.VIOLATION_HASH_MISMATCH);
                                }
                            } else {
                                return ChainVerificationResult.broken(record.getId(), ChainVerificationResult.VIOLATION_HASH_MISMATCH);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                return ChainVerificationResult.broken(record.getId(), ChainVerificationResult.VIOLATION_HASH_MISMATCH);
            }

            expectedPreviousHash = record.getHash();
        }

        return ChainVerificationResult.intact();
    }
    
    @Transactional
    public AuditRecord redactField(Long id, String field) {
        AuditRecord record = auditRecordRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Record not found"));
        try {
            if (record.getPayload() != null && !record.getPayload().isBlank()) {
                Map<String, Object> payloadMap = objectMapper.readValue(record.getPayload(), new TypeReference<Map<String, Object>>() {});
                if (payloadMap.containsKey(field)) {
                    payloadMap.put(field, "***REDACTED***");
                    record.setPayload(objectMapper.writeValueAsString(payloadMap));
                    return auditRecordRepository.save(record);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to redact", e);
        }
        return record;
    }
}
