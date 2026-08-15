const { request } = require('@playwright/test');

(async () => {
  const apiContext = await request.newContext({
    baseURL: 'http://localhost:8080',
    httpCredentials: { username: 'admin', password: 'secret-audit-key' }
  });
  
  console.log("Testing POST /api/audit/events...");
  const postRes = await apiContext.post('/api/audit/events', {
    data: {
      eventType: "USER_LOGIN",
      actorId: "user-123",
      resourceType: "CLIENT_ACCOUNT",
      resourceId: "account-789",
      payload: "{\"ip\": \"127.0.0.1\"}"
    }
  });
  console.log("POST Status:", postRes.status());
  if (postRes.status() === 200 || postRes.status() === 201) {
    console.log(await postRes.json());
  } else {
    console.log(await postRes.text());
  }
  
  console.log("\nTesting GET /api/audit/compliance/report...");
  const reportRes = await apiContext.get('/api/audit/compliance/report?resourceId=account-789');
  console.log("GET Status:", reportRes.status());
  console.log((await reportRes.body()).toString());

  console.log("\nTesting GET /api/audit/verify...");
  const verifyRes = await apiContext.get('/api/audit/verify');
  console.log("GET Status:", verifyRes.status());
  console.log(await verifyRes.json());
})();
