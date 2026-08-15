package com.schwab.audit.controller;

import com.schwab.audit.dto.AuditEventRequest;
import com.schwab.audit.dto.ChainVerificationResult;
import com.schwab.audit.model.AuditRecord;
import com.schwab.audit.service.AuditService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * REST controller providing endpoints for ingesting audit events,
 * querying audit records with filters/pagination, and verifying chain integrity.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Ingests a new audit event, appends it to the cryptographic hash chain, and returns the persisted record.
     *
     * @param request the validated audit event request
     * @return the persisted AuditRecord with HTTP status 201 Created
     */
    @PostMapping("/events")
    public ResponseEntity<AuditRecord> createEvent(@Valid @RequestBody AuditEventRequest request) {
        AuditRecord savedRecord = auditService.saveRecord(request.toAuditRecord());
        return ResponseEntity.status(HttpStatus.CREATED).body(savedRecord);
    }

    /**
     * Retrieves paginated audit records with optional multi-attribute filtering.
     *
     * @param actorId      optional actorId filter
     * @param resourceType optional resourceType filter
     * @param resourceId   optional resourceId filter
     * @param eventType    optional eventType filter
     * @param from         optional start timestamp filter (inclusive, ISO 8601)
     * @param to           optional end timestamp filter (inclusive, ISO 8601)
     * @param pageable     pagination and sorting parameters
     * @return a page of matching AuditRecord items with HTTP status 200 OK
     */
    @GetMapping("/events")
    public ResponseEntity<Page<AuditRecord>> getEvents(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Pageable pageable) {
        Page<AuditRecord> records = auditService.getRecords(actorId, resourceType, resourceId, eventType, from, to, pageable);
        return ResponseEntity.ok(records);
    }

    /**
     * Verifies the cryptographic integrity of the entire audit log chain from genesis.
     *
     * @return ChainVerificationResult detailing whether the chain is INTACT or BROKEN with HTTP status 200 OK
     */
    @GetMapping("/verify")
    public ResponseEntity<ChainVerificationResult> verifyChain() {
        ChainVerificationResult result = auditService.verifyChain();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/events/{id}/redact")
    public ResponseEntity<AuditRecord> redactField(@org.springframework.web.bind.annotation.PathVariable Long id, @Valid @RequestBody com.schwab.audit.dto.RedactRequest requestBody) {
        String field = requestBody.getField();
        if (field == null || field.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        AuditRecord updatedRecord = auditService.redactField(id, field);
        return ResponseEntity.ok(updatedRecord);
    }

    @PostMapping("/retention/run")
    public ResponseEntity<java.util.Map<String, Integer>> runRetention(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before) {
        int count = auditService.archiveOldRecords(before);
        return ResponseEntity.ok(java.util.Map.of("archivedCount", count));
    }

    @GetMapping("/export")
    public ResponseEntity<com.schwab.audit.dto.AuditExportBundle> export(@RequestParam String resourceId) {
        return ResponseEntity.ok(auditService.exportForResource(resourceId));
    }

    @GetMapping("/compliance/report")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> getComplianceReport(
            @RequestParam String resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        
        org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody stream = out -> {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(out)) {
                writer.print("ID,Timestamp,Event Type,Actor ID,Payload\r\n");
                
                Page<AuditRecord> records = auditService.getRecords(
                        null, "CLIENT_ACCOUNT", resourceId, null, from, to, Pageable.unpaged());
                
                for (AuditRecord record : records) {
                    String escapedPayload = "";
                    if (record.getPayload() != null) {
                        escapedPayload = "\"" + record.getPayload().replace("\"", "\"\"") + "\"";
                    }
                    writer.print(String.format("%d,%s,%s,%s,%s\r\n",
                            record.getId(),
                            record.getTimestamp(),
                            record.getEventType(),
                            record.getActorId(),
                            escapedPayload));
                }
            }
        };

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"compliance_report.csv\"")
                .body(stream);
    }
}