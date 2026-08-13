import { defineConfig, devices } from '@playwright/test';

// E2E for critical auth flows against a real backend + real PostgreSQL.
// Prerequisites:
//   1. docker compose up -d postgres
//   2. docker compose exec -T postgres psql -U db_owner -d sanitaslink_db \
//        -v ON_ERROR_STOP=1 -f ../docker/e2e-seed.sql
//   3. npm run build            (the preview server serves the production build)
//   4. npx playwright install chromium
// Run with: npm run test:e2e
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: 'npm run preview -- --port 4173 --strictPort',
      url: 'http://localhost:4173',
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
    },
    {
      command:
        'bash -c "./mvnw -q -DskipTests install && ./mvnw -pl app-module spring-boot:run -Dspring-boot.run.profiles=dev"',
      url: 'http://localhost:8080/v3/api-docs',
      reuseExistingServer: !process.env.CI,
      cwd: '../backend',
      timeout: 300_000,
      env: {
        SANITASLINK_CORS_ALLOWED_ORIGINS: 'http://localhost:4173',
      },
    },
  ],
});
