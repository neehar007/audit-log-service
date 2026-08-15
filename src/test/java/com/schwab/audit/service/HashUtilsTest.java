package com.schwab.audit.service;

import com.schwab.audit.model.AuditRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class HashUtilsTest {

    @Test
    @DisplayName("calculateSha256 produces correct hash for known test vector")
    void calculateSha256_KnownVectors() {
        // SHA-256 of empty string is e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        String emptyHash = HashUtils.calculateSha256("");
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHash);

        // SHA-256 for null should treat as empty string
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", HashUtils.calculateSha256(null));

        // SHA-256 of "hello"
        // 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        String helloHash = HashUtils.calculateSha256("hello");
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", helloHash);
    }

    @Test
    @DisplayName("computeHash with AuditRecord produces 64-char lowercase hex string")
    void computeHash_WithAuditRecord_ProducesHexHash() {
        Instant timestamp = Instant.ofEpochMilli(1700000000000L);
        AuditRecord record = new AuditRecord(
                "USER_LOGIN",
                "user-123",
                "AUTH",
                "session-456",
                "{\"ip\":\"127.0.0.1\"}",
                timestamp,
                HashUtils.GENESIS_PREVIOUS_HASH,
                null
        );

        String hash = HashUtils.computeHash(record);

        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("^[0-9a-f]{64}$"));

        // Deterministic output
        assertEquals(hash, HashUtils.computeHash(record));
    }

    @Test
    @DisplayName("computeHash throws IllegalArgumentException when record is null")
    void computeHash_NullRecord_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> HashUtils.computeHash((AuditRecord) null));
    }

    @Test
    @DisplayName("computeHash handles null fields gracefully")
    void computeHash_NullFields_ComputesHashWithoutError() {
        AuditRecord record = new AuditRecord();
        String hash = HashUtils.computeHash(record);

        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("^[0-9a-f]{64}$"));
    }

    @Test
    @DisplayName("computeHash is sensitive to any field changes (tamper resistance)")
    void computeHash_FieldVariations_ProduceDifferentHashes() {
        Instant timestamp = Instant.ofEpochMilli(1700000000000L);
        String genesis = HashUtils.GENESIS_PREVIOUS_HASH;

        String baseHash = HashUtils.computeHash("LOGIN", "u1", "R_TYPE", "r1", "{}", timestamp, genesis);

        // Change eventType
        assertNotEquals(baseHash, HashUtils.computeHash("LOGOUT", "u1", "R_TYPE", "r1", "{}", timestamp, genesis));

        // Change actorId
        assertNotEquals(baseHash, HashUtils.computeHash("LOGIN", "u2", "R_TYPE", "r1", "{}", timestamp, genesis));

        // Change resourceType
        assertNotEquals(baseHash, HashUtils.computeHash("LOGIN", "u1", "OTHER_TYPE", "r1", "{}", timestamp, genesis));

        // Change resourceId
        assertNotEquals(baseHash, HashUtils.computeHash("LOGIN", "u1", "R_TYPE", "r2", "{}", timestamp, genesis));

        // Change payload
        assertNotEquals(baseHash, HashUtils.computeHash("LOGIN", "u1", "R_TYPE", "r1", "{\"changed\":{\"nonce\":\"1\",\"hash\":\"changed_hash\"}}", timestamp, genesis));

        // Change timestamp
        assertNotEquals(baseHash, HashUtils.computeHash("LOGIN", "u1", "R_TYPE", "r1", "{}", timestamp.plusMillis(1), genesis));

        // Change previousHash
        assertNotEquals(baseHash, HashUtils.computeHash("LOGIN", "u1", "R_TYPE", "r1", "{}", timestamp, "1111111111111111111111111111111111111111111111111111111111111111"));
    }

    @Test
    @DisplayName("GENESIS_PREVIOUS_HASH constant is 64 zeros")
    void genesisConstant_Is64Zeros() {
        assertEquals(64, HashUtils.GENESIS_PREVIOUS_HASH.length());
        assertTrue(HashUtils.GENESIS_PREVIOUS_HASH.matches("^0{64}$"));
    }
}
