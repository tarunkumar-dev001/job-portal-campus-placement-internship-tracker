import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e-fullstack',
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:4174',
    ...devices['Desktop Chrome'],
  },
  webServer: [
    {
      command: 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\\start-fullstack-e2e.ps1',
      url: 'http://127.0.0.1:8080/api/auth/login',
      reuseExistingServer: false,
      timeout: 180_000,
    },
    {
      command: 'npm.cmd run dev -- --host 127.0.0.1 --port 4174 --strictPort',
      url: 'http://127.0.0.1:4174',
      reuseExistingServer: false,
      timeout: 30_000,
    },
  ],
});
