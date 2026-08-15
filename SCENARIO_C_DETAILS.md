# Scenario C: Compliance Reporting Plan

## 1. Requirement Clarification
**Original Product Request:** *"Regulators need to be able to audit access to client account data."*

### Identified Ambiguities & Questions
1. **What constitutes "access"?** 
   - Does this include only read operations (e.g., `ACCOUNT_VIEWED`), or does it also include modifications (`ACCOUNT_UPDATED`, `FUNDS_TRANSFERRED`)? 
2. **How is "client account data" identified?** 
   - We currently have `resourceType` and `resourceId` in our audit events. We can assume that client account data is tracked where `resourceType = "CLIENT_ACCOUNT"`.
3. **How will regulators consume this data?** 
   - Do they need a structured JSON API, a downloadable CSV report, or a web dashboard? Regulators often prefer spreadsheet-friendly formats like CSV.
4. **What is the data volume?** 
   - If an account has thousands of access logs per day, a synchronous API call for a 1-year report might time out.

### Assumptions Made for MVP
- "Access" includes both read and write events.
- Client accounts are identified by `resourceType = "CLIENT_ACCOUNT"`.
- A synchronous endpoint returning a CSV file is sufficient for the MVP.
- We will filter by a specific `resourceId` (Account ID) and a date range (`from` and `to`).

## 2. Clarified Requirement Statement
**"Provide a synchronous REST API endpoint that allows authorized compliance officers to download a CSV report of all audit events for a specific `resourceId` where `resourceType == 'CLIENT_ACCOUNT'`, filtered by a start and end date."**

## 3. Technical Design

### Scope Boundary
**In Scope:**
- A new `GET /api/audit/compliance/report` endpoint.
- Accepts parameters: `resourceId`, `from` (Instant), `to` (Instant).
- Validates the request and queries the database using existing repository methods.
- Streams the result back as a `text/csv` attachment to avoid large memory allocations for medium-sized reports.
- Includes core fields: `Timestamp`, `Event Type`, `Actor ID`, `Payload`.

**Out of Scope (Deferred):**
- Asynchronous report generation (e.g., email me when the report is ready). If the data exceeds millions of rows, synchronous streaming will tie up server threads. We accept this trade-off for the MVP.
- Complex payload parsing into separate CSV columns (the JSON payload will just be dumped into a single column).

## 4. Implementation Plan

1. **Create CSV Writer Utility**: We'll use a simple `PrintWriter` to stream data directly to the HTTP response `OutputStream`.
2. **Update `AuditController`**: Add the `/api/audit/compliance/report` endpoint returning a `ResponseEntity<StreamingResponseBody>`.
3. **Write Tests**: Create an integration test to generate some events and verify the CSV endpoint returns the correct headers and rows.
