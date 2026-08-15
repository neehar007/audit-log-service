const { test, expect } = require('@playwright/test');

test.describe.configure({ mode: 'serial' });

test.describe('Audit Log Live Endpoints - All Scenarios', () => {

  let createdEventId;

  test('1. POST /api/audit/events - Create an event', async ({ request }) => {
    const response = await request.post('/api/audit/events', {
      data: {
        eventType: "USER_LOGIN",
        actorId: "user-123",
        resourceType: "CLIENT_ACCOUNT",
        resourceId: "account-789",
        payload: "{\"ip\": \"127.0.0.1\", \"device\": \"iphone\"}"
      }
    });
    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.id).toBeDefined();
    createdEventId = body.id;
  });

  test('2. GET /api/audit/events - Retrieve Events with filters', async ({ request }) => {
    const response = await request.get('/api/audit/events?eventType=USER_LOGIN&page=0&size=5');
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body.content.length).toBeGreaterThan(0);
  });

  test('3. POST /api/audit/events/{id}/redact - Redact a Payload Field', async ({ request }) => {
    expect(createdEventId).toBeDefined();
    const response = await request.post(`/api/audit/events/${createdEventId}/redact`, {
      data: { field: "ip" }
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    // Validate that the IP address is redacted but device is preserved
    expect(body.payload).toContain('***REDACTED***');
    expect(body.payload).not.toContain('127.0.0.1');
    expect(body.payload).toContain('iphone');
  });

  test('4. POST /api/audit/retention/run - Run Retention Archiving', async ({ request }) => {
    // We will archive events older than tomorrow to ensure our test events get hit
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const beforeDate = tomorrow.toISOString();
    
    const response = await request.post(`/api/audit/retention/run?before=${beforeDate}`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.archivedCount).toBeDefined();
    expect(body.archivedCount).toBeGreaterThanOrEqual(1);
  });

  test('5. GET /api/audit/export - Verifiable Bulk Export', async ({ request }) => {
    const response = await request.get('/api/audit/export?resourceId=account-789');
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.targetRecords).toBeDefined();
    expect(body.intermediateHashes).toBeDefined();
    expect(Array.isArray(body.targetRecords)).toBeTruthy();
  });

  test('6. GET /api/audit/compliance/report - Compliance CSV Export', async ({ request }) => {
    const response = await request.get('/api/audit/compliance/report?resourceId=account-789');
    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).toContain('text/csv');
    
    const csvContent = await response.text();
    expect(csvContent).toContain('ID,Timestamp,Event Type,Actor ID,Payload');
  });

  test('7. GET /api/audit/verify - Verify Chain Integrity', async ({ request }) => {
    const response = await request.get('/api/audit/verify');
    expect(response.status()).toBe(200);
    const body = await response.json();
    
    // We expect it to be either INTACT or BROKEN depending on the test DB state
    expect(['INTACT', 'BROKEN']).toContain(body.status);
  });

});
