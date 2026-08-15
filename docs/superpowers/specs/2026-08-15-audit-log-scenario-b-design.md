## Goal Description
We are extending the Audit Log Service (Scenario B) to support three complex data lifecycle requirements while maintaining cryptographic integrity:
1. **Retention Policy**: Soft-deleting/archiving old records without breaking the hash chain.
2. **Structured Redaction**: Masking sensitive fields in the payload without invalidating the original hash.
3. **Bulk Export**: Providing a self-contained, cryptographically verifiable bundle of records for a specific resource or actor.

## User Review Required
> [!IMPORTANT]
> The redaction and bulk export features require changes to how we compute the hash chain. Please review the design options below to ensure they align with your expectations for the prototype.

## Open Questions
> [!NOTE]
> 1. **Retention Trigger**: Should the retention archiving run automatically via a Spring `@Scheduled` background job, or should we expose a manual `POST /api/audit/retention/run` endpoint for easier testing in this prototype?
> 2. **Redaction Granularity**: Is it acceptable to restrict redaction to top-level JSON keys in the `payload`, or do we need deep nested JSON redaction? (Top-level is much simpler to implement for a prototype).

---

## Proposed Changes

### 1. Structured Redaction
To redact data without breaking the hash, we must decouple the raw payload string from the hash calculation.
#### [MODIFY] `src/main/java/com/schwab/audit/model/AuditRecord.java`
- Add a new column `Map<String, String> payloadFieldHashes`.
- When an event is saved, we parse the JSON `payload`. For each top-level key-value pair, we compute a salted hash: `hash(key + value + random_nonce)`. We store this hash in `payloadFieldHashes`.
- The main `AuditRecord` hash will now be computed using the `payloadFieldHashes` instead of the raw `payload` string.
#### [NEW] `POST /api/audit/events/{id}/redact`
- Endpoint accepts a JSON key to redact.
- It replaces the value of that key in the `payload` with `"***REDACTED***"`, but leaves the `payloadFieldHashes` untouched.
- Because the `HashUtils` relies on `payloadFieldHashes`, the overall chain hash remains valid.

### 2. Retention Policy
When records expire, we want to reclaim space and remove data, but we cannot delete the row entirely, as it holds the `hash` and `previousHash` that glue the chain together.
#### [MODIFY] `src/main/java/com/schwab/audit/model/AuditRecord.java`
- Add an `ArchiveStatus` enum (`ACTIVE`, `ARCHIVED`).
#### [MODIFY] `src/main/java/com/schwab/audit/service/AuditService.java`
- Add an `archiveOldRecords(Instant before)` method.
- For archived records, we nullify the `payload`, `actorId`, `resourceType`, and `resourceId` to save space and clear data.
- Modify the `verifyChain` logic: if a record is `ARCHIVED`, it skips the payload hash recalculation (since the data is gone) and only verifies that its `previousHash` correctly links to the preceding record, and that the next record links to its `hash`.

### 3. Bulk Export
If a recipient asks for all records for `resourceId = "ABC"`, they might get records 5, 10, and 15. To prove no records were tampered with, they need to verify the chain from 5 to 15.
#### [NEW] `src/main/java/com/schwab/audit/dto/AuditExportBundle.java`
- `List<AuditRecord> targetRecords`: The actual requested records.
- `Map<Long, String> intermediateHashes`: The ID-to-Hash mapping for all records that occurred *between* the exported records. This allows the recipient to re-hash the target records and bridge the gaps using the intermediate hashes, proving the chain from the first exported record to the last is unbroken.
#### [NEW] `GET /api/audit/export?resourceId={id}`
- Controller endpoint that builds and returns this `AuditExportBundle`.

---

## Verification Plan

### Automated Tests
- `mvn test` will be updated with:
  - **Redaction Tests**: Verify that redacting a field successfully masks the data in the database, but `verifyChain()` still returns `INTACT`.
  - **Retention Tests**: Run the archiver on old records, ensure payloads are dropped, and verify `verifyChain()` succeeds.
  - **Export Tests**: Request an export bundle, write a standalone test utility to iterate through the bundle's `targetRecords` and `intermediateHashes` to verify the continuous chain.

### Manual Verification
1. Create several events via `POST /events`.
2. Hit the redaction endpoint for one of the events.
3. Call `GET /events` to visually confirm the payload is redacted.
4. Call `GET /verify` to confirm the chain is still intact.
