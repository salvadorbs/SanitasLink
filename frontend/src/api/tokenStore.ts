let accessToken: string | null = null;
let sessionGeneration = 0;

type SessionExpiredListener = () => void;
const sessionExpiredListeners = new Set<SessionExpiredListener>();

/**
 * In-memory access token holder. The token never touches localStorage, sessionStorage,
 * query keys, URLs or logs: anything persisted in the browser would widen the XSS exposure.
 */
export const tokenStore = {
  get(): string | null {
    return accessToken;
  },
  set(token: string | null): void {
    accessToken = token;
  },
  clear(): void {
    accessToken = null;
  },
  /**
   * Bumps the session generation: any refresh in flight when the session is invalidated
   * (logout or expiry) is discarded when it completes, so a stale refresh can never resurrect
   * the access token after the user logged out.
   */
  invalidateSession(): void {
    sessionGeneration += 1;
  },
  sessionGeneration(): number {
    return sessionGeneration;
  },
};

/** Registers a listener notified when the session can no longer be refreshed. */
export function subscribeSessionExpired(listener: SessionExpiredListener): () => void {
  sessionExpiredListeners.add(listener);
  return () => {
    sessionExpiredListeners.delete(listener);
  };
}

export function notifySessionExpired(): void {
  sessionExpiredListeners.forEach((listener) => listener());
}
