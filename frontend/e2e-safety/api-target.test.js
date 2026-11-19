import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { loadEnv } from 'vite';
import {
  configureFullstackApiTarget,
  FULLSTACK_API_BASE_URL,
} from '../e2e-fullstack/api-target.js';

test('pins an unset API target to the local full-stack backend', () => {
  const environment = {};

  assert.equal(configureFullstackApiTarget(environment), FULLSTACK_API_BASE_URL);
  assert.equal(environment.VITE_API_BASE_URL, 'http://127.0.0.1:8080/api');
});

test('accepts only the exact expected inherited API target', () => {
  const environment = { VITE_API_BASE_URL: FULLSTACK_API_BASE_URL };

  assert.equal(configureFullstackApiTarget(environment), FULLSTACK_API_BASE_URL);
  assert.equal(environment.VITE_API_BASE_URL, FULLSTACK_API_BASE_URL);
});

test('rejects an unexpected inherited local API target', () => {
  assert.throws(
    () => configureFullstackApiTarget({ VITE_API_BASE_URL: 'http://localhost:8080/api' }),
    /refuses an inherited VITE_API_BASE_URL/,
  );
});

test('rejects production and arbitrary external API targets', () => {
  for (const target of [
    'https://api.careerconnect.example/api',
    'https://staging.example.test/api',
  ]) {
    assert.throws(
      () => configureFullstackApiTarget({ VITE_API_BASE_URL: target }),
      /refuses an inherited VITE_API_BASE_URL/,
    );
  }
});

test('Vite dotenv values cannot override the forced process environment value', async () => {
  const previousApiBaseURL = process.env.VITE_API_BASE_URL;
  const envDirectory = await mkdtemp(path.join(os.tmpdir(), 'careerconnect-vite-env-'));

  try {
    delete process.env.VITE_API_BASE_URL;
    configureFullstackApiTarget(process.env);
    await writeFile(
      path.join(envDirectory, '.env.development.local'),
      'VITE_API_BASE_URL=https://external.example.test/api\n',
    );

    const loadedEnvironment = loadEnv('development', envDirectory, 'VITE_');
    assert.equal(loadedEnvironment.VITE_API_BASE_URL, FULLSTACK_API_BASE_URL);
  } finally {
    if (previousApiBaseURL === undefined) {
      delete process.env.VITE_API_BASE_URL;
    } else {
      process.env.VITE_API_BASE_URL = previousApiBaseURL;
    }
    await rm(envDirectory, { recursive: true, force: true });
  }
});
