package com.schwab.audit.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChainVerificationResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("intact factory method creates INTACT result")
    void intactFactory() {
        ChainVerificationResult result = ChainVerificationResult.intact();
        assertEquals("INTACT", result.getStatus());
        assertNull(result.getFirstInconsistentRecordId());
        assertNull(result.getViolationType());
        assertTrue(result.isIntact());
    }

    @Test
    @DisplayName("broken factory method creates BROKEN result with recordId and violationType")
    void brokenFactory() {
        ChainVerificationResult result = ChainVerificationResult.broken(42L, ChainVerificationResult.VIOLATION_HASH_MISMATCH);
        assertEquals("BROKEN", result.getStatus());
        assertEquals(42L, result.getFirstInconsistentRecordId());
        assertEquals("HASH_MISMATCH", result.getViolationType());
        assertFalse(result.isIntact());
    }

    @Test
    @DisplayName("equals, hashCode, toString and setters work properly")
    void gettersSettersEqualsHashCode() {
        ChainVerificationResult r1 = new ChainVerificationResult();
        r1.setStatus("BROKEN");
        r1.setFirstInconsistentRecordId(10L);
        r1.setViolationType("PREVIOUS_HASH_MISMATCH");

        ChainVerificationResult r2 = new ChainVerificationResult("BROKEN", 10L, "PREVIOUS_HASH_MISMATCH");

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertTrue(r1.toString().contains("PREVIOUS_HASH_MISMATCH"));
        assertTrue(r1.toString().contains("10"));
    }

    @Test
    @DisplayName("JSON serialization omits null fields for intact status")
    void jsonSerialization_Intact_OmitsNulls() throws Exception {
        ChainVerificationResult intact = ChainVerificationResult.intact();
        String json = objectMapper.writeValueAsString(intact);

        assertEquals("{\"status\":\"INTACT\"}", json);
    }

    @Test
    @DisplayName("JSON serialization includes all fields for broken status")
    void jsonSerialization_Broken_IncludesAllFields() throws Exception {
        ChainVerificationResult broken = ChainVerificationResult.broken(123L, "HASH_MISMATCH");
        String json = objectMapper.writeValueAsString(broken);

        assertTrue(json.contains("\"status\":\"BROKEN\""));
        assertTrue(json.contains("\"firstInconsistentRecordId\":123"));
        assertTrue(json.contains("\"violationType\":\"HASH_MISMATCH\""));
    }
}
