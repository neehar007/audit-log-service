package com.schwab.audit.service;

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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service managing audit log operations, sequential tamper-evident chaining,
 * query filtering, and full chain verification.
 */
@Service
public class AuditService {

    public static final String GENESIS_PREVIOUS_HASH = HashUtils.GENESIS_PREVIOUS_HASH;

    private final AuditRecordRepository auditRecordRepository;

    public AuditService(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    /**
     * Appends a new audit record to the tamper-evident chain.
     * Uses synchronized to ensure sequential appends and prevent chain forks.
     *
     * @param record the incoming audit record containing event metadata and payload
     * @return the persisted AuditRecord with server-assigned timestamp, previousHash, and computed hash
     */
    @Transactional
    public synchronized AuditRecord saveRecord(AuditRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("AuditRecord cannot be null");
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

    /**
     * Retrieves paginated audit records with optional multi-parameter filtering.
     *
     * @param actorId      optional actorId filter
     * @param resourceType optional resourceType filter
     * @param resourceId   optional resourceId filter
     * @param eventType    optional eventType filter
     * @param from         optional start timestamp filter (inclusive)
     * @param to           optional end timestamp filter (inclusive)
     * @param pageable     pagination information
     * @return page of audit records matching the specified criteria
     */
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

    /**
     * Retrieves paginated audit records without filtering.
     *
     * @param pageable pagination information
     * @return page of audit records
     */
    @Transactional(readOnly = true)
    public Page<AuditRecord> getRecords(Pageable pageable) {
        return auditRecordRepository.findAll(pageable);
    }

    /**
     * Verifies the cryptographic integrity of the entire audit record chain from genesis to latest.
     *
     * @return ChainVerificationResult detailing whether the chain is INTACT or BROKEN
     */
    @Transactional(readOnly = true)
    public ChainVerificationResult verifyChain() {
        List<AuditRecord> records = auditRecordRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        if (records.isEmpty()) {
            return ChainVerificationResult.intact();
        }

        String expectedPreviousHash = GENESIS_PREVIOUS_HASH;

        for (AuditRecord record : records) {
            // 1. Verify previousHash matches the expected previous hash
            if (!Objects.equals(record.getPreviousHash(), expectedPreviousHash)) {
                return ChainVerificationResult.broken(record.getId(), ChainVerificationResult.VIOLATION_PREVIOUS_HASH_MISMATCH);
            }

            // 2. Re-compute hash and verify
            String calculatedHash = HashUtils.computeHash(record);
            if (!Objects.equals(record.getHash(), calculatedHash)) {
                return ChainVerificationResult.broken(record.getId(), ChainVerificationResult.VIOLATION_HASH_MISMATCH);
            }

            // 3. Advance chain expectation
            expectedPreviousHash = record.getHash();
        }

        return ChainVerificationResult.intact();
    }
}
