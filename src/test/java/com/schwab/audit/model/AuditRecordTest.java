package com.schwab.audit.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuditRecordTest {

    @Test
    @DisplayName("Getters, setters, constructors, equals, hashCode and toString should work properly")
    void testEntityMethods() {
        Instant now = Instant.parse("2026-08-15T12:00:00Z");

        AuditRecord record1 = new AuditRecord();
        record1.setId(1L);
        record1.setEventType("LOGIN");
        record1.setActorId("actor1");
        record1.setResourceType("USER");
        record1.setResourceId("user1");
        record1.setPayload("{}");
        record1.setTimestamp(now);
        record1.setPreviousHash("prev1");
        record1.setHash("hash1");

        AuditRecord record2 = new AuditRecord(
                1L,
                "LOGIN",
                "actor1",
                "USER",
                "user1",
                "{}",
                now,
                "prev1",
                "hash1"
        );

        AuditRecord record3 = new AuditRecord(
                "LOGIN",
                "actor1",
                "USER",
                "user1",
                "{}",
                now,
                "prev1",
                "hash1"
        );

        assertThat(record1.getId()).isEqualTo(1L);
        assertThat(record1.getEventType()).isEqualTo("LOGIN");
        assertThat(record1.getActorId()).isEqualTo("actor1");
        assertThat(record1.getResourceType()).isEqualTo("USER");
        assertThat(record1.getResourceId()).isEqualTo("user1");
        assertThat(record1.getPayload()).isEqualTo("{}");
        assertThat(record1.getTimestamp()).isEqualTo(now);
        assertThat(record1.getPreviousHash()).isEqualTo("prev1");
        assertThat(record1.getHash()).isEqualTo("hash1");

        assertThat(record1).isEqualTo(record2);
        assertThat(record1.hashCode()).isEqualTo(record2.hashCode());
        assertThat(record1).isNotEqualTo(record3); // id differs: 1L vs null
        assertThat(record1).isNotEqualTo(null);
        assertThat(record1).isNotEqualTo("string");

        assertThat(record1.toString()).contains("id=1", "eventType='LOGIN'", "actorId='actor1'", "hash='hash1'");
    }
}
