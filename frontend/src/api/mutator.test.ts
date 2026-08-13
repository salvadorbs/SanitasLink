import { HttpResponse, http } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { mutator } from '@/api/mutator';
import { subscribeSessionExpired, tokenStore } from '@/api/tokenStore';
import { ME_PROFILE, REFRESHED_ACCESS_TOKEN, refreshCookiePair } from '@/test/handlers';
import { server } from '@/test/server';

describe('mutator', () => {
  it('sends the in-memory access token as a Bearer header and never persists it', async () => {
    let authorization: string | null = null;
    server.use(
      http.get('*/api/v1/auth/me', (info) => {
        authorization = info.request.headers.get('authorization');
        return HttpResponse.json(ME_PROFILE);
      }),
    );

    tokenStore.set('tok-abc');
    await mutator<typeof ME_PROFILE>({ url: '/api/v1/auth/me', method: 'GET' });

    expect(authorization).toBe('Bearer tok-abc');
    expect(localStorage.getItem('sanitaslink.access_token')).toBeNull();
    expect(sessionStorage.getItem('sanitaslink.access_token')).toBeNull();
  });

  it('does not attach a header when no token is in memory', async () => {
    let authorization: string | null = 'unset';
    server.use(
      http.get('*/api/v1/auth/me', (info) => {
        authorization = info.request.headers.get('authorization');
        return HttpResponse.json(ME_PROFILE);
      }),
    );

    await mutator<typeof ME_PROFILE>({ url: '/api/v1/auth/me', method: 'GET' });

    expect(authorization).toBeNull();
  });

  it('refreshes once on a 401 and retries the request with the new token', async () => {
    let refreshCalls = 0;
    server.use(
      http.post('*/api/v1/auth/refresh', () => {
        refreshCalls += 1;
        return HttpResponse.json({
          accessToken: REFRESHED_ACCESS_TOKEN,
          expiresInSeconds: 900,
          tokenType: 'Bearer',
        });
      }),
      http.get('*/api/v1/auth/me', (info) => {
        const authorization = info.request.headers.get('authorization');
        if (authorization !== `Bearer ${REFRESHED_ACCESS_TOKEN}`) {
          return new HttpResponse(null, { status: 401 });
        }
        return HttpResponse.json(ME_PROFILE);
      }),
    );

    tokenStore.set('expired-token');
    const result = await mutator<typeof ME_PROFILE>({ url: '/api/v1/auth/me', method: 'GET' });

    expect(refreshCalls).toBe(1);
    expect(result).toEqual(ME_PROFILE);
    expect(tokenStore.get()).toBe(REFRESHED_ACCESS_TOKEN);
  });

  it('coalesces concurrent 401 retries into a single refresh call', async () => {
    let refreshCalls = 0;
    let meCalls = 0;
    server.use(
      http.post('*/api/v1/auth/refresh', async () => {
        refreshCalls += 1;
        await new Promise((resolve) => setTimeout(resolve, 50));
        return HttpResponse.json({
          accessToken: REFRESHED_ACCESS_TOKEN,
          expiresInSeconds: 900,
          tokenType: 'Bearer',
        });
      }),
      http.get('*/api/v1/auth/me', (info) => {
        meCalls += 1;
        const authorization = info.request.headers.get('authorization');
        if (authorization !== `Bearer ${REFRESHED_ACCESS_TOKEN}`) {
          return new HttpResponse(null, { status: 401 });
        }
        return HttpResponse.json(ME_PROFILE);
      }),
    );

    tokenStore.set('expired-token');
    const [first, second] = await Promise.all([
      mutator<typeof ME_PROFILE>({ url: '/api/v1/auth/me', method: 'GET' }),
      mutator<typeof ME_PROFILE>({ url: '/api/v1/auth/me', method: 'GET' }),
    ]);

    expect(refreshCalls).toBe(1);
    expect(meCalls).toBe(4);
    expect(first).toEqual(ME_PROFILE);
    expect(second).toEqual(ME_PROFILE);
    expect(tokenStore.get()).toBe(REFRESHED_ACCESS_TOKEN);
  });

  it('notifies session expiry when the refresh fails and rejects the original request', async () => {
    server.use(
      http.post('*/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })),
      http.get('*/api/v1/auth/me', () => new HttpResponse(null, { status: 401 })),
    );

    const onExpired = vi.fn();
    const unsubscribe = subscribeSessionExpired(onExpired);
    tokenStore.set('expired-token');

    await expect(mutator({ url: '/api/v1/auth/me', method: 'GET' })).rejects.toBeDefined();

    expect(onExpired).toHaveBeenCalledTimes(1);
    expect(tokenStore.get()).toBeNull();
    unsubscribe();
  });

  it('never retries credential endpoints (login, refresh, logout) on 401', async () => {
    let loginCalls = 0;
    server.use(
      http.post('*/api/v1/auth/login', () => {
        loginCalls += 1;
        return new HttpResponse(null, { status: 401 });
      }),
    );

    tokenStore.set('whatever');
    await expect(
      mutator({ url: '/api/v1/auth/login', method: 'POST', data: { email: 'a@b.it', password: 'x' } }),
    ).rejects.toBeDefined();

    expect(loginCalls).toBe(1);
  });

  it('treats an empty access token from a successful refresh as session failure', async () => {
    server.use(
      http.post('*/api/v1/auth/refresh', () =>
        HttpResponse.json({
          accessToken: '',
          expiresInSeconds: 900,
          tokenType: 'Bearer',
        }),
      ),
      http.get('*/api/v1/auth/me', () => new HttpResponse(null, { status: 401 })),
    );

    const onExpired = vi.fn();
    const unsubscribe = subscribeSessionExpired(onExpired);
    tokenStore.set('expired-token');

    await expect(mutator({ url: '/api/v1/auth/me', method: 'GET' })).rejects.toBeDefined();

    expect(onExpired).toHaveBeenCalledTimes(1);
    expect(tokenStore.get()).toBeNull();
    unsubscribe();
  });

  it('discards a refresh that completes after the session was invalidated (logout race)', async () => {
    let refreshStarted = false;
    const refreshPayload = {
      accessToken: 'zombie-token',
      expiresInSeconds: 900,
      tokenType: 'Bearer',
    };
    let resolveRefresh!: (response: HttpResponse<typeof refreshPayload>) => void;
    const refreshGate = new Promise<HttpResponse<typeof refreshPayload>>((resolve) => {
      resolveRefresh = resolve;
    });
    server.use(
      http.post('*/api/v1/auth/refresh', () => {
        refreshStarted = true;
        return refreshGate;
      }),
      http.get('*/api/v1/auth/me', () => new HttpResponse(null, { status: 401 })),
    );

    tokenStore.set('expired-token');
    const pending = mutator({ url: '/api/v1/auth/me', method: 'GET' });
    await vi.waitFor(() => expect(refreshStarted).toBe(true));

    // Logout/session expiry happens while the refresh is still in flight.
    tokenStore.invalidateSession();
    resolveRefresh(HttpResponse.json(refreshPayload));

    await expect(pending).rejects.toBeDefined();
    // The zombie refresh must never resurrect the token: the pre-existing expired token stays
    // in memory unchanged, the fresh "zombie-token" from the discarded refresh is not applied.
    expect(tokenStore.get()).toBe('expired-token');
    expect(tokenStore.get()).not.toBe('zombie-token');
  });

  it('rotates the refresh cookie and rejects a replay of the rotated cookie', async () => {
    // Login issues the refresh cookie into the simulated jar.
    await mutator({
      url: '/api/v1/auth/login',
      method: 'POST',
      data: { email: 'a@b.it', password: 'x' },
    });

    // The browser would now send the issued cookie; attach it as jsdom never manages cookies.
    const issued = refreshCookiePair('login-refresh-token');
    const rotated = await mutator<{ accessToken: string }>({
      url: '/api/v1/auth/refresh',
      method: 'POST',
      headers: { Cookie: issued },
    });
    expect(rotated.accessToken).toBe(REFRESHED_ACCESS_TOKEN);

    // Presenting the already-rotated cookie again is rejected like a backend replay.
    await expect(
      mutator({ url: '/api/v1/auth/refresh', method: 'POST', headers: { Cookie: issued } }),
    ).rejects.toBeDefined();
  });
});
