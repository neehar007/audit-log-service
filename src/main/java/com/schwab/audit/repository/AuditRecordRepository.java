package com.schwab.audit.repository;

import com.schwab.audit.model.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long>, JpaSpecificationExecutor<AuditRecord> {

    /**
     * Find the latest audit record ordered by ID descending.
     *
     * @return an Optional containing the latest AuditRecord, or empty if no records exist.
     */
    Optional<AuditRecord> findTopByOrderByIdDesc();

    java.util.List<AuditRecord> findByTimestampBeforeAndStatus(java.time.Instant before, AuditRecord.ArchiveStatus status);
}
