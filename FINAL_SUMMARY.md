# Final Engineering Summary

## 1. Plan & Rationale
The Audit Log Service was designed to provide a robust, tamper-evident data store. We chose **Spring Boot** and **H2** (file-based) for rapid prototyping, combined with a manual SHA-256 hash chaining mechanism to enforce immutability. 
- **Scenario A (Core):** We implemented a sequential cryptographic lock where each record's hash includes the `previousHash`. This ensures any historical modification invalidates the entire chain downstream.
- **Scenario B (Redaction & Retention):** We mathematically decoupled the raw payload from the overall chain hash by hashing individual JSON payload fields (`payloadMetadataJson`) with a cryptographic nonce. This allowed us to replace sensitive values with `"***REDACTED***"` while maintaining verification integrity. We also implemented a soft-delete retention policy that nullifies data but preserves the hash link, and a verifiable bulk export endpoint optimized with Data Projections to prevent OOM errors.
- **Scenario C (Compliance):** We clarified an ambiguous regulatory requirement into a scoped, synchronous CSV streaming endpoint, intentionally accepting the trade-off of synchronous execution over asynchronous job polling for the MVP.

## 2. Artifacts
- `AuditController`: REST API definitions for ingestion, querying, and verification.
- `AuditService`: Core business and cryptographic logic.
- `HashUtils`: SHA-256 hash generation and metadata parsing.
- `AuditRecordRepository`: Data layer with optimized DTO projections.
- `ComplianceReportTest` / `AuditServiceTest`: 79 comprehensive integration and unit tests.
- `ATTESTATION.md`: Development attestation.

## 3. Risks & Trade-offs
- **Concurrency Risk:** The `saveRecord` method is `synchronized` to prevent hash race conditions. This severely limits horizontal scalability. In a production environment, this would need to be replaced with a distributed lock (e.g., Redis) or a single-writer queue (e.g., Kafka).
- **Synchronous CSV Export:** Streaming a CSV synchronously will tie up Tomcat threads. If compliance reports grow to millions of rows, this will cause socket timeouts. A future iteration must move this to an async worker queue (e.g., Spring Batch + S3 upload).
- **H2 Database Limitations:** H2 is not suitable for high-throughput production workloads. Migration to PostgreSQL or an append-only datastore is required before launch.

## 4. Validation & Quality Gates
- **Subagent-Driven Development (SDD):** The entire application was built using a strictly gated process where independent Reviewer subagents evaluated the code of Implementer subagents.
- **Test-Driven:** 79 tests validate boundary conditions, RFC 7807 error formatting, database state, CSV escaping logic, and cryptographic chain breaks (testing both intact chains and intentionally tampered databases).
- **Security:** The system degrades safely. Corrupted JSON or unexpected plaintext payloads are gracefully caught without throwing 500 errors to the client, preventing information leakage, while still securely failing the chain verification.

## 5. Assumptions & Limitations
- We assumed `resourceType = "CLIENT_ACCOUNT"` is the strict identifier for regulatory compliance data.
- We assumed HTTP Basic Auth is sufficient for the prototype, though OAuth2/OIDC would be required for a real system.
- The redaction mechanism assumes JSON payloads. Plaintext payloads do not benefit from field-level redaction, though they remain securely chained.
