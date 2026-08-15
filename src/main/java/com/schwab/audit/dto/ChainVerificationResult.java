package com.schwab.audit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Result DTO representing the outcome of audit log chain verification.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChainVerificationResult {

    public static final String STATUS_INTACT = "INTACT";
    public static final String STATUS_BROKEN = "BROKEN";
    public static final String VIOLATION_HASH_MISMATCH = "HASH_MISMATCH";
    public static final String VIOLATION_PREVIOUS_HASH_MISMATCH = "PREVIOUS_HASH_MISMATCH";

    private String status;
    private Long firstInconsistentRecordId;
    private String violationType;

    public ChainVerificationResult() {
    }

    public ChainVerificationResult(String status) {
        this.status = status;
    }

    public ChainVerificationResult(String status, Long firstInconsistentRecordId, String violationType) {
        this.status = status;
        this.firstInconsistentRecordId = firstInconsistentRecordId;
        this.violationType = violationType;
    }

    public static ChainVerificationResult intact() {
        return new ChainVerificationResult(STATUS_INTACT, null, null);
    }

    public static ChainVerificationResult broken(Long recordId, String violationType) {
        return new ChainVerificationResult(STATUS_BROKEN, recordId, violationType);
    }

    @JsonIgnore
    public boolean isIntact() {
        return STATUS_INTACT.equalsIgnoreCase(this.status);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getFirstInconsistentRecordId() {
        return firstInconsistentRecordId;
    }

    public void setFirstInconsistentRecordId(Long firstInconsistentRecordId) {
        this.firstInconsistentRecordId = firstInconsistentRecordId;
    }

    public String getViolationType() {
        return violationType;
    }

    public void setViolationType(String violationType) {
        this.violationType = violationType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChainVerificationResult that = (ChainVerificationResult) o;
        return Objects.equals(status, that.status) &&
                Objects.equals(firstInconsistentRecordId, that.firstInconsistentRecordId) &&
                Objects.equals(violationType, that.violationType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, firstInconsistentRecordId, violationType);
    }

    @Override
    public String toString() {
        return "ChainVerificationResult{" +
                "status='" + status + '\'' +
                ", firstInconsistentRecordId=" + firstInconsistentRecordId +
                ", violationType='" + violationType + '\'' +
                '}';
    }
}
