package com.schwab.audit.repository;

import com.schwab.audit.model.AuditRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AuditRecordRepositoryTest {

    @Autowired
    private AuditRecordRepository auditRecordRepository;

    @Test
    @DisplayName("findTopByOrderByIdDesc should return empty Optional when no records exist")
    void findTopByOrderByIdDesc_WhenEmpty_ReturnsEmpty() {
        Optional<AuditRecord> latest = auditRecordRepository.findTopByOrderByIdDesc();
        assertThat(latest).isEmpty();
    }

    @Test
    @DisplayName("save should persist all audit record fields and generate ID")
    void saveAndFindById_Success() {
        Instant now = Instant.parse("2026-08-15T12:00:00Z");
        AuditRecord record = new AuditRecord(
                "USER_LOGIN",
                "user-123",
                "ACCOUNT",
                "acc-456",
                "{\"ip\":\"192.168.1.1\",\"status\":\"SUCCESS\"}",
                now,
                "0000000000000000000000000000000000000000000000000000000000000000",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        );

        AuditRecord saved = auditRecordRepository.save(record);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEventType()).isEqualTo("USER_LOGIN");
        assertThat(saved.getActorId()).isEqualTo("user-123");
        assertThat(saved.getResourceType()).isEqualTo("ACCOUNT");
        assertThat(saved.getResourceId()).isEqualTo("acc-456");
        assertThat(saved.getPayload()).isEqualTo("{\"ip\":\"192.168.1.1\",\"status\":\"SUCCESS\"}");
        assertThat(saved.getTimestamp()).isEqualTo(now);
        assertThat(saved.getPreviousHash()).isEqualTo("0000000000000000000000000000000000000000000000000000000000000000");
        assertThat(saved.getHash()).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        Optional<AuditRecord> retrieved = auditRecordRepository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get()).isEqualTo(saved);
    }

    @Test
    @DisplayName("findTopByOrderByIdDesc should return the record with the largest ID")
    void findTopByOrderByIdDesc_MultipleRecords_ReturnsLatestRecord() {
        AuditRecord record1 = new AuditRecord(
                "USER_LOGIN",
                "user-1",
                "ACCOUNT",
                "acc-1",
                "{}",
                Instant.parse("2026-08-15T12:00:00Z"),
                "0000000000000000000000000000000000000000000000000000000000000000",
                "hash1"
        );
        AuditRecord record2 = new AuditRecord(
                "TRANSFER_FUNDS",
                "user-1",
                "TRANSACTION",
                "tx-999",
                "{\"amount\":1000}",
                Instant.parse("2026-08-15T12:01:00Z"),
                "hash1",
                "hash2"
        );
        AuditRecord record3 = new AuditRecord(
                "USER_LOGOUT",
                "user-1",
                "ACCOUNT",
                "acc-1",
                "{}",
                Instant.parse("2026-08-15T12:05:00Z"),
                "hash2",
                "hash3"
        );

        auditRecordRepository.save(record1);
        auditRecordRepository.save(record2);
        AuditRecord saved3 = auditRecordRepository.save(record3);

        Optional<AuditRecord> latest = auditRecordRepository.findTopByOrderByIdDesc();
        assertThat(latest).isPresent();
        assertThat(latest.get().getId()).isEqualTo(saved3.getId());
        assertThat(latest.get().getEventType()).isEqualTo("USER_LOGOUT");
        assertThat(latest.get().getHash()).isEqualTo("hash3");
        assertThat(latest.get().getPreviousHash()).isEqualTo("hash2");
    }

    @Test
    @DisplayName("findAll with Sort.by('id').ascending() should return records in sequential order")
    void findAll_OrderedByIdAscending_ReturnsSequentialList() {
        AuditRecord record1 = new AuditRecord(
                "EVENT_1", "actor-1", "RES", "res-1", "{}",
                Instant.now(), "0000", "hash1"
        );
        AuditRecord record2 = new AuditRecord(
                "EVENT_2", "actor-1", "RES", "res-2", "{}",
                Instant.now(), "hash1", "hash2"
        );

        AuditRecord saved1 = auditRecordRepository.save(record1);
        AuditRecord saved2 = auditRecordRepository.save(record2);

        List<AuditRecord> records = auditRecordRepository.findAll(Sort.by("id").ascending());
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getId()).isEqualTo(saved1.getId());
        assertThat(records.get(1).getId()).isEqualTo(saved2.getId());
    }

    @Test
    @DisplayName("save should persist large payloads properly")
    void save_WithLargePayload_Success() {
        StringBuilder largePayload = new StringBuilder("{");
        for (int i = 0; i < 500; i++) {
            largePayload.append("\"key").append(i).append("\":\"value").append(i).append("\",");
        }
        largePayload.append("\"final\":\"done\"}");

        AuditRecord record = new AuditRecord(
                "LARGE_EVENT",
                "system",
                "SYSTEM",
                "sys-1",
                largePayload.toString(),
                Instant.now(),
                "0000",
                "largehash"
        );

        AuditRecord saved = auditRecordRepository.save(record);
        Optional<AuditRecord> retrieved = auditRecordRepository.findById(saved.getId());

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getPayload()).isEqualTo(largePayload.toString());
    }
}
