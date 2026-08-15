# Audit Log Service

A tamper-evident, cryptographically verifiable audit log service built with Spring Boot and Java. 

This service ensures the integrity of recorded events by maintaining a cryptographic hash chain. Each new event's hash is calculated using its payload, metadata, and the SHA-256 hash of the immediately preceding event in the database. Any modification, deletion, or reordering of historical records will break the chain and can be detected instantly.

## Features
- **Tamper-Evident Storage**: Cryptographically linked event records.
- **Verification Engine**: API endpoint to scan the entire chain and identify tampered records.
- **Structured Redaction**: Safely redact sensitive JSON payload fields without breaking the immutable hash chain.
- **Retention Archiving**: Soft-delete old data to reclaim space while gracefully preserving cryptographic continuity.
- **Verifiable Bulk Export**: Export target records with bridging hashes for independent off-system chain verification.
- **RESTful API**: Standardized JSON APIs with robust RFC 7807 error handling.
- **Pagination & Filtering**: Flexible querying for historical audit records.
- **Security**: Secured via HTTP Basic Authentication.

## Prerequisites
- Java 17 or higher
- Maven 3.6+

## Setup & Running Locally

1. **Clone the repository**
   ```bash
   git clone git@github.com:neehar007/audit-log-service.git
   cd audit-log-service
   ```

2. **Build the project**
   ```bash
   mvn clean install
   ```

3. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

The service will start on `http://localhost:8080`.

> **Note**: The application uses a local file-based H2 database for persistence. Data is saved to `./data/auditdb`.

## Authentication

All API endpoints are secured using HTTP Basic Authentication. 

**Default Credentials:**
- **Username:** `admin`
- **Password:** `secret-audit-key`

## API Endpoints

### 1. Record an Event
**POST** `/api/audit/events`

*Request:*
```bash
curl -X POST http://localhost:8080/api/audit/events \
  -u admin:secret-audit-key \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "USER_LOGIN",
    "actorId": "user-123",
    "resourceType": "AUTH_SYSTEM",
    "resourceId": "session-456",
    "payload": "{\"ip\": \"192.168.1.1\", \"browser\": \"Chrome\"}"
  }'
```

### 2. Retrieve Events
**GET** `/api/audit/events`

Supports pagination (`page`, `size`) and optional filters (`eventType`, `actorId`, `resourceType`, `resourceId`).

*Request:*
```bash
curl -X GET "http://localhost:8080/api/audit/events?eventType=USER_LOGIN&page=0&size=10" \
  -u admin:secret-audit-key
```

### 3. Verify Chain Integrity
**GET** `/api/audit/verify`

Scans the entire database to ensure cryptographic integrity.

*Request:*
```bash
curl -X GET http://localhost:8080/api/audit/verify \
  -u admin:secret-audit-key
```

*Response (Intact):*
```json
{
  "status": "INTACT"
}
```

### 4. Redact a Payload Field
**POST** `/api/audit/events/{id}/redact`

Replaces a sensitive JSON key's value with `"***REDACTED***"` in the payload, without breaking the hash chain.

*Request:*
```bash
curl -X POST http://localhost:8080/api/audit/events/1/redact \
  -u admin:secret-audit-key \
  -H "Content-Type: application/json" \
  -d '{"field": "ip"}'
```

### 5. Run Retention Archiving
**POST** `/api/audit/retention/run`

Strips large payloads and metadata from events older than a given date, preserving only the hashes needed to maintain chain integrity.

*Request:*
```bash
curl -X POST "http://localhost:8080/api/audit/retention/run?before=2024-01-01T00:00:00Z" \
  -u admin:secret-audit-key
```

### 6. Verifiable Bulk Export
**GET** `/api/audit/export`

Exports all records for a given `resourceId`, bundling them with the missing intermediate hashes required to bridge the gaps and independently verify the chain.

*Request:*
```bash
curl -X GET "http://localhost:8080/api/audit/export?resourceId=session-456" \
  -u admin:secret-audit-key
```

## Architecture & Cryptography

The service links records sequentially. To support **Structured Redaction**, the `payload` is mathematically decoupled from the overall hash:
1. When an event is ingested, each top-level key/value pair in the JSON payload is hashed independently using a random cryptographic nonce. This metadata is stored securely.
2. The overall record hash is computed as:
   `SHA-256(eventType + actorId + resourceType + resourceId + sorted_payload_field_hashes + timestamp_epoch_millis + previousHash)`

The very first record in the system (the genesis record) uses a `previousHash` consisting of 64 zeros. This sequential lock guarantees that altering a past record invalidates its hash, which in turn invalidates the `previousHash` pointer of the next record, making the tamper easily verifiable. Because payload fields are hashed individually, we can swap a field's value for `"***REDACTED***"` while maintaining verification integrity through the stored nonce.

---

*This project is powered by Antigravity :)*
