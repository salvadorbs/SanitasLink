import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';

import { tokenStore } from '@/api/tokenStore';
import { useAuthStore } from '@/features/auth/authStore';
import i18n from '@/lib/i18n';
import { queryClient } from '@/lib/queryClient';

import { resetAuthJar } from './handlers';
import { server } from './server';

const storage = new Map<string, string>();

const localStorageShim: Storage = {
  getItem: (key) => storage.get(key) ?? null,
  setItem: (key, value) => {
    storage.set(key, value);
  },
  removeItem: (key) => {
    storage.delete(key);
  },
  clear: () => {
    storage.clear();
  },
  key: (index) => Array.from(storage.keys())[index] ?? null,
  get length() {
    return storage.size;
  },
};

Object.defineProperty(globalThis, 'localStorage', {
  value: localStorageShim,
  configurable: true,
});

beforeAll(async () => {
  server.listen({ onUnhandledRequest: 'error' });
  // Pin the test language to English (jsdom/Playwright default), keeping the UI assertions
  // deterministic and independent of the host environment.
  await i18n.changeLanguage('en');
});

afterEach(() => {
  server.resetHandlers();
  tokenStore.clear();
  resetAuthJar();
  useAuthStore.setState({ status: 'loading', profile: null });
  queryClient.clear();
  cleanup();
});

afterAll(() => server.close());
