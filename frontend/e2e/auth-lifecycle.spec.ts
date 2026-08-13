import { type Page, expect, test } from '@playwright/test';

const USER_EMAIL = process.env.E2E_USER_EMAIL ?? 'e2e@studio.example';
const USER_PASSWORD = process.env.E2E_USER_PASSWORD ?? 'E2E-Password-123!';

const API_ORIGIN = process.env.E2E_API_ORIGIN ?? 'http://localhost:8080';

const REFRESH_PATH = '/api/v1/auth/refresh';

async function capturedCookieAttributes(page: Page, trigger: () => Promise<void>): Promise<string[]> {
  const capture = page.waitForResponse(
    (response) => response.url().includes('/api/v1/auth/') && response.request().method() === 'POST',
  );
  await trigger();
  const response = await capture;
  const setCookie = await response.headerValue('set-cookie');
  expect(setCookie, 'expected a Set-Cookie header on the auth response').toBeTruthy();
  // Split on ';' only: the Expires date value contains commas and would corrupt a plain split.
  return setCookie!.split(';').map((s) => s.trim());
}

async function refreshViaPageContext(page: Page): Promise<{ status: number }> {
  // The browser cookie jar is origin-agnostic w.r.t. ports, so the sl_refresh cookie set by the
  // API (localhost:8080) is sent on this same-site request.
  const response = await page.context().request.post(`${API_ORIGIN}${REFRESH_PATH}`);
  return { status: response.status() };
}

test.describe('auth session lifecycle (E2E)', () => {
  test('login persists as an HttpOnly refresh cookie and never lands in web storage', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(USER_EMAIL);
    await page.getByLabel('Password').fill(USER_PASSWORD);

    const cookies = await capturedCookieAttributes(page, () => page.getByRole('button', { name: 'Sign in' }).click());

    const refreshAttributes = cookies;
    expect(
      refreshAttributes.some((a) => a.startsWith('sl_refresh=')),
      'Set-Cookie must contain sl_refresh',
    ).toBe(true);
    expect(refreshAttributes).toContain('HttpOnly');
    expect(refreshAttributes).toContain('SameSite=Strict');
    expect(refreshAttributes).toContain('Path=/api/v1/auth');
    expect(refreshAttributes.some((a) => a === 'Secure' || a.startsWith('Secure='))).toBe(false);

    await expect(page).toHaveURL('/app');
    await expect(page.getByRole('heading', { name: 'Your profile' })).toBeVisible();

    expect(await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length }))).toEqual({
      local: 0,
      session: 0,
    });

    const documentCookies = await page.evaluate(() => document.cookie);
    expect(documentCookies).not.toContain('sl_refresh');
    expect(documentCookies).not.toContain('sl_jwt');
  });

  test('page reload transparently refreshes the session through the cookie', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(USER_EMAIL);
    await page.getByLabel('Password').fill(USER_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL('/app');

    const refreshAttributes = await capturedCookieAttributes(page, () => page.reload());
    expect(
      refreshAttributes.some((a) => a.startsWith('sl_refresh=')),
      'Set-Cookie must contain sl_refresh',
    ).toBe(true);
    expect(refreshAttributes).toContain('HttpOnly');

    await expect(page.getByRole('heading', { name: 'Your profile' })).toBeVisible();
    expect(await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length }))).toEqual({
      local: 0,
      session: 0,
    });
  });

  test('logout expires the refresh cookie and the session cannot be revived', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(USER_EMAIL);
    await page.getByLabel('Password').fill(USER_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL('/app');

    const refreshAttributes = await capturedCookieAttributes(page, () =>
      page.getByRole('button', { name: 'Sign out' }).click(),
    );
    expect(
      refreshAttributes.some((a) => a.startsWith('sl_refresh=')),
      'logout must expire the refresh cookie',
    ).toBe(true);
    expect(refreshAttributes.some((a) => a.startsWith('Max-Age=0') || a.startsWith('Expires='))).toBe(true);

    await expect(page).toHaveURL('/login');

    const { status } = await refreshViaPageContext(page);
    expect(status).toBe(401);
  });
});
