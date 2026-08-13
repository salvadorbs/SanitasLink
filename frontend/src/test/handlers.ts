import { HttpResponse, http } from 'msw';

export const ME_PROFILE = {
  id: 'user-1',
  email: 'medico@studio.example',
  firstName: 'Anna',
  lastName: 'Rossi',
  status: 'ACTIVE',
  officeId: 'office-1',
  roles: ['MEDICO_TITOLARE'],
  permissions: ['CORE_OFFICE_READ', 'PATIENT_CREATE'],
};

export const ACCESS_TOKEN = 'access-token-1';
export const REFRESHED_ACCESS_TOKEN = 'access-token-2';

const REFRESH_COOKIE_NAME = 'sl_refresh';

/**
 * Server-side cookie jar simulating the HttpOnly refresh cookie: login/refresh issue it through
 * Set-Cookie headers, refresh requires the current value and rotates it, logout expires it. In
 * jsdom/Node axios does not manage cookies, so tests that want to exercise the browser cookie
 * semantics attach the current value with the `Cookie` header (helpers below); when no cookie is
 * attached the jar state is used as fallback (the client would have sent it).
 */
interface AuthJar {
  refreshToken: string | null;
  accessToken: string | null;
}

let jar: AuthJar = {
  refreshToken: 'bootstrap-refresh-token',
  accessToken: ACCESS_TOKEN,
};

/** Resets the jar to a fresh "browser that already holds a valid session" state. */
export function resetAuthJar(): void {
  jar = { refreshToken: 'bootstrap-refresh-token', accessToken: ACCESS_TOKEN };
}

/** The name=value pair a test attaches with the Cookie header to mimic the browser. */
export function refreshCookiePair(value: string): string {
  return `${REFRESH_COOKIE_NAME}=${value}`;
}

/** The Set-Cookie header the server would send when issuing/rotating the cookie. */
export function refreshCookieSetHeader(value: string): string {
  return `${REFRESH_COOKIE_NAME}=${value}; Path=/api/v1/auth; HttpOnly; SameSite=Strict; Secure`;
}

/** The Set-Cookie header the server sends on logout to expire the cookie. */
export function expiredRefreshCookieSetHeader(): string {
  return `${REFRESH_COOKIE_NAME}=; Path=/api/v1/auth; HttpOnly; SameSite=Strict; Secure; Max-Age=0`;
}

function presentedRefreshToken(request: Request): string | null {
  const header = request.headers.get('cookie');
  if (header) {
    const match = header.match(/(?:^|;\s*)sl_refresh=([^;]+)/);
    if (match) {
      return match[1];
    }
  }
  return jar.refreshToken;
}

export const handlers = [
  http.post('*/api/v1/auth/login', () => {
    jar.refreshToken = 'login-refresh-token';
    jar.accessToken = ACCESS_TOKEN;
    return HttpResponse.json(
      {
        accessToken: ACCESS_TOKEN,
        expiresInSeconds: 900,
        tokenType: 'Bearer',
      },
      { headers: { 'Set-Cookie': refreshCookieSetHeader(jar.refreshToken) } },
    );
  }),
  http.post('*/api/v1/auth/refresh', ({ request }) => {
    const presented = presentedRefreshToken(request);
    if (presented === null || presented !== jar.refreshToken) {
      return new HttpResponse(null, { status: 401 });
    }
    jar.refreshToken = `rotated-${presented}`;
    jar.accessToken = REFRESHED_ACCESS_TOKEN;
    return HttpResponse.json(
      {
        accessToken: REFRESHED_ACCESS_TOKEN,
        expiresInSeconds: 900,
        tokenType: 'Bearer',
      },
      { headers: { 'Set-Cookie': refreshCookieSetHeader(jar.refreshToken) } },
    );
  }),
  http.post('*/api/v1/auth/logout', () => {
    jar.refreshToken = null;
    jar.accessToken = null;
    return new HttpResponse(null, {
      status: 204,
      headers: { 'Set-Cookie': expiredRefreshCookieSetHeader() },
    });
  }),
  http.get('*/api/v1/auth/me', (info) => {
    const authorization = info.request.headers.get('authorization');
    if (authorization !== `Bearer ${jar.accessToken}`) {
      return new HttpResponse(null, { status: 401 });
    }
    return HttpResponse.json(ME_PROFILE);
  }),
];
