const { defineConfig } = require("@playwright/test");
module.exports = defineConfig({
  testDir: "./tests",
  testMatch: "ui.spec.cjs",
  workers: 1,
  retries: 0,
  use: { baseURL: "http://127.0.0.1:9990", browserName: "chromium", screenshot: "only-on-failure" },
  webServer: {
    command: "python main.py",
    url: "http://127.0.0.1:9990/health",
    reuseExistingServer: false,
    timeout: 30000,
    env: { INDEX_ON_STARTUP: "0", GOOGLE_API_KEY: "", YANGCAREER_DB: "db/ui-test.sqlite3" }
  }
});
