# Audit Log Service

A tamper-evident, cryptographically verifiable audit log service built with Spring Boot and Java. 

This service ensures the integrity of recorded events by maintaining a cryptographic hash chain. Each new event's hash is calculated using its payload, metadata, and the SHA-256 hash of the immediately preceding event in the database. Any modification, deletion, or reordering of historical records will break the chain and can be detected instantly.

## Features
- **Tamper-Evident Storage**: Cryptographically linked event records.
- **Verification Engine**: API endpoint to scan the entire chain and identify tampered records.
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

*Response (Tampered):*
```json
{
  "status": "HASH_MISMATCH",
  "failingRecordId": 42
}
```

## Architecture & Cryptography

The service links records sequentially. The hash for each record is computed as:
`SHA-256(eventType + actorId + resourceType + resourceId + payload + timestamp_epoch_millis + previousHash)`

The very first record in the system (the genesis record) uses a `previousHash` consisting of 64 zeros. This sequential lock guarantees that altering a past record invalidates its hash, which in turn invalidates the `previousHash` pointer of the next record, making the tamper easily verifiable.
