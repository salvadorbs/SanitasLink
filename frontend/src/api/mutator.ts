import axios, { type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';

import { notifySessionExpired, tokenStore } from './tokenStore';

// Endpoints that carry their own credential (body/cookie) must never trigger a refresh retry.
const AUTH_CREDENTIAL_ENDPOINTS = /^\/api\/v1\/auth\/(login|refresh|logout)$/;

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  // Required to send and receive the refresh token HttpOnly cookie.
  withCredentials: true,
});

// Only the Authorization Bearer header is sent; the tenant (office) is never sent by the client.
instance.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshPromise: Promise<boolean> | null = null;

/**
 * Refreshes the session through the HttpOnly cookie. Concurrent 401s share a single refresh
 * call; the result is awaited by every queued request. If the session was invalidated (logout
 * or expiry) while the refresh was in flight, the response is discarded so it can never
 * resurrect the access token after the user logged out.
 */
function requestRefresh(): Promise<boolean> {
  if (!refreshPromise) {
    const generation = tokenStore.sessionGeneration();
    refreshPromise = instance
      .post<{ accessToken?: string }>('/api/v1/auth/refresh')
      .then(({ data }) => {
        const token = data.accessToken;
        if (!token || token.length === 0) {
          // A success without a usable token is a failure: the server has no session to offer.
          tokenStore.clear();
          tokenStore.invalidateSession();
          notifySessionExpired();
          return false;
        }
        if (generation !== tokenStore.sessionGeneration()) {
          // The session was invalidated (logout/session expiry) while the refresh was in flight.
          return false;
        }
        tokenStore.set(token);
        return true;
      })
      .catch(() => {
        tokenStore.clear();
        tokenStore.invalidateSession();
        notifySessionExpired();
        return false;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

instance.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const isAuthCredentialRequest = config?.url != null && AUTH_CREDENTIAL_ENDPOINTS.test(config.url);
    if (
      axios.isAxiosError(error) &&
      error.response?.status === 401 &&
      config != null &&
      !config._retry &&
      !isAuthCredentialRequest
    ) {
      config._retry = true;
      if (await requestRefresh()) {
        const token = tokenStore.get();
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
          return instance(config);
        }
      }
    }
    return Promise.reject(error);
  },
);

/** Orval custom axios mutator used by the generated React Query client. */
export const mutator = <T>(config: AxiosRequestConfig, options?: AxiosRequestConfig): Promise<T> => {
  const source = axios.CancelToken.source();
  const promise = instance({ ...config, ...options, cancelToken: source.token }).then(
    ({ data }: AxiosResponse<T>) => data,
  );
  (promise as Promise<T> & { cancel?: () => void }).cancel = () => {
    source.cancel('Query was cancelled');
  };
  return promise;
};
