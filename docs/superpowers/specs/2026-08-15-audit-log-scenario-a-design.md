# Audit Log Service - Scenario A Design Spec

## 1. Objective
Build the Core Audit Log Service (Scenario A), a tamper-evident system that records an append-only history of events and guarantees that past records cannot be modified or deleted without detection.

## 2. Architecture & Tech Stack
- **Language/Framework**: Java, Spring Boot
- **Data Store**: Relational Database (H2 or PostgreSQL) accessed via Spring Data JPA.
- **Storage Strategy**: Relational tables provide natural sequential ordering via primary keys and native indexing for querying.

## 3. Data Model & Hashing Strategy
### Entity: `AuditRecord`
- `id`: Long, Auto-increment (Primary Key, ensures sequence)
- `eventType`: String (e.g., USER_LOGIN, RECORD_UPDATED)
- `actorId`: String (Who/What caused the event)
- `resourceType`: String (Type of resource affected)
- `resourceId`: String (Specific resource affected)
- `payload`: String (JSON payload)
- `timestamp`: Instant (Server-assigned for consistency)
- `previousHash`: String (SHA-256 hex string linking to the previous record)
- `hash`: String (SHA-256 hex string)

### Hashing Mechanism
- **Formula**: `SHA-256(eventType + actorId + resourceType + resourceId + payload + timestamp + previousHash)`
- **Genesis Block**: The very first record uses a constant `previousHash` of 64 zeros (`0000...0000`).
- **Concurrency**: Appending to the chain is serialized using database locks or a single-threaded queue to prevent chain forks or race conditions.

## 4. API Endpoints

### 4.1 Write API
- **Endpoint**: `POST /api/audit/events`
- **Behavior**: Accepts event details, assigns timestamp, computes hash linked to the last record, and synchronously saves to the database.
- **Response**: `201 Created` with the full saved record.

### 4.2 Query API
- **Endpoint**: `GET /api/audit/events`
- **Behavior**: Retrieves events with support for pagination (`page`, `size`).
- **Query Params**: `actorId`, `resourceType`, `resourceId`, `eventType`, `from` (timestamp), `to` (timestamp).
- **Response**: `Page<AuditRecord>`

### 4.3 Chain Verification API
- **Endpoint**: `GET /api/audit/verify`
- **Behavior**: Iterates through the entire chain from ID 1 to the latest record. Recalculates the hash for each step and compares it to the stored hash and `previousHash`.
- **Response (Intact)**: `{ "status": "INTACT" }`
- **Response (Broken)**: `{ "status": "BROKEN", "firstInconsistentRecordId": 123, "violationType": "HASH_MISMATCH" }`

## 5. Error Handling
- **400 Bad Request**: Missing required fields or invalid payload format. Standardized `ProblemDetail` (RFC 7807) response outlining validation failures.
- **401 Unauthorized**: Missing or invalid authentication credentials.
- **409 Conflict**: Returned if a concurrent write operation violates the sequential append constraint (client may retry).
- **500 Internal Server Error**: Catch-all for unhandled exceptions or data store unavailability.

## 6. Authentication
- **Mechanism**: Simple HTTP Basic Authentication using Spring Security.
- **Configuration**: A single system-level username and password will be configured via `application.properties` to protect all `/api/audit/**` endpoints.
