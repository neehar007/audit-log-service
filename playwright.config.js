const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:8080',
    httpCredentials: { username: 'admin', password: 'secret-audit-key' },
    trace: 'on',
  },
});
