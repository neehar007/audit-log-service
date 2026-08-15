package com.schwab.audit.service;

import com.schwab.audit.dto.ChainVerificationResult;
import com.schwab.audit.model.AuditRecord;
import com.schwab.audit.repository.AuditRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class AuditServiceTest {

    @Autowired
    private AuditRecordRepository repository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        auditService = new AuditService(repository);
    }

    @Nested
    @DisplayName("saveRecord Tests")
    class SaveRecordTests {

        @Test
        @DisplayName("saveRecord creates genesis record with 64-zero previousHash")
        void saveRecord_Genesis_SetsZeroPreviousHashAndComputesHash() {
            AuditRecord record = new AuditRecord(
                    "USER_LOGIN",
                    "user-001",
                    "ACCOUNT",
                    "acc-100",
                    "{\"action\":\"login\"}",
                    null,
                    null,
                    null
            );

            AuditRecord saved = auditService.saveRecord(record);

            assertNotNull(saved.getId());
            assertEquals(HashUtils.GENESIS_PREVIOUS_HASH, saved.getPreviousHash());
            assertNotNull(saved.getTimestamp());
            assertNotNull(saved.getHash());
            assertEquals(64, saved.getHash().length());

            // Recompute to verify hash correctness
            String expectedHash = HashUtils.computeHash(saved);
            assertEquals(expectedHash, saved.getHash());
        }

        @Test
        @DisplayName("saveRecord links subsequent records sequentially using previousHash")
        void saveRecord_Sequential_LinksPreviousHashes() {
            AuditRecord r1 = auditService.saveRecord(new AuditRecord(
                    "CREATE_USER", "admin", "USER", "u1", "{}", null, null, null
            ));
            AuditRecord r2 = auditService.saveRecord(new AuditRecord(
                    "UPDATE_USER", "admin", "USER", "u1", "{\"role\":\"ADMIN\"}", null, null, null
            ));
            AuditRecord r3 = auditService.saveRecord(new AuditRecord(
                    "DELETE_USER", "admin", "USER", "u1", "{}", null, null, null
            ));

            assertEquals(HashUtils.GENESIS_PREVIOUS_HASH, r1.getPreviousHash());
            assertEquals(r1.getHash(), r2.getPreviousHash());
            assertEquals(r2.getHash(), r3.getPreviousHash());

            assertEquals(HashUtils.computeHash(r1), r1.getHash());
            assertEquals(HashUtils.computeHash(r2), r2.getHash());
            assertEquals(HashUtils.computeHash(r3), r3.getHash());
        }

        @Test
        @DisplayName("saveRecord throws IllegalArgumentException when record is null")
        void saveRecord_NullRecord_ThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> auditService.saveRecord(null));
        }

        @Test
        @DisplayName("saveRecord supports concurrent execution safely maintaining valid chain")
        void saveRecord_ConcurrentWrites_MaintainsChainIntegrity() throws Exception {
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        latch.await();
                        AuditRecord rec = new AuditRecord(
                                "EVENT_" + idx, "actor-" + idx, "RES", "res-" + idx,
                                "{\"idx\":" + idx + "}", null, null, null
                        );
                        auditService.saveRecord(rec);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            latch.countDown(); // Start all threads simultaneously
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            // Verify total records saved
            assertEquals(threadCount, repository.count());

            // Verify chain integrity
            ChainVerificationResult result = auditService.verifyChain();
            assertTrue(result.isIntact(), "Chain should remain intact despite concurrent saves");
            assertEquals("INTACT", result.getStatus());
        }
    }

    @Nested
    @DisplayName("getRecords Query & Filter Tests")
    class GetRecordsTests {

        private Instant t1;
        private Instant t2;
        private Instant t3;

        @BeforeEach
        void initData() throws InterruptedException {
            AuditRecord r1 = auditService.saveRecord(new AuditRecord(
                    "LOGIN", "alice", "AUTH", "res1", "{}", null, null, null
            ));
            t1 = r1.getTimestamp();

            Thread.sleep(10);
            AuditRecord r2 = auditService.saveRecord(new AuditRecord(
                    "TRANSFER", "alice", "ACCOUNT", "res2", "{\"amount\":100}", null, null, null
            ));
            t2 = r2.getTimestamp();

            Thread.sleep(10);
            AuditRecord r3 = auditService.saveRecord(new AuditRecord(
                    "LOGOUT", "bob", "AUTH", "res1", "{}", null, null, null
            ));
            t3 = r3.getTimestamp();
        }

        @Test
        @DisplayName("getRecords with simple pagination returns all records")
        void getRecords_Paginated_ReturnsAll() {
            Page<AuditRecord> page = auditService.getRecords(PageRequest.of(0, 2, Sort.by("id")));
            assertEquals(2, page.getContent().size());
            assertEquals(3, page.getTotalElements());
            assertEquals(2, page.getTotalPages());
        }

        @Test
        @DisplayName("getRecords filtered by actorId")
        void getRecords_FilterByActorId() {
            Page<AuditRecord> page = auditService.getRecords(
                    "alice", null, null, null, null, null, PageRequest.of(0, 10)
            );
            assertEquals(2, page.getTotalElements());
            assertTrue(page.getContent().stream().allMatch(r -> "alice".equals(r.getActorId())));
        }

        @Test
        @DisplayName("getRecords filtered by eventType and resourceType")
        void getRecords_FilterByEventTypeAndResourceType() {
            Page<AuditRecord> page = auditService.getRecords(
                    null, "AUTH", null, "LOGIN", null, null, PageRequest.of(0, 10)
            );
            assertEquals(1, page.getTotalElements());
            assertEquals("LOGIN", page.getContent().get(0).getEventType());
            assertEquals("AUTH", page.getContent().get(0).getResourceType());
        }

        @Test
        @DisplayName("getRecords filtered by resourceId")
        void getRecords_FilterByResourceId() {
            Page<AuditRecord> page = auditService.getRecords(
                    null, null, "res1", null, null, null, PageRequest.of(0, 10)
            );
            assertEquals(2, page.getTotalElements());
        }

        @Test
        @DisplayName("getRecords filtered by timestamp range")
        void getRecords_FilterByTimestampRange() {
            Page<AuditRecord> page = auditService.getRecords(
                    null, null, null, null, t1, t2, PageRequest.of(0, 10)
            );
            assertEquals(2, page.getTotalElements());

            Page<AuditRecord> pageT3 = auditService.getRecords(
                    null, null, null, null, t3, null, PageRequest.of(0, 10)
            );
            assertEquals(1, pageT3.getTotalElements());
        }
    }

    @Nested
    @DisplayName("verifyChain Tests")
    class VerifyChainTests {

        @Test
        @DisplayName("verifyChain on empty repository returns INTACT")
        void verifyChain_EmptyRepository_ReturnsIntact() {
            ChainVerificationResult result = auditService.verifyChain();
            assertTrue(result.isIntact());
            assertEquals(ChainVerificationResult.STATUS_INTACT, result.getStatus());
            assertNull(result.getFirstInconsistentRecordId());
            assertNull(result.getViolationType());
        }

        @Test
        @DisplayName("verifyChain with single valid record returns INTACT")
        void verifyChain_SingleValidRecord_ReturnsIntact() {
            auditService.saveRecord(new AuditRecord(
                    "LOGIN", "user1", "AUTH", "id1", "{}", null, null, null
            ));

            ChainVerificationResult result = auditService.verifyChain();
            assertTrue(result.isIntact());
            assertEquals(ChainVerificationResult.STATUS_INTACT, result.getStatus());
        }

        @Test
        @DisplayName("verifyChain with multi-record valid chain returns INTACT")
        void verifyChain_MultipleValidRecords_ReturnsIntact() {
            auditService.saveRecord(new AuditRecord("LOGIN", "u1", "AUTH", "1", "{}", null, null, null));
            auditService.saveRecord(new AuditRecord("TRADE", "u1", "STOCK", "AAPL", "{\"qty\":10}", null, null, null));
            auditService.saveRecord(new AuditRecord("LOGOUT", "u1", "AUTH", "1", "{}", null, null, null));

            ChainVerificationResult result = auditService.verifyChain();
            assertTrue(result.isIntact());
            assertEquals(ChainVerificationResult.STATUS_INTACT, result.getStatus());
        }

        @Test
        @DisplayName("verifyChain detects tampered genesis previousHash")
        void verifyChain_GenesisPreviousHashTampered_ReturnsBroken() {
            AuditRecord r1 = auditService.saveRecord(new AuditRecord("LOGIN", "u1", "AUTH", "1", "{}", null, null, null));
            
            // Tamper previousHash of genesis record directly in repository
            r1.setPreviousHash("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
            repository.save(r1);

            ChainVerificationResult result = auditService.verifyChain();
            assertFalse(result.isIntact());
            assertEquals(ChainVerificationResult.STATUS_BROKEN, result.getStatus());
            assertEquals(r1.getId(), result.getFirstInconsistentRecordId());
            assertEquals(ChainVerificationResult.VIOLATION_PREVIOUS_HASH_MISMATCH, result.getViolationType());
        }

        @Test
        @DisplayName("verifyChain detects tampered payload in genesis record")
        void verifyChain_GenesisPayloadTampered_ReturnsBrokenHashMismatch() {
            AuditRecord r1 = auditService.saveRecord(new AuditRecord("LOGIN", "u1", "AUTH", "1", "{\"role\":\"USER\"}", null, null, null));

            // Tamper payload directly in DB
            r1.setPayload("{\"role\":\"SUPERADMIN\"}");
            repository.save(r1);

            ChainVerificationResult result = auditService.verifyChain();
            assertFalse(result.isIntact());
            assertEquals(ChainVerificationResult.STATUS_BROKEN, result.getStatus());
            assertEquals(r1.getId(), result.getFirstInconsistentRecordId());
            assertEquals(ChainVerificationResult.VIOLATION_HASH_MISMATCH, result.getViolationType());
        }

        @Test
        @DisplayName("verifyChain detects tampered hash in genesis record")
        void verifyChain_GenesisHashTampered_ReturnsBrokenHashMismatch() {
            AuditRecord r1 = auditService.saveRecord(new AuditRecord("LOGIN", "u1", "AUTH", "1", "{}", null, null, null));

            // Tamper hash directly in DB
            r1.setHash("badhash1234567890badhash1234567890badhash1234567890badhash12345678");
            repository.save(r1);

            ChainVerificationResult result = auditService.verifyChain();
            assertFalse(result.isIntact());
            assertEquals(ChainVerificationResult.STATUS_BROKEN, result.getStatus());
            assertEquals(r1.getId(), result.getFirstInconsistentRecordId());
            assertEquals(ChainVerificationResult.VIOLATION_HASH_MISMATCH, result.getViolationType());
        }

        @Test
        @DisplayName("verifyChain detects tampered event metadata in intermediate record")
        void verifyChain_IntermediateRecordMetadataTampered_ReturnsBrokenHashMismatch() {
            AuditRecord r1 = auditService.saveRecord(new AuditRecord("LOGIN", "u1", "AUTH", "1", "{}", null, null, null));
            AuditRecord r2 = auditService.saveRecord(new AuditRecord("TRADE", "u1", "STOCK", "AAPL", "{\"qty\":10}", null, null, null));
            AuditRecord r3 = auditService.saveRecord(new AuditRecord("LOGOUT", "u1", "AUTH", "1", "{}", null, null, null));

            // Tamper r2's resourceId directly in DB
            r2.setResourceId("GOOG");
            repository.save(r2);

            ChainVerificationResult result = auditService.verifyChain();
            assertFalse(result.isIntact());
            assertEquals(ChainVerificationResult.STATUS_BROKEN, result.getStatus());
            assertEquals(r2.getId(), result.getFirstInconsistentRecordId());
            assertEquals(ChainVerificationResult.VIOLATION_HASH_MISMATCH, result.getViolationType());
        }

        @Test
        @DisplayName("verifyChain detects tampered previousHash in intermediate record")
        void verifyChain_IntermediatePreviousHashTampered_ReturnsBrokenPreviousHashMismatch() {
            AuditRecord r1 = auditService.saveRecord(new AuditRecord("LOGIN", "u1", "AUTH", "1", "{}", null, null, null));
            AuditRecord r2 = auditService.saveRecord(new AuditRecord("TRADE", "u1", "STOCK", "AAPL", "{\"qty\":10}", null, null, null));

            // Tamper r2's previousHash directly in DB
            r2.setPreviousHash("badprevhash1234567890badprevhash1234567890badprevhash123456789012");
            repository.save(r2);

            ChainVerificationResult result = auditService.verifyChain();
            assertFalse(result.isIntact());
            assertEquals(ChainVerificationResult.STATUS_BROKEN, result.getStatus());
            assertEquals(r2.getId(), result.getFirstInconsistentRecordId());
            assertEquals(ChainVerificationResult.VIOLATION_PREVIOUS_HASH_MISMATCH, result.getViolationType());
        }

        @Test
        @DisplayName("verifyChain detects record deletion in the middle of chain")
        void verifyChain_RecordDeletedInMiddle_ReturnsBrokenPreviousHashMismatch() {
            AuditRecord r1 = auditService.saveRecord(new AuditRecord("LOGIN", "u1", "AUTH", "1", "{}", null, null, null));
            AuditRecord r2 = auditService.saveRecord(new AuditRecord("TRADE", "u1", "STOCK", "AAPL", "{\"qty\":10}", null, null, null));
            AuditRecord r3 = auditService.saveRecord(new AuditRecord("LOGOUT", "u1", "AUTH", "1", "{}", null, null, null));

            // Delete r2 from DB
            repository.delete(r2);

            ChainVerificationResult result = auditService.verifyChain();
            assertFalse(result.isIntact());
            assertEquals(ChainVerificationResult.STATUS_BROKEN, result.getStatus());
            assertEquals(r3.getId(), result.getFirstInconsistentRecordId());
            assertEquals(ChainVerificationResult.VIOLATION_PREVIOUS_HASH_MISMATCH, result.getViolationType());
        }
    }

    @Nested
    @DisplayName("ArchiveRecords Tests")
    class ArchiveRecordsTests {

        @Test
        @DisplayName("archiveOldRecords sets status to ARCHIVED and nullifies fields")
        void archiveOldRecords_SetsArchivedAndNullifiesFields() {
            AuditRecord r1 = auditService.saveRecord(new AuditRecord("LOGIN", "user1", "AUTH", "id1", "{\"role\":\"USER\"}", null, null, null));
            AuditRecord r2 = auditService.saveRecord(new AuditRecord("LOGIN", "user2", "AUTH", "id2", "{\"role\":\"ADMIN\"}", null, null, null));
            
            Instant before = Instant.now().plusSeconds(10);
            int count = auditService.archiveOldRecords(before);
            
            assertEquals(2, count);
            
            List<AuditRecord> records = repository.findAll();
            for (AuditRecord r : records) {
                assertEquals(AuditRecord.ArchiveStatus.ARCHIVED, r.getStatus());
                assertNull(r.getPayload());
                assertNull(r.getPayloadMetadataJson());
                assertNull(r.getActorId());
                assertNull(r.getResourceType());
                assertNull(r.getResourceId());
            }
            
            // Verify chain still intact
            ChainVerificationResult result = auditService.verifyChain();
            assertTrue(result.isIntact());
        }
    }
}
