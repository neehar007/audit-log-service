# Task 2: Retention Policy Implementation

## What you implemented
- Added `ArchiveStatus` enum (`ACTIVE`, `ARCHIVED`) to `AuditRecord`.
- Added `status` field to `AuditRecord` with default `ACTIVE`.
- Made `actorId`, `resourceType`, and `resourceId` fields nullable in `AuditRecord` to support archiving.
- Added `findByTimestampBeforeAndStatus` to `AuditRecordRepository`.
- Implemented `archiveOldRecords` in `AuditService` that sets the status to `ARCHIVED` and nullifies `payload`, `payloadMetadataJson`, `actorId`, `resourceType`, and `resourceId` to reclaim space.
- Modified `verifyChain` in `AuditService` to gracefully skip hashing for archived records while still verifying their previous hash link.
- Added `POST /api/audit/retention/run` endpoint to `AuditController` that triggers `archiveOldRecords(before)` and returns the archived count.

## What you tested and test results
- Fixed `AuditRecord` fields to ensure no `DataIntegrityViolation` occurred during nullification.
- Added `ArchiveRecordsTests` nested class in `AuditServiceTest` to test `archiveOldRecords`.
- Verified that `archiveOldRecords` nullifies the necessary fields and sets the `status` to `ARCHIVED`.
- Verified that `verifyChain` still correctly returns an intact chain over archived records.
- Executed `mvn test`: 76 tests passed.

## Files changed
- `src/main/java/com/schwab/audit/model/AuditRecord.java`
- `src/main/java/com/schwab/audit/repository/AuditRecordRepository.java`
- `src/main/java/com/schwab/audit/service/AuditService.java`
- `src/main/java/com/schwab/audit/controller/AuditController.java`
- `src/test/java/com/schwab/audit/service/AuditServiceTest.java`

## Self-review findings
- The changes adhere strictly to the task description.
- To nullify the `actorId`, `resourceType`, and `resourceId` fields when archiving, they had to be made nullable at the database level (`nullable = true`), which wasn't explicitly stated but was implicitly required to satisfy "nullifies the ... actorId ... to reclaim space".

## Any issues or concerns
- None.
